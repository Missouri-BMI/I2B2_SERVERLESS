/**
  * Load the file in scala REPL to test HubClient
  * To Run the test, start the scala REPL and load the file
  * Enter the following commands using the command line
 *
 * #1. Start scala REPL
  * mvn scala:console -Dfullstacktrace=true -Dlogback.configurationFile=hub/service/src/scripts/resources/logback.xml
 *
 * #2. Load file in REPL:
  * :load <path-to-file>
 *
 * On shrine-webclient-dev-hub.catalyst.harvard.edu:6443
 * Ran 100 queries in 73803 milliseconds. 738 milliseconds per query. Average latency is 12810 milliseconds before SHRINE2020-569
 * Ran 100 queries in 68735 milliseconds. 687 milliseconds per query. Average latency is 7965 milliseconds with SHRINE2020-569
 *
 * On shrine-01.shrine-scaleup.catalyst.harvard.edu:6443
 * Ran 100 queries in 957071 milliseconds. 9570 milliseconds per query. Average latency is 565969 milliseconds before SHRINE2020-569
 * Ran 100 queries in 732124 milliseconds. 7321 milliseconds per query. Average latency is 450387 milliseconds with SHRINE2020-569
*/
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

import cats.effect.{CancelToken, IO}
import com.typesafe.config.ConfigValueFactory
import net.shrine.config.ConfigSource
import net.shrine.hub.data.client.CouldNotCompleteApiTaskButOKToRetryException
import net.shrine.hub.mom.ShrineMomClient
import net.shrine.protocol.version.v2.{Node, Query, QueryProgress, Researcher, Result, RunQueryAtHub, UpdateQueryAtQep}
import net.shrine.protocol.version.{Envelope, JsonText, NodeKey, NodeName, QueryId, UserDomainName}

import scala.collection.JavaConverters.seqAsJavaList
import scala.concurrent.duration.{DurationInt, DurationLong, FiniteDuration}

val configMap: Map[String, AnyRef] = Map(
  "shrine.nodeKey" -> "loadTestNode",
  "shrine.shrineHubBaseUrl" -> "https://shrine-01.shrine-scaleup.catalyst.harvard.edu:6443", //"https://shrine-webclient-dev-hub.catalyst.harvard.edu:6443",
  "shrine.problem.problemHandler" -> "net.shrine.problem.LoggingProblemHandler$",
  "shrine.hub.messagequeue.implementation" ->"net.shrine.messagequeueclient.MessageQueueWebClient"
)

//no type annotation used. This delays loading the class until inside this block
val hubServiceClient = ConfigSource.atomicConfig.configForBlock(configMap, "HubLoadTest") {
  import net.shrine.hub.data.client.HubHttpClient
  HubHttpClient // it is enough to bring the class into context here - with the tweaked config
}

val network = hubServiceClient.getNetworkIO.unsafeRunSync()

val loadTestNode = Node(
  name = new NodeName("Load Test Node"),
  key = new NodeKey("loadTestNode"),
  userDomainName = new UserDomainName("load.test.node.domain"),
  adminEmail = "david_walend@hms.harviard.edu"
).versionWithSendQueries(false)

val username = "shrine"
val password = "demouser"

val foundNode: Node = ConfigSource.atomicConfig.configForBlock(configMap, "HubLoadTest") {
  try {
    hubServiceClient.getNodeForKeyIO(new NodeKey("loadTestNode")).unsafeRunSync()
  } catch {
    case x:CouldNotCompleteApiTaskButOKToRetryException => hubServiceClient.putNodeIO(username, password,loadTestNode).unsafeRunSync()
  }
}

//test injecting a query and getting the expected number of results
val researcher:Researcher = Researcher.createWithRepeatableId(
  userName = "David",
  userDomainName = foundNode.userDomainName.underlying,
  nodeId = foundNode.id
)

val queryDefinition =
  """<query_definition>
    |	<query_name>Female@16:48:08</query_name>
    |	<query_timing>ANY</query_timing>
    |	<specificity_scale>0</specificity_scale>
    |	<use_shrine>1</use_shrine>
    |	<panel>
    |		<panel_number>1</panel_number>
    |		<panel_accuracy_scale>100</panel_accuracy_scale>
    |		<invert>0</invert>
    |		<panel_timing>ANY</panel_timing>
    |		<total_item_occurrences>1</total_item_occurrences>
    |		<item>
    |			<hlevel>3</hlevel>
    |			<item_name>Female</item_name>
    |			<item_key>\\ACT_DEMO\ACT\Demographics\Sex\Female\</item_key>
    |			<tooltip>Demographics \ Sex \ Female</tooltip>
    |			<class>ENC</class>
    |			<item_icon>LA</item_icon>
    |			<item_is_synonym>false</item_is_synonym>
    |		</item>
    |	</panel>
    |</query_definition>
    |""".stripMargin

val outputTypes =
  """<result_output_list><result_output priority_index="0" name="patient_count_xml" />
    |</result_output_list>
    |""".stripMargin

//hack to prime MessageQueueService
ConfigSource.atomicConfig.configForBlock(configMap, "HubLoadTest") {
  ShrineMomClient.serviceIO.queuesIO.unsafeRunSync()
}

val expectedDownstreamNodeCount = 59 //3
val queriesPerMinute = 100 //10 //33 //100
val testTime = 1.minute
val queryCount:Int = (queriesPerMinute * testTime).toMinutes.toInt

val queryIdToStartTime = new ConcurrentHashMap[QueryId,Long]()
val queryIdToCount = new ConcurrentHashMap[QueryId,AtomicInteger]()
val queryIdToEndTime = new ConcurrentHashMap[QueryId,Long]()

def dispatchIO(envelope: Envelope,remainingAttempts:Int):IO[Boolean] = {
  ConfigSource.atomicConfig.configForBlock(configMap, "HubLoadTest") {
    envelope match {
      case Envelope(contentsType, _, contents, _) if contentsType == UpdateQueryAtQep.envelopeType =>
        IO(UpdateQueryAtQep.tryRead(new JsonText(contents)).get).
          flatMap(_ => IO(true))
      case Envelope(contentsType, _, contents, _) if contentsType == Result.envelopeType => //todo remove with SHRINE2020-1365
        IO(Result.tryRead(new JsonText(contents)).get).
          map { result =>
            if (result.status.isFinal) {
              val counter = queryIdToCount.get(result.queryId)
              val count = counter.incrementAndGet()
              if (count >= expectedDownstreamNodeCount) {
                queryIdToEndTime.putIfAbsent(result.queryId, System.currentTimeMillis())
              }
            }
          }.flatMap(_ => IO(true))
    }
  }
}

val stopper: CancelToken[IO] = ConfigSource.atomicConfig.configForBlock(configMap, "HubLoadTest") {
  ShrineMomClient.receiveUntilStop(IO(foundNode.momQueueName),dispatchIO)
}

def createQuery: QueryProgress = {
  Query.create(
    queryDefinitionXml = queryDefinition,
    outputTypesXml = outputTypes,
    queryName = "Load Test Query",
    nodeOfOriginId = foundNode.id,
    researcherId = researcher.id,
    topicId = topic.id,
  ).withStatus(QueryStatuses.SentToHub)
}

val startTime = System.currentTimeMillis().milliseconds
val querySchedule: Seq[FiniteDuration] = (0 until queryCount).map(i => (i * testTime/queryCount) + startTime )

for(nextTime <- querySchedule) {
  ConfigSource.atomicConfig.configForBlock(configMap, "HubLoadTest") {
    val now = System.currentTimeMillis().milliseconds
    if ((nextTime - now).toMillis > 0) Thread.sleep((nextTime - now).toMillis)
    else Thread.`yield`()

    val query = createQuery
    val queryLaunchTime = System.currentTimeMillis().milliseconds
    queryIdToStartTime.putIfAbsent(query.id, queryLaunchTime.toMillis)
    queryIdToCount.putIfAbsent(query.id, new AtomicInteger(0))
    ConfigSource.atomicConfig.configForBlock(configMap, "HubLoadTest") {
      ShrineMomClient.sendToHubIO(
        subjectId = query.id,
        envelopeContents = RunQueryAtHub(query, researcher),
        envelopeContentsCompanion = RunQueryAtHub,
        logString = s"query command ${query.id}"
      ).unsafeRunSync()
    }
  }
}

do {
  Thread.sleep(1000L)
} while (!queryIdToEndTime.keySet().containsAll(queryIdToStartTime.keySet()))

val endTime = System.currentTimeMillis().milliseconds

stopper.unsafeRunSync

import scala.collection.JavaConverters.mapAsScalaConcurrentMap

val averageLatencyMillis: Long = mapAsScalaConcurrentMap(queryIdToEndTime).foldLeft(0L){
  case (soFar,(queryId,endTime)) => soFar + (endTime - queryIdToStartTime.get(queryId))
}/queryCount

val timePerQuery: FiniteDuration = (endTime - startTime)/queryCount

println(s"Ran $queryCount queries in ${(endTime - startTime).toMillis} milliseconds. ${timePerQuery.toMillis} milliseconds per query. Average latency is $averageLatencyMillis milliseconds")
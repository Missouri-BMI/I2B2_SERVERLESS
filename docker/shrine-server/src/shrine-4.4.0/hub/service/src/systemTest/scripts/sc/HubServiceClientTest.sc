/**
  * Load the file in scala REPL to test HubClient
  * To Run the test, start the scala REPL and load the file
  * Enter the following commands using the command line

  * #1. Start scala REPL
  * mvn scala:console

  * #2. Load file in REPL:
  * :load <path-to-file>
*/
import net.shrine.config.ConfigSource
import net.shrine.hub.mom.ShrineMomClient
import net.shrine.messagequeueservice.{Message, Queue}
import net.shrine.protocol.version.v1.{Node, Result, ResultProgress}
import net.shrine.protocol.version.{Envelope, JsonText, MomQueueName, NodeId, NodeKey, NodeName, QueryId, UserDomainName}

import scala.concurrent.duration.DurationInt

val configMap: Map[String, String] = Map(
  "shrine.shrineHubBaseUrl" -> "https://shrine-dev1.catalyst:6443"
)

//no type annotation used. This delays loading the class until inside this block
val hubServiceClient = ConfigSource.atomicConfig.configForBlock(configMap, "HubWebClientTest") {
  import net.shrine.hub.data.client.HubHttpClient
  HubHttpClient // it is enough to bring the class into context here
}

val network = hubServiceClient.getNetworkIO.unsafeRunSync()

val expectedNode = Node(
  name = new NodeName("Test Node"),
  key = new NodeKey("testNode"),
  userDomainName = new UserDomainName("test.node.domain"),
  adminEmail = "david_walend@hms.harviard.edu"
).versionWithSendQueries(false)

val username = "shrine"
val password = "demouser"
val nodePut = hubServiceClient.putNodeIO(username, password,expectedNode).unsafeRunSync()
assert(nodePut == expectedNode)

val updatedNode = nodePut.versionWithNodeName("Name with a .")

val updatedNodePut = hubServiceClient.putNodeIO(username,password,updatedNode).unsafeRunSync()
assert(updatedNodePut == updatedNode)

val updatedNodeGot: Node = hubServiceClient.getNodeForKeyIO(updatedNode.key).unsafeRunSync()

//test injecting a result
val messageQueueService = ConfigSource.atomicConfig.configForBlock(configMap, "HubWebClientTest") {
  import net.shrine.messagequeueservice.MessageQueueService
  ShrineMomClient.serviceIO
}

val queueName: MomQueueName = updatedNodeGot.momQueueName

val queue: Queue = messageQueueService.createQueueIfAbsentIO(queueName.underlying).unsafeRunSync()

val expectedResult = ResultProgress(
  queryId = new QueryId(1),
  adapterNodeId = new NodeId(2),
  adapterNodeName = new NodeName("test adapter")
)
val envelope = Envelope(Result.envelopeType,expectedResult.queryId.underlying,expectedResult.asJsonText.underlying)

messageQueueService.sendIO(envelope.asJsonText.underlying,queue).unsafeRunSync()

import scala.language.postfixOps
val shouldBeMessage = messageQueueService.receiveIO(queue,30 seconds).unsafeRunSync()
shouldBeMessage.fold(assert(false,"Should be a message")){ message: Message =>
  val received = Result.tryRead(new JsonText(message.contents)).get
  assert(received == expectedResult)
}

val shouldBeNoMessage = messageQueueService.receiveIO(queue,1 second).unsafeRunSync()

assert(shouldBeNoMessage.isEmpty)

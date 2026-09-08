package net.shrine.protocol.version.v2

import net.shrine.problem.{JsonProblemDigest, TestProblem}
import net.shrine.protocol.version.v2.querydefinition.QueryDefinitionTest
import net.shrine.protocol.version.{DateStamp, EnvelopeContents, ItemVersion, JsonText, MomQueueName, NodeId, NodeKey, NodeName, QueryId, ResearcherId, ResultId, ShrineVersion, UserDomainName, UserName}
import net.shrine.util.ShouldMatchersForJUnit
import org.scalatest.Assertion

import scala.io.Source
import scala.util.Try


object V2JsonTest extends ShouldMatchersForJUnit {
  val staticUserDomainName = new UserDomainName("testUserDomainName")
  val staticUserName = new UserName("testResearcherName")
  val staticQueryId = 0
  val staticNodeId = new NodeId(0)
  val staticResultId = new ResultId(0)
  val staticResearcherId = new ResearcherId(0)
  val staticDateStamp = new DateStamp(0)
  val staticVersionInfo: VersionInfo = VersionInfo(
    protocolVersion = versionId,
    shrineVersion = ShrineVersion.current,
    itemVersion = ItemVersion.one,
    createDate = staticDateStamp,
    changeDate = staticDateStamp
  )
  val staticNode = new Node(
    id = staticNodeId,
    name = new NodeName("testNode"),
    key = new NodeKey("testNode"),
    userDomainName = new UserDomainName("testnode.domain"),
    adminEmail = "email@foo",
    versionInfo = staticVersionInfo,
    momQueueName = MomQueueName("testMomQueueName"),
    understandsProtocol = versionId,
    momId = "testNode",
  )

  lazy val staticQueryProgress: QueryProgress = Query.create(
    id = staticQueryId,
    versionInfo = staticVersionInfo,
    queryDefinition = QueryDefinitionTest.menTreatedForHypertensionGotBetter,
    breakdownNames = Seq.empty,
    queryName = "queryName",
    queryNotes = None,
    queryFaved = false,
    nodeOfOriginId = staticNodeId,
    researcherId = staticResearcherId,
  )

  val staticQueryError: QueryError = QueryError(
    id = new QueryId(staticQueryId),
    versionInfo = staticVersionInfo,
    status = QueryStatus.HubError,
    queryDefinition = QueryDefinitionTest.menWithHypertension,
    breakdownNames = Seq.empty,
    queryName = "queryName",
    queryNotes = None,
    queryFaved = false,
    nodeOfOriginId = staticNodeId,
    researcherId = staticResearcherId,
    problemDigest = JsonProblemDigest(TestProblem())
  )


  val staticResearcher: Researcher = Researcher(
    id = staticResearcherId,
    versionInfo = staticVersionInfo,
    userName = staticUserName,
    userDomainName = staticUserDomainName,
    nodeId = staticNodeId
  )


  val obfuscatingParams = ObfuscatingParameters(binSize = 5,stdDev = 6.5,noiseClamp = 10,lowLimit = 10)

  val staticResultProgress: ResultProgress = ResultProgress(
    id = V2JsonTest.staticResultId,
    versionInfo = V2JsonTest.staticVersionInfo,
    queryId = new QueryId(3),//V2JsonTest.staticQueryProgress.id,
    adapterNodeId = V2JsonTest.staticNodeId,
    adapterNodeName = V2JsonTest.staticNode.name,
    status = ResultStatus.IdAssigned,
    statusMessage = Some("test status message"),
    crcQueryInstanceId = Some(0),
    resultMetadata = ResultMetadata(Option(obfuscatingParams))
  )


  def testRoundTrip[A <: EnvelopeContents](json: String, obj: A, trans: JsonText => Try[A]): Assertion = {
    testObjToStrippedJson(json, obj)
    testJsonToObj(json, obj, trans)
  }

  def testJsonToObj[A <: EnvelopeContents](json: String, obj: A, trans: JsonText => Try[A]): Assertion = {
    assertResult(obj){ trans(new JsonText(json)).get }
  }

  /*
     This function strips ALL whitespace from the incoming JSON, and then also removes
     all whitespace from the JSON representation of the incoming object.
     Warning: flattened JSON strings which differ only by whitespace inside string
     values will be deemed a match.
     Note also that the order of the fields in the JSON string matters.
   */
  def testObjToStrippedJson[A <: EnvelopeContents](json: String, obj: A): Assertion = {
    val flattenedLeft = json.replaceAll("\\s", "")
    val flattenedRight = obj.asJsonText.underlying.replaceAll("\\s", "")
    assertResult(flattenedLeft){ flattenedRight }
  }

  def readJsonFile(filename: String): String = {
    val source = Source.fromFile(Option(getClass.getResource(filename)).getOrElse{throw new IllegalArgumentException(s"$filename not found")}.getFile)
    try source.mkString finally source.close()
  }
}
package net.shrine.hub.data.store

import net.shrine.problem.ProblemNotYetEncoded
import net.shrine.protocol.version.ItemVersion.next
import net.shrine.protocol.version.v2.querydefinition.QueryDefinition
import net.shrine.protocol.version.v2.{Breakdowns, CountResult, Network, Node, NodeSystemSpec, ObfuscatingParameters, Query, Researcher, Result, ResultMetadata, ResultProgress, ResultStatus, UpdateNodeSystemSpec, UpdateResult, UpdateResultWithCount, UpdateResultWithProgress}
import net.shrine.protocol.version.{DateStamp, ItemVersion, JsonText, MomQueueName, NodeId, NodeKey, NodeName, QueryId, ResearcherId, ResultId, UserDomainName, UserName}
import org.junit.{After, Before, Test}
import org.scalatestplus.junit.AssertionsForJUnit

import scala.util.Try

/**
  * @author david
  * @since 1.26
  */
class HubDbTest extends AssertionsForJUnit {

  import cats.effect.unsafe.implicits.global
  val resultRow: ResultRow = ResultRow(
    id = new ResultId(0),
    protocolVersion = net.shrine.protocol.version.v2.versionId,
    itemVersion = new ItemVersion(1),
    createDate = DateStamp.now,
    changeDate = DateStamp.now,
    jsonText = new JsonText("Test Json Text"),
    queryId = new QueryId(2),
    adapterNodeId = new NodeId(3),
    status = ResultStatus.IdAssigned.statusName
  )

  @Test
  def testInsertResultRow():Unit = {
    HubDb.db.insertResultRowIO(resultRow).unsafeRunSync()

    assertResult(Seq(resultRow)){HubDb.db.selectAllResultRowsIO.unsafeRunSync()}
  }

  val resultInProgress: ResultProgress = ResultProgress(
    queryId = new QueryId(2),
    adapterNodeId = new NodeId(3),
    adapterNodeName = new NodeName("test node"),
    status = ResultStatus.ResultFromCRC,
    statusMessage = None,
    resultMetadata = ResultMetadata(Option(ObfuscatingParameters(5, 6.5, 10, 10)))
  )

  val crcResult: CountResult = resultInProgress.toCrcResult(count = 2050, crcQueryInstanceId = 3L, resultMetadata = ResultMetadata(Option(ObfuscatingParameters(5,6.5,10,10))), adapterTime = DateStamp.now)

  @Test
  def testFirstInsertResult():Unit = {
    HubDb.db.upsertResultIO(crcResult).unsafeRunSync()

    assertResult(Seq(Try(crcResult))){HubDb.db.selectAllResultsHistoryIO.unsafeRunSync()}
  }

  @Test
  def testFirstInsertResultWithBreakdowns():Unit = {
    val crcResultWithBreakdowns: CountResult = resultInProgress.toCrcResult(count = 2050,
      crcQueryInstanceId = 3L,
      breakdowns = Some(Breakdowns(Seq("colors" -> Seq(
            "red" -> 300,
            "blue" -> 600,
            "green" -> 30
          )))),
      resultMetadata = ResultMetadata(Option(ObfuscatingParameters(5,6.5,10,10))),
      adapterTime = DateStamp.now)

    HubDb.db.upsertResultIO(crcResultWithBreakdowns).unsafeRunSync()

    assertResult(Seq(Try(crcResultWithBreakdowns))){HubDb.db.selectAllResultsHistoryIO.unsafeRunSync()}
  }

  @Test
  def testResultsRoundTrip():Unit = {
    val error = ResultProgress(queryId = new QueryId(2),
      adapterNodeId = new NodeId(3),
      adapterNodeName = new NodeName("test node"),
      status = ResultStatus.ResultFromCRC,
      statusMessage = None,
      resultMetadata = ResultMetadata(Option(ObfuscatingParameters(5, 6.5, 10, 10)))).toCrcError(
      ProblemNotYetEncoded("Darn"),
      Option(5),None,DateStamp.now,
      resultMetadata = ResultMetadata(Option(ObfuscatingParameters(5, 6.5, 10, 10))))
    HubDb.db.upsertResultIO(error).unsafeRunSync()

    HubDb.db.upsertResultIO(crcResult).unsafeRunSync()

    assertResult(Seq(Try(error),Try(crcResult))){HubDb.db.selectAllResultsHistoryIO.unsafeRunSync()}
  }

  @Test
  def testSubsequentInsertResult(): Unit = {
    HubDb.db.upsertResultIO(resultInProgress).unsafeRunSync()
    val nextResult = crcResult
    HubDb.db.upsertResultIO(nextResult).unsafeRunSync()

    assertResult(Seq(Try(nextResult),Try(resultInProgress))){HubDb.db.selectAllResultsHistoryIO.unsafeRunSync()}
  }

  @Test
  def testDuplicateSubsequentInsertResult(): Unit = {
    HubDb.db.upsertResultIO(resultInProgress).unsafeRunSync()
    val nextResult = crcResult
    HubDb.db.upsertResultIO(nextResult).unsafeRunSync()
    HubDb.db.upsertResultIO(nextResult).unsafeRunSync()

    assertResult(Seq(Try(nextResult),Try(resultInProgress))){HubDb.db.selectAllResultsHistoryIO.unsafeRunSync()}
  }

  @Test
  def testDuplicateFirstInsertResult(): Unit = {
    HubDb.db.upsertResultIO(resultInProgress).unsafeRunSync()
    HubDb.db.upsertResultIO(resultInProgress).unsafeRunSync()

    assertResult(Seq(Try(resultInProgress))){HubDb.db.selectAllResultsHistoryIO.unsafeRunSync()}
  }

  @Test
  def testContradictorySubsequentInsertResult(): Unit = {
    HubDb.db.upsertResultIO(resultInProgress).unsafeRunSync()
    val nextResult = crcResult
    val differentNextResult = resultInProgress.copy(statusMessage = Some("Breaking this on purpose"))
    HubDb.db.upsertResultIO(nextResult).unsafeRunSync()

    val caught: ItemVersionRaceLostException[ResultId, Result, ResultRow] = intercept[ItemVersionRaceLostException[ResultId,Result,ResultRow]] {
      HubDb.db.upsertResultIO(differentNextResult).unsafeRunSync()
    }

    assertResult(differentNextResult)(caught.item)

    assertResult(Seq(Try(nextResult),Try(resultInProgress))){HubDb.db.selectAllResultsHistoryIO.unsafeRunSync()}
  }


  @Test
  def testQueryRoundTrip():Unit = {
    val query = Query.create(
      queryDefinition = QueryDefinition.standIn,
      breakdownNames = Seq.empty,
      queryName = "test query",
      queryNotes = Some("test query notes"),
      queryFaved = false,
      nodeOfOriginId = new NodeId(1),
      researcherId = new ResearcherId(2),
    )

    HubDb.db.upsertQueryIO(query).unsafeRunSync()

    assertResult(Seq(Try(query))){HubDb.db.selectAllQueriesIO.unsafeRunSync()}
  }

  @Test
  def testNodeRoundTrip():Unit = {
    val node = Node(
      name = new NodeName("testNodeName"),
      key = new NodeKey("testNodeKey"),
      userDomainName = new UserDomainName("testUserDomainName"),
      momId = "testNode",
      adminEmail = "email@foo",
    )

    HubDb.db.upsertNodeIO(node).unsafeRunSync()

    assertResult(Seq(Try(node))){HubDb.db.selectLatestNodesIO.unsafeRunSync()}
  }

  @Test
  def testSelectNode():Unit = {
    val node = Node(
      name = new NodeName("testNodeName"),
      key = new NodeKey("testNodeKey"),
      userDomainName = new UserDomainName("testUserDomainName"),
      momId = "testNode",
      adminEmail = "email@foo",
    )
    HubDb.db.upsertNodeIO(node).unsafeRunSync()

    val nextNode = node.copy(versionInfo = node.versionInfo.next(),sendQueries = false)
    HubDb.db.upsertNodeIO(nextNode).unsafeRunSync()

    assertResult(Seq(Try(nextNode))){HubDb.db.selectLatestNodesIO.unsafeRunSync()}
    assertResult(Some(nextNode)){HubDb.db.selectNodeIO(node.id).unsafeRunSync()}
    assertResult(Some(nextNode)){HubDb.db.selectNodeByKeyIO(node.key).unsafeRunSync()}

    assertResult(None){HubDb.db.selectNodeIO(new NodeId(0)).unsafeRunSync()}
    assertResult(None){HubDb.db.selectNodeByKeyIO(new NodeKey("notthere")).unsafeRunSync()}

  }

  @Test
  def testSelectNodeByKey():Unit = {
    val node = Node(
      name = new NodeName("testNodeName"),
      key = new NodeKey("testNodeKey"),
      userDomainName = new UserDomainName("testUserDomainName"),
      momId = "testNode1",
      adminEmail = "email@foo",
    )
    HubDb.db.upsertNodeIO(node).unsafeRunSync()

    val nextNode = node.copy(versionInfo = node.versionInfo.next(),sendQueries = false)
    HubDb.db.upsertNodeIO(nextNode).unsafeRunSync()

    assertResult(Seq(Try(nextNode))){HubDb.db.selectLatestNodesIO.unsafeRunSync()}
    assertResult(Some(nextNode)){HubDb.db.selectNodeIO(node.id).unsafeRunSync()}

    assertResult(Some(nextNode)){HubDb.db.selectNodeByKeyIO(node.key).unsafeRunSync()}

    assertResult(None){HubDb.db.selectNodeIO(new NodeId(0)).unsafeRunSync()}
    assertResult(None){HubDb.db.selectNodeByKeyIO(new NodeKey("notthere")).unsafeRunSync()}

  }

  @Test
  def testNodeSystemSpecRoundTrip(): Unit = {
    val node = Node(
      name = new NodeName("testNodeName"),
      key = new NodeKey("testNodeKey"),
      userDomainName = new UserDomainName("testUserDomainName"),
      momId = "testNode",
      adminEmail = "email@foo",
    )
    val nodeSystemSpec = NodeSystemSpec.create(node)

    val update = UpdateNodeSystemSpec(nodeSystemSpec)

    HubDb.db.updateNodeSystemSpecIO(update).unsafeRunSync()

    assertResult(Seq(Try(nodeSystemSpec))) {
      HubDb.db.selectLatestNodeSystemSpecsIO.unsafeRunSync()
    }
  }

  @Test
  def testNetworkRoundTrip():Unit = {
    val network = Network(
      networkName = "Test network",
      hubQueueName = MomQueueName("testHub"),
      adminEmail = "yourname@example.com",
      momId = "testNetwork",
      awsSqsConfig = None,
      kafkaConfig = None
    )

    HubDb.db.upsertNetworkIO(network).unsafeRunSync()

    assertResult(network){HubDb.db.selectTheNetworkIO.unsafeRunSync()}
  }

  @Test
  def testResearcherRoundTrip():Unit = {
    val researcher = Researcher(
      userName = new UserName("testResearcherName"),
      userDomainName = new UserDomainName("testUserDomainName"),
      nodeId = new NodeId(-14)
    )

    HubDb.db.upsertResearcherIO(researcher).unsafeRunSync()

    assertResult(Seq(researcher)){HubDb.db.selectAllResearchersIO.unsafeRunSync()}
  }

  @Test
  def testUpsertQueryUpdateResearcherWithNewDescriptionAndItemVersion1():Unit = {
    val node = Node.create(name = "testNode",key = "testNode",userDomainName = "demodata",adminEmail = "slarty_bartfast@hms.harvard.edu",momId = "testNode")
    val researcher = Researcher.create(userName = "shrine",userDomainName = "demodata",nodeId = node.id)
    val originalQuery = Query.create(
      queryDefinition = QueryDefinition.standIn,
      breakdownNames = Seq.empty,
      queryName = "Original Query",
      queryNotes = Some("Original Query, notes"),
      queryFaved = false,
      nodeOfOriginId = node.id,
      researcherId = researcher.id,
    )

    HubDb.db.upsertQueryUpdateAndResearcher(originalQuery,researcher).unsafeRunSync()
    val firstResearcher = HubDb.db.selectResearcherByIdIO(researcher.id).unsafeRunSync().get
    val firstQuery = HubDb.db.selectQueryIO(originalQuery.id).unsafeRunSync().get

    assertResult(researcher)(firstResearcher)
    assertResult(originalQuery)(firstQuery)

    val shrine300Query = Query.create(
      queryDefinition = QueryDefinition.standIn,
      breakdownNames = Seq.empty,
      queryName = "Original Query",
      queryNotes = Some("Original Query, notes"),
      queryFaved = false,
      nodeOfOriginId = node.id,
      researcherId = researcher.id,
    )

    HubDb.db.upsertQueryUpdateAndResearcher(shrine300Query,researcher).unsafeRunSync()
    val secondResearcher = HubDb.db.selectResearcherByIdIO(researcher.id).unsafeRunSync().get
    val secondQuery = HubDb.db.selectQueryIO(shrine300Query.id).unsafeRunSync().get

    assertResult(researcher)(secondResearcher)
    assertResult(shrine300Query)(secondQuery)

  }

  @Test
  def testUpdateResultSequence():Unit = {
    val idResult = ResultProgress(
      queryId = new QueryId(0L),
      adapterNodeId = new NodeId(1L),
      adapterNodeName = new NodeName("testNode"),
      resultMetadata = ResultMetadata(Option(ObfuscatingParameters(5, 6.5, 10, 10)))
    )

    val sentResult = idResult.withStatus(ResultStatus.SentToAdapter, resultMetadata = idResult.resultMetadata)
    val receivedResult = sentResult.withStatus(ResultStatus.ReceivedByAdapter, resultMetadata = sentResult.resultMetadata)
    val submittedResult = receivedResult.withStatus(ResultStatus.SubmittedToCRC, resultMetadata = receivedResult.resultMetadata)
    val crcResult = submittedResult.toCrcResult(count = 30, crcQueryInstanceId = 2L, resultMetadata = ResultMetadata(Option(ObfuscatingParameters(5,6.5,10,10))), adapterTime = DateStamp.now)

    val results: Seq[ResultProgress] = Seq(idResult,sentResult,receivedResult,submittedResult)

    val updates: Seq[UpdateResult] = results.map(r => UpdateResultWithProgress(r)).appended(UpdateResultWithCount(crcResult)).reverse

    import cats.implicits._
    updates.traverse(HubDb.db.updateResultIO).unsafeRunSync()

    val expected: Seq[Result] = results.appended(crcResult).sortBy(_.versionInfo.itemVersion.underlying).reverse
    val got: Seq[Result] = HubDb.db.selectAllResultRowsIO.unsafeRunSync().map(_.toResult.get)
    assertResult(expected)(got)
  }

  @Test
  def testUpdateResultSequenceConflictResolution():Unit = {
    val idResult = ResultProgress(
      queryId = new QueryId(0L),
      adapterNodeId = new NodeId(1L),
      adapterNodeName = new NodeName("testNode"),
      resultMetadata = ResultMetadata(Option(ObfuscatingParameters(5, 6.5, 10, 10)))
    )

    val sentResult = idResult.withStatus(ResultStatus.SentToAdapter, resultMetadata = idResult.resultMetadata) //sent twice, second time should be ignored

    val receivedResult = sentResult.withStatus(ResultStatus.ReceivedByAdapter, resultMetadata = sentResult.resultMetadata)
    val submittedResult = sentResult.withStatus(ResultStatus.SubmittedToCRC, resultMetadata = sentResult.resultMetadata) //jinked up

    val crcResult = submittedResult.toCrcResult(count = 30, crcQueryInstanceId = 2L, resultMetadata = ResultMetadata(Option(ObfuscatingParameters(5, 6.5, 10, 10))), adapterTime = DateStamp.now)

    val results: Seq[ResultProgress] = Seq(idResult, sentResult, receivedResult, sentResult, submittedResult)

    val updates: Seq[UpdateResult] = results.map(r => UpdateResultWithProgress(r)).appended(UpdateResultWithCount(crcResult))

    import cats.implicits._
    updates.traverse(HubDb.db.updateResultIO).unsafeRunSync()

    val expectedSubmittedResult = submittedResult.copy(versionInfo = submittedResult.versionInfo.copy(itemVersion = next(submittedResult.versionInfo.itemVersion)))
    val expectedCrcResult = crcResult.copy(versionInfo = crcResult.versionInfo.copy(itemVersion = next(crcResult.versionInfo.itemVersion)))

    val expected: Seq[Result] = Seq(idResult, sentResult, receivedResult, expectedSubmittedResult,expectedCrcResult).sortBy(_.versionInfo.itemVersion.underlying).reverse
    val got: Seq[Result] = HubDb.db.selectAllResultRowsIO.unsafeRunSync().map(_.toResult.get)
    assertResult(expected)(got)
  }

  @Before
  def beforeEach(): Unit = {
    HubDb.db.createTables()
  }

  @After
  def afterEach(): Unit = {
    HubDb.db.dropTables()
  }

}
package net.shrine.qep.querydb

import cats.effect.unsafe.implicits.global
import ch.qos.logback.classic.Level
import net.shrine.authentication.pm.User
import net.shrine.problem.{AbstractProblem, JsonProblemDigest, ProblemSources}
import net.shrine.protocol.version.v2.{Node, Query, QueryError, QueryStatus, Researcher, ResultMetadata, ResultStatus, UpdateQueryAtQepWithError}
import net.shrine.protocol.version.v2.querydefinition.QueryDefinition
import net.shrine.protocol.version.{NodeKey, NodeName, UserDomainName}
import net.shrine.protocol.i2b2.{Credential, DefaultBreakdownResultOutputTypes, I2b2Result, ResultOutputType}
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.{AfterEach, BeforeEach, Test}

/**
  * @author david 
  * @since 1/20/16
  */
class QepQueryDbTest {

  val qepQuery: QepQuery = QepQuery(
    networkId = 1L,
    userName = "ben",
    userDomain = "testDomain",
    queryName = "testQuery1",
    queryNotes = None,
    queryFaved = false,
    dateCreated = 0L,
    deleted = false,
    JSON = "",
    changeDate = System.currentTimeMillis(),
    status = QueryStatus.ReadyForAdapters.statusName
  )

  val secondQepQuery: QepQuery = QepQuery(
    networkId = 2L,
    userName = "dave",
    userDomain = "testDomain",
    queryName = "testQuery2",
    queryNotes = None,
    queryFaved = false,
    deleted = false,
    dateCreated = 1000L,
    JSON = "",
    changeDate = System.currentTimeMillis() - 1000,
    status = QueryStatus.ReadyForAdapters.statusName
  )

  val queryInsertDate: Long = System.currentTimeMillis() - 2000
  val thirdQepQuery: QepQuery = QepQuery(
    networkId = 3L,
    userName = "ben",
    userDomain = "testDomain",
    queryName = "testQuery3",
    queryNotes = Some("testQuery3, notes"),
    queryFaved = false,
    dateCreated = 2000L,
    deleted = false,
    JSON = "",
   changeDate = queryInsertDate,
    status = QueryStatus.ReadyForAdapters.statusName
  )

  val testNode: Node = Node(
    name = new NodeName("testNodeName"),
    key = new NodeKey("testNodeKey"),
    userDomainName = new UserDomainName("testUserDomainName"),
    adminEmail = "email@foo",
    momId = "testNode",
  )

  val researcherUserName: String = "ben"
  val researcherFullName: String  = researcherUserName

  val researcherUser: User = User(
    fullName = researcherUserName,
    username = researcherFullName,
    domain = "domain",
    credential = Credential("researcher's password",isToken = false),
    params = Map(),
    rolesByProject = Map()
  )

  @Test
  def testInsertQepQuery():Unit = {

    QepQueryDb.db.insertQepQuery(qepQuery)
    QepQueryDb.db.insertQepQuery(secondQepQuery)

    val results = QepQueryDb.db.selectAllQepQueries
    assertEquals(results,Seq(qepQuery,secondQepQuery))
  }

  @Test
  def testSelectQueryById():Unit = {

    QepQueryDb.db.insertQepQuery(qepQuery)
    QepQueryDb.db.insertQepQuery(secondQepQuery)

    val result1: Option[QepQuery] = QepQueryDb.db.selectQueryById(qepQuery.networkId)
    assertEquals(result1,Some(qepQuery))

    val result2: Option[QepQuery] = QepQueryDb.db.selectQueryById(secondQepQuery.networkId)
    assertEquals(result2,Some(secondQepQuery))

    val result3: Option[QepQuery] = QepQueryDb.db.selectQueryById(-1L)
    assertEquals(result3,None)
  }

  @Test
  def testSelectQepQueriesForUserWithLimit():Unit = {

    QepQueryDb.db.insertQepQuery(qepQuery)
    QepQueryDb.db.insertQepQuery(secondQepQuery)
    QepQueryDb.db.insertQepQuery(thirdQepQuery)

    val queryWithResultStatusQuery1 = QueryWithResultStatus(
      qepQuery.networkId,
      qepQuery.userName,
      qepQuery.userDomain,
      qepQuery.queryName,
      qepQuery.queryNotes,
      qepQuery.queryFaved,
      qepQuery.dateCreated,
      qepQuery.deleted,
      qepQuery.JSON,
      qepQuery.changeDate,
      qepQuery.status,
      isComplete = false,
      isQueryError = false,
      isResultsError = false,
      observed = false
    )

    val queryWithResultStatusQuery3 = QueryWithResultStatus(
      thirdQepQuery.networkId,
      thirdQepQuery.userName,
      thirdQepQuery.userDomain,
      thirdQepQuery.queryName,
      thirdQepQuery.queryNotes,
      thirdQepQuery.queryFaved,
      thirdQepQuery.dateCreated,
      thirdQepQuery.deleted,
      thirdQepQuery.JSON,
      thirdQepQuery.changeDate,
      thirdQepQuery.status,
      isComplete = false,
      isQueryError = false,
      isResultsError = false,
      observed = false
    )

    val totalQueryCount = 2

    val expectedResult = (totalQueryCount, Seq(queryWithResultStatusQuery3, queryWithResultStatusQuery1))

    val results = QepQueryDb.db.selectPreviousQueriesWithResultStatusIO("ben","testDomain",None,Some(100)).unsafeRunSync()
    assertEquals(results,expectedResult)
  }

  @Test
  def testSelectQepQueriesForUserWithSkipAndLimit():Unit = {

    QepQueryDb.db.insertQepQuery(qepQuery)
    QepQueryDb.db.insertQepQuery(secondQepQuery)
    QepQueryDb.db.insertQepQuery(thirdQepQuery)

    val queryWithResultStatus = QueryWithResultStatus(
      qepQuery.networkId,
      qepQuery.userName,
      qepQuery.userDomain,
      qepQuery.queryName,
      qepQuery.queryNotes,
      qepQuery.queryFaved,
      qepQuery.dateCreated,
      qepQuery.deleted,
      qepQuery.JSON,
      qepQuery.changeDate,
      qepQuery.status,
      isComplete = false,
      isQueryError = false,
      isResultsError = false,
      observed = false
    )

    val totalQueryCount = 2
    val expectedResult = (totalQueryCount, Seq(queryWithResultStatus))
    val results = QepQueryDb.db.selectPreviousQueriesWithResultStatusIO("ben","testDomain",Some(1),Some(100)).unsafeRunSync()

    assertEquals(results,expectedResult)
  }

  @Test
  def testSelectQepQueriesForUserWhenDuplicatesExists():Unit = {

    QepQueryDb.db.insertQepQuery(qepQuery)
    QepQueryDb.db.insertQepQuery(secondQepQuery)
    QepQueryDb.db.insertQepQuery(thirdQepQuery)
    QepQueryDb.db.insertQepQuery(thirdQepQuery)

    val queryWithResultStatusQuery1 = QueryWithResultStatus(
      qepQuery.networkId,
      qepQuery.userName,
      qepQuery.userDomain,
      qepQuery.queryName,
      qepQuery.queryNotes,
      qepQuery.queryFaved,
      qepQuery.dateCreated,
      qepQuery.deleted,
      qepQuery.JSON,
      qepQuery.changeDate,
      qepQuery.status,
      isComplete = false,
      isQueryError = false,
      isResultsError = false,
      observed = false
    )

    val queryWithResultStatusQuery3 = QueryWithResultStatus(
      thirdQepQuery.networkId,
      thirdQepQuery.userName,
      thirdQepQuery.userDomain,
      thirdQepQuery.queryName,
      thirdQepQuery.queryNotes,
      thirdQepQuery.queryFaved,
      thirdQepQuery.dateCreated,
      thirdQepQuery.deleted,
      thirdQepQuery.JSON,
      thirdQepQuery.changeDate,
      thirdQepQuery.status,
      isComplete = false,
      isQueryError = false,
      isResultsError = false,
      observed = false
    )

    val totalQueryCount = 2
    val expectedResult = (totalQueryCount, Seq(queryWithResultStatusQuery3, queryWithResultStatusQuery1))
    val results = QepQueryDb.db.selectPreviousQueriesWithResultStatusIO("ben","testDomain",None,Some(100)).unsafeRunSync()

    assertEquals(results,expectedResult)
  }
  
  val qepResultRowFromExampleCom: QueryResultRow = QueryResultRow(
    resultId = 10L,
    networkQueryId = 1L,
    instanceId = 100L,
    adapterNode = "example.com",
    size = 30L,
    startDate = Some(System.currentTimeMillis() - 60),
    endDate = Some(System.currentTimeMillis() - 30),
    status = ResultStatus.ResultFromCRC.statusName,
    statusMessage = None,
    resultMetadata = ResultMetadata(obfuscatingParameters = None),
    changeDate = System.currentTimeMillis()
  )

  @Test
  def testInsertQueryResultRow():Unit = {

    QepQueryDb.db.insertQepResultRow(qepResultRowFromExampleCom)

    val results = QepQueryDb.db.selectMostRecentQepResultRowsForIO(1L).unsafeRunSync()
    assertEquals(results,Seq(qepResultRowFromExampleCom))
  }

  val qepResultRowFromExampleComInThePast: QueryResultRow = qepResultRowFromExampleCom.copy(
    status = ResultStatus.ResultFromCRC.statusName,
    endDate = None,
    changeDate = qepResultRowFromExampleCom.changeDate - 40
  )

  val qepResultRowFromGeneralHospital: QueryResultRow = QueryResultRow(
    resultId = 100L,
    networkQueryId = 1L,
    instanceId = 100L,
    adapterNode = "generalhospital.org",
    size = 100L,
    startDate = Some(System.currentTimeMillis() - 60),
    endDate = Some(System.currentTimeMillis() - 30),
    status = ResultStatus.ResultFromCRC.statusName,
    statusMessage = None,
    resultMetadata = ResultMetadata(obfuscatingParameters = None),
    changeDate = System.currentTimeMillis()
  )

  @Test
  def testGetMostRecentResultRows():Unit = {

    QepQueryDb.db.insertQepResultRow(qepResultRowFromExampleComInThePast)
    QepQueryDb.db.insertQepResultRow(qepResultRowFromGeneralHospital)
    QepQueryDb.db.insertQepResultRow(qepResultRowFromExampleCom)

    val results = QepQueryDb.db.selectMostRecentQepResultRowsForIO(1L).unsafeRunSync()
    assertEquals(Set(qepResultRowFromExampleCom,qepResultRowFromGeneralHospital), results.toSet)
  }

  @Test
  def testGetMostRecentResultRowsOutOfOrder():Unit = {
    QepQueryDb.db.insertQepResultRow(qepResultRowFromGeneralHospital)
    QepQueryDb.db.insertQepResultRow(qepResultRowFromExampleCom)
    QepQueryDb.db.insertQepResultRow(qepResultRowFromExampleComInThePast)

    val results = QepQueryDb.db.selectMostRecentQepResultRowsForIO(1L).unsafeRunSync()
    assertEquals(results.toSet,Set(qepResultRowFromExampleCom,qepResultRowFromGeneralHospital))
  }

  def maleRow(breakdownChangeDate:Long): QepQueryBreakdownResultsRow = QepQueryBreakdownResultsRow(
    networkQueryId = 1L,
    adapterNode = "example.com",
    resultId = 100L,
    resultType = DefaultBreakdownResultOutputTypes.PATIENT_GENDER_COUNT_XML,
    dataKey = "male",
    value = 388,
    changeDate = breakdownChangeDate
  )

  def femaleRow(breakdownChangeDate:Long): QepQueryBreakdownResultsRow = QepQueryBreakdownResultsRow(
    networkQueryId = 1L,
    adapterNode = "example.com",
    resultId = 100L,
    resultType = DefaultBreakdownResultOutputTypes.PATIENT_GENDER_COUNT_XML,
    dataKey = "female",
    value = 390,
    changeDate = breakdownChangeDate
  )

  def unknownRow(breakdownChangeDate:Long): QepQueryBreakdownResultsRow = QepQueryBreakdownResultsRow(
    networkQueryId = 1L,
    adapterNode = "example.com",
    resultId = 100L,
    resultType = DefaultBreakdownResultOutputTypes.PATIENT_GENDER_COUNT_XML,
    dataKey = "unknown",
    value = 4,
    changeDate = breakdownChangeDate
  )

  @Test
  def testInsertBreakdownRows(): Unit = {
    val breakdownChangeDate = System.currentTimeMillis()

    val expectedMaleRow = maleRow(breakdownChangeDate)
    val expectedFemaleRow = femaleRow(breakdownChangeDate)
    val expectedUnknownRow = unknownRow(breakdownChangeDate)

    QepQueryDb.db.insertQueryBreakdown(maleRow(breakdownChangeDate))
    QepQueryDb.db.insertQueryBreakdown(femaleRow(breakdownChangeDate))
    QepQueryDb.db.insertQueryBreakdown(unknownRow(breakdownChangeDate))

    val results = QepQueryDb.db.selectAllBreakdownResultsRows
    assertEquals(results.toSet,Set(expectedMaleRow,expectedFemaleRow,expectedUnknownRow))
  }

  val breakdowns: Map[ResultOutputType, I2b2Result] = Map(
    DefaultBreakdownResultOutputTypes.PATIENT_GENDER_COUNT_XML -> I2b2Result(DefaultBreakdownResultOutputTypes.PATIENT_GENDER_COUNT_XML,Map(
      "male" -> 388,"female" -> 390,"unknown" -> 4)))
  /*todo replace test for breakdowns in SHRINE2020-1097

  @Test
  def testInsertQueryResultWithBreakdowns(): Unit = {
    val queryResultWithBreakdowns = queryResult.copy(breakdowns = breakdowns)

    QepQueryDb.db.insertQueryResult(2L,queryResultWithBreakdowns)

    val results = QepQueryDb.db.selectMostRecentQepResultsFor(2L)

    //Had a sporadic failure 8-9-2016 on David's lap top. Did not fail on rerun.
    assertEquals(results,Seq(queryResultWithBreakdowns))
  }

  @Test
  def testInsertQueryResultWithBreakdownsWithDifferentTimestamps(): Unit = {
    val queryResultWithBreakdowns = queryResult.copy(breakdowns = breakdowns)

    QepQueryDb.db.insertQueryResult(1L,queryResult)

    val breakdownChangeDate = System.currentTimeMillis()

    QepQueryDb.db.insertQueryBreakdown(maleRow(breakdownChangeDate))
    QepQueryDb.db.insertQueryBreakdown(femaleRow(breakdownChangeDate))
    QepQueryDb.db.insertQueryBreakdown(unknownRow(breakdownChangeDate+1))

    val results = QepQueryDb.db.selectMostRecentQepResultsFor(1L)

    //Had a sporadic failure 8-9-2016 on David's lap top. Did not fail on rerun.
    assertEquals(results,Seq(queryResultWithBreakdowns))
  }

  @Test
  def testInsertQueryResultWithProblem(): Unit = {
    val queryResultWithProblem = queryResult.copy(statusType = StatusType.Error,problem = Some(XmlProblemDigest.create(TestProblem())))

    QepQueryDb.db.insertQueryResult(2L,queryResultWithProblem)

    val results = QepQueryDb.db.selectMostRecentQepResultsFor(2L)

    assertEquals(results,Seq(queryResultWithProblem))
  }
*/
  @Test
  def testInsertQepQueryWithError():Unit = {
    val researcher = Researcher.createWithRepeatableId(
      userName = "ben",
      userDomainName = "testDomain",
      nodeId = testNode.id
    )
    val networkId = 3L
    val queryWithId: Query = Query.create(
      id = networkId,
      queryDefinition = QueryDefinition.standIn,
      breakdownNames = Seq.empty,
      queryName = "my query name",
      queryNotes  = Some("my query notes"),
      queryFaved = false,
      nodeOfOriginId = testNode.id,
      researcherId = researcher.id,
    )

    QepQueryDb.db.insertQueryIO(queryWithId,researcher).unsafeRunSync()

    case class ErrorRunningQuery(t: Throwable) extends AbstractProblem(ProblemSources.Qep){
      override def logLevel: Level = Level.WARN

      override def summary: String = "Error running query"
      override def description: String = "An error occurred when running query"
    }

    val problem = ErrorRunningQuery(new Exception("This is a query with an error"))

    val queryWithError: QueryError = queryWithId.toError(
      problem = JsonProblemDigest(problem)
    )
    val updateQueryWithError = UpdateQueryAtQepWithError(queryWithId,problem)

    QepQueryDb.db.updateQepQueryIO(updateQueryWithError).unsafeRunSync()

    val results: Option[FullQuery] = QepQueryDb.db.selectFullQuery(networkId).unsafeRunSync()
    val resultQuery:Query = results.get.query.v2Query

    val problemDigestRow = QueryProblemDigestRow(queryWithError)
    val expectedResultWrongTimes = FullQuery(Some(problemDigestRow), QepQuery(queryWithError, researcher))

    val expectedResult = expectedResultWrongTimes.copy(
      query = expectedResultWrongTimes.query.copy(
        changeDate = results.get.query.changeDate,
        JSON = expectedResultWrongTimes.query.v2Query.withChangeDate(resultQuery.versionInfo.changeDate).asJsonText.underlying)
    )
    assertEquals(expectedResult,results.get)
  }

  @Test
  def testSortQueryResults(): Unit = {
    def createQueryResultRow(id: Long, name: String, count: Long, status:String): QueryResultRow = {
      QueryResultRow(id, 0, 0, name, count, None, None, status, None, ResultMetadata(obfuscatingParameters = None),0)
    }
    val qr1 = createQueryResultRow(1, "A Node", 10, "Result From CRC")
    val qr2 = createQueryResultRow(2, "B Node", 15, "Result From CRC")
    val qr3 = createQueryResultRow(3, "C Node", 5, "Result From CRC")
    val qr4 = createQueryResultRow(4, "D Node", 20, "Result From CRC")
    val qrs = Seq(qr1, qr2, qr3, qr4)

    val sortedSeq1 = QueryResultRow.sortResults(qrs, "site.asc")
    assertEquals(qrs,sortedSeq1)

    val sortedSeq2 = QueryResultRow.sortResults(qrs, "site.desc")
    assertEquals(Seq(qr4, qr3, qr2, qr1),sortedSeq2)

    val sortedSeq3 = QueryResultRow.sortResults(qrs, "status.asc")
    assertEquals(Seq(qr3, qr1, qr2, qr4),sortedSeq3)

    val sortedSeq4 = QueryResultRow.sortResults(qrs, "status.desc")
    assertEquals(Seq(qr4, qr2, qr1, qr3),sortedSeq4)

    val qr5 = createQueryResultRow(5, "E Node", 0, ResultStatus.ErrorInShrine.statusName)
    val qr6 = createQueryResultRow(6, "F Node", 0, ResultStatus.IdAssigned.statusName)
    val qr7 = createQueryResultRow(7, "G Node", 0, ResultStatus.ErrorFromCrc.statusName)
    val qr8 = createQueryResultRow(8, "H Node", 0, ResultStatus.QueuedByCRC.statusName)
    val moreQrs = Seq(qr1, qr2, qr3, qr4, qr5, qr6, qr7, qr8)

    //results in ascending order of count, then queries in order of least progress to most, then errors
    val sortedSeq5 = QueryResultRow.sortResults(moreQrs, "status.asc")
    assertEquals(Seq(qr3, qr1, qr2, qr4, qr6, qr8, qr5, qr7),sortedSeq5)

    //results in descending order of count, then queries in order of most progress to least, then errors
    val sortedSeq6 = QueryResultRow.sortResults(moreQrs, "status.desc")
    assertEquals(Seq(qr4, qr2, qr1, qr3, qr8, qr6, qr7, qr5),sortedSeq6)
  }

  @BeforeEach
  def beforeEach(): Unit = {
    QepQueryDb.db.createTables()
  }

  @AfterEach
  def afterEach(): Unit = {
    QepQueryDb.db.dropTables()
  }
}
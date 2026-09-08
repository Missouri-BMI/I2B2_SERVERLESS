package net.shrine.adapter.dao

import net.shrine.adapter.dao.model.ShrineQuery
import net.shrine.adapter.i2b2Protocol.RawCrcRunQueryResponse
import net.shrine.dao.DateHelpers
import net.shrine.problem.{TestProblem, XmlProblemDigest}
import net.shrine.protocol.i2b2.DefaultBreakdownResultOutputTypes.{PATIENT_AGE_COUNT_XML, PATIENT_GENDER_COUNT_XML}
import net.shrine.protocol.i2b2.ResultOutputType.PATIENT_COUNT_XML
import net.shrine.protocol.i2b2.query.{I2b2QueryDefinition, Term}
import net.shrine.protocol.i2b2.{AuthenticationInfo, Credential, I2b2Result, QueryResult, ResultOutputType}
import net.shrine.util.ShouldMatchersForJUnit
import net.shrine.xml.XmlDateHelper
import org.h2.jdbc.{JdbcBatchUpdateException, JdbcSQLIntegrityConstraintViolationException}
import org.junit.{After, Before, Test}

import javax.xml.datatype.XMLGregorianCalendar

class AdapterQueryHistoryDbTest extends ShouldMatchersForJUnit {
  import cats.effect.unsafe.implicits.global
  private val authn = AuthenticationInfo("some-domain", "some-user", Credential("laskhdakslhd", isToken = false))

  private val queryDef1 = I2b2QueryDefinition("foo", Term("blarg", "blargName"))

  val now: Option[XMLGregorianCalendar] = Option(XmlDateHelper.now)

  private val count = 999L
  private val resultId = 1L
  private val instanceId = 2L
  private val desc = Some("xyz")
  private val message1 = "something bad happened"

  private val onlyAgeBreakdown = Map(PATIENT_AGE_COUNT_XML -> I2b2Result(PATIENT_AGE_COUNT_XML, Map("x" -> 1, "y" -> 2)))
  private val onlyGenderBreakdown = Map(PATIENT_GENDER_COUNT_XML -> I2b2Result(PATIENT_GENDER_COUNT_XML, Map("a" -> 123, "b" -> 456)))

  private val breakdownsByType = onlyAgeBreakdown ++ onlyGenderBreakdown

  private val networkQueryId1 = 123L

  private val masterId1 = "abc"

  private val countQueryResult = QueryResult(resultId, instanceId, Some(PATIENT_COUNT_XML), count, now, now, desc, QueryResult.StatusType.Finished, None)
  private val queuedResult = QueryResult(resultId,instanceId,Some(PATIENT_COUNT_XML),-1,now,now,desc,QueryResult.StatusType.Queued,None)

  private val errorQueryResult1 = QueryResult.errorResult(desc, message1,TestProblem())
  private val breakdownQueryResult1 = QueryResult(resultId, instanceId, Some(PATIENT_AGE_COUNT_XML), countQueryResult.setSize, now, now, desc, QueryResult.StatusType.Finished, None, breakdowns = onlyAgeBreakdown)
  private val breakdownQueryResult2 = QueryResult(resultId, instanceId, Some(PATIENT_GENDER_COUNT_XML), countQueryResult.setSize, now, now, desc, QueryResult.StatusType.Finished, None, breakdowns = onlyGenderBreakdown)

  private val countRunQueryResponse = RawCrcRunQueryResponse(networkQueryId1, now.get, authn.username, authn.domain, queryDef1, instanceId, RawCrcRunQueryResponse.toQueryResultMap(Seq(countQueryResult)))
  private val onlyBreakdownsRunQueryResponse = countRunQueryResponse.withResults(Seq(breakdownQueryResult1, breakdownQueryResult2))

  @Test
  def testInsertQueryHistory(): Unit = {

    val hasBeenRun = true

    AdapterQueryHistoryDb.db.insertQueryHistoryIO(
      masterId1,
      networkQueryId1,
      authn,
      queryDef1,
      hasBeenRun = hasBeenRun
    ).unsafeRunSync()

    val queryHistoryIO = AdapterQueryHistoryDb.db.selectQueryHistoryByQueryIdIO(networkQueryId1)

    val queryHistory: QueryHistory = queryHistoryIO.unsafeRunSync().get

    val shrineQuery: ShrineQuery = queryHistory.toShrineQuery

    assertResult(authn.domain)(shrineQuery.domain)
    assertResult(authn.username)(shrineQuery.username)
    assertResult(1)(shrineQuery.id)
    assertResult(masterId1)(shrineQuery.i2b2MasterQueryId)
    assertResult(queryDef1.name)(shrineQuery.name)
    assertResult(networkQueryId1)(shrineQuery.networkId)
    assertResult(I2b2QueryDefinition(queryDef1.name, queryDef1.expr))(shrineQuery.queryDefinition)

  }

  private def doInsertQueryResultsTest(response: RawCrcRunQueryResponse): (Map[ResultOutputType, Seq[Int]], Seq[QueryResultRowSlick]) = {

    val queryId: Int = AdapterQueryHistoryDb.db.insertQueryHistoryIO(masterId1, networkQueryId1, authn, queryDef1, hasBeenRun = true).unsafeRunSync()

    assertResult(1)(queryId)
    val resultIdsByType: Map[ResultOutputType, Seq[Int]] = AdapterQueryHistoryDb.db.insertQueryResultsIO(queryId, response.results).unsafeRunSync()

    val resultRows: Seq[QueryResultRowSlick] = AdapterQueryHistoryDb.db.selectAllQueryResultsByQueryId(queryId).unsafeRunSync()

    resultRows.foreach { resultRow =>
      assert(resultRow.timeElapsed.nonEmpty)
      assert(resultRow.lastUpdated > 0 )
      assertResult(queryId)(resultRow.queryId)
    }

    val countResultRows = AdapterQueryHistoryDb.db.selectCountResultsByQueryIdIO(networkQueryId1).unsafeRunSync()
    val errorResultRows = AdapterQueryHistoryDb.db.selectErrorResultsByQueryIdIO(networkQueryId1).unsafeRunSync()
    val breakdownResultRows = AdapterQueryHistoryDb.db.selectBreakdownResultsByQueryIdIO(networkQueryId1).unsafeRunSync()

    assert(countResultRows.isEmpty)
    assert(errorResultRows.isEmpty)
    assert(breakdownResultRows.isEmpty)

    (resultIdsByType, resultRows)
  }

  @Test
  def testInsertQueryResultsOnlyCount(): Unit = {

    val (resultIdsByType, resultRows) = doInsertQueryResultsTest(countRunQueryResponse)

    val resultRow: QueryResultRowSlick = resultRows.head

    assertResult(PATIENT_COUNT_XML)(resultRow.resultType)
    assertResult(QueryResult.StatusType.Finished)(resultRow.status)

    assertResult(Map(PATIENT_COUNT_XML -> Seq(resultRow.id)))(resultIdsByType)

    //Should fail due to foreign key constraint
    assertThrows[JdbcSQLIntegrityConstraintViolationException](AdapterQueryHistoryDb.db.insertQueryResultsIO(1234567, countRunQueryResponse.results).unsafeRunSync())
  }

  @Test
  def testSelectQueuedQueryIds(): Unit = {

    val queuedQueryIds1: Seq[QueryResultStatus] = AdapterQueryHistoryDb.db.selectQueuedQueries().unsafeRunSync()

    assert(queuedQueryIds1.isEmpty)
    val queryId = AdapterQueryHistoryDb.db.insertQueryHistoryIO(
      masterId1,
      networkQueryId1,
      authn, queryDef1,
      hasBeenRun = true
    ).unsafeRunSync()

    val response = RawCrcRunQueryResponse(networkQueryId1, now.get, authn.username, authn.domain, queryDef1, instanceId,
      RawCrcRunQueryResponse.toQueryResultMap(Seq(queuedResult)))

    AdapterQueryHistoryDb.db.insertQueryResultsIO(queryId, response.results).unsafeRunSync()

    val queuedQueries : Seq[QueryResultStatus] = AdapterQueryHistoryDb.db.selectQueuedQueries().unsafeRunSync()
    assert(queuedQueries.size == 1)
    val queuedQuery: QueryResultStatus = queuedQueries.head

    assertResult(authn.domain)(queuedQuery.domain)
    assertResult(authn.username)(queuedQuery.username)
    assertResult(QueryResult.StatusType.Queued.name)(queuedQuery.status)
    assertResult(networkQueryId1)(queuedQuery.networkQueryId)
    assert(queuedQuery.timestamp.before(DateHelpers.toTimestamp(XmlDateHelper.now)))
  }

  @Test
  def testInsertCountResult(): Unit =  {
    AdapterQueryHistoryDb.db.createTables()
    val now = Option(XmlDateHelper.now)

    val countRunQueryResponse = RawCrcRunQueryResponse(networkQueryId1, now.get, authn.username, authn.domain, queryDef1, instanceId, RawCrcRunQueryResponse.toQueryResultMap(Seq(countQueryResult)))

    val hasBeenRun = true

    val queryId = AdapterQueryHistoryDb.db.insertQueryHistoryIO(
      masterId1,
      networkQueryId1,
      authn,
      queryDef1,
      hasBeenRun = hasBeenRun
    ).unsafeRunSync()

    val resultIdsByType = AdapterQueryHistoryDb.db.insertQueryResultsIO(queryId, countRunQueryResponse.results).unsafeRunSync()
    val queryResultId = resultIdsByType(ResultOutputType.PATIENT_COUNT_XML).head

    val countResultsBefore = AdapterQueryHistoryDb.db.selectCountResultsByQueryIdIO(networkQueryId1).unsafeRunSync()
    assert(countResultsBefore.isEmpty)

    AdapterQueryHistoryDb.db.insertCountResultIO(queryResultId, countQueryResult.setSize + 2).unsafeRunSync()

    val countResultsAfter = AdapterQueryHistoryDb.db.selectCountResultsByQueryIdIO(networkQueryId1).unsafeRunSync().get

    assert(countResultsAfter.dateCreated > 0)
    assertResult(countQueryResult.setSize + 2)(countResultsAfter.obfuscatedValue)
    assertResult(resultId)(countResultsAfter.resultId)

    //Should fail due to foreign key constraint
   assertThrows[JdbcSQLIntegrityConstraintViolationException](AdapterQueryHistoryDb.db.insertCountResultIO(12345, countQueryResult.setSize + 2).unsafeRunSync())
  }

  @Test
  def testInsertErrorResult() : Unit = {

    val queryId = AdapterQueryHistoryDb.db.insertQueryHistoryIO(masterId1,
      networkQueryId1,
      authn,
      queryDef1,
      hasBeenRun = true
    ).unsafeRunSync()

    val response = countRunQueryResponse.withResults(Seq(errorQueryResult1))

    val resultIdsByType = AdapterQueryHistoryDb.db.insertQueryResultsIO(queryId, response.results).unsafeRunSync()
    val queryResultId = resultIdsByType(ResultOutputType.ERROR).head

    val pd = XmlProblemDigest.create(TestProblem())

    AdapterQueryHistoryDb.db.insertErrorResultIO(queryResultId, message1,pd.codec,pd.stampText,pd.summary,pd.description,pd.detailsXml).unsafeRunSync()

    val Seq(errorResultRow) = AdapterQueryHistoryDb.db.selectErrorResultsByQueryIdIO(networkQueryId1).unsafeRunSync()

    assertResult(message1)(errorResultRow.message)
    assertResult(queryResultId)(errorResultRow.resultId)

    //Should fail due to foreign key constraint
    assertThrows[JdbcSQLIntegrityConstraintViolationException](AdapterQueryHistoryDb.db.insertErrorResultIO(12345, "",pd.codec,pd.stampText,pd.summary,pd.description,pd.detailsXml).unsafeRunSync())
  }

  @Test
  def testInsertBreakdownResults(): Unit =  {
    val (resultIdsByType, _) = doInsertQueryResultsTest(onlyBreakdownsRunQueryResponse)

    resultIdsByType.keySet should equal(Set(PATIENT_AGE_COUNT_XML, PATIENT_GENDER_COUNT_XML))

    resultIdsByType.values.forall(_.size == 1) should be(true)

    AdapterQueryHistoryDb.db.insertBreakdownResultsIO(resultIdsByType, breakdownsByType, breakdownsByType.view.mapValues(_.mapValues(_ + 1)).toMap).unsafeRunSync()

    val breakdownResults: Seq[BreakdownResult] = AdapterQueryHistoryDb.db.selectBreakdownResultsByQueryIdIO(networkQueryId1).unsafeRunSync()

    val ageBreakdownRows = breakdownResults.filter(_.resultId == resultIdsByType(PATIENT_AGE_COUNT_XML).head)
    val genderBreakdownRows = breakdownResults.filter(_.resultId == resultIdsByType(PATIENT_GENDER_COUNT_XML).head)

    val ageRowsByName = ageBreakdownRows.map(row => (row.dataKey, row)).toMap
    assertResult(2)(ageRowsByName("x").obfuscatedCount)
    assertResult(3)(ageRowsByName("y").obfuscatedCount)


    val genderRowsByName = genderBreakdownRows.map(row => (row.dataKey, row)).toMap
    assertResult(124)(genderRowsByName("a").obfuscatedCount)
    assertResult(457)(genderRowsByName("b").obfuscatedCount)

    val updateException = intercept[JdbcBatchUpdateException] {
      //Should fail due to foreign key constraint
      AdapterQueryHistoryDb.db.insertBreakdownResultsIO(Map(PATIENT_AGE_COUNT_XML -> Seq(-1000)),
        breakdownsByType, breakdownsByType).unsafeRunSync()
    }
    assert(updateException.getMessage.contains("Referential integrity constraint violation"))
  }

  @Test
  def testRenameQuery(): Unit =  {
    AdapterQueryHistoryDb.db.insertQueryHistoryIO(masterId1, networkQueryId1, authn, queryDef1, hasBeenRun = true).unsafeRunSync()

    {
      val Some(query) = AdapterQueryHistoryDb.db.selectQueryHistoryByQueryIdIO(networkQueryId1).unsafeRunSync()

      assertResult(queryDef1.name)(query.queryName)
    }

    val newName = "zuh"

    AdapterQueryHistoryDb.db.renameQueryIO(networkQueryId1, newName).unsafeRunSync()

    val Some(renamedQuery) = AdapterQueryHistoryDb.db.selectQueryHistoryByQueryIdIO(networkQueryId1).unsafeRunSync()

    assertResult(newName)(renamedQuery.queryName)
    assertResult(1)(renamedQuery.id)
    assertResult(networkQueryId1)(renamedQuery.networkId)
    assertResult(authn.username)(renamedQuery.userName)
    assertResult(authn.domain)(renamedQuery.userDomain)
    assert(renamedQuery.dateCreated > 0)

    val renamedShrineQuery = renamedQuery.toShrineQuery
    assert(renamedShrineQuery.queryDefinition.name != queryDef1.name)
    assertResult(queryDef1.expr)(renamedShrineQuery.queryDefinition.expr)
    assertResult(queryDef1.timing)(renamedShrineQuery.queryDefinition.timing)
    assertResult(queryDef1.id)(renamedShrineQuery.queryDefinition.id)
    assertResult(queryDef1.queryType)(renamedShrineQuery.queryDefinition.queryType)
    assertResult(queryDef1.constraints)(renamedShrineQuery.queryDefinition.constraints)
    assertResult(queryDef1.subQueries)(renamedShrineQuery.queryDefinition.subQueries)
    assert(renamedShrineQuery.queryDefinition != queryDef1)
  }

  @Before
  def beforeEach(): Unit = {
    AdapterQueryHistoryDb.db.createTables()
  }

  @After
  def afterEach(): Unit = {
    AdapterQueryHistoryDb.db.dropTables()
  }
}

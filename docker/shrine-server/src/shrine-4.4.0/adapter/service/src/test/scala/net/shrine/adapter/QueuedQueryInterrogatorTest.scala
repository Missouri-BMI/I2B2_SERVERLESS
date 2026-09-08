package net.shrine.adapter

import net.shrine.adapter.dao.{AdapterDao, AdapterQueryHistoryDb}
import net.shrine.adapter.i2b2Protocol.{CrcRequest, ErrorResponse, HiveCredentials, ReadQueryResultRequest, ReadQueryResultResponse, ReadResultRequest, ShrineResponse}
import net.shrine.adapter.translators.{ExpressionTranslator, QueryDefinitionTranslator}
import net.shrine.config.{ConfigExtensions, ConfigSource}
import net.shrine.crypto.SealerRevealer
import net.shrine.http4s.client.legacy.{HttpClient, HttpResponse, Poster}
import net.shrine.problem.TestProblem
import net.shrine.protocol.i2b2.DefaultBreakdownResultOutputTypes.PATIENT_AGE_COUNT_XML
import net.shrine.protocol.i2b2.query.{I2b2QueryDefinition, Term}
import org.junit.{After, Before, Ignore, Test}
import net.shrine.protocol.i2b2.{AuthenticationInfo, Credential, DefaultBreakdownResultOutputTypes, I2b2Result, QueryResult, RequestType, ResultOutputType, ShrineRequest}
import net.shrine.protocol.version.v2.ObfuscatingParameters
import net.shrine.util.ShouldMatchersForJUnit

import javax.xml.datatype.XMLGregorianCalendar
import scala.annotation.unused
import scala.concurrent.duration.{Duration, DurationInt}
import scala.util.Success
import scala.xml.NodeSeq

/**
 * @author clint
 * @since Nov 7, 2012
 */
final class QueuedQueryInterrogatorTest extends ShouldMatchersForJUnit {
  import cats.effect.unsafe.implicits.global

  private val authn = AuthenticationInfo("some-domain", "some-user", Credential("alskdjlkasd", isToken = false))
  private val shrineNetworkQueryId = 123L
  private val localMasterId = shrineNetworkQueryId.toString
  private val queryExpr = Term("foo", "fooName")
  private val fooQuery = I2b2QueryDefinition("some-query",queryExpr)
  private val instanceId = 999L
  private val setSize = 12345L
  private val obfSetSize = setSize + 1

  def makeAdapter(httpClient: HttpClient): QueuedQueryInterrogator = new QueuedQueryInterrogator(
    Poster("", httpClient),
    QueryRetrieverTestJig.hiveCredentials,
    true,
    DefaultBreakdownResultOutputTypes.toSet,
    obfuscator = Obfuscator(ObfuscatingParameters(1, 1.3, 3, 10)),
    queuedQueryTimeToLive = Duration("4 days")
  )

  def makeRequest(queryId: Long, authn: AuthenticationInfo): ReadQueryResultRequest = ReadQueryResultRequest("some-project-id", 10.seconds, authn, queryId)

//todo rename after bringing in all the methods
  //todo type of response
  private def doGetResults(adapter: QueuedQueryInterrogator): Either[ErrorResponse, ShrineResponse] = {
    val timeout = ConfigSource.config.getFiniteDuration("shrine.adapter.queuedQueryCrcPollHttpCallTimeout")
    adapter.askCrc(shrineNetworkQueryId,timeout,authn).unsafeRunSync()
  }

  @Test
  def testProcessRequestMissingQuery(): Unit = {
    val adapter = makeAdapter(MockHttpClient)

    val timeout = ConfigSource.config.getFiniteDuration("shrine.adapter.queuedQueryCrcPollHttpCallTimeout")
    val response: Either[ErrorResponse, ShrineResponse] = adapter.askCrc(shrineNetworkQueryId, timeout, authn).unsafeRunSync()
    response.isLeft should be(true)
  }

  def doTestProcessRequestIncompleteQuery(countQueryShouldWork: Boolean): Unit = {

    val dbQueryId = AdapterQueryHistoryDb.db.insertQueryHistoryIO(localMasterId, shrineNetworkQueryId, authn, fooQuery, hasBeenRun = true).unsafeRunSync()

    import net.shrine.protocol.i2b2.ResultOutputType._
    import net.shrine.xml.XmlDateHelper.now

    val breakdowns = Map(PATIENT_AGE_COUNT_XML -> I2b2Result(PATIENT_AGE_COUNT_XML, Map("a" -> 1L, "b" -> 2L)))

    val obfscBreakdowns = breakdowns.view.mapValues(_.mapValues(_ + 1))

    val startDate = now
    val elapsed = 100L

    val endDate = {
      import net.shrine.xml.XmlGcEnrichments._

      import scala.concurrent.duration.DurationLong

      startDate + elapsed.milliseconds
    }

    val countResultId = 456L
    val breakdownResultId = 98237943265436L

    val incompleteCountResult = QueryResult(
      resultId = countResultId,
      instanceId = instanceId,
      resultType = Some(PATIENT_COUNT_XML),
      setSize = setSize,
      startDate = Option(startDate),
      endDate = Option(endDate),
      description = Some("results from node X"),
      statusType = QueryResult.StatusType.Processing,
      statusMessage = None,
      breakdowns = breakdowns)

    val breakdownResult = breakdowns.head match {
      case (resultType, data) => incompleteCountResult.withId(breakdownResultId).withBreakdowns(Map(resultType -> data)).withResultType(resultType)
    }

    val idsByResultType = AdapterQueryHistoryDb.db.insertQueryResultsIO(dbQueryId, incompleteCountResult :: breakdownResult :: Nil).unsafeRunSync()

    final class MightWorkMockHttpClient(expectedHiveCredentials: HiveCredentials) extends HttpClient {
      override def post(input: String, url: String): HttpResponse = {
        def makeFinished(queryResult: QueryResult) = queryResult.copy(statusType = QueryResult.StatusType.Finished)

        def validateAuthnAndProjectId(req: ShrineRequest): Unit = {
          req.authn should equal(expectedHiveCredentials.toAuthenticationInfo)

          req.projectId should equal(expectedHiveCredentials.projectId)
        }

        val response = CrcRequest.fromI2b2String(DefaultBreakdownResultOutputTypes.toSet)(input) match {
          case Success(req: ReadResultRequest) if req.localResultId == countResultId.toString =>
            validateAuthnAndProjectId(req)

            if (countQueryShouldWork) {
              i2b2Protocol.ReadResultResponse(123L, makeFinished(incompleteCountResult), I2b2Result(PATIENT_COUNT_XML, Map(PATIENT_COUNT_XML.name -> incompleteCountResult.setSize)))
            } else {
              ErrorResponse(TestProblem(summary = "Retrieving count result failed"))
            }
          case Success(req: ReadResultRequest) if req.localResultId == breakdownResultId.toString =>
            validateAuthnAndProjectId(req)

            i2b2Protocol.ReadResultResponse(123L, makeFinished(breakdownResult), breakdowns.head._2)
          case _ => fail(s"Unknown input: $input") //todo SHRINE-2384 there's no ReadQueryResultResponse so this will never work
        }

        HttpResponse.ok(response.toI2b2String)
      }
    }

    val adapter: QueuedQueryInterrogator = makeAdapter(new MightWorkMockHttpClient(QueryRetrieverTestJig.hiveCredentials))

    def getResults(): Either[ErrorResponse, ShrineResponse] = doGetResults(adapter)

    getResults().isLeft should be(true)

    AdapterQueryHistoryDb.db.insertCountResultIO(idsByResultType(PATIENT_COUNT_XML).head, obfSetSize).unsafeRunSync()

    AdapterQueryHistoryDb.db.insertBreakdownResultsIO(idsByResultType, breakdowns, obfscBreakdowns.toMap).unsafeRunSync()

    //The query shouldn't be 'done', since its status is PROCESSING
    AdapterQueryHistoryDb.db.findResultsForIO(shrineNetworkQueryId).unsafeRunSync().get.count.statusType should be(QueryResult.StatusType.Processing)

    //Now, calling processRequest (via getResults) should cause the query to be re-retrieved from the CRC

    val resultEither = getResults()

    val result: ReadQueryResultResponse = resultEither.map(r => r.asInstanceOf[ReadQueryResultResponse]).getOrElse(fail(s"getResults() returned $resultEither"))

    //Which should cause the query to be re-stored with a 'done' status (since that's what our mock CRC returns)
    val expectedStatusType = if (countQueryShouldWork) QueryResult.StatusType.Finished else QueryResult.StatusType.Processing

    AdapterQueryHistoryDb.db.findResultsForIO(shrineNetworkQueryId).unsafeRunSync().get.count.statusType should be(expectedStatusType)

    if (!countQueryShouldWork) {
      //no op result.isInstanceOf[ErrorResponse] should be(true)
    } else {
      val actualNetworkQueryId = result.queryId
      val actualQueryResult = result.singleNodeResult

      actualNetworkQueryId should equal(shrineNetworkQueryId)

      import ObfuscatorTest.within3

      actualQueryResult.resultType should equal(Some(PATIENT_COUNT_XML))
      within3(setSize, actualQueryResult.setSize) should be(true)
      actualQueryResult.description should be(Some("results from node X"))
      actualQueryResult.statusType should equal(QueryResult.StatusType.Finished)
      actualQueryResult.statusMessage should be(Some(QueryResult.StatusType.Finished.name))

      actualQueryResult.breakdowns.foreach {
        case (rt, I2b2Result(_, data)) =>
          data.forall { case (key, value) => within3(value, breakdowns(rt).data(key)) }
      }

      for {
        startDate <- actualQueryResult.startDate
        endDate <- actualQueryResult.endDate
      } {
        def toMillis(xmlGc: XMLGregorianCalendar): Long = xmlGc.toGregorianCalendar.getTimeInMillis
        val actualElapsed = toMillis(endDate) - toMillis(startDate)

        actualElapsed should equal(elapsed)
      }
    }
  }

  @Test
  @Ignore //todo turn back on SHRINE-2384
  def testProcessRequestIncompleteQuery(): Unit = doTestProcessRequestIncompleteQuery(countQueryShouldWork = true)

  @Test
  @Ignore //todo turn back on SHRINE-2384
  def testProcessRequestIncompleteQueryCountResultRetrievalFails(): Unit = doTestProcessRequestIncompleteQuery(false)

  @Test
  @Ignore //todo turn back on SHRINE-2384
  def testProcessRequestQueuedQuery(): Unit = {

    import net.shrine.protocol.i2b2.ResultOutputType._
    import net.shrine.xml.XmlDateHelper.now
    val startDate = now
    val elapsed = 100L

    val endDate = {
      import net.shrine.xml.XmlGcEnrichments._

      import scala.concurrent.duration.DurationLong

      startDate + elapsed.milliseconds
    }
    val incompleteCountResult = QueryResult(-1L, -1L, Some(PATIENT_COUNT_XML), -1L, Option(startDate), Option(endDate), Some("results from node X"), QueryResult.StatusType.Queued, None)

    val insertedQueryId = AdapterQueryHistoryDb.db.insertQueryHistoryIO(localMasterId, shrineNetworkQueryId, authn, fooQuery, hasBeenRun = false).unsafeRunSync()

    //NB: We need to insert dummy QueryResult and Count records so that calls to StoredQueries.retrieve() in
    //AbstractReadQueryResultAdapter, called when retrieving results for previously-queued-or-incomplete
    //queries, will work.

    val insertedQueryResultIds = AdapterQueryHistoryDb.db.insertQueryResultsIO(insertedQueryId, Seq(incompleteCountResult)).unsafeRunSync()

    val countQueryResultId = insertedQueryResultIds(ResultOutputType.PATIENT_COUNT_XML).head

    AdapterQueryHistoryDb.db.insertCountResultIO(countQueryResultId, -1L).unsafeRunSync()

    object MockHttpClient extends HttpClient {
      override def post(input: String, url: String): HttpResponse = ??? //todo fix with SHRINE-2384
    }

    val adapter: QueuedQueryInterrogator = makeAdapter(MockHttpClient)

    def getResults() = doGetResults(adapter)

    getResults().isLeft should be(true)

    //The query shouldn't be 'done', since its status is QUEUED
    AdapterQueryHistoryDb.db.findResultsForIO(shrineNetworkQueryId).unsafeRunSync().get.count.statusType should be(QueryResult.StatusType.Queued)

    //Now, calling processRequest (via getResults) should NOT cause the query to be re-retrieved from the CRC, because the query was previously queued

    val result = getResults()

    result.isLeft should be(true)

    AdapterQueryHistoryDb.db.findResultsForIO(shrineNetworkQueryId).unsafeRunSync().get.count.statusType should be(QueryResult.StatusType.Queued)
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

object QueryRetrieverTestJig {
  val hiveCredentials: HiveCredentials = HiveCredentials("some-hive-domain", "hive-username", SealerRevealer.seal("hive-password"), "hive-project")

  val doObfuscation = true

  def runQueryAdapter(@unused dao: AdapterDao, poster: Poster): RunQueryInterrogator = {
    val translator = new QueryDefinitionTranslator(ExpressionTranslator(Map("foo" -> Set("bar"))))

    new RunQueryInterrogator(
      poster = poster,
      hiveCredentials = hiveCredentials,
      conceptTranslator = translator,
      doObfuscation = doObfuscation,
      runQueriesImmediately = true,
      breakdownTypes = DefaultBreakdownResultOutputTypes.toSet,
      botCountTimeThresholds = Seq.empty,
      obfuscator = Obfuscator(ObfuscatingParameters(5,6.5,10,10))
    )
  }

  import scala.concurrent.duration.DurationInt

  final class BogusRequest extends ShrineRequest("fooProject", 1.second, null) {
    override val requestType: RequestType = null

    protected override def i2b2MessageBody: NodeSeq = <foo></foo>

    override def toXml: NodeSeq = <x></x>
  }
}

package net.shrine.qep

import cats.data.OptionT
import cats.effect.IO
import cats.implicits.{catsSyntaxEither => _}
import cats.effect.unsafe.implicits.global
import ch.qos.logback.classic.Level
import io.circe.Decoder.Result
import io.circe.generic.auto.exportEncoder
import io.circe.syntax.EncoderOps
import io.circe.{DecodingFailure, Json}
import net.shrine.api.ontology.LuceneSearcher
import net.shrine.audit.{LongQueryId, Time}
import net.shrine.authentication.http4s.BasicAuthentication
import net.shrine.authentication.pm.User
import net.shrine.config.ConfigSource
import net.shrine.hub.HubLifecycle
import net.shrine.hub.data.store.HubDb
import net.shrine.log.LogCensor
import net.shrine.problem.{AbstractProblem, ProblemSources, TestProblem}
import net.shrine.protocol.version.v2.{Breakdowns, CountResult, Network, Node, ObfuscatingParameters, Query, QueryProgress, QueryStatus, Researcher, ResultMetadata, ResultProgress, ResultStatus, UpdateQueryAtQepWithError}
import net.shrine.protocol.version.v2.querydefinition.{FlagConstraints, QueryDefinition, Concept => V2Concept, ConceptGroup => V2ConceptGroup, EventConstraint => V2EventConstraint, Timeline => V2Timeline}
import net.shrine.protocol.version.{DateStamp, MomQueueName, NodeId, NodeKey, NodeName, QueryId, ResearcherId, UserDomainName}
import net.shrine.protocol.i2b2.{Credential, QueryResult}
import net.shrine.qep.querydb.{QepQuery, QepQueryDb, QueryResultRow, QueryWithResultStatus}
import org.http4s.circe.jsonOf
import org.http4s.dsl.io.{BadRequest, Ok, RangeNotSatisfiable, Unauthorized}
import org.http4s.headers.Authorization
import org.http4s.{AuthScheme, BasicCredentials, Credentials, EntityDecoder, Headers, Method, Request, Response, Uri}
import org.junit.jupiter.api.Assertions.{assertEquals, fail}
import org.junit.jupiter.api.{AfterEach, BeforeEach, Test}

import java.util.Base64
import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration
import scala.io.Source
import org.http4s.syntax.literals._

import scala.annotation.unused

class QepServiceTest {

  implicit val jsonDecoder: EntityDecoder[IO, Json] = jsonOf[IO, Json]

  def extractResponse(request: Request[IO]): Response[IO] = {
    val responseOptionIo: OptionT[IO, Response[IO]] = QepService().router.run(request)

    responseOptionIo.map { r: Response[IO] => r }.fold {
      fail("No response from service")
    } { r: Response[IO] => r
    }.unsafeRunSync()
  }

  @Test
  def testReplyToPing() : Unit = {
    val pingRequest: Request[IO] = Request(method = Method.GET, uri = Uri.unsafeFromString(s"/ping"))

    val response: Response[IO] = extractResponse(pingRequest)

    assertEquals(Ok,response.status)
    val entityText = response.as[String].unsafeRunSync()
    assertEquals("pong",entityText)
  }

  @Test
  def testLogin(): Unit =  {
    val username = "admin"
    val password = "pwd"
    val loginRequest: Request[IO] = Request(method = Method.GET,
      uri = uri"/login",
      headers = Headers(Authorization(BasicCredentials(username, password)))
    )
    val response: Response[IO] = extractResponse(loginRequest)
    val result: Json = response.as[Json].unsafeRunSync()
    val expectedResult = UserSessionToken(username, "pwd", sessionTimeoutMs = Some(1000)).asJson
    assertEquals(Ok,response.status)
    assertEquals(expectedResult,result)
  }

  @Test
  def testInvalidPassword(): Unit = {
    val username = "admin"
    val password = "bad pwd"
    val loginRequest: Request[IO] = Request(method = Method.GET,
      uri = uri"/login",
      headers = Headers(Authorization(BasicCredentials(username, password)))
    )
    val response: Response[IO] = extractResponse(loginRequest)
    assertEquals(Unauthorized,response.status)
  }

  @Test
  def replyWithPreviousQueries(): Unit = {
    QepQueryDb.db.insertQepQuery(qepQuery)
    QepQueryDb.db.insertQepResultRow(qepResultRowFromBch)
    QepQueryDb.db.insertQepResultRow(qepResultRowFromMgh)
    QepQueryDb.db.insertQepResultRow(qepResultRowFromDfci)
    QepQueryDb.db.insertQepResultRow(qepResultRowFromPartners)

    val queryResultTableRequest: Request[IO] = Request(method = Method.GET,
      uri = Uri.unsafeFromString(s"/queryResult"),
      headers = Headers(Authorization(BasicCredentials(researcherUser.username, "kapow"))))

    val response: Response[IO] = extractResponse(queryResultTableRequest)

    val result: Json = response.as[Json].unsafeRunSync()
    val queryRowCount: Int = QepQueryDb.db.countPreviousQueriesByUserAndDomain(
      userName = researcherUser.username,
      domain = researcherUser.domain
    )

    val queriesAndResult: PreviousQueriesAndResult = getQueryDataIO(researcherUser).flatMap(queriesAndResult => {

      IO(PreviousQueriesAndResult(queryRowCount, 0, allQueries = queriesAndResult.queryCells))
    }).unsafeRunSync()
    val expectedJson: Json = queriesAndResult.asJson
    assertEquals(Ok,response.status)
    assertEquals(expectedJson,result)
  }

  @Test
  def testUnAuthorizedUser(): Unit = {
    val queryResultTableRequest: Request[IO] = Request(method = Method.GET,
      uri = Uri.unsafeFromString(s"/queryResult"))

    val response: Response[IO] = extractResponse(queryResultTableRequest)
    val entityText = response.as[String].unsafeRunSync()

    assertEquals(Unauthorized,response.status)
    assertEquals(BasicAuthentication.unauthorizedMsg,entityText)
  }

  @Test
  def testQueryResultsSkipAndLimit(): Unit = {
    QepQueryDb.db.insertQepQuery(qepQuery)
    QepQueryDb.db.insertQepResultRow(qepResultRowFromBch)
    QepQueryDb.db.insertQepResultRow(qepResultRowFromDfci)
    QepQueryDb.db.insertQepResultRow(qepResultRowFromPartners)

    val queryResultTableRequest: Request[IO] = Request(method = Method.GET,
      uri = Uri.unsafeFromString(s"/queryResult?skip=2&limit=2"),
      headers = Headers(Authorization(BasicCredentials(researcherUser.username, "kapow"))))

    val response: Response[IO] = extractResponse(queryResultTableRequest)

    val result: Json = response.as[Json].unsafeRunSync()

    val skip = 2
    val queriesAndResult: PreviousQueriesAndResult = getQueryDataIO(researcherUser, skipOption = Some(skip), limitOption = Some(2)).flatMap(queriesAndResult => {

      IO(PreviousQueriesAndResult(queriesAndResult.queryRowCount, skip, allQueries = queriesAndResult.queryCells))
    }).unsafeRunSync()

    val expectedJson: Json = queriesAndResult.asJson
    assertEquals(Ok,response.status)
    assertEquals(expectedJson,result)
  }

  @Test
  def testQueryResultsSortBy(): Unit = {
    QepQueryDb.db.insertQepQuery(qepQuery)
    QepQueryDb.db.insertQepQuery(qep2Query)
    QepQueryDb.db.insertQepQuery(qep3Query)
    QepQueryDb.db.insertQepResultRow(qepResultRowFromMgh)
    QepQueryDb.db.insertQepResultRow(qepResultRowFromDfci)
    QepQueryDb.db.insertQepResultRow(qepResultRowFromPartners)
    QepQueryDb.db.insertQepResultRow(qepResultRowFromBch)
    QepQueryDb.db.insertQepResultRow(qepResultRowFromHarvard)

    val queryResultTableRequest: Request[IO] = Request(method = Method.GET,
      uri = Uri.unsafeFromString(s"/queryResult?limit=2&sortBy=queryName.asc"),
      headers = Headers(Authorization(BasicCredentials(researcherUser.username, "kapow"))))

    val response: Response[IO] = extractResponse(queryResultTableRequest)

    val result: Json = response.as[Json].unsafeRunSync()

    val queriesAndResult: PreviousQueriesAndResult = getQueryDataIO(researcherUser, limitOption = Some(2), sortByOption = Some("queryName.asc")).flatMap(queriesAndResult => {

      IO(PreviousQueriesAndResult(queriesAndResult.queryRowCount, 0, allQueries = queriesAndResult.queryCells))
    }).unsafeRunSync()

    val expectedJson: Json = queriesAndResult.asJson
    assertEquals(Ok,response.status)
    assertEquals(expectedJson,result)
  }

  @Test
  def testQueryResultAndPreviousQueries(): Unit = {
    val networkId = qep2Query.networkId
    QepQueryDb.db.insertQepQuery(qep2Query)
    QepQueryDb.db.insertQepResultRow(qepResultRowFromHarvard)
    QepQueryDb.db.insertQepResultRow(qepResultRowFromNU)

    val queryResultTableRequest: Request[IO] = Request(method = Method.GET,
      uri = Uri.unsafeFromString(s"/queryResult?networkId=$networkId&timeoutSeconds=0"),
      headers = Headers(Authorization(BasicCredentials(researcherUser.username, "kapow"))))

    val response: Response[IO] = extractResponse(queryResultTableRequest)

    assertEquals(Ok,response.status)

    import io.circe.generic.auto.exportDecoder
    val resultJson: Json = response.as[Json].unsafeRunSync()
    val result = resultJson.as[PreviousQueriesAndResult]
    val dataVersion: Time = result.map{_.selectedQuery.get.dataVersion}.getOrElse{throw new Exception(s"Could not translate $result into PreviousQueriesAndResult")}

    val expectedJson: Json = getQueryResultIO(researcherUser,networkId).unsafeRunSync()
    val expected = expectedJson.as[PreviousQueriesAndResult].map{ pr =>
      pr.copy(selectedQuery = Option(pr.selectedQuery.get.copy(dataVersion = dataVersion)))
    }
    assertEquals(expected,result)
  }

  @Test
  def testQueryResultSorting(): Unit ={
    val networkId = qep2Query.networkId
    QepQueryDb.db.insertQepQuery(qep2Query)
    QepQueryDb.db.insertQepResultRow(qepResultRowFromHarvard)
    QepQueryDb.db.insertQepResultRow(qepResultRowFromNU)

    val queryResultTableRequest: Request[IO] = Request(method = Method.GET,
      uri = Uri.unsafeFromString(s"/queryResult?networkId=$networkId&sortSiteBy=site.desc"),
      headers = Headers(Authorization(BasicCredentials(researcherUser.username, "kapow"))))

    val response: Response[IO] = extractResponse(queryResultTableRequest)

    assertEquals(Ok,response.status)

    val result: Json = response.as[Json].unsafeRunSync()

    val expectedJson = getQueryResultIO(researcherUser,networkId, sortSiteByOption = Some("site.desc")).unsafeRunSync()
    assertEquals(expectedJson,result)
  }

  @Test
  def testHighLimit(): Unit = {
    val networkId = qepQuery.networkId
    QepQueryDb.db.insertQepQuery(qepQuery)
    QepQueryDb.db.insertQepResultRow(qepResultRowFromBch)
    QepQueryDb.db.insertQepResultRow(qepResultRowFromMgh)
    QepQueryDb.db.insertQepResultRow(qepResultRowFromDfci)
    QepQueryDb.db.insertQepResultRow(qepResultRowFromPartners)

    val queryResultTableRequest: Request[IO] = Request(method = Method.GET,
      uri = Uri.unsafeFromString(s"/queryResult?networkId=$networkId&timeoutSeconds=1&limit=300"),
      headers = Headers(Authorization(BasicCredentials(researcherUser.username, "kapow"))))

    val response: Response[IO] = extractResponse(queryResultTableRequest)

    assertEquals(RangeNotSatisfiable,response.status)
  }

  @Test
  def testQueryResultOrderIsOK(): Unit ={
    def queryResultRowForNetworkId(qrr: QueryResultRow, resultId: Long, newNetworkId: Long): QueryResultRow = {
      QueryResultRow(resultId, newNetworkId, qrr.instanceId, qrr.adapterNode,
        qrr.size, qrr.startDate, qrr.endDate, qrr.status, qrr.statusMessage, qrr.resultMetadata, qrr.changeDate)
    }

    val networkId = qep4Query.networkId
    QepQueryDb.db.insertQepQuery(qep4Query)
    QepQueryDb.db.insertQepResultRow(queryResultRowForNetworkId(qepResultRowFromHarvard, 1L, networkId)) // Status: "FINISHED", count: "30000"
    QepQueryDb.db.insertQepResultRow(queryResultRowForNetworkId(qepResultRowFromNU, 2L, networkId)) // Status: "FINISHED", count: "80000"
    QepQueryDb.db.insertQepResultRow(queryResultRowForNetworkId(qepResultRowFromBch, 3L, networkId)) // Status: "FINISHED", count: "3000"
    QepQueryDb.db.insertQepResultRow(queryResultRowForNetworkId(qepResultRowFromDfci, 4L, networkId)) // Status: "FINISHED", count: "30000"
    QepQueryDb.db.insertQepResultRow(queryResultRowForNetworkId(qepResultRowFromNUError, 5L, networkId)) // Status: "Site Error", count: "30000"
    QepQueryDb.db.insertQepResultRow(queryResultRowForNetworkId(qepResultRowFromHarvardError, 6L, networkId)) // Status: "Error", count: "80000"

    val queryResultTableRequestAsc: Request[IO] = Request(method = Method.GET,
      uri = Uri.unsafeFromString(s"/queryResult?networkId=$networkId&sortSiteBy=status.asc"),
      headers = Headers(Authorization(BasicCredentials(researcherUser.username, "kapow"))))
    val responseAsc: Response[IO] = extractResponse(queryResultTableRequestAsc)

    assertEquals(Ok,responseAsc.status)

    val resultAsc: Json = responseAsc.as[Json].unsafeRunSync()
    val resultOrderAsc: Vector[Int] = resultAsc.asObject.get("selectedQuery").get.asObject.get("results").get.asArray.get.map(jo => jo.asObject.get("resultId").get.asNumber.get.toInt.get)
    assertEquals(Vector(3, 4, 1, 2, 6, 5),resultOrderAsc)


    val queryResultTableRequestDesc: Request[IO] = Request(method = Method.GET,
      uri = Uri.unsafeFromString(s"/queryResult?networkId=$networkId&sortSiteBy=status.desc"),
      headers = Headers(Authorization(BasicCredentials(researcherUser.username, "kapow"))))
    val responseDesc: Response[IO] = extractResponse(queryResultTableRequestDesc)

    assertEquals(Ok,responseDesc.status)

    val resultDesc: Json = responseDesc.as[Json].unsafeRunSync()
    val resultOrderDesc: Vector[Int] = resultDesc.asObject.get("selectedQuery").get.asObject.get("results").get.asArray.get.map(jo => jo.asObject.get("resultId").get.asNumber.get.toInt.get)
    assertEquals(Vector(2, 1, 4, 3, 5, 6),resultOrderDesc)
  }

  @Test
  def testQueryResult(): Unit = {
    val networkId = qep2Query.networkId
    QepQueryDb.db.insertQepQuery(qep2Query)
    QepQueryDb.db.insertQepResultRow(qepResultRowFromHarvard)
    QepQueryDb.db.insertQepResultRow(qepResultRowFromNU)

    val queryResultTableRequest: Request[IO] = Request(method = Method.GET,
      uri = Uri.unsafeFromString(s"/queryResult?networkId=$networkId"),
      headers = Headers(Authorization(BasicCredentials(researcherUser.username, "kapow"))))

    val response: Response[IO] = extractResponse(queryResultTableRequest)

    assertEquals(Ok,response.status)

    @unused
    val result: Json = response.as[Json].unsafeRunSync()
  }

  @Test
  def testAllResultsHaveError(): Unit = {
    val networkId = qepQueryWithResultErrors.networkId
    QepQueryDb.db.insertQepQuery(qepQueryWithResultErrors)
    QepQueryDb.db.insertQepResultRow(qepResultRowFromNUError)
    QepQueryDb.db.insertQepResultRow(qepResultRowFromHarvardError)

    val queryResultTableRequest: Request[IO] = Request(method = Method.GET,
      uri = Uri.unsafeFromString(s"/queryResult?networkId=$networkId&timeoutSeconds=1"),
      headers = Headers(Authorization(BasicCredentials(researcherUser.username, "kapow"))))

    val dateCreatedPattern = "\"dateCreated\" : \\d+".r
    val changedDatePattern = "\"changeDate\" : \\d+".r
    val dataVersionPattern = "\"dataVersion\" : \\d+".r
    val stampTextPattern = "\"stampText\" : (.*)\\d+(.*)".r
    val epochPattern = "\"epoch\" : \\d+".r

    val expectedJson = readFile("/allResultsHaveError.json")
    val expectedJsonNoDateCreated = dateCreatedPattern.replaceAllIn(expectedJson, "\"dateCreated\" : ")
    val expectedJsonNoChangeDate = changedDatePattern.replaceAllIn(expectedJsonNoDateCreated, "\"changeDate\" : ")
    val expectedJsonNoDataVersion = dataVersionPattern.replaceAllIn(expectedJsonNoChangeDate, "\"dataVersion\" :")
    val expectedJsonNoStampText = stampTextPattern.replaceAllIn(expectedJsonNoDataVersion, "\"stampText\" :")
    val expectedJsonNoEpoch = epochPattern.replaceAllIn(expectedJsonNoStampText, "\"epoch\" : ")

    val response: Response[IO] = extractResponse(queryResultTableRequest)

    assertEquals(Ok,response.status)

    val result: Json = response.as[Json].unsafeRunSync()

    val resultJson: String = result.toString()

    val resultJsonNoDateCreated = dateCreatedPattern.replaceAllIn(resultJson, "\"dateCreated\" : ")
    val resultJsonNoChangeDate = changedDatePattern.replaceAllIn(resultJsonNoDateCreated, "\"changeDate\" : ")
    val resultJsonNoDataVersion = dataVersionPattern.replaceAllIn(resultJsonNoChangeDate, "\"dataVersion\" :")
    val resultJsonNoStampText = stampTextPattern.replaceAllIn(resultJsonNoDataVersion, "\"stampText\" :")
    val resultJsonNoEpoch = epochPattern.replaceAllIn(resultJsonNoStampText, "\"epoch\" : ")

    assertEquals(expectedJsonNoEpoch,resultJsonNoEpoch)
  }

  @Test
  def testQueryErrorStatus(): Unit = {

    val networkId = qepQueryWithErrors.networkId
    QepQueryDb.db.insertQepQuery(qepQueryWithErrors)

    val queryResultTableRequest: Request[IO] = Request(method = Method.GET,
      uri = Uri.unsafeFromString(s"/queryResult?networkId=$networkId&timeoutSeconds=1"),
      headers = Headers(Authorization(BasicCredentials(researcherUser.username, "kapow"))))

    val response: Response[IO] = extractResponse(queryResultTableRequest)

    assertEquals(Ok,response.status)

    val result: Json = response.as[Json].unsafeRunSync()

    val expectedJson: String = """{
                                 |  "rowCount" : 1,
                                 |  "rowOffset" : 0,
                                 |  "selectedQuery" : {
                                 |    "query" : {
                                 |      "networkId" : "50",
                                 |      "queryName" : "TestQuery",
                                 |      "queryNotes" : null,
                                 |      "dateCreated" : ,
                                 |      "changeDate" : ,
                                 |      "queryFaved" : false,
                                 |      "status" : "Network Error",
                                 |      "internalStatus" : "HubError",
                                 |      "isQueryError" : true,
                                 |      "isResultsError" : false,
                                 |      "isQueryComplete" : true,
                                 |      "problemDigest" : null,
                                 |      "observed" : true,
                                 |      "queryAsHtmlString" : "<span class=\"criteriaPanelSeparator\">Find patients</span> <span class=\"criteriaPanelSeparator\"> with</span> (Demographics 10-17 years old) or (Demographics No Information) starting from 03/01/2020 to 03/06/2020 and occurs at least 6 times<br/><span class=\"criteriaPanelSeparator\">and</span> <span class=\"criteriaPanelSeparator\"> without</span> (Demographics Black or African American) or (Demographics 85-89 years old)<br/><span class=\"criteriaPanelSeparator\">and</span> <span class=\"criteriaPanelSeparator\"> with</span> (Laboratory Tests Bicarbonate in Blood abnormal flag high)"
                                 |    },
                                 |    "results" : [
                                 |    ],
                                 |    "aggregateDemographics" : [
                                 |    ],
                                 |    "siteCount" : 0,
                                 |    "patientCount" : 0,
                                 |    "isComplete" : false,
                                 |    "dataVersion" :
                                 |  },
                                 |  "isAllQueriesComplete" : true,
                                 |  "allQueries" : [
                                 |    {
                                 |      "networkId" : "50",
                                 |      "queryName" : "TestQuery",
                                 |      "queryNotes" : null,
                                 |      "dateCreated" : ,
                                 |      "changeDate" : ,
                                 |      "queryFaved" : false,
                                 |      "status" : "Network Error",
                                 |      "internalStatus" : "HubError",
                                 |      "isQueryError" : true,
                                 |      "isResultsError" : false,
                                 |      "isQueryComplete" : true,
                                 |      "problemDigest" : null,
                                 |      "observed" : true,
                                 |      "queryAsHtmlString" : null
                                 |    }
                                 |  ]
                                 |}""".stripMargin

    val resultJson: String = result.toString()
    val dateCreatedPattern = "\"dateCreated\" : \\d+".r
    val changedDatePattern = "\"changeDate\" : \\d+".r
    val dataVersionPattern = "\"dataVersion\" : \\d+".r
    val resultJsonNoDateCreated = dateCreatedPattern.replaceAllIn(resultJson, "\"dateCreated\" : ")
    val resultJsonNoChangeDate = changedDatePattern.replaceAllIn(resultJsonNoDateCreated, "\"changeDate\" : ")
    val resultJsonNoDataVersion = dataVersionPattern.replaceAllIn(resultJsonNoChangeDate, "\"dataVersion\" :")
    assertEquals(expectedJson,resultJsonNoDataVersion)
  }

  @Test
  def returnImmediatelyWhenNoVersion(): Unit = {
    val networkId = qepQuery.networkId
    QepQueryDb.db.insertQepQuery(qepQuery)
    QepQueryDb.db.insertQepResultRow(qepResultRowFromBch)
    QepQueryDb.db.insertQepResultRow(qepResultRowFromMgh)
    QepQueryDb.db.insertQepResultRow(qepResultRowFromDfci)
    QepQueryDb.db.insertQepResultRow(qepResultRowFromPartners)

    val queryResultTableRequest: Request[IO] = Request(
      method = Method.GET,
      uri = Uri.unsafeFromString(s"/queryResult?networkId=$networkId"),
      headers = Headers(Authorization(BasicCredentials(researcherUser.username, "kapow")))
    )

    val responseOptionIo: OptionT[IO, Response[IO]] = QepService().router.run(queryResultTableRequest)

    val responseIO = responseOptionIo.map { r: Response[IO] => r }.fold {
        fail("No response from service")
      } { r: Response[IO] => r
    }

    val response = responseIO.unsafeRunSync()

    assertEquals(Ok,response.status)

    val resultJson: Json = response.as[Json].unsafeRunSync()

    import io.circe.generic.auto.exportDecoder
    val queriesAndResults = resultJson.as[PreviousQueriesAndResult]
     queriesAndResults.fold({df:DecodingFailure => fail(df)},{ _ => })
  }

  @Test
  def testSameQueryResultWhenNoChange(): Unit = {
    val networkId: LongQueryId = qepQuery.networkId
    QepQueryDb.db.insertQepQuery(qepQuery)
    QepQueryDb.db.insertQepResultRow(qepResultRowFromBch)
    QepQueryDb.db.insertQepResultRow(qepResultRowFromMgh)
    QepQueryDb.db.insertQepResultRow(qepResultRowFromDfci)
    QepQueryDb.db.insertQepResultRow(qepResultRowFromPartners)

    def queryResultTableRequest(): Request[IO] = {
      val timeoutSeconds = 1L
      Request(method = Method.GET,
        uri = Uri.unsafeFromString(s"/queryResult?networkId=$networkId&timeoutSeconds=$timeoutSeconds"),
        headers = Headers(Authorization(BasicCredentials(researcherUser.username, "kapow"))))
    }

    val responseOptionIo: OptionT[IO, Response[IO]] = QepService().router.run(queryResultTableRequest())
    val responseIO = responseOptionIo.map { r: Response[IO] => r }.fold {
      fail("No response from service")
    } { r: Response[IO] => r
    }
    val response = responseIO.unsafeRunSync()
    assertEquals(Ok,response.status)
    val resultJson: Json = response.as[Json].unsafeRunSync()

    import io.circe.generic.auto.exportDecoder
    val queriesAndResults: Result[PreviousQueriesAndResult] = resultJson.as[PreviousQueriesAndResult]

    val _ = queriesAndResults.fold({df:DecodingFailure => fail(df)},{ previousQueriesAndResults:PreviousQueriesAndResult =>
      //todo the structure still doesn't expose a version with a checksum, so figure out the latest timestamp and use that
      val maxQueryChange = previousQueriesAndResults.allQueries.maxBy(_.changeDate).changeDate
      val maxResultChange = previousQueriesAndResults.selectedQuery.fold(0L){resultsRow =>
        resultsRow.results.maxBy(_.changeDate).changeDate
      }
      Math.max(maxQueryChange,maxResultChange)
    })

    val responseOptionIo2: OptionT[IO, Response[IO]] = QepService().router.run(queryResultTableRequest())
    val responseIO2 = responseOptionIo2.map { r: Response[IO] => r }.fold {
      fail("No response from service")
    } { r: Response[IO] => r
    }

    val query2StartTime = System.currentTimeMillis()

    val response2 = responseIO2.unsafeRunSync()
    assertEquals(Ok,response2.status)
    val resultJson2: Json = response2.as[Json].unsafeRunSync()
    val dataVersion: Time = queriesAndResults.map{_.selectedQuery.get.dataVersion}.getOrElse{throw new Exception(s"Could not translate $queriesAndResults into PreviousQueriesAndResult")}

    val expected = resultJson2.as[PreviousQueriesAndResult].map{ pr =>
      pr.copy(selectedQuery = Option(pr.selectedQuery.get.copy(dataVersion = dataVersion)))
    }
    assertEquals(expected,queriesAndResults)
    assert(System.currentTimeMillis() - query2StartTime >= 1000L,"Time between request and reply was less than one second.")
  }

  @Test
  def testReturnUpdatedQueryResult(): Unit  = {
    val networkId = qepQuery.networkId
    QepQueryDb.db.insertQepQuery(qepQuery)
    QepQueryDb.db.insertQepResultRow(incompleteResultRowFromBch)
    QepQueryDb.db.insertQepResultRow(qepResultRowFromMgh)
    QepQueryDb.db.insertQepResultRow(qepResultRowFromDfci)
    QepQueryDb.db.insertQepResultRow(qepResultRowFromPartners)

    def queryResultTableRequest: Request[IO] = Request(method = Method.GET,
      uri = Uri.unsafeFromString(s"/queryResult?networkId=$networkId"),
      headers = Headers(Authorization(BasicCredentials(researcherUser.username, "kapow"))))

    val responseOptionIo: OptionT[IO, Response[IO]] = QepService().router.run(queryResultTableRequest)
    val responseIO = responseOptionIo.map { r: Response[IO] => r }.fold {
      fail("No response from service")
    } { r: Response[IO] => r
    }
    val response = responseIO.unsafeRunSync()
    assertEquals(Ok,response.status)
    val resultJson: Json = response.as[Json].unsafeRunSync()

    import io.circe.generic.auto.exportDecoder
    val queriesAndResults: Result[PreviousQueriesAndResult] = resultJson.as[PreviousQueriesAndResult]

    queriesAndResults.fold({ df:DecodingFailure => fail(df)},{ previousQueriesAndResults:PreviousQueriesAndResult =>
      //todo the structure still doesn't expose a version with a checksum, so figure out the latest timestamp and use that
      val maxQueryChange = previousQueriesAndResults.allQueries.maxBy(_.changeDate).changeDate
      val maxResultChange = previousQueriesAndResults.selectedQuery.fold(0L){resultsRow =>
        resultsRow.results.maxBy(_.changeDate).changeDate
      }
      Math.max(maxQueryChange,maxResultChange)
    })

    //trigger a change after a two-second delay. On my laptop the service is taking about half a second to just make the request
    IO.sleep(FiniteDuration(2L,TimeUnit.SECONDS)).flatMap{_ =>
      IO(QepQueryDb.db.insertQepResultRow(qepResultRowFromBch))
    }.unsafeRunAndForget()

    val responseOptionIo2: OptionT[IO, Response[IO]] = QepService().router.run(queryResultTableRequest)
    val responseIO2 = responseOptionIo2.map { r: Response[IO] => r }.fold {
      fail("No response from service")
    } { r: Response[IO] => r
    }

    val query2StartTime = System.currentTimeMillis()

    val response2 = responseIO2.unsafeRunSync()
    assertEquals(Ok,response2.status)
    val resultJson2: Json = response2.as[Json].unsafeRunSync()
    val queriesAndResults2: Result[PreviousQueriesAndResult] = resultJson2.as[PreviousQueriesAndResult]

    assert(System.currentTimeMillis() - query2StartTime < 15000L,"Time between request and reply was greater than 15 seconds.")
    assert(System.currentTimeMillis() - query2StartTime > 1000L,"Time between request and reply was less than 1 second.")
    assert(queriesAndResults != queriesAndResults2)
  }
  /*
  /* Uncomment and fix once the observed faved is added to the QueryCell
  */
  "QepService" should "return an OK and whether query result was previously viewed" in {
    val networkId = qep2Query.networkId
    QepQueryDb.db.insertQepQuery(qep2Query)

    val queryResultTableRequest: Request[IO] = Request(method = Method.GET,
      uri = Uri.unsafeFromString(s"/queryResult?networkId=$networkId&timeoutSeconds=1"),
      headers = Headers(Authorization(BasicCredentials(researcherUser.username, "kapow"))))

    val response: Response[IO] = extractResponse(queryResultTableRequest)
    val result: Json = response.as[Json].unsafeRunSync()

    val observedInitial = false
    val observedResult = result.findAllByKey("selectedQuery").head.findAllByKey("observed").head.asBoolean.get
    assertEquals(Ok)(response.status)
    assertEquals(observedInitial)(observedResult)

    val secondResponse: Response[IO] = extractResponse(queryResultTableRequest)
    val observed = true
    val secondResult: Json = secondResponse.as[Json].unsafeRunSync()
    val secondObservedResult = secondResult.findAllByKey("selectedQuery").head.findAllByKey("observed").head.asBoolean.get
    assertEquals(Ok)(secondResponse.status)
    assertEquals(observed)(secondObservedResult)
  }*/

  @Test
  def testReturnPreviousQueries(): Unit = {
    QepQueryDb.db.insertQepQuery(qep2Query)

    val queryResultTableRequest: Request[IO] = Request(method = Method.GET,
      uri = Uri.unsafeFromString(s"/queryResult"),
      headers = Headers(Authorization(BasicCredentials(researcherUser.username, "kapow"))))

    val response: Response[IO] = extractResponse(queryResultTableRequest)
    val result: Json = response.as[Json].unsafeRunSync()

    assertEquals(Ok,response.status)

    val queriesAndResult: PreviousQueriesAndResult = getQueryDataIO(researcherUser).flatMap(queriesAndResult => {

      IO(PreviousQueriesAndResult(queriesAndResult.queryRowCount, 0, allQueries = queriesAndResult.queryCells))
    }).unsafeRunSync()

    val expectedJsonStr: Json = queriesAndResult.asJson
    assertEquals(expectedJsonStr,result)
  }

  @Test
  def testProblemDigestWithError(): Unit = {

    val testNode = Node(
      name = new NodeName("testNodeName"),
      key = new NodeKey("testNodeKey"),
      userDomainName = new UserDomainName("testUserDomainName"),
      momId = "testNode1",
      adminEmail = "email@foo",
    )

    val networkId = 4L

    val researcher = Researcher.createWithRepeatableId(
      userName = researcherUser.username,
      userDomainName = researcherUser.domain,
      nodeId = testNode.id
    )

    val queryWithId = Query.create(
      id = networkId,
      queryDefinition = QueryDefinition.standIn,
      breakdownNames = Seq.empty,
      queryName = "my query name",
      queryNotes = None,
      queryFaved = false,
      nodeOfOriginId = testNode.id,
      researcherId = researcher.id,
    )

    case class ErrorRunningQuery(t: Throwable) extends AbstractProblem(ProblemSources.Qep){
      override def logLevel: Level = Level.WARN

      override def summary: String = "Error running query"

      override def description: String = "An error occurred when running query"
    }

    val problem = ErrorRunningQuery(new Exception("This is a query with an error"))

    QepQueryDb.db.insertQueryIO(queryWithId,researcher).unsafeRunSync()

    val storeQueryStatus = UpdateQueryAtQepWithError(queryWithId,problem)

    QepQueryDb.db.updateQepQueryIO(storeQueryStatus).unsafeRunSync()

    val queryResultTableRequest: Request[IO] = Request(method = Method.GET,
      uri = Uri.unsafeFromString(s"/queryResult?networkId=$networkId&timeoutSeconds=1"),
      headers = Headers(Authorization(BasicCredentials(researcherUser.username, "kapow"))))

    extractResponse(queryResultTableRequest)
    import io.circe.generic.auto.exportDecoder

    val response: Response[IO] = extractResponse(queryResultTableRequest)
    val resultJson: Json = response.as[Json].unsafeRunSync()
    val result = resultJson.as[PreviousQueriesAndResult]
    val dataVersion: Time = result.map{_.selectedQuery.get.dataVersion}.getOrElse{throw new Exception(s"Could not translate $result into PreviousQueriesAndResult")}

    val expectedJson: Json = getQueryResultIO(researcherUser,networkId, None).unsafeRunSync()
    val expected = expectedJson.as[PreviousQueriesAndResult].map{ pr =>
      pr.copy(selectedQuery = Option(pr.selectedQuery.get.copy(dataVersion = dataVersion)))
    }

    assertEquals(expected,result)
    assertEquals(expectedJson.findAllByKey("selectedQuery").head.findAllByKey("problemDigest").head,
      resultJson.findAllByKey("selectedQuery").head.findAllByKey("problemDigest").head)
  }

  @Test
  def testOkStartQuery(): Unit = {
    val body =
      """{
        | "name": "Query Rest",
        | "notes": null,
        | "faved" : false,
        | "projectId": "SHRINE",
        | "queryTiming": "ANY",
        | "dataDistributionTypes" : [],
        | "conceptGroups": [
        |   {
        |     "concepts": [
        |       {
        |         "displayName": "Female",
        |         "path": "\\\\SHRINE\\SHRINE\\Demographics\\Sex\\Female\\"
        |       }
        |     ],
        |     "isExcluded": false,
        |     "options": {
        |       "occurrences": 1,
        |       "startDate": 1575559140000,
        |       "endDate": 1576163940000
        |     }
        |   }
        | ]
        |}""".stripMargin

    val runQueryRequest: Request[IO] = Request(method = Method.POST,
      uri = Uri.unsafeFromString(s"/startQuery"),
      headers = Headers(Authorization(BasicCredentials(researcherUser.username, "kapow")))).withEntity(body)

    val response: Response[IO] = extractResponse(runQueryRequest)

    assertEquals(Ok, response.status)
    val entityText = response.as[String].unsafeRunSync()

    /*
    entityText should be something like:

    {
      "networkQueryId" : "6475474393885852204",
      "status" : "RECEIVED_BY_QEP"
    }
     */
    assert(entityText.contains("networkQueryId"))
    assert(entityText.contains(""""status" : "RECEIVED_BY_QEP""""))
  }

  @Test
  def testUnauthorizedUser(): Unit = {
    val body = """{
                 | "name": "Query Rest",
                 | "projectId": "SHRINE",
                 | "queryTiming": "ANY",
                 | "dataDistributionTypes" : [],
                 | "conceptGroups": [
                 |   {
                 |     "concepts": [
                 |       {
                 |         "displayName": "Female",
                 |         "path": "\\\\SHRINE\\SHIRNE\\Demographics\\Sex\\Female\\",
                 |         "constraint": {
                 |        	"constraintType": "LT",
                 |        	"unit": "mmol/L",
                 |        	"value": ["1"]
                 |         }
                 |       }
                 |     ],
                 |     "isExcluded": false,
                 |     "options": {
                 |       "occurrences": 1
                 |     }
                 |   }
                 | ]
                 |}""".stripMargin

    val runQueryRequest: Request[IO] = Request(method = Method.POST,
      uri = Uri.unsafeFromString(s"/startQuery"),
      headers = Headers(Authorization(BasicCredentials(researcherUser.username, "kapow2")))).withEntity(body)

    val response: Response[IO] = extractResponse(runQueryRequest)

    assertEquals(Unauthorized,response.status)
    val entityText = response.as[String].unsafeRunSync()
    assertEquals("Username or password does not match credentials.",entityText)
  }

  @Test
  def testBadRequestForBrokenJson(): Unit = {
    val body =
      """{
        | "name": "Query Rest",
        | "projectId": "SHRINE",
        | "queryTiming": "ANY",
        | "dataDistributionTypes" : [],
        | "conceptGroups": [
        |   {
        |     "concepts": [
        |       {
        |         "displayName": "Female",
        |         "path": "\\\\SHRINE\\SHRINE\\Demographics\\Sex\\Female\\"
        |       }
        |     ],
        |     "isExcluded": false,
        |     "options": {
        |       "occurrences": 1,
        |       "startDate": 1575559140000,
        |       "endDate": 1576163940000
        |     }
        |   }
        | ]
        |""".stripMargin //no final closing bracket

    val runQueryRequest: Request[IO] = Request(method = Method.POST,
      uri = Uri.unsafeFromString(s"/startQuery"),
      headers = Headers(Authorization(BasicCredentials(researcherUser.username, "kapow")))).withEntity(body)

    val response: Response[IO] = extractResponse(runQueryRequest)

    assertEquals(BadRequest, response.status)
    val entityText = response.as[String].unsafeRunSync()
    assertEquals("""Error running query: Malformed message body: Invalid JSON""", entityText)
  }

  @Test
  def testBadRequestForWrongJson(): Unit = {
    val body = //no name field, due to typo
      """{
        | "typo": "Query Rest",
        | "projectId": "SHRINE",
        | "queryTiming": "ANY",
        | "dataDistributionTypes" : [],
        | "conceptGroups": [
        |   {
        |     "concepts": [
        |       {
        |         "displayName": "Female",
        |         "path": "\\\\SHRINE\\SHRINE\\Demographics\\Sex\\Female\\"
        |       }
        |     ],
        |     "isExcluded": false,
        |     "options": {
        |       "occurrences": 1,
        |       "startDate": 1575559140000,
        |       "endDate": 1576163940000
        |     }
        |   }
        | ]
        |}""".stripMargin

    val runQueryRequest: Request[IO] = Request(method = Method.POST,
      uri = Uri.unsafeFromString(s"/startQuery"),
      headers = Headers(Authorization(BasicCredentials(researcherUser.username, "kapow")))).withEntity(body)

    val response: Response[IO] = extractResponse(runQueryRequest)

    assertEquals(BadRequest, response.status)
    val entityText = response.as[String].unsafeRunSync()
    assert(entityText.contains("""Error running query: Invalid message body: Could not decode JSON:"""))
  }

  @Test
  def testBadRequestForWrongJsonFromReverseEngineering(): Unit = {
    val body = //no name field, due to typo
      """{
        |    "name": "@09:59:32",
        |    "dataDistributionTypes" : [],
        |    "conceptGroups": [
        |        {
        |            "isExcluded": false,
        |            "options": {
        |                "startDate": null,
        |                "endDate": null,
        |                "occurrences": 1
        |            },
        |            "concepts": [
        |                {
        |                    "displayName": "  18-34 years old",
        |                    "path": \\\\i2b2_DEMO\\i2b2\\Demographics\\Age\\18-34 years old\\,
        |                    "constraint": null
        |                }
        |            ]
        |        },
        |        {
        |            "isExcluded": false,
        |            "options": {
        |                "startDate": null,
        |                "endDate": null,
        |                "occurrences": 1
        |            },
        |            "concepts": [
        |                {
        |                    "displayName": "Diseases of the digestive system (k00-k94)",
        |                    "path": \\\\ICD10_ICD9\\Diagnoses\\(K00-K94) Dise~rl1r\\,
        |                    "constraint": null
        |                }
        |            ]
        |        }
        |    ],
        |    "timeline": null
        |}""".stripMargin

    val runQueryRequest: Request[IO] = Request(method = Method.POST,
      uri = Uri.unsafeFromString(s"/startQuery"),
      headers = Headers(Authorization(BasicCredentials(researcherUser.username, "kapow")))).withEntity(body)

    val response: Response[IO] = extractResponse(runQueryRequest)

    assertEquals(BadRequest, response.status)
    val entityText = response.as[String].unsafeRunSync()
    assertEquals("""Error running query: Malformed message body: Invalid JSON""", entityText)
  }

  @Test
  def testBadRequestIfPanelStartDateBeforeEndDate(): Unit = {
    val body = """{
                 | "name": "Query Rest",
                 | "projectId": "SHRINE",
                 | "queryTiming": "ANY",
                 | "dataDistributionTypes" : [],
                 | "conceptGroups": [
                 |   {
                 |     "concepts": [
                 |       {
                 |         "displayName": "Female",
                 |         "path": "\\\\SHRINE\\SHRINE\\Demographics\\Sex\\Female\\"
                 |       }
                 |     ],
                 |     "isExcluded": false,
                 |     "options": {
                 |       "occurrences": 1,
                 |       "startDate": 1576163940000,
                 |       "endDate": 1575559140000
                 |     }
                 |   }
                 | ]
                 |}""".stripMargin

    val runQueryRequest: Request[IO] = Request(method = Method.POST,
      uri = Uri.unsafeFromString(s"/startQuery"),
      headers = Headers(Authorization(BasicCredentials(researcherUser.username, "kapow")))).withEntity(body)

    val response: Response[IO] = extractResponse(runQueryRequest)

    assertEquals(BadRequest,response.status)
    val entityText = response.as[String].unsafeRunSync()
    assertEquals("Error running query: Start date: 1576163940000 must be before endDate: 1575559140000.",entityText)
  }

  @Test
  def testBadRequestIfInvalidQueryName(): Unit = {
    def bodyTemplate(queryName: String): String = s"""{
                      | "name": "$queryName",
                      | "notes": null,
                      | "faved": false,
                      | "projectId": "SHRINE",
                      | "queryTiming": "ANY",
                      | "dataDistributionTypes" : [],
                      | "conceptGroups": [
                      |   {
                      |     "concepts": [
                      |       {
                      |         "displayName": "Female",
                      |         "path": "\\\\\\\\SHRINE\\\\SHRINE\\\\Demographics\\\\Sex\\\\Female\\\\"
                      |       }
                      |     ],
                      |     "isExcluded": false,
                      |     "options": {
                      |       "occurrences": 1,
                      |       "startDate": 1576163940000,
                      |       "endDate": 1576559140000,
                      |       "linkedBy" : null
                      |     }
                      |   }
                      | ]
                      |}""".stripMargin

    def test400RequestWithBody(body: String, errorText: String): Unit = {
      val runQueryRequest: Request[IO] = Request(method = Method.POST,
        uri = Uri.unsafeFromString(s"/startQuery"),
        headers = Headers(Authorization(BasicCredentials(researcherUser.username, "kapow")))).withEntity(body)
      val response: Response[IO] = extractResponse(runQueryRequest)

      assertEquals(BadRequest,response.status)
      val entityText = response.as[String].unsafeRunSync()
      assertEquals(errorText,entityText)
    }

    def queryNameErrorResponseText(trimmedQueryName: String, reasonFailed: String): String = {
      s"""Error running query: Invalid input: "$trimmedQueryName"\nReason: $reasonFailed"""
    }

    val longQueryName = "a" * 250
    test400RequestWithBody(bodyTemplate(longQueryName), queryNameErrorResponseText(longQueryName, "name must be less than 250 characters"))
    test400RequestWithBody(bodyTemplate(""), queryNameErrorResponseText("", "name must not be empty"))
    test400RequestWithBody(bodyTemplate("    \\t\\t\\n "), queryNameErrorResponseText("    \t\t\n ", "name must not start with a space"))

  }

  @Test
  def testAcceptAValidQueryName(): Unit = {
    def testValidName(validName: String): Unit = {
      try {
        val validQuery = BasicQuery(
          name = validName,
          notes = Some("some notes"),
          faved = false,
          dataDistributionTypes = Set.empty,
          conceptGroups = Seq.empty,
          timeline = None
        )

        assertEquals(validName,validQuery.name)
      } catch {
        case e: InvalidTextInputException => fail("Threw InvalidTextInputException for valid query name.", e)
        case e: Throwable => fail("Threw unexpected exception", e)
      }
    }

    testValidName("normalName1")
    testValidName("(:@_-) all valid characters ()@:_-")
    testValidName("!")
    testValidName("~{}09|zAaZ")
  }




  @Test
  def testValidateValueTypeofLabValues(): Unit = {
    val body = """{
                 | "name": "Query Rest",
                 | "projectId": "SHRINE",
                 | "queryTiming": "ANY",
                 | "dataDistributionTypes" : [],
                 | "conceptGroups": [
                 |   {
                 |     "concepts": [
                 |       {
                 |         "displayName": "Female",
                 |         "path": "\\\\SHRINE\\SHIRNE\\Demographics\\Sex\\Female\\",
                 |         "constraint": {
                 |        	"constraintType": "BADCONSTRAINTTYPE",
                 |        	"unit": "mmol/L",
                 |        	"value": ["10"]
                 |         }
                 |       }
                 |     ],
                 |     "isExcluded": false,
                 |     "options": {
                 |       "occurrences": 1,
                 |       "linkedBy" : null
                 |     }
                 |   }
                 | ]
                 |}""".stripMargin

    val runQueryRequest: Request[IO] = Request(method = Method.POST,
      uri = Uri.unsafeFromString(s"/startQuery"),
      headers = Headers(Authorization(BasicCredentials(researcherUser.username, "kapow")))).withEntity(body)

    val response: Response[IO] = extractResponse(runQueryRequest)

    assertEquals(BadRequest,response.status)
    val entityText = response.as[String].unsafeRunSync()
    assertEquals("Error running query: Invalid constraintType BADCONSTRAINTTYPE. Expected either NORMAL, LOW, HIGH, EQ, LT, GT, LE, GE, or BETWEEN.",entityText)
  }

  @Test
  def testAggregateSiteAndPatientCount(): Unit = {
    val networkId = qep2Query.networkId
    QepQueryDb.db.insertQepQuery(qep2Query)
    QepQueryDb.db.insertQepResultRow(qepResultRowFromHarvard)
    QepQueryDb.db.insertQepResultRow(qepResultRowFromNU)

    val queryResultTableRequest: Request[IO] = Request(method = Method.GET,
      uri = Uri.unsafeFromString(s"/queryResult?networkId=$networkId"),
      headers = Headers(Authorization(BasicCredentials(researcherUser.username, "kapow"))))

    val response: Response[IO] = extractResponse(queryResultTableRequest)

    assertEquals(Ok,response.status)
    val resultJson: Json = response.as[Json].unsafeRunSync()
    val resultStr: String = resultJson.toString()

    val siteCountText: String = "\"siteCount\" : 2"
    val patientCountText: String = "\"patientCount\" : 110020"
    assert(resultStr.contains(siteCountText))
    assert(resultStr.contains(patientCountText))

    import io.circe.generic.auto.exportDecoder
    val result = resultJson.as[PreviousQueriesAndResult]
    val dataVersion: Time = result.map{_.selectedQuery.get.dataVersion}.getOrElse{throw new Exception(s"Could not translate $result into PreviousQueriesAndResult")}

    val expectedResponse = s"""{
                              |  "rowCount" : 1,
                              |  "rowOffset" : 0,
                              |  "selectedQuery" : {
                              |    "query" : {
                              |      "networkId" : "2",
                              |      "queryName" : "Another Query",
                              |      "queryNotes" : null,
                              |      "dateCreated" : 1556572887469,
                              |      "changeDate" : 1556572887469,
                              |      "queryFaved" : false,
                              |      "status" : "Completed",
                              |      "internalStatus" : "Sent to Adapters",
                              |      "isQueryError" : false,
                              |      "isResultsError" : false,
                              |      "isQueryComplete" : true,
                              |      "problemDigest" : null,
                              |      "observed" : true,
                              |      "queryAsHtmlString" : "<span class=\\\"criteriaPanelSeparator\\\">Find patients</span> <span class=\\\"criteriaPanelSeparator\\\"> with</span> (Demographics 10-17 years old) or (Demographics No Information) starting from 03/01/2020 to 03/06/2020 and occurs at least 6 times<br/><span class=\\\"criteriaPanelSeparator\\\">and</span> <span class=\\\"criteriaPanelSeparator\\\"> without</span> (Demographics Black or African American) or (Demographics 85-89 years old)<br/><span class=\\\"criteriaPanelSeparator\\\">and</span> <span class=\\\"criteriaPanelSeparator\\\"> with</span> (Laboratory Tests Bicarbonate in Blood abnormal flag high)"
                              |    },
                              |    "results" : [
                              |      {
                              |        "resultId" : 502,
                              |        "networkQueryId" : 2,
                              |        "instanceId" : 100,
                              |        "adapterNode" : "HARVARD",
                              |        "count" : 30000,
                              |        "status" : "Completed",
                              |        "internalStatus" : "Result From CRC",
                              |        "statusMessage" : null,
                              |        "changeDate" : 1556572887664,
                              |        "breakdowns" : [
                              |        ],
                              |        "problemDigest" : null,
                              |        "resultMetadata" : {
                              |          "obfuscatingParameters" : {
                              |            "binSize" : 5,
                              |            "stdDev" : 6.5,
                              |            "noiseClamp" : 10,
                              |            "lowLimit" : 10
                              |          },
                              |          "custom" : null
                              |        }
                              |      },
                              |      {
                              |        "resultId" : 501,
                              |        "networkQueryId" : 2,
                              |        "instanceId" : 120,
                              |        "adapterNode" : "NU",
                              |        "count" : 80000,
                              |        "status" : "Completed",
                              |        "internalStatus" : "Result From CRC",
                              |        "statusMessage" : null,
                              |        "changeDate" : 1556572887662,
                              |        "breakdowns" : [
                              |        ],
                              |        "problemDigest" : null,
                              |        "resultMetadata" : {
                              |          "obfuscatingParameters" : {
                              |            "binSize" : 5,
                              |            "stdDev" : 6.5,
                              |            "noiseClamp" : 10,
                              |            "lowLimit" : 10
                              |          },
                              |          "custom" : null
                              |        }
                              |      }
                              |    ],
                              |    "aggregateDemographics" : [
                              |    ],
                              |    "siteCount" : 2,
                              |    "patientCount" : 110020,
                              |    "isComplete" : true,
                              |    "dataVersion" : $dataVersion
                              |  },
                              |  "isAllQueriesComplete" : true,
                              |  "allQueries" : [
                              |    {
                              |      "networkId" : "2",
                              |      "queryName" : "Another Query",
                              |      "queryNotes" : null,
                              |      "dateCreated" : 1556572887469,
                              |      "changeDate" : 1556572887469,
                              |      "queryFaved" : false,
                              |      "status" : "Completed",
                              |      "internalStatus" : "Sent to Adapters",
                              |      "isQueryError" : false,
                              |      "isResultsError" : false,
                              |      "isQueryComplete" : true,
                              |      "problemDigest" : null,
                              |      "observed" : true,
                              |      "queryAsHtmlString" : null
                              |    }
                              |  ]
                              |}""".stripMargin

    assertEquals(expectedResponse,resultStr)
  }


  ////////
  ////// Migrate tests for fav message below to query notes
  ///////


  @Test
  def testFavQuery() : Unit = {
    QepQueryDb.db.insertQepQuery(qep2Query)
    QepQueryDb.db.insertQepResultRow(qepResultRowFromHarvard)
    QepQueryDb.db.insertQepResultRow(qepResultRowFromNU)

    val favData: FavData = FavData(faved = true)

    val uri = Uri.unsafeFromString(s"/changeQueryFav/${qep2Query.networkId}")
    val favQueryRequest: Request[IO] = Request(method = Method.POST,
      headers = Headers(Authorization(BasicCredentials(researcherUser.username, "kapow")))).withUri(uri)
      .withEntity(favData.asJson.toString)

    val response: Response[IO] = extractResponse(favQueryRequest)

    assertEquals(Ok,response.status)

    val queryResultTableRequest: Request[IO] = Request(method = Method.GET,
      uri = Uri.unsafeFromString(s"/queryResult?networkId=${qep2Query.networkId}"),
      headers = Headers(Authorization(BasicCredentials(researcherUser.username, "kapow"))))

    val expectFavText = s""""queryFaved" : true,"""
    val qresponse: Response[IO] = extractResponse(queryResultTableRequest)
    val qresult: Json = qresponse.as[Json].unsafeRunSync()

    assert(qresult.toString().contains(expectFavText))
  }

  @Test
  def testUnfavQuery() : Unit = {
    QepQueryDb.db.insertQepQuery(qep2Query)
    QepQueryDb.db.insertQepResultRow(qepResultRowFromHarvard)
    QepQueryDb.db.insertQepResultRow(qepResultRowFromNU)

    val favData: FavData = FavData(faved = false)

    val uri = Uri.unsafeFromString(s"/changeQueryFav/${qep2Query.networkId}")
    val unfavQueryRequest: Request[IO] = Request(method = Method.POST,
      headers = Headers(Authorization(BasicCredentials(researcherUser.username, "kapow")))).withUri(uri)
      .withEntity(favData.asJson.toString)

    val response: Response[IO] = extractResponse(unfavQueryRequest)

    assertEquals(Ok,response.status)

    val queryResultTableRequest: Request[IO] = Request(method = Method.GET,
      uri = Uri.unsafeFromString(s"/queryResult?networkId=${qep2Query.networkId}"),
      headers = Headers(Authorization(BasicCredentials(researcherUser.username, "kapow"))))

    val expectFavText = s""""queryFaved" : false,"""
    val qresponse: Response[IO] = extractResponse(queryResultTableRequest)
    val qresult: Json = qresponse.as[Json].unsafeRunSync()

    assert(qresult.toString().contains(expectFavText))
  }

  // workhorse method for testing the validation of string-valued query fields (name and notes)
  // if expectedErrorMessage is None, it expects validation to pass
  // otherwise, it will expect a InvalidTextInputException to be thrown
  def executeStringInputValidationTest(newName:String, newNotes:Option[String], expectedErrorMessage:Option[String]) : Unit = {

    QepQueryDb.db.insertQepQuery(qep2Query)

    // Record the state of the database before the attempted, invalid change

    val queryResultTableRequest: Request[IO] = Request(method = Method.GET,
      uri = Uri.unsafeFromString(s"/queryResult?networkId=${qep2Query.networkId}"),
      headers = Headers(Authorization(BasicCredentials(researcherUser.username, "kapow"))))
    val gresponse: Response[IO] = extractResponse(queryResultTableRequest)
    val initialGresult: Json = gresponse.as[Json].unsafeRunSync()

    // try to update the name and notes of the existing query
    // bypass validation on newQueryNameAndNotesData, the only reason
    // we need it is to create the URI call's JSON payload easily. We could
    // alternatively hard-coded the JSON

    val newQueryNameAndNotesData = new QueryNameAndNotesData(newName, newNotes)
    val uri = Uri.unsafeFromString(s"/changeQueryNameAndNotes/${qep2Query.networkId}")
    val favQueryRequest: Request[IO] = Request(method = Method.POST,
      headers = Headers(Authorization(BasicCredentials(researcherUser.username, "kapow")))).withUri(uri)
      .withEntity(newQueryNameAndNotesData.asJson.toString)
    val response: Response[IO] = extractResponse(favQueryRequest)
    val responseContent = response.as[String].unsafeRunSync()

    // Evaluate the response

    expectedErrorMessage match {
      case None =>
        assertEquals(Ok, response.status)
      case s: Option[String] =>
        assertEquals(BadRequest, response.status)
        assertEquals(s.get, responseContent)
        // Assert that the state of the query in the database has not changed
        // BUT only if the expected error essage is not None
        val queryResultTableRequestAfter: Request[IO] = Request(method = Method.GET,
          uri = Uri.unsafeFromString(s"/queryResult?networkId=${qep2Query.networkId}"),
          headers = Headers(Authorization(BasicCredentials(researcherUser.username, "kapow"))))
        val gresponseAfter: Response[IO] = extractResponse(queryResultTableRequestAfter)
        val gresultAfter: Json = gresponseAfter.as[Json].unsafeRunSync()

        assertEquals(gresultAfter, initialGresult)
    }

  }

  @Test
  def testQueryNameValidationWithLeadingSpace() : Unit = {

    // add a trailing carriage return because there was a bug where it prevented the leading space from being correctly detected
    val newName = " some query name with leading space"
    val newNotes = None
    val expectedErrorMessage = Some(
      """Error changing query name and/or notes: Invalid input: " some query name with leading space"
        |Reason: name must not start with a space""".stripMargin
    )
    executeStringInputValidationTest(newName, newNotes, expectedErrorMessage)

  }

  @Test
  def testQueryNameValidationWithEmptyName() : Unit = {

    val newName = ""
    val newNotes = None
    val expectedErrorMessage = Some(
      """Error changing query name and/or notes: Invalid input: ""
      |Reason: name must not be empty""".stripMargin
    )
    executeStringInputValidationTest(newName, newNotes, expectedErrorMessage)

  }

  @Test
  def testQueryNameValidationWithReturns(): Unit = {

    val newName = "a\nb"
    val newNotes = None
    val expectedResponseContent = Some(
      """Error changing query name and/or notes: Invalid input: "a
        |b"
        |Reason: name must not contain new lines""".stripMargin
    )
    executeStringInputValidationTest(newName, newNotes, expectedResponseContent)

  }

  @Test
  def testQueryNameValidationWithLongName(): Unit = {

    val newName = "a" * 250
    val newNotes = None
    val expectedResponseContent = Some(
      """Error changing query name and/or notes: Invalid input: "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
      |Reason: name must be less than 250 characters""".stripMargin
    )
    executeStringInputValidationTest(newName, newNotes, expectedResponseContent)

    val newName2 = "a" * 249
    val newNotes2 = None
    // getting an unexpected error means that a validation error was not thrown
    val expectedResponseContent2 = None
    executeStringInputValidationTest(newName2, newNotes2, expectedResponseContent2)

  }

  @Test
  def testQueryNotesValidationWithEmptyNotes(): Unit = {

    val newName = "a"
    val newNotes = None
    executeStringInputValidationTest(newName, newNotes, None)

  }

  @Test
  def testQueryNotesValidationWithReturns(): Unit = {

    val newName = "a"
    val newNotes = Some("a\nb")
    executeStringInputValidationTest(newName, newNotes, None)

  }

  @Test
  def testQueryNotesValidationWithLongNotes(): Unit = {

    val newName = "1"
    val newNotes = Some("a" * 1000)
    val expectedResponseContent = Some(
      s"""Error changing query name and/or notes: Invalid input: "${"a"*1000}"
        |Reason: notes must be less than 1000 characters""".stripMargin
    )
    executeStringInputValidationTest(newName, newNotes, expectedResponseContent)

    val newName2 = "2"
    val newNotes2 = Some("a" * 999)
    // getting an unexpected error means that a validation error was not thrown
    val expectedResponseContent2 = None
    executeStringInputValidationTest(newName2, newNotes2, expectedResponseContent2)

  }

  @Test
  def testMultiTermQueryAsString(): Unit = {
    val networkId = qep2Query.networkId
    QepQueryDb.db.insertQepQuery(qep2Query)
    QepQueryDb.db.insertQepResultRow(qepResultRowFromHarvard)
    QepQueryDb.db.insertQepResultRow(qepResultRowFromNU)

    val queryResultTableRequest: Request[IO] = Request(method = Method.GET,
      uri = Uri.unsafeFromString(s"/queryResult?networkId=$networkId&timeoutSeconds=0"),
      headers = Headers(Authorization(BasicCredentials(researcherUser.username, "kapow"))))

    val response: Response[IO] = extractResponse(queryResultTableRequest)

    assertEquals(Ok,response.status)

    val resultJson: Json = response.as[Json].unsafeRunSync()

    val result: String = resultJson.toString()

    val expectedHtmlString = """"<span class=\"criteriaPanelSeparator\">Find patients</span> <span class=\"criteriaPanelSeparator\"> with</span> (Demographics 10-17 years old) or (Demographics No Information) starting from 03/01/2020 to 03/06/2020 and occurs at least 6 times<br/><span class=\"criteriaPanelSeparator\">and</span> <span class=\"criteriaPanelSeparator\"> without</span> (Demographics Black or African American) or (Demographics 85-89 years old)<br/><span class=\"criteriaPanelSeparator\">and</span> <span class=\"criteriaPanelSeparator\"> with</span> (Laboratory Tests Bicarbonate in Blood abnormal flag high)""""

    val expectedQueryAsString = s""""selectedQuery" : {
                                  |    "query" : {
                                  |      "networkId" : "2",
                                  |      "queryName" : "Another Query",
                                  |      "queryNotes" : null,
                                  |      "dateCreated" : 1556572887469,
                                  |      "changeDate" : 1556572887469,
                                  |      "queryFaved" : false,
                                  |      "status" : "Completed",
                                  |      "internalStatus" : "Sent to Adapters",
                                  |      "isQueryError" : false,
                                  |      "isResultsError" : false,
                                  |      "isQueryComplete" : true,
                                  |      "problemDigest" : null,
                                  |      "observed" : true,
                                  |      "queryAsHtmlString" : $expectedHtmlString
                                  |    }""".stripMargin

    assert(result.contains(expectedQueryAsString))
  }

  @Test
  def testTimelineQueryWithPopulationAsString(): Unit = {
    val networkId = timelineConstraintWithPopulationQepQuery.networkId
    QepQueryDb.db.insertQepQuery(timelineConstraintWithPopulationQepQuery)
    QepQueryDb.db.insertQepResultRow(qepResultRowFromUnsupported1)
    QepQueryDb.db.insertQepResultRow(qepResultRowFromUnsupported2)

    val queryResultTableRequest: Request[IO] = Request(method = Method.GET,
      uri = Uri.unsafeFromString(s"/queryResult?networkId=$networkId&timeoutSeconds=0"),
      headers = Headers(Authorization(BasicCredentials(researcherUser.username, "kapow"))))

    val response: Response[IO] = extractResponse(queryResultTableRequest)

    assertEquals(Ok,response.status)

    val resultJson: Json = response.as[Json].unsafeRunSync()

    val result: String = resultJson.toString()

    val expectedQueryAsHtmlString = """"<span class=\"criteriaPanelSeparator\">Find patients</span> <span class=\"criteriaPanelSeparator\"> with</span> (Demographics Female)<br/><span class=\"criteriaPanelSeparator\">when the start of any </span><span class=\"criteriaPanelSeparator\"> with</span> (Demographics Asian)<br/><span class=\"criteriaPanelSeparator\">occurs before the start of any </span><span class=\"criteriaPanelSeparator\"> with</span> (Demographics 18-34 years old)""""

    val expectedQueryAsString = s""""selectedQuery" : {
                                  |    "query" : {
                                  |      "networkId" : "300",
                                  |      "queryName" : "Temporal Constraint With Population Query",
                                  |      "queryNotes" : null,
                                  |      "dateCreated" : 1556572887574,
                                  |      "changeDate" : 1556572887572,
                                  |      "queryFaved" : false,
                                  |      "status" : "Submitted",
                                  |      "internalStatus" : "Received by Hub",
                                  |      "isQueryError" : false,
                                  |      "isResultsError" : false,
                                  |      "isQueryComplete" : false,
                                  |      "problemDigest" : null,
                                  |      "observed" : true,
                                  |      "queryAsHtmlString" : $expectedQueryAsHtmlString
                                  |    }""".stripMargin

    assert(result.contains(expectedQueryAsString))
  }

  @Test
  def testKeepAlive(): Unit = {
    val bearerToken: String =
      """{"username": "userSession", "sessionId": "1234567"}
      """.stripMargin

    val encodedBearerToken: String = Base64.getEncoder.encodeToString(bearerToken.getBytes)
    val request: Request[IO] = Request(method = Method.GET,
      uri = uri"/keepAlive",
      headers = Headers(Authorization(Credentials.Token(AuthScheme.Bearer, encodedBearerToken)))
    )

    val response: Response[IO]= extractResponse(request)

    assertEquals(Ok,response.status)
  }

  @Test
  def testGetQuery(): Unit = {
    QepQueryDb.db.insertQepQuery(qepQuery)

    //todo this test depends on the order tests are run. If it runs by itself then "id" : 1 instead of "id" : 6
    val expectedReloadedQueryJson =
      """{
        |  "name" : "NewTQuery",
        |  "conceptGroups" : [
        |    {
        |      "concepts" : [
        |        {
        |          "displayName" : "10-17 years old",
        |          "path" : "\\\\ACT_DEMO\\ACT\\Demographics\\Age\\10-17 years old\\",
        |          "conceptCategory" : "Demographic",
        |          "isLab" : false,
        |          "constraint" : null
        |        },
        |        {
        |          "displayName" : "No Information",
        |          "path" : "\\\\ACT_DEMO\\ACT\\Demographics\\Hispanic\\No Information\\",
        |          "conceptCategory" : "Demographic",
        |          "isLab" : false,
        |          "constraint" : null
        |        }
        |      ],
        |      "isExcluded" : false,
        |      "options" : {
        |        "startDate" : 1583038800000,
        |        "endDate" : 1583470800000,
        |        "occurrences" : 6,
        |        "linkedBy" : null
        |      }
        |    },
        |    {
        |      "concepts" : [
        |        {
        |          "displayName" : "Black or African American",
        |          "path" : "\\\\ACT_DEMO\\ACT\\Demographics\\Race\\Black or African American\\",
        |          "conceptCategory" : "Demographic",
        |          "isLab" : false,
        |          "constraint" : null
        |        },
        |        {
        |          "displayName" : "85-89 years old",
        |          "path" : "\\\\ACT_DEMO\\ACT\\Demographics\\Age\\85-89 years old\\",
        |          "conceptCategory" : "Demographic",
        |          "isLab" : false,
        |          "constraint" : null
        |        }
        |      ],
        |      "isExcluded" : true,
        |      "options" : {
        |        "startDate" : null,
        |        "endDate" : null,
        |        "occurrences" : 1,
        |        "linkedBy" : null
        |      }
        |    },
        |    {
        |      "concepts" : [
        |        {
        |          "displayName" : "Bicarbonate in Blood",
        |          "path" : "\\\\ACT_LAB\\ACT\\Labs\\LP40271-6\\LP19403-2\\LP46218-1\\1959-6\\",
        |          "conceptCategory" : "Laboratory Test",
        |          "isLab" : true,
        |          "constraint" : {
        |            "constraintType" : "HIGH",
        |            "value" : [
        |              "H"
        |            ],
        |            "unit" : null
        |          }
        |        }
        |      ],
        |      "isExcluded" : false,
        |      "options" : {
        |        "startDate" : null,
        |        "endDate" : null,
        |        "occurrences" : 1,
        |        "linkedBy" : null
        |      }
        |    }
        |  ],
        |  "timeline" : null,
        |  "hasUnsupportedFeatures" : false
        |}""".stripMargin

    val reloadRequest: Request[IO] = Request(method = Method.GET,
      uri = Uri.unsafeFromString(s"/query/${qepQuery.networkId}"),
      headers = Headers(Authorization(BasicCredentials(researcherUser.username, "kapow")))
    )

    val response: Response[IO] = extractResponse(reloadRequest)

    assertEquals(Ok,response.status)

    val resultJson: Json = response.as[Json].unsafeRunSync()
    val result: String = resultJson.toString()

    assertEquals(expectedReloadedQueryJson,result)
  }

  @Test
  def testGetQueryWithRootConcepts(): Unit = {
    QepQueryDb.db.insertQepQuery(qepQueryWithRootConcepts)

    //todo this test depends on the order tests are run. If it runs by itself then "id" : 1 instead of "id" : 13
    val expectedRootConceptsReloadJson: String =
      """{
        |  "name" : "NewTQuery",
        |  "conceptGroups" : [
        |    {
        |      "concepts" : [
        |        {
        |          "displayName" : "ACT Diagnoses ICD-10",
        |          "path" : "\\\\ACT_DX_ICD10_2018\\ACT\\Diagnosis\\ICD10\\V2_2018AA\\A20098492\\",
        |          "conceptCategory" : "Diagnosis",
        |          "isLab" : false,
        |          "constraint" : null
        |        },
        |        {
        |          "displayName" : "ACT Procedures HCPCS",
        |          "path" : "\\\\ACT_PX_HCPCS_2018\\ACT\\Procedures\\HCPCS\\V2_2018AA\\A13475665\\",
        |          "conceptCategory" : "Procedure",
        |          "isLab" : false,
        |          "constraint" : null
        |        },
        |        {
        |          "displayName" : "ACT Procedures ICD-10-PCS",
        |          "path" : "\\\\ACT_PX_ICD10_2018\\ACT\\Procedures\\ICD10\\V2_2018AA\\A16077350\\",
        |          "conceptCategory" : "Procedure",
        |          "isLab" : false,
        |          "constraint" : null
        |        },
        |        {
        |          "displayName" : "ACT Procedures   ICD-9-Proc",
        |          "path" : "\\\\ACT_PX_ICD9_2018\\ACT\\Procedures\\ICD9\\V2_2018AA\\A18090800\\A8352133\\",
        |          "conceptCategory" : "Procedure",
        |          "isLab" : false,
        |          "constraint" : null
        |        }
        |      ],
        |      "isExcluded" : true,
        |      "options" : {
        |        "startDate" : 1583101380000,
        |        "endDate" : 1583533380000,
        |        "occurrences" : 4,
        |        "linkedBy" : null
        |      }
        |    }
        |  ],
        |  "timeline" : null,
        |  "hasUnsupportedFeatures" : false
        |}""".stripMargin

    val reloadRequest: Request[IO] = Request(method = Method.GET,
      uri = Uri.unsafeFromString(s"/query/${qepQueryWithRootConcepts.networkId}"),
      headers = Headers(Authorization(BasicCredentials(researcherUser.username, "kapow")))
    )

    val response: Response[IO] = extractResponse(reloadRequest)

    assertEquals(Ok,response.status)

    val resultJson: Json = response.as[Json].unsafeRunSync()
    val result: String = resultJson.toString()

    assertEquals(expectedRootConceptsReloadJson,result)
  }

  @Test
  def testReloadQuery(): Unit = {
    QepQueryDb.db.insertQepQuery(qepQuery)

    val reloadRequest: Request[IO] = Request(method = Method.GET,
      uri = Uri.unsafeFromString(s"/query/${qepQuery.networkId}"),
      headers = Headers(Authorization(BasicCredentials(researcherUser.username, "kapow")))
    )

    val response: Response[IO] = extractResponse(reloadRequest)

    assertEquals(Ok,response.status)

    val resultJson: Json = response.as[Json].unsafeRunSync()
    @unused
    val result: String = resultJson.toString()
  }

  @Test
  def testCensorUserSessionToken():Unit = {
    val userTokenString = UserSessionToken("username","sessionId", Some(100)).toString
    val expectedString = "UserSessionToken(username,REDACTED,Some(100))"

    val result = LogCensor.censor(userTokenString)
    assertEquals(expectedString, result)

    val userTokenStringNoTimeout = UserSessionToken("username","sessionId", None).toString
    val expectedStringNoTimeout = "UserSessionToken(username,REDACTED,None)"

    val resultNoTimeout = LogCensor.censor(userTokenStringNoTimeout)
    assertEquals(expectedStringNoTimeout, resultNoTimeout)
  }

  @Test
  def testDemographicCSV():Unit = {
    val testNode = Node(
      name = new NodeName("testNodeName"),
      key = new NodeKey("testNodeKey"),
      userDomainName = new UserDomainName("testUserDomainName"),
      adminEmail = "email@foo",
      momId = "testNode1",
    )

    val researcher = Researcher.createWithRepeatableId(
      userName = researcherUser.username,
      userDomainName = researcherUser.domain,
      nodeId = testNode.id
    )

    val networkId = 1L
    val queryWithId = Query.create(
      id = networkId,
      queryDefinition = QueryDefinition.standIn,
      breakdownNames = Seq.empty,
      queryName = "my query name",
      queryNotes = None,
      queryFaved = false,
      nodeOfOriginId = testNode.id,
      researcherId = researcher.id,
    )
    QepQueryDb.db.insertQueryIO(queryWithId, researcher).unsafeRunSync()

    val resultMetadata = ResultMetadata(Option(ObfuscatingParameters(5,6.5,10,10)))
    val resultInProgress1: ResultProgress = ResultProgress(
      queryId = new QueryId(qepQuery.networkId),
      adapterNodeId = testNode.id,
      adapterNodeName = testNode.name,
      status = ResultStatus.ResultFromCRC,
      statusMessage = None,
      resultMetadata = resultMetadata
    )
    val crcResultWithBreakdowns1: CountResult = resultInProgress1.toCrcResult(count = 2100,
        crcQueryInstanceId = 3L,
        breakdowns = Some(Breakdowns(scala.collection.immutable.Seq("PATIENT_GENDER_COUNT_XML" -> scala.collection.immutable.Seq(
            "female" -> 100,
            "male" -> 100,
            "unreported" -> -1
          )))),
        resultMetadata = resultInProgress1.resultMetadata,
        adapterTime = DateStamp.now
      )
    QepQueryDb.db.insertQueryResultIO(crcResultWithBreakdowns1).unsafeRunSync()

    val testNode2 = Node(
      name = new NodeName("testNodeName2"),
      key = new NodeKey("testNodeKey2"),
      userDomainName = new UserDomainName("testUserDomainName2"),
      adminEmail = "email2@foo",
      momId = "testNode1",
    )

    val resultMetadata2 = ResultMetadata(Option(ObfuscatingParameters(5,6.5,9,9)))
    val resultInProgress2: ResultProgress = ResultProgress(queryId = new QueryId(qepQuery.networkId), adapterNodeId = testNode2.id, adapterNodeName = testNode2.name, status = ResultStatus.ResultFromCRC, statusMessage = None, resultMetadata = resultMetadata2)
    val crcResultWithBreakdowns2: CountResult = resultInProgress2.toCrcResult(count = 2200, crcQueryInstanceId = 3L, breakdowns = Some(Breakdowns(scala.collection.immutable.Seq("PATIENT_GENDER_COUNT_XML" -> scala.collection.immutable.Seq(
            "female" -> 200,
            "other" -> -1
          )))), resultMetadata = resultInProgress2.resultMetadata,
      adapterTime = DateStamp.now)
    QepQueryDb.db.insertQueryResultIO(crcResultWithBreakdowns2).unsafeRunSync()

    val testNode3 = Node(
      name = new NodeName("testNodeName3"),
      key = new NodeKey("testNodeKey3"),
      userDomainName = new UserDomainName("testUserDomainName3"),
      adminEmail = "email3@foo",
      momId = "testNode1",
    )

    val resultMetadata3 = ResultMetadata(Option(ObfuscatingParameters(5,6.5,8,8)))
    val resultInProgress3: ResultProgress = ResultProgress(queryId = new QueryId(qepQuery.networkId), adapterNodeId = testNode3.id, adapterNodeName = testNode3.name, status = ResultStatus.ResultFromCRC, statusMessage = None, resultMetadata = resultMetadata3)
    val crcResultWithBreakdowns3: CountResult = resultInProgress3.toCrcResult(count = 2300, crcQueryInstanceId = 3L, breakdowns = Some(Breakdowns(scala.collection.immutable.Seq("PATIENT_GENDER_COUNT_XML" -> scala.collection.immutable.Seq(
            "female" -> 300,
            "male" -> 300
          )))), resultMetadata = resultInProgress3.resultMetadata,
      adapterTime = DateStamp.now)
    QepQueryDb.db.insertQueryResultIO(crcResultWithBreakdowns3).unsafeRunSync()

    val testNode10 = Node(
      name = new NodeName("testNodeName10"),
      key = new NodeKey("testNodeKey10"),
      userDomainName = new UserDomainName("testUserDomainName10"),
      adminEmail = "email10@foo",
      momId = "testNode1",
    )

    val resultMetadata4 = ResultMetadata(obfuscatingParameters = None)
    val incompleteResultInProgress1: ResultProgress = ResultProgress(queryId = new QueryId(qepQuery.networkId), adapterNodeId = testNode10.id, adapterNodeName = testNode10.name, status = ResultStatus.SentToAdapter, statusMessage = None, resultMetadata = resultMetadata4)
    QepQueryDb.db.insertQueryResultIO(incompleteResultInProgress1).unsafeRunSync()

    val queryResultTableRequest: Request[IO] = Request(method = Method.POST,
      uri = Uri.unsafeFromString(s"/demographic/csv?queryId=${qepQuery.networkId}"),
      headers = Headers(Authorization(BasicCredentials(researcherUser.username, "kapow"))))

    val response: Response[IO] = extractResponse(queryResultTableRequest)

    assertEquals(Ok,response.status)

    val csvResult: String = response.as[String].unsafeRunSync()

    val expectedCsv = readFile("/demographic_distribution.csv")

    assertEquals(expectedCsv,csvResult)
  }

  @Test
  def testCountCSV():Unit = {
    val testNode = Node(
      name = new NodeName("Node 1"),
      key = new NodeKey("node1Key"),
      userDomainName = new UserDomainName("domainName1"),
      adminEmail = "email@foo",
      momId = "testNode1",
    )

    val researcher = Researcher.createWithRepeatableId(
      userName = researcherUser.username,
      userDomainName = researcherUser.domain,
      nodeId = testNode.id
    )

    val networkId = 1L
    val queryWithId = Query.create(
      id = networkId,
      queryDefinition = QueryDefinition.standIn,
      breakdownNames = Seq.empty,
      queryName = "my query name",
      queryNotes = None,
      queryFaved = false,
      nodeOfOriginId = testNode.id,
      researcherId = researcher.id,
    )

    QepQueryDb.db.insertQueryIO(queryWithId, researcher).unsafeRunSync()

    val resultMetadata = ResultMetadata(Option(ObfuscatingParameters(5,6.5,10,10)))
    val incompleteResult: ResultProgress = ResultProgress(queryId = new QueryId(qepQuery.networkId), adapterNodeId = testNode.id, adapterNodeName = testNode.name, status = ResultStatus.SubmittedToCRC, statusMessage = None, resultMetadata = resultMetadata)

    QepQueryDb.db.insertQueryResultIO(incompleteResult).unsafeRunSync()

    val testNode10 = Node(
      name = new NodeName("Node 10"),
      key = new NodeKey("node10Key"),
      userDomainName = new UserDomainName("domainName10"),
      adminEmail = "email10@foo",
      momId = "testNode1",
    )

    val resultMetadata1 = ResultMetadata(Option(ObfuscatingParameters(5,6.5,10,10)))
    val resultInProgress10: ResultProgress = ResultProgress(
      queryId = new QueryId(qepQuery.networkId),
      adapterNodeId = testNode10.id,
      adapterNodeName = testNode10.name,
      status = ResultStatus.SubmittedToCRC,
      statusMessage = None,
      resultMetadata = resultMetadata
    )
    val errorResult10 = resultInProgress10.toCrcError(TestProblem(), Option(5L),statusMessage=Some("Error"), resultMetadata = resultMetadata1)
    QepQueryDb.db.insertQueryResultIO(errorResult10).unsafeRunSync()

    val testNode2 = Node(
      name = new NodeName("Node 2"),
      key = new NodeKey("node2Key"),
      userDomainName = new UserDomainName("domainName2"),
      adminEmail = "email2@foo",
      momId = "testNode1",
    )
    val resultMetadata2 = ResultMetadata(Option(ObfuscatingParameters(5,6.5,7,7)))
    val resultInProgress: ResultProgress = ResultProgress(queryId = new QueryId(qepQuery.networkId), adapterNodeId = testNode2.id, adapterNodeName = testNode2.name, status = ResultStatus.ResultFromCRC, statusMessage = None, resultMetadata = resultMetadata2)
    val completedResult = resultInProgress.toCrcResult(count = 2000, crcQueryInstanceId = 23L, breakdowns = None, resultMetadata = resultMetadata2, adapterTime = DateStamp.now)

    QepQueryDb.db.insertQueryResultIO(completedResult).unsafeRunSync()

    val testNode3 = Node(
      name = new NodeName("Node 3"),
      key = new NodeKey("node3Key"),
      userDomainName = new UserDomainName("domainName3"),
      adminEmail = "email3@foo",
      momId = "testNode1",
    )

    val resultMetadata3 = ResultMetadata(Option(ObfuscatingParameters(5,6.5,8,8)))

    val resultInProgress3: ResultProgress = ResultProgress(queryId = new QueryId(qepQuery.networkId), adapterNodeId = testNode3.id, adapterNodeName = testNode3.name, status = ResultStatus.ResultFromCRC, statusMessage = None, resultMetadata = resultMetadata3)
    val completedResult3 = resultInProgress3.toCrcResult(count = -1, crcQueryInstanceId = 23L, breakdowns = None, resultMetadata = resultMetadata3, adapterTime = DateStamp.now)

    QepQueryDb.db.insertQueryResultIO(completedResult3).unsafeRunSync()

    val queryResultTableRequest: Request[IO] = Request(method = Method.POST,
      uri = Uri.unsafeFromString(s"/count/csv?queryId=${qepQuery.networkId}"),
      headers = Headers(Authorization(BasicCredentials(researcherUser.username, "kapow"))))

    val response: Response[IO] = extractResponse(queryResultTableRequest)

    assertEquals(Ok,response.status)

    val csvResult: String = response.as[String].unsafeRunSync()

    val expectedCsv = readFile("/site_counts.csv")

    assertEquals(expectedCsv,csvResult)
  }

  private def getQueryDataIO(user: User,
                             limitOption : Option[Int] = None,
                             skipOption : Option[Int] = None,
                             sortByOption: Option[String] = None): IO[QueriesAndCount] ={
    val queriesAndCountIO: IO[(Int, Seq[QueryWithResultStatus])] = QepQueryDb.db.selectPreviousQueriesWithResultStatusIO(
      userName = user.username,
      domain = user.domain,
      skip = skipOption,
      limit = limitOption,
      sortBy = sortByOption
    )

    queriesAndCountIO.flatMap(queriesAndCount => {
      val queryCells: Seq[QueryCell] = queriesAndCount._2.map(q =>  {
        QueryCell(q)
      })

      IO(QueriesAndCount(queriesAndCount._1, queryCells))
    })
  }

  def getQueryResultIO(user: User,
                       networkQueryId: LongQueryId,
                       limitOption : Option[Int] = None,
                       skipOption : Option[Int] = None,
                       sortByOption: Option[String] = None,
                       sortSiteByOption: Option[String] = None): IO[Json] = {
    import io.circe.generic.auto.exportEncoder
    import io.circe.syntax.EncoderOps

    val sortSiteBy = sortSiteByOption.getOrElse("site.asc")
    for{
      queryResults <- QepQueryDb.db.selectResultsRowIO(networkQueryId, user, sortSiteBy)
      queriesAndCount <- getQueryDataIO(user, limitOption, skipOption, sortByOption)
      codeCategoryMap <- LuceneSearcher.getCodeCategoriesMap
    }
      yield{
        val resultsRow: ResultsRow = ResultsRow(queryResults, codeCategoryMap)
        val queriesAndResult: PreviousQueriesAndResult = PreviousQueriesAndResult(queriesAndCount.queryRowCount, skipOption.getOrElse(0), queriesAndCount.queryCells, Some(resultsRow))
        val resultJson = queriesAndResult.asJson
        resultJson
      }
  }


  def getPreviousQueryDataIO(user: User,
                             limitOption : Option[Int],
                             skipOption : Option[Int],
                             sortByOption: Option[String],
                             selectedQueryId: Option[LongQueryId] = None,
                             queryIdListOption: Option[List[LongQueryId]] = None): IO[QueriesAndCount] ={
    val queriesAndCountIO: IO[(Int, Seq[QueryWithResultStatus])] = QepQueryDb.db.selectPreviousQueriesWithResultStatusIO(
      userName = user.username,
      domain = user.domain,
      skip = skipOption,
      limit = limitOption,
      sortBy = sortByOption,
      selectedQueryId = selectedQueryId,
      queryIdListOption
    )

    queriesAndCountIO.map(queriesAndCount => {
      val queryCells: Seq[QueryCell] = queriesAndCount._2.map(q =>  {
        QueryCell(q)
      })

      QueriesAndCount(queriesAndCount._1, queryCells)
    })
  }

  def readFile(filename: String): String = {
    val source = Source.fromFile(getClass.getResource(filename).getFile)
    try source.mkString finally source.close()
  }

  val researcherUserName: String = "ben"
  val researcherFullName: String  = researcherUserName

  lazy val researcherUser: User = User(
    fullName = researcherUserName,
    username = researcherFullName,
    domain = "domain",
    credential = Credential("researcher's password",isToken = false),
    params = Map(),
    rolesByProject = Map()
  )

  val queryReceiveTime: Long = 1556572887669L

  val exampleQueryDefinition: QueryDefinition = QueryDefinition.allOf(Seq(
    V2ConceptGroup.atLeastOneOf(Seq(
      V2Concept("10-17 years old","""\\ACT_DEMO\ACT\Demographics\Age\10-17 years old\""",None),
      V2Concept("No Information","""\\ACT_DEMO\ACT\Demographics\Hispanic\No Information\""",None),
    )).withOccursAtLeast(6).withStartDate(new DateStamp(1583038800000L)).withEndDate(new DateStamp(1583470800000L)),
    V2ConceptGroup.noneOf(Seq(
      V2Concept("Black or African American","""\\ACT_DEMO\ACT\Demographics\Race\Black or African American\""",None),
      V2Concept("85-89 years old","""\\ACT_DEMO\ACT\Demographics\Age\85-89 years old\""",None),
    )),
    V2ConceptGroup.atLeastOneOf(Seq(
      V2Concept("Bicarbonate in Blood","""\\ACT_LAB\ACT\Labs\LP40271-6\LP19403-2\LP46218-1\1959-6\""",Option(FlagConstraints.High))
    ))
  ))

  val exampleQuery:Query = Query.create(
    queryDefinition = exampleQueryDefinition,
    breakdownNames = Seq.empty,
    queryName = "NewTQuery",
    queryNotes = None,
    queryFaved = false,
    nodeOfOriginId = new NodeId(0L),
    researcherId = new ResearcherId(1L),
  )

  val timelineQueryDefinition: QueryDefinition = QueryDefinition.allOf(Seq(
    V2ConceptGroup.atLeastOneOf(Seq(
      V2Concept("Female","""\\ACT_DEMO\ACT\Demographics\Sex\Female\""",None)
    )) ,
    V2Timeline(
      V2ConceptGroup.atLeastOneOf(Seq(
        V2Concept("Asian","""\\ACT_DEMO\ACT\Demographics\Race\Asian\""",None)
      ))
    ).appendWithEvent(
      event = V2ConceptGroup.atLeastOneOf(Seq(
        V2Concept("18-34 years old","""\\ACT_DEMO\ACT\Demographics\Age\18-34 years old\""",None)
      )),
      previousEventConstraint = V2EventConstraint(),
      thisEventConstraint = V2EventConstraint(),
    )
  ))

  val timelineQuery: QueryProgress = Query.create(
    queryDefinition = timelineQueryDefinition,
    breakdownNames = Seq.empty,
    queryName = "timeline query",
    queryNotes = None,
    queryFaved = false,
    nodeOfOriginId = new NodeId(0L),
    researcherId = new ResearcherId(1L),
  )

  val exampleQueryConceptGroups: List[ConceptGroup] = List(
    ConceptGroup(List(Concept("10-17 years old", """\\ACT_DEMO\ACT\Demographics\Age\10-17 years old\""", None), Concept("No Information", """\\ACT_DEMO\ACT\Demographics\Hispanic\No Information\""", None)), isExcluded = false, ConceptGroupOptions(Some(1583101380000L), Some(1583533380000L), 6)),
    ConceptGroup(List(Concept("Black or African American", """\\ACT_DEMO\ACT\Demographics\Race\Black or African American\""", None), Concept("85-89 years old", """\\ACT_DEMO\ACT\Demographics\Age\85-89 years old\""", None)), isExcluded = true, ConceptGroupOptions(None, None, 1)),
    ConceptGroup(List(Concept("Bicarbonate in Blood", """\\ACT_LAB\ACT\Labs\LP40271-6\LP19403-2\LP46218-1\1959-6\""", Some(ConceptValueConstraint("HIGH", List("H"), None)))), isExcluded = false, ConceptGroupOptions(None, None, 1))
  )

  val exampleQueryDefinition2: QueryDefinition = QueryDefinition.allOf(Seq(
    V2ConceptGroup.noneOf(Seq(
      V2Concept("ACT Diagnoses ICD-10","""\\ACT_DX_ICD10_2018\ACT\Diagnosis\ICD10\V2_2018AA\A20098492\""",None),
      V2Concept("ACT Procedures HCPCS","""\\ACT_PX_HCPCS_2018\ACT\Procedures\HCPCS\V2_2018AA\A13475665\""",None),
      V2Concept("ACT Procedures ICD-10-PCS","""\\ACT_PX_ICD10_2018\ACT\Procedures\ICD10\V2_2018AA\A16077350\""",None),
      V2Concept("ACT Procedures   ICD-9-Proc","""\\ACT_PX_ICD9_2018\ACT\Procedures\ICD9\V2_2018AA\A18090800\A8352133\""",None),
    )).withOccursAtLeast(4).withStartDate(new DateStamp(1583101380000L)).withEndDate(new DateStamp(1583533380000L)),
  ))

  val exampleQuery2:Query = Query.create(
    queryDefinition = exampleQueryDefinition2,
    breakdownNames = Seq.empty,
    queryName = "NewTQuery",
    queryNotes = None,
    queryFaved = false,
    nodeOfOriginId = new NodeId(0L),
    researcherId = new ResearcherId(1L),
  )

  val qepQueryWithRootConcepts: QepQuery = QepQuery(
    networkId = 41L,
    userName = researcherUser.username,
    userDomain = researcherUser.domain,
    queryName = "Root Concept Query",
    queryNotes = None,
    queryFaved = false,
    dateCreated = queryReceiveTime,
    deleted = false,
    changeDate = queryReceiveTime,
    JSON = exampleQuery2.asJsonText.underlying,
    status = QueryStatus.SentToAdapters.statusName
  )

  val qepQuery: QepQuery = QepQuery(
    networkId = 1L,
    userName = researcherUser.username,
    userDomain = researcherUser.domain,
    queryName = "TestQuery",
    queryNotes = None,
    queryFaved = false,
    dateCreated = queryReceiveTime,
    deleted = false,
    JSON = exampleQuery.asJsonText.underlying,
    changeDate = queryReceiveTime,
    status = QueryStatus.SentToAdapters.statusName
  )

  lazy val qep2Query: QepQuery = QepQuery(
    networkId = 2L,
    userName = researcherUser.username,
    userDomain = researcherUser.domain,
    queryName = "Another Query",
    queryNotes = None,
    queryFaved = false,
    dateCreated = queryReceiveTime - 200 ,
    deleted = false,
    JSON = exampleQuery.asJsonText.underlying,
    changeDate = queryReceiveTime - 200,
    status = QueryStatus.SentToAdapters.statusName
  )

  lazy val qep3Query: QepQuery = QepQuery(
    networkId = 3L,
    userName = researcherUser.username,
    userDomain = researcherUser.domain,
    queryName = "AnewQuery",
    queryNotes = None,
    queryFaved = false,
    dateCreated = queryReceiveTime + 100,
    deleted = false,
    JSON = exampleQuery.asJsonText.underlying,
    changeDate = queryReceiveTime,
    status = QueryStatus.ReceivedAtHub.statusName
  )

  lazy val qep4Query: QepQuery = QepQuery(
    networkId = 4L,
    userName = researcherUser.username,
    userDomain = researcherUser.domain,
    queryName = "A fourth Query",
    queryNotes = None,
    queryFaved = false,
    dateCreated = queryReceiveTime - 100,
    deleted = false,
    JSON = exampleQuery.asJsonText.underlying,
    changeDate = queryReceiveTime - 50,
    status = QueryStatus.ReceivedAtHub.statusName
  )

  lazy val timelineConstraintWithPopulationQepQuery: QepQuery = QepQuery(
    networkId = 300L,
    userName = researcherUser.username,
    userDomain = researcherUser.domain,
    queryName = "Temporal Constraint With Population Query",
    queryNotes = None,
    queryFaved = false,
    dateCreated = queryReceiveTime - 95 ,
    deleted = false,
    JSON = timelineQuery.asJsonText.underlying,
    changeDate = queryReceiveTime - 97,
    status = QueryStatus.ReceivedAtHub.statusName
  )

  lazy val qepResultRowFromMgh: QueryResultRow = QueryResultRow(
    resultId = 10L,
    networkQueryId = 1L,
    instanceId = 100L,
    adapterNode = "MGH",
    size = 30L,
    startDate = Some(queryReceiveTime + 10),
    endDate = Some(queryReceiveTime + 20),
    status = ResultStatus.ResultFromCRC.statusName,
    statusMessage = None,
    resultMetadata = ResultMetadata(Option(ObfuscatingParameters(5,6.5,10,10))),
    changeDate = queryReceiveTime + 20
  )

  val qepResultRowFromPartners: QueryResultRow = QueryResultRow(
    resultId = 11L,
    networkQueryId = 1L,
    instanceId = 100L,
    adapterNode = "Partners",
    size = 300L,
    startDate = Some(queryReceiveTime + 10),
    endDate = Some(queryReceiveTime + 20),
    status = ResultStatus.ResultFromCRC.statusName,
    statusMessage = None,
    resultMetadata = ResultMetadata(Option(ObfuscatingParameters(5,6.5,10,10))),
    changeDate = queryReceiveTime + 30
  )

  val incompleteResultRowFromBch: QueryResultRow = QueryResultRow(
    resultId = 12L,
    networkQueryId = 1L,
    instanceId = 100L,
    adapterNode = "BCH",
    size = -1L,
    startDate = Some(queryReceiveTime + 10),
    endDate = None,
    status = ResultStatus.ReceivedByAdapter.statusName,
    statusMessage = None,
    resultMetadata = ResultMetadata(Option(ObfuscatingParameters(5,6.5,10,10))),
    changeDate = queryReceiveTime + 20
  )

  lazy val qepResultRowFromBch: QueryResultRow = QueryResultRow(
    resultId = 12L,
    networkQueryId = 1L,
    instanceId = 100L,
    adapterNode = "BCH",
    size = 3000L,
    startDate = Some(queryReceiveTime + 10),
    endDate = Some(queryReceiveTime + 20),
    status = ResultStatus.ResultFromCRC.statusName,
    statusMessage = None,
    resultMetadata = ResultMetadata(Option(ObfuscatingParameters(5,6.5,10,10))),
    changeDate = queryReceiveTime + 40
  )

  lazy val qepResultRowFromDfci: QueryResultRow = QueryResultRow(
    resultId = 13L,
    networkQueryId = 1L,
    instanceId = 100L,
    adapterNode = "DFCI",
    size = 30000L,
    startDate = Some(queryReceiveTime + 10),
    endDate = Some(queryReceiveTime + 20),
    status = ResultStatus.ResultFromCRC.statusName,
    statusMessage = None,
    resultMetadata = ResultMetadata(Option(ObfuscatingParameters(5,6.5,10,10))),
    changeDate = queryReceiveTime + 50
  )

  lazy val qepResultRowFromHarvard: QueryResultRow = QueryResultRow(
    resultId = 502L,
    networkQueryId = 2L,
    instanceId = 100L,
    adapterNode = "HARVARD",
    size = 30000L,
    startDate = Some(queryReceiveTime - 12),
    endDate = Some(queryReceiveTime - 10),
    status = ResultStatus.ResultFromCRC.statusName,
    statusMessage = None,
    resultMetadata = ResultMetadata(Option(ObfuscatingParameters(5,6.5,10,10))),
    changeDate = queryReceiveTime - 5
  )

  lazy val qepResultRowFromNU: QueryResultRow = QueryResultRow(
    resultId = 501L,
    networkQueryId = 2L,
    instanceId = 120L,
    adapterNode = "NU",
    size = 80000L,
    startDate = Some(queryReceiveTime - 5),
    endDate = Some(queryReceiveTime - 9),
    status = ResultStatus.ResultFromCRC.statusName,
    statusMessage = None,
    resultMetadata = ResultMetadata(Option(ObfuscatingParameters(5,6.5,10,10))),
    changeDate = queryReceiveTime - 7
  )

  val qepQueryWithResultErrors: QepQuery = QepQuery(
    networkId = 50L,
    userName = researcherUser.username,
    userDomain = researcherUser.domain,
    queryName = "TestQuery",
    queryNotes = None,
    queryFaved = false,
    dateCreated = queryReceiveTime,
    deleted = false,
    JSON = exampleQuery.asJsonText.underlying,
    changeDate = queryReceiveTime,
    status = QueryStatus.SentToAdapters.statusName
  )

  val qepQueryWithErrors: QepQuery = QepQuery(
    networkId = 50L,
    userName = researcherUser.username,
    userDomain = researcherUser.domain,
    queryName = "TestQuery",
    queryNotes = None,
    queryFaved = false,
    dateCreated = queryReceiveTime,
    deleted = false,
    JSON = exampleQuery.asJsonText.underlying,
    changeDate = queryReceiveTime,
    status = QueryStatus.HubError.toString
  )

  lazy val qepResultRowFromNUError: QueryResultRow = QueryResultRow(
    resultId = 501L,
    networkQueryId = 50L,
    instanceId = 120L,
    adapterNode = "NU",
    size = 80000L,
    startDate = Some(queryReceiveTime - 9),
    endDate = Some(queryReceiveTime - 5),
    status = ResultStatus.ErrorInShrine.statusName,
    statusMessage = None,
    resultMetadata = ResultMetadata(obfuscatingParameters = None),
    changeDate = queryReceiveTime - 7
  )

  lazy val qepResultRowFromHarvardError: QueryResultRow = QueryResultRow(
    resultId = 502L,
    networkQueryId = 50L,
    instanceId = 100L,
    adapterNode = "HARVARD",
    size = 30000L,
    startDate = Some(queryReceiveTime - 12),
    endDate = Some(queryReceiveTime - 10),
    status = ResultStatus.ErrorInShrine.statusName,
    statusMessage = None,
    resultMetadata = ResultMetadata(obfuscatingParameters = None),
    changeDate = queryReceiveTime - 5
  )

  lazy val qepResultRowFromUnsupported1: QueryResultRow = QueryResultRow(
    resultId = 250L,
    networkQueryId = 200L,
    instanceId = 210L,
    adapterNode = "NU1",
    size = 80000L,
    startDate = Some(queryReceiveTime - 5),
    endDate = Some(queryReceiveTime - 9),
    status = ResultStatus.ResultFromCRC.statusName,
    statusMessage = None,
    resultMetadata = ResultMetadata(obfuscatingParameters = None),
    changeDate = queryReceiveTime - 7
  )


  lazy val qepResultRowFromUnsupported2: QueryResultRow = QueryResultRow(
    resultId = 251L,
    networkQueryId = 200L,
    instanceId = 211L,
    adapterNode = "NU2",
    size = 80000L,
    startDate = Some(queryReceiveTime - 5),
    endDate = Some(queryReceiveTime - 9),
    status = QueryResult.StatusType.Finished.name,
    statusMessage = None,
    resultMetadata = ResultMetadata(obfuscatingParameters = None),
    changeDate = queryReceiveTime - 7
  )

  @BeforeEach
  def beforeEach(): Unit = {
    QepQueryDb.db.createTables()

    val network: Network = Network(
      networkName = "testNetwork",
      hubQueueName = MomQueueName("testHub"),
      adminEmail = "yourname@example.com",
      momId = "testNetwork",
      awsSqsConfig = None,
      kafkaConfig = None
    )

    val node: Node = {
      val thisNodeKey = ConfigSource.config.getString("shrine.nodeKey")

      Node.create(thisNodeKey,thisNodeKey,s"$thisNodeKey.example.co.uk", adminEmail = "email@foo", momId = "testNode1")
    }

    HubDb.db.createTables()

    HubDb.db.upsertNetworkIO(network).unsafeRunSync()
    HubDb.db.upsertNodeIO(node).unsafeRunSync()

    HubLifecycle.queuesFromDatabaseIO().unsafeRunSync()
  }

  @AfterEach
  def afterEach(): Unit = {
    HubDb.db.dropTables()
    QepQueryDb.db.dropTables()
  }
}
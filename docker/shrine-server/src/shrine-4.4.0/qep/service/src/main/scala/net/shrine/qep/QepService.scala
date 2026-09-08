package net.shrine.qep

import java.io.{ByteArrayInputStream, InputStream, StringWriter}
import java.util.concurrent.TimeUnit
import cats.effect.IO
import fs2.io.readInputStream
import ch.qos.logback.classic.Level
import com.opencsv.CSVWriter
import io.circe.{Decoder, HCursor}
import net.shrine.api.ontology.{CodeCategory, LuceneSearcher, OntologyPath}
import net.shrine.audit.{LongQueryId, QueryName, Time}
import net.shrine.authentication.http4s.AuthMiddlewareSelector
import net.shrine.authentication.pm.User
import net.shrine.hub.data.client.HubClient
import net.shrine.hub.mom.ShrineMomClient
import net.shrine.log.Log
import net.shrine.problem.{AbstractProblem, JsonProblemDigest, ProblemSources}
import net.shrine.protocol.version.v2.QueryStatus.UnknownFinal
import net.shrine.protocol.version.v2.ResultStatus.ResultFromCRC
import net.shrine.protocol.version.v2.{Query, QueryProgress, QueryStatus, Researcher, ResultMetadata, ResultStatus, UpdateQueryAtHub, UpdateQueryAtHubWithFaving, UpdateQueryAtHubWithNameAndNotes}
import net.shrine.protocol.version.QueryId
import net.shrine.protocol.i2b2.{QueryResult, ResultOutputType}
import net.shrine.qep.querydb.{AdapterResult, BreakdownResultsForType, DataKeyAndValue, DemographicsData, FullQuery, FullQueryResult, QepQuery, QepQueryDb, QepQueryDbChangeNotifier, QueryAndResults, QueryStateObserved, QueryWithResultStatus}
import net.shrine.util.Sort
import org.http4s.dsl.impl.{+&, ->, /, :?, Auth, LongVar, OptionalQueryParamDecoderMatcher, QueryParamDecoderMatcher}
import org.http4s.dsl.io.{BadRequest, GET, InternalServerError, NotFound, Ok, POST, RangeNotSatisfiable, Root, ServiceUnavailable, http4sBadRequestSyntax, http4sInternalServerErrorSyntax, http4sNotFoundSyntax, http4sOkSyntax, http4sRangeNotSatisfiableSyntax, http4sServiceUnavailableSyntax}
import org.http4s.headers.`Content-Type`
import org.http4s.server.Router
import org.http4s.{AuthedRequest, AuthedRoutes, DecodeFailure, EntityDecoder, HttpRoutes, Response, InvalidMessageBodyFailure => InvalidMessageBodyException}

import java.net.ConnectException
import scala.concurrent.duration.FiniteDuration
import scala.util.control.NonFatal

case class QepService() extends Auth {

  private val qepServiceInfo: String  =
    """
      |The SHRINE qep service.
      |
      |This API retrieves data from the SHRINE QEP.
      |
    """.stripMargin


  def router: HttpRoutes[IO] = {
    Router[IO](
      "/ping" -> pingService,
      "/about" -> aboutService,
      "/login" -> JsonContentService(loginService),
      "" -> JsonContentService(privateQepService),
    )
  }

  private object OptionalNetworkIdParam extends OptionalQueryParamDecoderMatcher[Long]("networkId") //todo rename to queryId SHRINE2020-467
  private object OptionalLimitParam extends OptionalQueryParamDecoderMatcher[Int]("limit")
  private object OptionalSkipParam extends OptionalQueryParamDecoderMatcher[Int]("skip")
  private object OptionalSortByParam extends OptionalQueryParamDecoderMatcher[String]("sortBy")
  private object OptionalSortSiteByParam extends OptionalQueryParamDecoderMatcher[String]("sortSiteBy")
  private object OptionalTimeoutSecondsParam extends OptionalQueryParamDecoderMatcher[Long]("timeoutSeconds")
  private object OptionalAfterVersionParam extends OptionalQueryParamDecoderMatcher[Long]("afterVersion")

  private object QueryIdParam extends QueryParamDecoderMatcher[String]("queryId")

  private val pingService: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case GET -> Root => Ok("pong")

    case x => NotFound(s"The QEP service does not respond to $x")
  }

  private val aboutService: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case GET -> Root => Ok(qepServiceInfo)

    case x => NotFound(s"The QEP service does not respond to $x")
  }

  /**
    * Login service: Authenticates user using Basic auth and returns an object containing the user name and session Id.
    */

  import io.circe.generic.auto._
  import io.circe.syntax._

  private val authQepLoginRoute: AuthedRoutes[User, IO] = AuthedRoutes.of[User, IO] {

    case GET -> Root as user =>
      val resultJson = UserSessionToken(
        username = user.username,
        sessionId = user.credential.value,
        sessionTimeoutMs = user.sessionTimeoutMs.map(_.toLong)
      ).asJson.toString()
      Ok(resultJson)
  }

  private val loginService:  HttpRoutes[IO] = AuthMiddlewareSelector(authQepLoginRoute)

  /**
    * Main QEP service -- i.e. querying
    */
  private val authQepRoutes: AuthedRoutes[User, IO] = AuthedRoutes.of[User, IO] {
    case _@GET -> Root / "authPing" as _ => Ok("pong")

    case _ @ GET -> Root / "queryResult"
      :? OptionalTimeoutSecondsParam(timeoutSec)
      +& OptionalNetworkIdParam(networkId)
      +& OptionalLimitParam(limit)
      +& OptionalSkipParam(skip)
      +& OptionalSortByParam(sortBy)
      +& OptionalSortSiteByParam(sortSiteBy)
      +& OptionalAfterVersionParam(afterVersion) as user =>
      pollForQueryResults(
        user = user,
        queryId = networkId,
        timeoutSeconds = timeoutSec,
        limitOption = limit,
        skipOption = skip,
        sortByOption = sortBy,
        sortSiteByOption = sortSiteBy,
        afterVersionOption = afterVersion
      )
    case request @ POST -> Root / "startQuery" as _ => startQuery(request)

    case request @ POST -> Root / "changeQueryFav" / LongVar(queryId) as _ => changeQueryFav(queryId, request)

    case request @ POST -> Root / "changeQueryNameAndNotes" / LongVar(queryId) as _ => changeQueryNameAndNotes(queryId, request)

    case _ @ GET -> Root / "query" / LongVar(queryId) as _ => getQuery(queryId)

    case _@GET -> Root / "keepAlive" as _ => Ok()

    case _@POST -> Root / "demographic" / "csv" :? QueryIdParam(queryId) as user =>
      val inputStreamIO: IO[InputStream] = createDemographicCSV(queryId.toLong, user)
      Ok(readInputStream(
        fis = inputStreamIO,
        chunkSize = 4096,
        closeAfterUse = true
      ))

    case _@POST -> Root / "count" / "csv" :? QueryIdParam(queryId) as user =>
      val inputStreamIO: IO[InputStream] = createCountsCSV(queryId.toLong, user)
      Ok(readInputStream(
        fis = inputStreamIO,
        chunkSize = 4096,
        closeAfterUse = true
      ))

    case x => NotFound(s"The QEP service does not respond to $x")

  }

  private def privateQepService: HttpRoutes[IO] = AuthMiddlewareSelector(authQepRoutes)

  def startQuery(request: AuthedRequest[IO, User]):IO[Response[IO]] = {
    import io.circe.generic.auto.exportDecoder
    import org.http4s.circe.jsonOf
    implicit val decoder: Decoder[Set[ResultOutputType]] = (hCursor: HCursor) => {
      hCursor.values.fold[Decoder.Result[Set[ResultOutputType]]](Right(Set.empty))(p => {
        Right(p.map(q => ResultOutputType.valueOf(ResultOutputType.allValidTypes.toSet)(q.asString.get).get).toSet)
      })
    }

    implicit val basicQueryDecoder: EntityDecoder[IO, BasicQuery] = jsonOf[IO, BasicQuery]

    val basicQueryIO: IO[BasicQuery] = request.req.as[BasicQuery]

    basicQueryIO.
      flatMap(startQuery(_, request.context)).
      handleErrorWith {
        case x:InvalidMessageBodyException =>
          Log.error(s"Error running query: ${x.getMessage}",x)
          BadRequest(s"Error running query: ${x.getMessage}")
        case x :DecodeFailure =>
          Log.error(s"Error running query: ${x.getMessage}", x)
          BadRequest(s"Error running query: ${x.getMessage}")
        case x :InvalidStartQueryRequestException =>
          Log.error(s"Error running query: ${x.getMessage}",x)
          BadRequest(s"Error running query: ${x.getMessage}")
        case x :ConnectException =>
          Log.error("Could not connect to supporting service",x)
          ServiceUnavailable("Could not connect to supporting service")
        case NonFatal(x) =>
          Log.error("startQuery NonFatal",x)
          InternalServerError(s"NonFatal trap")
        case x =>
          Log.error("startQuery catch-all",x)
          InternalServerError(s"catch-all trap")
    }
  }

  private def getPreviousQueryDataIO(user: User,
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

  private def getQueryResultAsStringIO(user: User,
                                       limitOption : Option[Int],
                                       skipOption : Option[Int],
                                       sortByOption: Option[String],
                                       queryAndResults: QueryAndResults): IO[String] = {
    import io.circe.generic.auto.exportEncoder
    import io.circe.syntax.EncoderOps

    for {
      queryStatedObserved <- IO(QueryStateObserved(queryAndResults))
      existingQueryStateObserved <- QepQueryDb.db.selectQueryStateObservedByChecksum(queryStatedObserved)
      _ <- existingQueryStateObserved.fold(QepQueryDb.db.insertQueryStateObservedIO(queryStatedObserved))(_ => IO.unit)
      queriesAndCount  <- getPreviousQueryDataIO(user, limitOption, skipOption, sortByOption, Some(queryAndResults.fullQuery.query.networkId))
      codeCategoryMap <- LuceneSearcher.getCodeCategoriesMap
    } yield {
      val queryResults = ResultsRow(queryAndResults, codeCategoryMap)
      val queriesAndResult = PreviousQueriesAndResult(rowCount = queriesAndCount.queryRowCount, rowOffset = skipOption.getOrElse(0), allQueries = queriesAndCount.queryCells, selectedQuery = Some(queryResults))

      val resultJson = queriesAndResult.asJson.toString().filter(_ != '\n')

      var finalResult: String = resultJson
      if(queriesAndResult.selectedQuery.get.isComplete){
        finalResult = finalResult.concat("\n")
      }

      finalResult
    }
  }

  def getQueryResultIO(user: User,
                       limitOption : Option[Int],
                       skipOption : Option[Int],
                       sortByOption: Option[String],
                       queryAndResults: QueryAndResults): IO[(PreviousQueriesAndResult, Boolean)] = {
    val queryId = queryAndResults.fullQuery.query.networkId
    for {
      queryStatedObserved <- IO(QueryStateObserved(queryAndResults))
      existingQueryStateObserved <- QepQueryDb.db.selectQueryStateObservedByChecksum(queryStatedObserved)
      isSelectedQueryStateObserved <- existingQueryStateObserved.fold{QepQueryDb.db.insertQueryStateObservedIO(queryStatedObserved).flatMap(_ => IO(false))}(_ => IO(true))
      queriesAndCount  <- getPreviousQueryDataIO(user, limitOption, skipOption, sortByOption, Some(queryId))
      codeCategoryMap <- LuceneSearcher.getCodeCategoriesMap
    } yield {
      val queryResults = ResultsRow(queryAndResults, codeCategoryMap)
      val queriesAndResult = PreviousQueriesAndResult(queriesAndCount.queryRowCount, skipOption.getOrElse(0), queriesAndCount.queryCells, Some(queryResults))

      (queriesAndResult, isSelectedQueryStateObserved)
    }
  }

  private def pollForQueryResults(
                           user: User,
                           queryId: Option[LongQueryId] = None,
                           timeoutSeconds: Option[Long] = None,
                           limitOption : Option[Int] = None,
                           skipOption : Option[Int] = None,
                           sortByOption: Option[String] = None,
                           sortSiteByOption: Option[String] = None,
                           queryIdListOption: Option[List[LongQueryId]] = None,
                           afterVersionOption:Option[Long] = None
                         ): IO[Response[IO]] = {
    import io.circe.generic.auto.exportEncoder
    import io.circe.syntax.EncoderOps

    val maximumQueryLimit = 200
    val defaultQueryLimit = 50
    val validatedQueryLimit = limitOption.getOrElse(defaultQueryLimit)
    val sortSiteBy = sortSiteByOption.getOrElse("site.asc")

    if(validatedQueryLimit > maximumQueryLimit) RangeNotSatisfiable(s"Shrine cannot supply $validatedQueryLimit Queries. Request $maximumQueryLimit or fewer using the limit parameter.")
    else {
      queryId match {
        case Some(nId) =>
          val timeoutMilliseconds: Long = timeoutSeconds.getOrElse(15L) * 1000
          val requestStartTime = System.currentTimeMillis()
          val deadline = requestStartTime + timeoutMilliseconds

          val queryAndResultsIO: IO[QueryAndResults] = QepQueryDb.db.selectResultsRowIO(nId, user, sortSiteBy)
          val previousQueriesAndResultWithObservedIO: IO[(PreviousQueriesAndResult, Boolean)] = queryAndResultsIO.flatMap { queryAndResults => {
              val queryResultIO = getQueryResultIO(user, limitOption, skipOption, sortByOption, queryAndResults)
              queryResultIO
            }
          }

          previousQueriesAndResultWithObservedIO.flatMap(previousQueriesAndResult => {

            if (shouldRespondNow(deadline, previousQueriesAndResult._1, previousQueriesAndResult._2,afterVersionOption.getOrElse(0L))) {
              Log.debug(s"Responding now for $nId")
              val queryResultString: String = previousQueriesAndResult._1.toJsonText
              Log.debug(s"Will respond to request for $queryId immediately with $queryResultString")

              Ok(queryResultString)
            }
            else {
              val timeout = FiniteDuration(deadline - System.currentTimeMillis(), TimeUnit.MILLISECONDS)
              QepQueryDbChangeNotifier.setTriggerForChange(nId, timeout, replyWithQueryResults(
                nId,
                user,
                limitOption,
                skipOption,
                sortByOption,
                sortSiteBy
              ))
            }
          })
        case None =>
          //todo if there is no nId then it doesn't poll - if no query is selected
          val queriesAndCountIO: IO[QueriesAndCount] = getPreviousQueryDataIO(user, limitOption, skipOption, sortByOption, queryIdListOption = queryIdListOption)

          queriesAndCountIO.flatMap(queriesAndCount => {
            val queriesNoResult = PreviousQueriesAndResult(queriesAndCount.queryRowCount, skipOption.getOrElse(0), allQueries = queriesAndCount.queryCells)
            Ok(queriesNoResult.asJson.toString())
          })
      }
    }
  }


  private def replyWithQueryResults(
                                     queryId: LongQueryId,
                                     user: User,
                                     limitOption : Option[Int],
                                     skipOption : Option[Int],
                                     sortByOption: Option[String],
                                     sortSiteBy: String
                                   ): IO[Response[IO]] = {

    //todo just one trip to the database SHRINE-3389
    QepQueryDb.db.selectResultsRowIO(queryId, user, sortSiteBy).
      flatMap(getQueryResultAsStringIO(user, limitOption, skipOption, sortByOption, _)).
      flatMap(Ok(_)) //todo context shift for reply
  }

  /**
    * @param deadline time when a response must go
    * @param previousQueriesAndResult either the PreviousQueriesAndResult or something is not right
    * @return true to respond now, false to dither
    */
  private def shouldRespondNow(deadline: Long,
                               previousQueriesAndResult: PreviousQueriesAndResult,
                               selectedResultObserved: Boolean,
                               afterVersion:Long):Boolean = {
    val previousQueriesUpdated = previousQueriesAndResult.allQueries
      .filter(q => q.changeDate >= afterVersion) //It's already been seen bolded
      .exists(q => !q.observed && !q.isQueryComplete) //Anything that hasn't been observed yet it got on the last pass

    val currentTime = System.currentTimeMillis()

    val shouldRespond =  (currentTime >= deadline) || (previousQueriesUpdated || !selectedResultObserved)

    Log.debug(s"Should respond for query ${previousQueriesAndResult.selectedQuery.get.query.networkId} is $shouldRespond")

    shouldRespond
  }

  private def startQuery(basicQuery: BasicQuery, user: User): IO[Response[IO]] = {
    val breakdownNames: Seq[String] = basicQuery.dataDistributionTypes.map(_.name).toSeq

    val queryIO: IO[QueryProgress] = for {
      localNode <- HubClient.getLocalNodeIO
      //ideally the QEP would try to select the researcher before creating a new one. See SHRINE2020-1104
      // The QEP does not have access to a collection of researchers. When it does this code can be replaced.
      researcher <- IO {
        Researcher.createWithRepeatableId(
          userName = user.username,
          userDomainName = user.domain,
          nodeId = localNode.id
        )
      }
    } yield {
      Query.create(
        queryDefinition = basicQuery.toV2QueryDefinition,
        breakdownNames = breakdownNames,
        queryName = basicQuery.name,
        queryNotes = basicQuery.notes,
        queryFaved = basicQuery.faved,
        nodeOfOriginId = localNode.id,
        researcherId = researcher.id,
      )
    }

    queryIO.flatMap(query => QueryRunner.runQuery(user,query)
      .flatMap(response => Ok(response.asJson.toString())))
  }

  private def getQuery(queryId: LongQueryId): IO[Response[IO]] = {
    import io.circe.generic.auto.exportEncoder
    import io.circe.syntax.EncoderOps

    val qepQueryIO: IO[Option[QepQuery]] = QepQueryDb.db.selectQueryByIdIO(queryId)

    //todo maybe this belongs in QueryNameAndConcepts
    qepQueryIO.flatMap{ maybeQuery: Option[QepQuery] =>
      try {
        val basicQuery = BasicQuery.fromV2Query(maybeQuery.get.v2Query)
        val ontologyMapIO = ConceptGroup.getOntologyMapIOForConceptGroups(basicQuery.conceptGroups ++
          basicQuery.timeline.map{tl => tl.timelineEvents}.getOrElse(Seq.empty))
        ontologyMapIO.map(QueryNameAndConcepts(basicQuery,_))
      } catch {
        case _:UnsupportedQueryFeatureException => IO(QueryNameAndConcepts.hasUnsupportedFeatures(maybeQuery.get.queryName))
      }
    }.flatMap(rq => Ok(rq.asJson.toString))
  }

  private def changeQueryFav(queryId: Long, request: AuthedRequest[IO, User]):IO[Response[IO]] = {
    import org.http4s.circe.jsonOf

    implicit val favDataDecoder: EntityDecoder[IO, FavData] = jsonOf[IO, FavData]

    val favDataIO = request.req.as[FavData]

    favDataIO.flatMap(favData => {
      sendUpdateQueryIO(UpdateQueryAtHubWithFaving(
        queryId = new QueryId(queryId),
        expectedItemVersion = None,
        faved = favData.faved
      )).flatMap(_ => QepQueryDb.db.updateQueryFavedIO(queryId, favData.faved)
      ).flatMap(_ => Ok())
    }).handleErrorWith {
      case x: InvalidChangeFavQueryException =>
        Log.error(s"Error changing query faved: ${x.getMessage}")
        BadRequest(s"Error changing query faved: ${x.getMessage}")
      case t => throw t
    }
  }

  private def changeQueryNameAndNotes(qid: Long, request: AuthedRequest[IO, User]):IO[Response[IO]] = {
    import org.http4s.circe.jsonOf

    implicit val queryNameAndNotesDataDecoder: EntityDecoder[IO, QueryNameAndNotesData] = jsonOf[IO, QueryNameAndNotesData]

    val queryNameAndNotesDataIO = request.req.as[QueryNameAndNotesData]

    queryNameAndNotesDataIO.flatMap(queryNameAndNotesData => {
      sendUpdateQueryIO(UpdateQueryAtHubWithNameAndNotes(
        queryId = new QueryId(qid),
        expectedItemVersion = None,
        queryName = queryNameAndNotesData.name,
        queryNotes = queryNameAndNotesData.notes
      )).flatMap(_ => QepQueryDb.db.updateQueryNameAndNotesIO(
        queryId = qid,
        name = queryNameAndNotesData.name,
        notes = queryNameAndNotesData.notes
      ).flatMap(_ => Ok()))
    }).handleErrorWith {
      case x :InvalidTextInputException  =>
        Log.error(s"Error changing query name and/or notes: ${x.getMessage}")
        BadRequest(s"Error changing query name and/or notes: ${x.getMessage}")
      case t => throw t
    }
  }

  private def sendUpdateQueryIO(updateQuery:UpdateQueryAtHub): IO[Unit] = {

    ShrineMomClient.sendToHubIO(
      subjectId = updateQuery.queryId,
      envelopeContents = updateQuery,
      envelopeContentsCompanion = UpdateQueryAtHub,
      logString = s"${updateQuery.getClass.getSimpleName} for ${updateQuery.queryId}"
    )
      .map(_ => Log.debug(s"sent $updateQuery")).
    handleErrorWith(t => IO(Log.error("Exception while running QepService.sendUpdateQuery", t)))
  }

  private def createDemographicCSV(queryId: LongQueryId, user: User): IO[InputStream] = {
    Log.info("running create demo csv")
    val stringWriter = new StringWriter()
    val csvWriter = new CSVWriter(stringWriter)
    val demographicsDataIO: IO[Seq[DemographicsData]] = QepQueryDb.db.selectDemographicsDataIO(queryId, user)

    val csvStringIO: IO[String] = demographicsDataIO.map(demographicsData => {

      val sortedDemographicsData: Seq[DemographicsData] = demographicsData.sortWith((x, y) => Sort.compareAlphaNumerically(x.adapterName, y.adapterName) < 0)
      val sortedNodes: Seq[String] = sortedDemographicsData.map(_.adapterName)

      val sortedDataKeyAndValue: Seq[Seq[DataKeyAndValue]] = demographicsData.map(_.dataKeyAndValues)
      val dataKeys: Seq[String] = sortedDataKeyAndValue.flatMap(p => p.map(_.dataKey)).distinct.sorted(Sort.compareAlphaNumerically)
      val headerRow: Seq[String] = Seq("SHRINE QUERY RESULTS (OBFUSCATED PATIENT COUNTS)", "") ++ sortedNodes
      csvWriter.writeNext(headerRow.toArray)

      val obfuscationNoiseClampRow: Seq[String] = Seq("Obfuscation Noise Clamp", "") ++
        sortedDemographicsData.map(_.obfuscatingParameters.fold("unavailable")(_.noiseClamp.toString))
      csvWriter.writeNext(obfuscationNoiseClampRow.toArray)

      val obfuscationLowLimitRow: Seq[String] = Seq("Obfuscation Low Limit", "") ++
        sortedDemographicsData.map(_.obfuscatingParameters.fold("unavailable")(_.lowLimit.toString))
      csvWriter.writeNext(obfuscationLowLimitRow.toArray)

      val obfuscationBinSizeRow: Seq[String] = Seq("Obfuscation Bin Size", "") ++
        sortedDemographicsData.map(_.obfuscatingParameters.fold("unavailable")(_.binSize.toString))
      csvWriter.writeNext(obfuscationBinSizeRow.toArray)

      val obfuscationStandardDeviationRow: Seq[String] = Seq("Obfuscation Standard Deviation", "") ++
        sortedDemographicsData.map(_.obfuscatingParameters.fold("unavailable")(_.stdDev.toString))
      csvWriter.writeNext(obfuscationStandardDeviationRow.toArray)

      dataKeys.foreach(dataKey => {
        val rowLabel = List(dataKey, "")

        val row: List[String] = rowLabel ++ sortedNodes.map(node => {
          val nodeData: Option[DemographicsData] = demographicsData.find(_.adapterName == node)

          demographicsData.find(_.adapterName == node).map(_.dataKeyAndValues).getOrElse(Seq.empty).find(_.dataKey == dataKey).map(p => {
            p.value match {
              case Left(s) => s.map(_.uiString).getOrElse("Unknown")
              case Right(c) if c == -1 => nodeData.fold("unavailable")(node => node.obfuscatingParameters.map(p => s"${p.lowLimit} patients or fewer").getOrElse("unavailable"))
              case Right(c) => c.toString
            }
          }).getOrElse("unavailable")
        }).toList

        csvWriter.writeNext(row.toArray)
      })

      csvWriter.flush()
      csvWriter.close()

      stringWriter.toString
    })

    csvStringIO.map(csvString => {
      val byteArray: Array[Byte] = csvString.getBytes
      new ByteArrayInputStream(byteArray)
    })
  }

  private def createCountsCSV(queryId: LongQueryId, user: User): IO[InputStream] = {
    val stringWriter = new StringWriter()
    val csvWriter = new CSVWriter(stringWriter)
    val headerRow: List[String] = List("Site", "Status", "Obfuscation Noise Clamp", "Obfuscation Low Limit", "Obfuscation Bin Size", "Obfuscation Standard Deviation")
    csvWriter.writeNext(headerRow.toArray)

    val demographicsDataMapIO: IO[Seq[AdapterResult]] = QepQueryDb.db.selectAdapterResultsIO(queryId, user)

    val csvStringIO: IO[String] = demographicsDataMapIO.map(adapterResults => {
      adapterResults.foreach(adapterResult => {
        val countResultOrStatus: String = adapterResult.countOrStatus match {
          case Right(c) if c == -1 => s"${adapterResult.obfuscatingParameters.get.lowLimit} patients or fewer"
          case Right(c) => c.toString
          case Left(s) => s.map(_.uiString).getOrElse("Unknown")
        }

        val obfParams: Seq[String] = adapterResult.obfuscatingParameters.map(o => {
            Seq(o.noiseClamp.toString, o.lowLimit.toString, o.binSize.toString, o.stdDev.toString)
        }).getOrElse(Seq("unavailable", "unavailable", "unavailable", "unavailable"))

        val row = List(adapterResult.adapterNode, countResultOrStatus) ++ obfParams
        csvWriter.writeNext(row.toArray)
      })

      csvWriter.flush()
      csvWriter.close()

      stringWriter.toString
    })

    csvStringIO.map(csvString => {
      val byteArray: Array[Byte] = csvString.getBytes
      new ByteArrayInputStream(byteArray)
    })
  }
}

case class FavData(faved: Boolean)

case class PreviousQueriesAndResult(rowCount:Int,
                                    rowOffset:Int,
                                    selectedQuery: Option[ResultsRow],
                                    isAllQueriesComplete: Boolean,
                                    allQueries:Seq[QueryCell]
                            )
{
  def toJsonText: String = {
    import io.circe.generic.auto.exportEncoder
    import io.circe.syntax.EncoderOps

    val resultJson = this.asJson.toString().filter(_ != '\n')

    var finalResult: String = resultJson
    if(this.selectedQuery.get.isComplete){
      finalResult = finalResult.concat("\n")
    }

    finalResult
  }
}
object PreviousQueriesAndResult {

  def apply(
            rowCount:Int,
            rowOffset:Int,
            allQueries:Seq[QueryCell],
            selectedQuery: Option[ResultsRow] = None
            ): PreviousQueriesAndResult = {

    val allQueriesComplete: Boolean  = allQueries.forall(_.isQueryComplete)
    PreviousQueriesAndResult(
      rowCount,
      rowOffset,
      selectedQuery,
      allQueriesComplete,
      allQueries
    )
  }
}

object JsonContentService {

  //noinspection TypeAnnotation because the type is amazingly complex and uninformative
  def apply(service: HttpRoutes[IO]):HttpRoutes[IO] = {
    val jsonContentHeader = `Content-Type`(org.http4s.MediaType.application.json)
    service.map { resp: Response[IO] =>
      if (resp.status.isSuccess) resp.copy(headers = resp.headers.put(jsonContentHeader))
      else resp
    }
  }
}

case class QueryCell(networkId:String, //todo can this be a Long or a QueryId? SHRINE2020-467
                     queryName: QueryName,
                     queryNotes: Option[String],
                     dateCreated: Time,
                     changeDate: Time,
                     queryFaved: Boolean,
                     status: String,
                     internalStatus: String,
                     isQueryError: Boolean,
                     isResultsError: Boolean,
                     isQueryComplete: Boolean,
                     problemDigest: Option[JsonProblemDigest] = None,
                     observed: Boolean,
                     queryAsHtmlString: Option[String] = None,
                    )

object QueryCell {

  def apply(queryWithResultStatus: QueryWithResultStatus): QueryCell = {

    val problemDigestOption: Option[JsonProblemDigest] = if(queryWithResultStatus.isResultsError) Some(JsonProblemDigest(AllResultsHaveErrorProblem(queryWithResultStatus.networkId))) else None
    QueryCell(
      networkId = queryWithResultStatus.networkId.toString,
      queryName = queryWithResultStatus.queryName,
      queryNotes = queryWithResultStatus.queryNotes,
      dateCreated = queryWithResultStatus.dateCreated,
      changeDate = queryWithResultStatus.changeDate,
      queryFaved = queryWithResultStatus.queryFaved,
      status = queryStatusToUiString(queryWithResultStatus.status, queryWithResultStatus.isComplete, queryWithResultStatus.isResultsError),
      internalStatus = queryWithResultStatus.status,
      observed = queryWithResultStatus.observed,
      isQueryError = queryWithResultStatus.isQueryError,
      isResultsError = queryWithResultStatus.isResultsError,
      isQueryComplete = queryWithResultStatus.isComplete || queryWithResultStatus.isQueryError,
      problemDigest = problemDigestOption
    )
  }

  def apply(fullQuery: FullQuery,
            isComplete: Boolean,
            isQueryError: Boolean,
            isResultsError: Boolean,
            observed: Boolean,
            queryCriteriaTextOption: Option[String]
           ): QueryCell = {

    val problemDigestOption: Option[JsonProblemDigest] = if(isResultsError) Some(JsonProblemDigest(AllResultsHaveErrorProblem(fullQuery.query.networkId)))
    else fullQuery.problemDigest.map(JsonProblemDigest(_))

    QueryCell(
      networkId = fullQuery.query.networkId.toString, //todo try as a Long in SHRINE2020-467
      queryName = fullQuery.query.queryName,
      queryNotes = fullQuery.query.queryNotes,
      dateCreated = fullQuery.query.dateCreated,
      changeDate = fullQuery.query.changeDate,
      queryFaved = fullQuery.query.queryFaved,
      status = queryStatusToUiString(fullQuery.query.status, isComplete, isResultsError),
      internalStatus = fullQuery.query.status,
      problemDigest = problemDigestOption,
      observed = observed,
      isQueryError = isQueryError,
      isResultsError = isResultsError,
      isQueryComplete = isComplete || isQueryError,
      queryAsHtmlString = queryCriteriaTextOption
    )

  }

  private def queryStatusToUiString(internalStatus: String, isComplete: Boolean, isResultsError: Boolean): String = {

    val maybeStatus = QueryStatus.namesToStatuses.get(internalStatus)
    maybeStatus.map{q =>
      if(isResultsError)  {
        QueryStatus.HubError.uiString
      }
      else if(isComplete)  "Completed"
      else q.uiString
    }.getOrElse("Unknown")
  }
}

case class BreakdownResultsForTypeWithNoise(
                                             resultType:ResultOutputType,
                                             results:Seq[BreakdownResultWithNoise]
                                           )
case class BreakdownResultWithNoise(
                                     dataKey: String,
                                     value: Long,
                                     changeDate: Long,
                                     noiseClamp: Int,
                                     lowLimit: Int
                                   )

case class ResultsRow(
                      query: QueryCell,
                      results: Seq[QResult],
                      aggregateDemographics:
                      Seq[BreakdownResultsForTypeWithNoise],
                      siteCount: Long,
                      patientCount: Long,
                      isComplete: Boolean,
                      dataVersion: Time
                     )
object ResultsRow {

  def apply(
             queryResults: QueryAndResults,
             codeCategoryMap: Map[OntologyPath, CodeCategory]
           ): ResultsRow = {
    val queryId = queryResults.fullQuery.query.networkId
    val results: Seq[QResult] = queryResults.fullResults.map(QResult(queryId, _))

    val allBreakdownResultsForTypeWithNoise:Seq[BreakdownResultsForTypeWithNoise] = results.flatMap(result1 => {
      val obfParams =  result1.resultMetadata.obfuscatingParameters
      val noiseClamp =obfParams.map(_.noiseClamp).getOrElse(-1)
      val lowLimit =obfParams.map(_.lowLimit).getOrElse(-1)

      result1.breakdowns.map(breakdownResultType => {
        val breakdownWithNoise = breakdownResultType.results.map(breakdown => BreakdownResultWithNoise(breakdown.dataKey, breakdown.value, breakdown.changeDate, noiseClamp, lowLimit))
        BreakdownResultsForTypeWithNoise(breakdownResultType.resultType, breakdownWithNoise)
      })
    })

    val breakdownsWithNoise:Seq[BreakdownResultsForTypeWithNoise] = allBreakdownResultsForTypeWithNoise.groupBy(_.resultType).map{ resultTypeToBreakdowns: (ResultOutputType, Seq[BreakdownResultsForTypeWithNoise]) =>
      val breakdownResults = resultTypeToBreakdowns._2.flatMap(_.results).groupBy(_.dataKey)
        .map{ dataKeyToBreakdownResults: (String, Seq[BreakdownResultWithNoise]) =>
          val latest = dataKeyToBreakdownResults._2.map(_.changeDate).sum
          val noiseClampTotal = dataKeyToBreakdownResults._2.filter(n => n.noiseClamp >= 0 && n.value >= 0).map(_.noiseClamp).sum

          //-1 signals "10 or fewer", so filter that nonsense out. An option would be better
          val total = dataKeyToBreakdownResults._2.map(_.value).filter(_ >= 0).sum
          val totalWithNoise = total + noiseClampTotal

          val lowLimits = dataKeyToBreakdownResults._2.map(_.lowLimit).filter(_ >= 0)
          val minLowLimit = if(lowLimits.nonEmpty) lowLimits.min else 0

          BreakdownResultWithNoise(dataKeyToBreakdownResults._1,totalWithNoise,latest, noiseClampTotal, minLowLimit)
        }.toSeq.sortWith((label1, label2) => {
        Sort.compareAlphaNumerically(label1.dataKey, label2.dataKey) <= 0
      })
      BreakdownResultsForTypeWithNoise(resultTypeToBreakdowns._1,breakdownResults)
    }.toSeq.sortBy(_.resultType.name)

    val isComplete = if (results.isEmpty) false
                      else results.forall(_.isComplete)

    val isQueryError = QueryStatus.namesToStatuses.get(queryResults.fullQuery.query.status).exists(_.isError)
    val isResultsError = if(results.isEmpty) false else results.forall(_.isError)

    val basicQuery:BasicQuery = BasicQuery.fromV2Query(queryResults.fullQuery.query.v2Query)
    val queryCriteria:Option[String] = Option(basicQuery.htmlQueryText(codeCategoryMap))

    val queryCell: QueryCell = QueryCell(
      fullQuery = queryResults.fullQuery,
      isComplete = isComplete,
      isQueryError = isQueryError,
      isResultsError = isResultsError,
      observed = true,
      queryCriteriaTextOption = queryCriteria
    )

    val dataVersion: Long = (queryResults.fullResults.map(_.changeDate):+queryResults.fullQuery.query.changeDate).max
    val patientsCountList: Seq[Long] = results.collect{
      case r if r.isComplete && !r.isError => if(r.count >= 0) r.count + r.resultMetadata.obfuscatingParameters.map(_.noiseClamp).getOrElse(0) else 0
    }

    val siteCount: Long = patientsCountList.size.toLong
    val patientCount: Long = patientsCountList.sum

    ResultsRow(
      query = queryCell,
      results = results,
      aggregateDemographics = breakdownsWithNoise,
      siteCount = siteCount,
      patientCount = patientCount,
      isComplete = isComplete,
      dataVersion = dataVersion
    )
  }
}

case class QResult(
                    resultId:Long,
                    networkQueryId:LongQueryId, //todo rename with front-end SHRINE2020-467
                    instanceId:Long,
                    adapterNode:String,
                    count:Long,
                    status:String,
                    internalStatus: String,
                    statusMessage:Option[String],
                    changeDate:Long,
                    breakdowns: Seq[BreakdownResultsForType],
                    problemDigest:Option[JsonProblemDigest],
                    resultMetadata: ResultMetadata,
                  ) {

  def isComplete: Boolean = {
    ResultStatus.namesToStatuses.get(internalStatus).map(_.isFinal).getOrElse {
      QueryResult.StatusType.valueOf(internalStatus).get.isDone
    }
  }

  def isError: Boolean = {
    ResultStatus.namesToStatuses.get(internalStatus).map(_.isError).getOrElse {
      QueryResult.StatusType.valueOf(internalStatus).get.isError
    }
  }
}

object QResult {
  def apply(queryId:LongQueryId, fullQueryResult: FullQueryResult): QResult = {
    //Try to catch and log SHRINE-2239 in the wild
    if(queryId != fullQueryResult.queryId) {
      val isx = new IllegalStateException(s"queryId $queryId != fullQueryResult.networkQueryId ${fullQueryResult.queryId}")
      isx.fillInStackTrace()
      Log.debug("SHRINE-2239 trapped ",isx)
    }

    new QResult(
      resultId = fullQueryResult.resultId,
      networkQueryId = queryId,
      instanceId  = fullQueryResult.instanceId,
      adapterNode = fullQueryResult.adapterNode,
      count = fullQueryResult.count,
      status = resultStatusToUiString(fullQueryResult.status),
      internalStatus = fullQueryResult.status,
      statusMessage = fullQueryResult.statusMessage,
      changeDate = fullQueryResult.changeDate,
      breakdowns = fullQueryResult.breakdownTypeToResults.map(tToR => BreakdownResultsForType(fullQueryResult.adapterNode,tToR._1,tToR._2)).toSeq,
      problemDigest = fullQueryResult.problemDigest.map(JsonProblemDigest(_)),
      resultMetadata = fullQueryResult.resultMetadata
    )
  }

  private def resultStatusToUiString(internalStatus: String): String = {
    val legacyStatusMap: Map[String, String] =
      Map("FINISHED" -> ResultFromCRC.uiString)
        .withDefaultValue(UnknownFinal.uiString) //Anything from 1.25.4 or earlier is never coming back

    ResultStatus.namesToStatuses.get(internalStatus).fold{legacyStatusMap(internalStatus)}{ status => status.uiString}
  }
}

case class QueriesAndCount(queryRowCount: Int, queryCells: Seq[QueryCell])

case class QueryNameAndNotesData(name:String, notes:Option[String] = None)
object QueryNameAndNotesData
{
  def apply(name: String, notes:Option[String]): QueryNameAndNotesData = {
    // is this an ok place to do input validation?
    TextInputValidator.validate("name", name)
    TextInputValidator.validate("notes", notes.getOrElse(""), true, true, 1000)
    new QueryNameAndNotesData(name, notes)
  }
}

case class RunQueryResponse(networkQueryId: String, status: String) //todo rename and change to Long with front-end SHRINE2020-467

abstract class InvalidStartQueryRequestException(message: String) extends IllegalArgumentException(message)
case class InvalidValueConstraintException(message: String) extends InvalidStartQueryRequestException(message)
case class InvalidConceptGroupOptionsException(message: String) extends InvalidStartQueryRequestException(message)
case class InvalidTextInputException(message: String, queryName: QueryName) extends InvalidStartQueryRequestException(s"""Invalid input: "$queryName"\nReason: $message""")

case class InvalidChangeFavQueryException(message: String) extends IllegalArgumentException(message)

case class UserSessionToken(username: String,
                            sessionId: String,
                            sessionTimeoutMs: Option[Long] = None,
                            // The timeout is returned by the server when the user logs in for the first time using Basic Auth
                            )

case class AllResultsHaveErrorProblem(queryId:Long) extends AbstractProblem(ProblemSources.Adapter) {
  override def logLevel: Level = Level.TRACE

  override def summary: String = s"All results returned an  error for query $queryId ."
  override def description:String = s"Query $queryId found but all its results are in an error state."
}
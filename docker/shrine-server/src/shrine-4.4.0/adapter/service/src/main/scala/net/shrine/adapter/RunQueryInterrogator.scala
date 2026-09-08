package net.shrine.adapter

import java.sql.SQLException
import cats.effect.{FiberIO, IO}
import ch.qos.logback.classic.Level
import com.typesafe.config.Config
import net.shrine.adapter.dao.{AdapterQueryHistoryDb, BotDetectedException}
import net.shrine.adapter.i2b2Protocol.{ErrorResponse, HiveCredentials, RawCrcRunQueryResponse, ReadResultRequest, ReadResultResponse, RunQueryRequest, RunQueryResponse, XmlNodeName}
import net.shrine.adapter.mappings.AdapterMappings
import net.shrine.adapter.translators.{ExpressionTranslator, QueryDefinitionTranslator}
import net.shrine.config.ConfigExtensions
import net.shrine.http4s.client.legacy.{EndpointConfig, Poster}
import net.shrine.hub.mom.ShrineMomClient
import net.shrine.log.Loggable
import net.shrine.problem.{AbstractProblem, Problem, ProblemSources}
import net.shrine.protocol.i2b2.query.{CouldNotMapAllTermsException, CouldNotTranslateQueryDefinitionException, I2b2QueryDefinition}
import net.shrine.protocol.i2b2.{AuthenticationInfo, Credential, I2b2Result, QueryResult, ResultOutputType, ShrineRequest}
import net.shrine.protocol.version.v2.{ResultMetadata, ResultProgress, ResultStatus, RunQueryForResult, UpdateResult, UpdateResultWithError, UpdateResultWithProgress}

import java.util.Date
import scala.concurrent.duration.{Duration, DurationInt}
import scala.language.postfixOps
import scala.util.control.NonFatal
import scala.xml.NodeSeq

/**
 * @author Bill Simons
 * @author clint
 * @since 4/15/11
 * @see http://cbmi.med.harvard.edu
 * @see http://chip.org
 *       <p/>
 *       NOTICE: This software comes with NO guarantees whatsoever and is
 *       licensed as Lgpl Open Source
 * @see http://www.gnu.org/licenses/lgpl.html
 */
final case class RunQueryInterrogator(
  poster: Poster,
  hiveCredentials: HiveCredentials,
  conceptTranslator: QueryDefinitionTranslator,
  doObfuscation: Boolean,
  runQueriesImmediately: Boolean,
  breakdownTypes: Set[ResultOutputType],
  botCountTimeThresholds:Seq[(Long,Duration)],
  obfuscator: Obfuscator,
  crcRunQueryTimeLimit:Duration = 30 seconds
) extends Loggable {

  private[adapter] lazy val runQueryClientClient: CrcClient[RunQueryRequest, RunQueryResponse] = CrcClient[RunQueryRequest, RunQueryResponse](poster, hiveCredentials)
  private lazy val readResultClient = new CrcClient[ReadResultRequest, ReadResultResponse](poster, hiveCredentials)

  logStartup()

  def startRunQueryForExpectedResult(runQueryForResult: RunQueryForResult): IO[FiberIO[Unit]] = {
    //todo See SHRINE2020-1377 and SHRINE2020-1303 - background might be a better method than start, but really this needs a rethink through the dispatcher
    runQueryForExpectedResultIO(runQueryForResult).start
  }

  private def runQueryForExpectedResultIO(runQueryForResult: RunQueryForResult):IO[Unit] = {
    val obfuscatingParams = if(doObfuscation){ Option(obfuscator.obfuscatorParameters) } else None
    val resultReceived: ResultProgress = runQueryForResult.resultProgress.withStatus(ResultStatus.ReceivedByAdapter, resultMetadata = ResultMetadata(obfuscatingParameters = obfuscatingParams))

    val preliminariesAndResultIO: IO[UpdateResult] = for {
      runQueryRequest <- IO(runQueryForResultToRunQueryRequest(runQueryForResult))
      _ <- sendUpdateResultIO(UpdateResultWithProgress(resultReceived))
      _ <- AdapterQueryHistoryDb.db.checkIfBotIO(runQueryRequest.authn, botCountTimeThresholds)
      result <- runQueryNowOrLater(resultReceived,runQueryRequest)
    } yield result

    val resultFromRunIO: IO[UpdateResult] = preliminariesAndResultIO.handleErrorWith{ throwable =>
      val problem = throwable match {
        case e: AdapterMappingException => AdapterMappingProblem(e)
        case e: SQLException => AdapterDatabaseProblem(e)
        case e: CrcInvocationException => CrcCouldNotBeInvoked(e.invokedUrl, e.request, e)
        case e: BotDetectedException => BotDetected(e)
        case NonFatal(x) => ExceptionWhileAttemptingToRunQuery(x)
      }
      val errorUpdate = UpdateResultWithError(resultReceived.toError(
        problem,
        ResultStatus.ErrorInShrine,
        resultMetadata = resultReceived.resultMetadata
      ))
      debug(s"errorUpdate is $errorUpdate", throwable)
      IO(errorUpdate)
    }

    for{
      r <- resultFromRunIO
      _ <- sendUpdateResultIO(r)
    } yield ()
  }

  private[adapter] def runQueryNowOrLater(resultReceived:ResultProgress,runQueryRequest:RunQueryRequest):IO[UpdateResult] = {
    if (runQueriesImmediately) {
      debug(s"Performing query $resultReceived from user ${runQueryRequest.authn.domain}:${runQueryRequest.authn.username}")
      val translatedRequest: RunQueryRequest = translateRequest(runQueryRequest)
      debug(s"$resultReceived translated to $translatedRequest")

      IO(runQueryInCrcAndStoreResults(runQueryRequest, resultReceived, translatedRequest))
    } else {
      debug(s"Storing query from user ${runQueryRequest.authn.domain}:${runQueryRequest.authn.username} to run manually")

      AdapterQueryHistoryDb.db.storeQueryIO(runQueryRequest.authn, runQueryRequest).map(_ => {
        val updateProgress = UpdateResultWithProgress(
          result = resultReceived.withStatus(ResultStatus.QueuedForManualSubmission, Option("Adapter configured to submit queries manually"), resultMetadata = ResultMetadata(obfuscatingParameters = Option(obfuscator.obfuscatorParameters)) )
        )
        updateProgress
      })
    }
  }

  private[adapter] def translateRequest(request: RunQueryRequest): RunQueryRequest = {
    val HiveCredentials(domain, username, password, project) = hiveCredentials
    val authInfo = AuthenticationInfo(domain, username, Credential(password, isToken = false))
    translateNetworkToLocal(request.withAuthn(authInfo).withProject(project))
  }

  private def runQueryInCrcAndStoreResults (
                                             runQueryRequest: RunQueryRequest,
                                             resultReceived:ResultProgress,
                                             translatedRequest: RunQueryRequest
                                           ): UpdateResult = {

    val resultSubmitted = resultReceived.withStatus(ResultStatus.SubmittedToCRC, resultMetadata = resultReceived.resultMetadata)
    val updateResultSubmitted = UpdateResultWithProgress(resultSubmitted)

    sendUpdateResult(updateResultSubmitted)
    try {

//todo clean up with SHRINE2020-1277
      val i2b2Response: String = runQueryClientClient.callCrc(translatedRequest)

      //NB: Pass through ErrorResponses received from the CRC.
      //See: https://open.med.harvard.edu/jira/browse/SHRINE-794
      val responseFromCrc: Either[ErrorResponse, RunQueryResponse] =
        runQueryClientClient.parseShrineErrorResponseWithFallback(
          i2b2Response,resultSubmitted.queryId.underlying,
          parseShrineResponse(runQueryRequest.authn, runQueryRequest)
        )

      val updateResult: UpdateResult = responseToResult(resultSubmitted, responseFromCrc)
      debug(s"updateResult is $updateResult")

      updateResult
    } catch {
      case NonFatal(x) =>
        UpdateResultWithError(
          resultSubmitted.toCrcError(
            problem = ExceptionWhileAttemptingToRunQuery(x),
            crcQueryInstanceId = resultSubmitted.crcQueryInstanceId,
            resultMetadata = resultSubmitted.resultMetadata
          )
        )
    }
  }

  private[adapter] def runQueryForResultToRunQueryRequest(runQueryForResult: RunQueryForResult):RunQueryRequest = {

    val authToUse = AuthenticationInfo.noPasswordFromResearcher(runQueryForResult.researcher)

    val outputTypes: Set[ResultOutputType] = ResultOutputType.fromBreakdownNames(runQueryForResult.query.breakdownNames)

    val queryDefinition: I2b2QueryDefinition = try {
      I2b2QueryDefinition.fromShrineV2(runQueryForResult.query)
    } catch {
      case cntqdx: CouldNotTranslateQueryDefinitionException => throw cntqdx
      case NonFatal(x) => throw CouldNotTranslateQueryDefinitionException(runQueryForResult.query.asJsonText.underlying,Option(x))
    }

    i2b2Protocol.RunQueryRequest(
      projectId = hiveCredentials.projectId,
      waitTime = crcRunQueryTimeLimit,
      authn = authToUse,
      networkQueryId = runQueryForResult.query.id.underlying,
      outputTypes = outputTypes,
      queryDefinition = queryDefinition,
      nodeId = Some(XmlNodeName.fromProtocolNode(runQueryForResult.node))
    )
  }

  protected[adapter] def parseShrineResponse(authnToUse: AuthenticationInfo,request: RunQueryRequest)(xml: NodeSeq,queryId:Long): Either[ErrorResponse,RunQueryResponse] = {
    val rawRunQueryResponse = RawCrcRunQueryResponse.fromI2b2(breakdownTypes)(xml).get //TODO: Avoid .get call}
    Right[ErrorResponse,RunQueryResponse](processRawCrcRunQueryResponse(authnToUse, request, rawRunQueryResponse,queryId))
  }

  private def translateNetworkToLocal(request: RunQueryRequest): RunQueryRequest = {
    try {
      debug(s"start of translateNetworkToLocal")
      request.mapQueryDefinition(conceptTranslator.translate)
    }
    catch {
      case c:CouldNotMapAllTermsException =>
        info(s"Could not map query term(s)",c)
        throw AdapterMappingException(request, s"Could not map query term(s) ${c.unmappable.mkString(", ")}}.", c)
      case m:MappingException =>
        debug(s"caught MappingException",m)
        throw AdapterMappingException(request, m.message, m)
      case NonFatal(x) =>
        debug(s"caught NonFatal ",x)
        throw x
      case x:Throwable =>
        debug(s"caught ",x)
        throw x
    }
  }

  private def responseToResult(resultSubmitted:ResultProgress, shrineResponse: Either[ErrorResponse, RunQueryResponse]):UpdateResult = {
    shrineResponse.fold({errorResponse =>
      errorResponse.toUpdateResult(resultSubmitted)
    },{runQueryResponse =>
      runQueryResponse.singleNodeResult.createUpdateResult(
        resultSubmitted,
        runQueryResponse.queryInstanceId,
        resultSubmitted.resultMetadata.obfuscatingParameters
      )
    })
  }

  private def sendUpdateResultIO(updateResult: UpdateResult): IO[Unit] = {
    ShrineMomClient.sendToHubIO(updateResult.result.queryId, updateResult, UpdateResult, s"$updateResult to hub").
      handleErrorWith(t => IO(error("Exception while running RunQueryAdapter.sendUpdateResult", t)))
  }

  //todo delete with SHRINE2020-1278
  private def sendUpdateResult(updateResult: UpdateResult): Unit = {
    import cats.effect.unsafe.implicits.global
    sendUpdateResultIO(updateResult).unsafeRunAndForget()
  }

  private[adapter] def processRawCrcRunQueryResponse(
                                                      authnToUse: AuthenticationInfo,
                                                      request: RunQueryRequest,
                                                      rawRunQueryResponse: RawCrcRunQueryResponse,
                                                      queryId:Long
                                                    ): RunQueryResponse = {
    import cats.effect.unsafe.implicits.global
    def isBreakdown(result: QueryResult) = result.resultType.exists(_.isBreakdown)

    //here in the adapter only ever expect one count QueryResult
    val originalResults: Seq[QueryResult] = rawRunQueryResponse.results

    val (originalBreakdownResults, originalNonBreakDownResults): (Seq[QueryResult],Seq[QueryResult]) = originalResults.partition(isBreakdown)

    val resultsWithLeastCompleteState = resultsWithCountStateOfLeastCompleteState(request,originalResults,originalBreakdownResults)
    debug(s"resultsWithLeastCompleteState is $resultsWithLeastCompleteState")

    //there's always at least one count result, but it's not always the head
    val countResultWithLeastCompleteState = resultsWithLeastCompleteState.filter(_.resultType.exists(!_.isBreakdown)).head

    //if all the results are done without error it's OK to call the CRC and ask about breakdowns
    val originalBreakdownCountAttempts: Seq[(QueryResult, Either[ErrorResponse, QueryResult])] =
      if(countResultWithLeastCompleteState.statusType.isDone && !countResultWithLeastCompleteState.statusType.isError) {
        attemptToRetrieveBreakdowns(request, originalBreakdownResults, queryId)
      }
      //if any breakdowns are in some error or not-done state, don't attempt to get the breakdown counts from the CRC
      else Seq.empty

    val (successfulBreakdownCountAttempts, failedBreakdownCountAttempts) = originalBreakdownCountAttempts.partition { case (_, t) => t.isRight }

    val failedBreakdownCountAttemptsWithProblems: Seq[(QueryResult, Either[ErrorResponse, QueryResult])] = failedBreakdownCountAttempts.map { attempt: (QueryResult, Either[ErrorResponse, QueryResult]) =>
      val originalResult: QueryResult = attempt._1
      val queryResult:QueryResult = if (originalResult.problem.isDefined) originalResult
      else {
        attempt._2.fold({errorResponse =>
          val problem: Problem = errorResponse.problem
          originalResult.copy(
            statusType = QueryResult.StatusType.Error,
            statusMessage = Option(problem.summary),
            problem = Option(problem)
          )
        },identity)
      }
      (queryResult,attempt._2)
    }

    logBreakdownFailures(rawRunQueryResponse, failedBreakdownCountAttemptsWithProblems)

    val successAndFailedBreakdowns: Seq[QueryResult] =
      successfulBreakdownCountAttempts.map(_._2.getOrElse(throw new IllegalStateException("partition did not work"))) ++ failedBreakdownCountAttemptsWithProblems.map(_._1)

    val originalMergedBreakdowns: Map[ResultOutputType, I2b2Result] = {
      val withBreakdownCounts = successfulBreakdownCountAttempts.collect { case (_, Right(queryResultWithBreakdowns)) => queryResultWithBreakdowns }

      withBreakdownCounts.map(_.breakdowns).fold(Map.empty)(_ ++ _)
    }

    //Now we've got a new set of breakdown results from the CRC
    //some of which could conceivably be in different states than the first query
    //so check it a second time
    val resultWithBreakdownState = resultsWithCountStateOfLeastCompleteState(request,resultsWithLeastCompleteState,successAndFailedBreakdowns)

    val obfuscatedQueryResults: Seq[QueryResult] = resultWithBreakdownState.map(obfuscator.obfuscate)

    debug(s"obfuscatedQueryResults is $obfuscatedQueryResults")

    val obfuscatedNonBreakdownQueryResults: Seq[QueryResult] = obfuscatedQueryResults.filterNot(isBreakdown)

    val obfuscatedMergedBreakdowns: Map[ResultOutputType, I2b2Result] = originalMergedBreakdowns.view.mapValues(_.mapValues(obfuscator.obfuscate)).toMap

    val failedBreakdownTypes: Seq[ResultOutputType] = failedBreakdownCountAttemptsWithProblems.flatMap { case (qr, _) => qr.resultType }

    AdapterQueryHistoryDb.db.storeResultsIO(
      authenticationInfo = authnToUse,
      masterId = rawRunQueryResponse.queryId.toString,
      networkQueryId = request.networkQueryId,
      queryDefinition = request.queryDefinition,
      rawQueryResults = resultWithBreakdownState,
      obfuscatedQueryResults = obfuscatedQueryResults,
      failedBreakdownTypes = failedBreakdownTypes,
      mergedBreakdowns = originalMergedBreakdowns,
      obfuscatedBreakdowns = obfuscatedMergedBreakdowns).unsafeRunSync() //todo clean up with SHRINE2020-1278

    // at this point the queryResult could be a mix of successes and failures.
    // SHRINE reports only the successes. See SHRINE-1567 for details
    val queryResults: Seq[QueryResult] = if (doObfuscation) obfuscatedNonBreakdownQueryResults else originalNonBreakDownResults
    val breakdownsToReturn: Map[ResultOutputType, I2b2Result] = if (doObfuscation) obfuscatedMergedBreakdowns else originalMergedBreakdowns

    //can failedBreakdownCountAttempts be mixed back in here?
    //there's always at least one count result, but it's not always the head
    val obfuscatedCountResult: QueryResult = queryResults.filter(_.resultType.exists(!_.isBreakdown)).head

    val resultWithBreakdowns: QueryResult = obfuscatedCountResult.withBreakdowns(breakdownsToReturn)

    debug(s"resultWithBreakdowns is $resultWithBreakdowns")

    if(debugEnabled) {
      def justBreakdowns(breakdowns: Map[ResultOutputType, I2b2Result]) = breakdowns.view.mapValues(_.data)

      debug(s"Returning QueryResult with count ${resultWithBreakdowns.setSize} ")
      debug(s"Returning QueryResult with breakdowns ${justBreakdowns(resultWithBreakdowns.breakdowns)} ")
    }

    //if any results are queued the resultWithBreakdowns should already be queued

    //for the first run of a query with breakdowns that has any errors - this is the right behavior.
    val problem: Option[Problem] = failedBreakdownCountAttemptsWithProblems.headOption.flatMap(x => x._1.problem)
    val queryResult: QueryResult = problem.fold(resultWithBreakdowns){pd =>
      if(resultWithBreakdowns.problem.isEmpty )
        QueryResult.errorResult(Some(pd.description),"Error in breakdown from CRC",pd)
      else resultWithBreakdowns
    }

    debug(s"Final queryResult: $queryResult")

    rawRunQueryResponse.toRunQueryResponse.withResult(queryResult)
  }

  private def resultsWithCountStateOfLeastCompleteState(
                                                         request: RunQueryRequest,
                                                         originalResults: Seq[QueryResult],
                                                         originalBreakdownResults: Seq[QueryResult]
                                                       ): Seq[QueryResult] = {
    //there's always at least one count result, but it's not always the head
    //and sometimes there's no head because the CRC sent something other than results
    val countResult = originalResults.find(_.resultType.exists(!_.isBreakdown)).getOrElse(throw ResultsContainNoCountResultException(originalResults))

    val countQueryState: QueryResult.StatusType = countResult.statusType
    val firstBreakdownError: Option[QueryResult] = originalBreakdownResults.find(_.statusType.isError)
    val firstBreakdownQueued: Option[QueryResult] = originalBreakdownResults.find(!_.statusType.isDone)
    val expectedBreakdownsAreAbsentFromRaw:Boolean = request.outputTypes.exists(_.isBreakdown) && originalBreakdownResults.isEmpty

    //if the query is in error, pass through
    if(countQueryState.isError) originalResults
    //if any breakdowns are in error change the query's state to error
    else if(firstBreakdownError.isDefined) Seq(countResult.consolidateWithError(firstBreakdownError.get))
    //if the query is queued, pass through
    else if(countQueryState.crcPromisedToFinishAfterReply) originalResults
    //else if any breakdowns are queued change the query's state to that queued state
    else if(firstBreakdownQueued.isDefined) Seq(countResult.withStatus(
      statusType = firstBreakdownQueued.get.statusType,
      statusMessage = firstBreakdownQueued.get.statusMessage.orElse(Some("No status message for the firstBreakdownQueued"))
    ))
    //if breakdowns are expected, but don't appear at all yet, send back some pending state
    else if(expectedBreakdownsAreAbsentFromRaw) Seq(countResult.copy(
      statusType = QueryResult.StatusType.Processing,
      statusMessage = Some("The CRC does not know about the breakdowns yet, but should soon")
    ))
    //else everything is finished. Pass through
    else originalResults
  }

  private def getResultFromCrc(parentRequest: RunQueryRequest, resultId: Long, queryId:Long): Either[ErrorResponse, ReadResultResponse] = {
    val readResultRequest = ReadResultRequest(hiveCredentials.projectId, parentRequest.waitTime, hiveCredentials.toAuthenticationInfo, resultId.toString)

    readResultClient.request(ReadResultResponse.fromI2b2)(readResultRequest,queryId)
  }

  private[adapter] def attemptToRetrieveBreakdowns(runQueryReq: RunQueryRequest, breakdownResults: Seq[QueryResult],queryId:Long): Seq[(QueryResult, Either[ErrorResponse, QueryResult])] = {
    breakdownResults.map { origBreakdownResult =>
      origBreakdownResult -> (for {
        breakdownData <- getResultFromCrc(runQueryReq, origBreakdownResult.resultId,queryId).map(_.data)
      } yield origBreakdownResult.withBreakdown(breakdownData))
    }
  }

  private[adapter] def logBreakdownFailures(response: RawCrcRunQueryResponse,failures: Seq[(QueryResult, Either[ErrorResponse, QueryResult])]) : Unit = {
    for {
      (origQueryResult, Left(e)) <- failures
    } {
      error(
        s"""Couldn't load breakdown for QueryResult with masterId: ${response.queryId}, instanceId: ${origQueryResult.instanceId}, resultId: ${origQueryResult.resultId}.
           |Asked for result type: ${origQueryResult.resultType}.
           |${e.problem}""".stripMargin)
    }
  }

  private def logStartup(): Unit = {
    val message = if (runQueriesImmediately) { s"${getClass.getSimpleName} will run queries immediately" }
                  else { s"${getClass.getSimpleName} will queue queries for later execution" }

    info(message)
  }
}

object RunQueryInterrogator {
  def apply(config:Config): RunQueryInterrogator = {
    val shrineConfig = config.getConfig("shrine")

    val adapterConfig = shrineConfig.getConfig("adapter")

    val crcEndpoint: EndpointConfig = adapterConfig.getConfigured("crcEndpoint",EndpointConfig(_))
    val crcPoster: Poster = Poster(crcEndpoint)

    val breakdownTypes: Set[ResultOutputType] = ResultOutputType.breakdownTypes

    val crcHiveCredentials: HiveCredentials = shrineConfig.getConfigured("hiveCredentials", HiveCredentials(_, HiveCredentials.CRC))

    val adapterMappingsFile = adapterConfig.getString("adapterMappingsFileName")
    val adapterMappings: AdapterMappings = AdapterMappings(adapterMappingsFile)
    val expressionTranslator: ExpressionTranslator = ExpressionTranslator(adapterMappings)

    val queryDefinitionTranslator: QueryDefinitionTranslator = new QueryDefinitionTranslator(expressionTranslator)

    val doObfuscation = adapterConfig.getBoolean("setSizeObfuscation")
    val runQueriesImmediately = adapterConfig.getBoolean("immediatelyRunIncomingQueries")

    val botCountTimeThresholds: Seq[(Long, Duration)] = {
      import scala.jdk.CollectionConverters.ListHasAsScala
      import scala.concurrent.duration.DurationLong

      val countsAndMilliseconds: Seq[Config] = adapterConfig.getConfig("botDefense").
                                                getConfigList("countsAndMilliseconds").asScala.toSeq
      countsAndMilliseconds.map(pairConfig =>
        (pairConfig.getLong("count"),pairConfig.getLong("milliseconds").milliseconds))
    }

    val obfuscator:Obfuscator = adapterConfig.getConfigured("obfuscation",Obfuscator(_))

    val crcRunQueryTimeLimit = adapterConfig.get("crcRunQueryTimeLimit",Duration(_))

    new RunQueryInterrogator(
      crcPoster,
      crcHiveCredentials,
      queryDefinitionTranslator,
      doObfuscation,
      runQueriesImmediately,
      breakdownTypes,
      botCountTimeThresholds,
      obfuscator,
      crcRunQueryTimeLimit
    )
  }
}

case class ExceptionWhileAttemptingToRunQuery(x:Throwable) extends AbstractProblem(ProblemSources.Adapter) {
  override def logLevel: Level = Level.WARN
  override val throwable: Option[Throwable] = Some(x)
  override val summary: String = "The adapter could not run the query."
  override val description = "The adapter could not run the query due to an exception."
}

case class CrcCouldNotBeInvoked(crcUrl:String,request:ShrineRequest,x:CrcInvocationException) extends AbstractProblem(ProblemSources.Adapter) {
  override def logLevel: Level = Level.WARN
  override val throwable: Option[Throwable] = Some(x)
  override val summary: String = s"Error communicating with I2B2 CRC."
  override val description: String = s"Error invoking the CRC at '$crcUrl' with a ${request.getClass.getSimpleName}."
  override def detailsText: Option[String] = Some(s"Request is $request. Error invoking the CRC due to ${throwable.get}")
}

case class AdapterMappingProblem(x:AdapterMappingException) extends AbstractProblem(ProblemSources.Adapter) {
  override def logLevel: Level = Level.DEBUG
  override val throwable: Option[Throwable] = Some(x)
  override val summary: String = x.message
  override val description = s"The Shrine Adapter on ${stamp.host.getHostName} cannot map this query to its local terms."
  override def detailsText: Option[String] = Some(
    s"""
       |Query Definition is ${x.runQueryRequest.queryDefinition}
       |RunQueryRequest is ${x.runQueryRequest}
     """.stripMargin)
}

case class AdapterDatabaseProblem(x:SQLException) extends AbstractProblem(ProblemSources.Adapter) {
  override def logLevel: Level = Level.ERROR
  override val throwable: Option[Throwable] = Some(x)
  override val summary: String = "Problem using the Adapter database."
  override val description = "The Shrine Adapter encountered a problem using a database."
}

case class BotDetected(bdx:BotDetectedException) extends AbstractProblem(ProblemSources.Adapter) {
  override def logLevel: Level = Level.WARN
  override val throwable: Option[Throwable] = Some(bdx)
  override val summary: String = s"A user has run so many queries in a period of time that the adapter suspects a bot."
  override val description: String = s"${bdx.domain}:${bdx.username} has run ${bdx.detectedCount} queries since ${new Date(bdx.sinceMs)}, more than the limit of ${bdx.limit} allowed in this time frame."
}

case class ResultsContainNoCountResultException(originalResults: Seq[QueryResult])
  extends Exception(s"The CRC's response contains no count result in $originalResults")

case class MappingException(message: String) extends RuntimeException(message)

case class AdapterMappingException(runQueryRequest: RunQueryRequest, message: String, cause: Throwable) extends
  AdapterException(s"$message for request $runQueryRequest", cause)
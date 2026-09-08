package net.shrine.adapter

import cats.effect.IO
import ch.qos.logback.classic.Level
import com.typesafe.config.Config
import net.shrine.adapter.dao.model.{ShrineQuery, ShrineQueryResult}
import net.shrine.adapter.dao.{AdapterQueryHistoryDb, QueryHistory, QueryResultStatus}
import net.shrine.adapter.i2b2Protocol.{ErrorResponse, HasQueryResults, HiveCredentials, QueryInstance, ReadInstanceResultsRequest, ReadInstanceResultsResponse, ReadQueryInstancesRequest, ReadQueryInstancesResponse, ReadQueryResultResponse, ReadResultRequest, ReadResultResponse, ShrineResponse}
import net.shrine.config.ConfigExtensions
import net.shrine.http4s.client.legacy.{EndpointConfig, Poster}
import net.shrine.log.Loggable
import net.shrine.problem.{AbstractProblem, Problem, ProblemSources}
import net.shrine.protocol.i2b2.query.I2b2QueryDefinition
import net.shrine.protocol.i2b2.{AuthenticationInfo, I2b2Result, QueryResult, ResultOutputType}

import scala.concurrent.duration.Duration
import scala.util.control.NonFatal


/**
 * Follows up on the double-handful of "ask the CRC later" states, all of which shrine's
 * adapter treats as the equivalent of QUEUED.
 *
 * QueuedQueryInterrogator is triggered every 60 seconds (by default, controlled by shrine.adapter.queuedQueryPollInterval.)
 * The polling is one-at-a-time for queued queries.
 *
 * If CRC reports a final state – a result or an error – to the adapter then the adapter reports that to the hub
 * (which passes the update to the researcher’s local QEP). Otherwise the adapter does not report anything.
 * (~5 days later the hub should give up on the result and report an error to the QEP.)
 *
 * The adapter waits at least 15 seconds after the initial reply from the CRC before checking on a newly QUEUED query.
 *
 * In accordance with guidance from the i2b2 team shrine makes three http requests in series – to be sure the previous
 * was successful before reporting a count – and to report any error in the series as a final error.
 *
 * Those requests are
 *
 * 1) a CRC_QRY_getQueryInstanceList_fromQueryMasterId
 * 2) a CRC_QRY_getQueryResultInstanceList_fromQueryInstanceId
 * 3) a CRC_QRY_getResultDocument_fromResultInstanceId
 *
 * For breakdowns - shrine makes an additional CRC_QRY_getResultDocument_fromResultInstanceId per breakdown count needed.
 *
 * @author clint
 * @since Nov 2, 2012
 */
final case class QueuedQueryInterrogator(
																				poster: Poster,
																				hiveCredentials: HiveCredentials,
																				doObfuscation: Boolean,
																				breakdownTypes: Set[ResultOutputType],
																				obfuscator: Obfuscator,
																				queuedQueryTimeToLive:Duration
																			) extends Loggable {

	private lazy val readQueryInstanceResultClient = new CrcClient[ReadQueryInstancesRequest, ReadQueryInstancesResponse](poster, hiveCredentials)
	private lazy val readInstanceResultClient = new CrcClient[ReadInstanceResultsRequest, ReadInstanceResultsResponse](poster, hiveCredentials)
	private lazy val readResultClient = new CrcClient[ReadResultRequest, ReadResultResponse](poster, hiveCredentials)

	private def requestQueuedResultFromCrc(
																					queryId: Long,  //network query id
																					i2b2MasterQueryId: Long,
																					waitTime: Duration,
																					researcherAuthn: AuthenticationInfo,
																					shrineQueryResult: ShrineQueryResult
																				): Either[ErrorResponse, ShrineResponse ] = { //Can be a ReadQueryInstancesResponse, ReadInstanceResultsResponse, or ReadQueryResultResponse
		val errorOrResponse = requestReadQueryInstanceInCrc(queryId,i2b2MasterQueryId,waitTime).flatMap{ rqiResponse: ReadQueryInstancesResponse =>
			if (rqiResponse.queryInstances.forall(_.queryStatus.isDone) &&
				!rqiResponse.queryInstances.exists(_.queryStatus.isError)) {
				//get that queryInstanceId carefully
				rqiResponse.queryInstances.headOption.map{ queryInstance: QueryInstance =>
					retrieveIfComplete(
						queryId = queryId,
						i2b2QueryInstanceId = queryInstance.queryInstanceId.toLong,
						waitTime = waitTime,
						researcherAuthn = researcherAuthn,
						shrineQueryResult = shrineQueryResult
					)
				}.getOrElse(Left(ErrorResponse(NoQueryInstanceFromCrc(queryId,rqiResponse))))
			} else {
				//the CRC told us the query was incomplete or has an error.
				debug(s"Query is in an incomplete state or has an error. Reply was $rqiResponse ")
				Right(rqiResponse)
			}
		}
		//record select responses to the adapter's database
		errorOrResponse.fold({ errorResponse: ErrorResponse =>
			//if an erroring query has aged out, record it in the adapters database as an error (and give up asking the CRC for it)
			if(System.currentTimeMillis() - shrineQueryResult.dateCreated.toGregorianCalendar.getTimeInMillis > queuedQueryTimeToLive.toMillis) {
				info(s"Adapter gave up on query $queryId after $queuedQueryTimeToLive")
				val errorResult = QueryResult.errorResult(Some(errorResponse.errorMessage), s"Adapter gave up on query after $queuedQueryTimeToLive", errorResponse.problem)
				val response: ReadQueryResultResponse = ReadQueryResultResponse(queryId, errorResult)
				storeResultIfNecessary(shrineQueryResult, response, researcherAuthn, queryId, Set.empty)
			}
		},{ _ => //shrineQueryResult already saved todo save the result here SHRINE2020-1278
		})

		errorOrResponse
	}

	private def requestReadQueryInstanceInCrc(
																	queryId: Long,
																	i2b2MasterQueryId: Long,
																	waitTime: Duration
																): Either[ErrorResponse, ReadQueryInstancesResponse] = {

		//Use a ReadQueryInstancesRequest to find out if it is safe to ask for the results
		val readQueryInstancesRequest: ReadQueryInstancesRequest = ReadQueryInstancesRequest(
			projectId = hiveCredentials.projectId,
			waitTime = waitTime,
			authn = hiveCredentials.toAuthenticationInfo,
			i2b2MasterQueryId = i2b2MasterQueryId
		)

		readQueryInstanceResultClient.request(ReadQueryInstancesResponse.fromI2b2)(readQueryInstancesRequest,queryId)
	}

	/*
Send a ReadInstanceResultsRequest to see if the query is actually completed before risking asking for a result
 */
	private def retrieveIfComplete(queryId: Long,
																 i2b2QueryInstanceId: Long,
																 waitTime: Duration,
																 researcherAuthn: AuthenticationInfo,
																 shrineQueryResult: ShrineQueryResult
																): Either[ErrorResponse, HasQueryResults] = {

		val queryCompletedAttempt: Either[ErrorResponse, ReadInstanceResultsResponse] = readInstanceResults(queryId,i2b2QueryInstanceId,waitTime)

		queryCompletedAttempt.fold(Left(_),{ queryCompletedResponse: ReadInstanceResultsResponse =>
			if(queryCompletedResponse.results.forall(_.statusType.isDone)) {
				//Finally ask for the results.
				debug(s"OK to ask the CRC for the results for $queryId")
				retrieveQueryResults(queryId, waitTime, researcherAuthn, shrineQueryResult, queryCompletedResponse)
			}else {
				debug(s"Not safe to ask the CRC for the results for $queryId queryCompletedAttempt is $queryCompletedAttempt")
				queryCompletedAttempt
			}
		})
	}

	private def storeResultIfNecessary(shrineQueryResult: ShrineQueryResult,
																		 response: ReadQueryResultResponse,
																		 authn: AuthenticationInfo,
																		 queryId: Long, //network query id
																		 failedBreakdownTypes: Set[ResultOutputType]): IO[Unit] = {
		debug(s"storeResultIfNecessary ${response.results}")
		val responseIsDone = response.results.forall(_.statusType.isDone)

		if (responseIsDone) {
			storeResultIO(shrineQueryResult, response, authn, queryId, failedBreakdownTypes)
		} else IO.unit

	}

	//See if the query is actually completed.
	private def readInstanceResults( queryId: Long,
																	 localQueryId: Long,
																	 waitTime: Duration
																 ): Either[ErrorResponse, ReadInstanceResultsResponse] = {

		val readInstanceResultsRequest = ReadInstanceResultsRequest(
			projectId = hiveCredentials.projectId,
			waitTime = waitTime,
			authn = hiveCredentials.toAuthenticationInfo,
			queryInstanceId = localQueryId
		)
		readInstanceResultClient.request(ReadInstanceResultsResponse.fromI2b2)(readInstanceResultsRequest,queryId)
	}

	private def retrieveQueryResults(
																		queryId: Long,
																		waitTime:Duration,
																		researcherAuthn:AuthenticationInfo,
																		shrineQueryResult: ShrineQueryResult,
																		queryCompletedResponse: ReadInstanceResultsResponse
																	): Either[ErrorResponse, ReadQueryResultResponse] = {

		// use the queryCompletedResponse to get the count
		val countResponse: Either[ErrorResponse, ReadResultResponse] = retrieveCountResults(waitTime, shrineQueryResult,queryId)

		info(s"countResponse is $countResponse")
		countResponse.flatMap{ goodCountResponse: ReadResultResponse =>

			val breakdownResults: Either[Seq[ErrorResponse], Map[ResultOutputType, I2b2Result]] = retrieveBreakdownResults(waitTime, queryCompletedResponse,queryId)

			info(s"breakdownResults are $breakdownResults")

			breakdownResults.fold({ breakdownErrorResponses =>
				//Someday store errors in breakdowns after they get redesigned. For now report the first error
				//storeResultIfNecessary(shrineQueryResult, countResponse, researcherAuthn, queryId, getFailedBreakdownTypes(breakdownResponseAttempts))

				Left(breakdownErrorResponses.head)
			}, { breakdownsByType =>
				val queryResultWithBreakdowns: QueryResult = goodCountResponse.metadata.withBreakdowns(breakdownsByType)
				val queryResultToReturn: QueryResult = if (doObfuscation) obfuscator.obfuscate(queryResultWithBreakdowns) else queryResultWithBreakdowns

				val response: ReadQueryResultResponse = i2b2Protocol.ReadQueryResultResponse(queryId, queryResultToReturn)
				//NB: Only store the result if needed, that is, if all results are done
				import cats.effect.unsafe.implicits.global
				storeResultIfNecessary(shrineQueryResult, response, researcherAuthn, queryId, Set.empty).unsafeRunSync() //todo clean up with SHRINE2020-1278

				Right(response)
			})
		}
	}

	private def storeResultIO(
														 shrineQueryResult: ShrineQueryResult,
														 response: ReadQueryResultResponse,
														 authn: AuthenticationInfo,
														 queryId: Long,
														 failedBreakdownTypes: Set[ResultOutputType]): IO[Unit]= {
		debug(s"storeResultIO $response")
		val rawResults: Seq[QueryResult] = response.results
		val obfuscatedResults: Seq[QueryResult] = obfuscator.obfuscateResults(doObfuscation)(response.results)
		debug(s"queryId $queryId")

		val shrineQueryOptionIO : IO[Option[ShrineQuery]] = AdapterQueryHistoryDb.db.selectQueryHistoryByQueryIdIO(queryId).map(p => p.map(_.toShrineQuery))

		val storeIO: IO[Unit] = shrineQueryOptionIO.flatMap{ shrineQueryOption: Option[ShrineQuery] =>
			debug(s"shrineQueryOption $shrineQueryOption")
			val optionIOStore: Option[IO[Unit]] = for {
				shrineQuery <- shrineQueryOption
				queryResult: QueryResult <- rawResults.headOption
				obfuscatedQueryResult: QueryResult <- obfuscatedResults.headOption
			} yield {
				val queryDefinition = I2b2QueryDefinition(shrineQuery.name, shrineQuery.queryDefinition.expr)
				debug(s"storeResultIO $obfuscatedQueryResult")

				if(queryResult.statusType.isDone) //After a query is QUEUED then only store a completed query
					AdapterQueryHistoryDb.db.storeResultsIO(authn, shrineQueryResult.localId, queryId, queryDefinition, rawResults, obfuscatedResults, failedBreakdownTypes.toSeq, queryResult.breakdowns, obfuscatedQueryResult.breakdowns)
				else
					IO.unit
			}
			optionIOStore.getOrElse(IO.unit)
		}
		storeIO
	}

	private def retrieveCountResults(
																		waitTime:Duration,
																		shrineQueryResult: ShrineQueryResult,
																		shrineNetworkId:Long
																	): Either[ErrorResponse, ReadResultResponse] = {
		val countRequest = i2b2Protocol.ReadResultRequest(hiveCredentials.projectId, waitTime, hiveCredentials.toAuthenticationInfo, shrineQueryResult.count.localId.toString)
		readResultClient.request(ReadResultResponse.fromI2b2)(countRequest,shrineNetworkId)
	}

	private def retrieveBreakdownResults(
																				waitTime:Duration,
																				queryCompletedResponse: ReadInstanceResultsResponse,
																				shrineNetworkQueryId:Long
																			): Either[Seq[ErrorResponse], Map[ResultOutputType, I2b2Result]] = {

		val breakdownIds: Seq[String] = queryCompletedResponse.results.filter(_.resultType.exists(_.isBreakdown)).map(_.resultId.toString)

		val breakdownAttempts: Seq[Either[ErrorResponse, ReadResultResponse]] = breakdownIds.map{ breakdownId =>
			val breakdownRequest = i2b2Protocol.ReadResultRequest(hiveCredentials.projectId, waitTime, hiveCredentials.toAuthenticationInfo, breakdownId)
			readResultClient.request(ReadResultResponse.fromI2b2)(breakdownRequest,shrineNetworkQueryId)
		}

		//split between error responses and successful results
		val (breakdownErrorResponses, breakdownResponses) = breakdownAttempts.partitionMap(identity)
		//Log errors retrieving breakdown
		breakdownErrorResponses.foreach(e => error(s"Error requesting breakdown result from the CRC: '$e'"))

		if(breakdownErrorResponses.isEmpty) {
			val breakdownsByType: Map[ResultOutputType, I2b2Result] = (for {
				breakdownResponse <- breakdownResponses
				resultType <- breakdownResponse.metadata.resultType
			} yield resultType -> breakdownResponse.data).toMap

			Right(breakdownsByType)
		} else Left(breakdownErrorResponses)
	}

	def askCrc(
							queryId: Long,  //network query id
							waitTime: Duration,
							researcherAuthn: AuthenticationInfo
						): IO[Either[ErrorResponse, ShrineResponse ]] = { //Can be a ReadQueryInstancesResponse, ReadInstanceResultsResponse, or ReadQueryResultResponse

		try {
			AdapterQueryHistoryDb.db.selectQueryHistoryByQueryIdIO(queryId).flatMap{ p: Option[QueryHistory] => {

				val res: Option[IO[Either[ErrorResponse, ShrineResponse ]]] = p.map(_.toShrineQuery).map(shrineQueryRow => {
					val responseIOOption: IO[Option[Either[ErrorResponse, ShrineResponse]]] = AdapterQueryHistoryDb.db.findResultsForIO(queryId).map (shrineQueryOption => {

						shrineQueryOption.map { shrineQueryResult: ShrineQueryResult =>
							info(s"Will ask the CRC for for results of $queryId")
							//if this times out (or hits some other CRC web api trouble) just send back the best answer from the SHRINE adapter database
							requestQueuedResultFromCrc(
								queryId = queryId,
								i2b2MasterQueryId = shrineQueryRow.i2b2MasterQueryId.toLong,
								waitTime = waitTime,
								researcherAuthn = researcherAuthn,
								shrineQueryResult = shrineQueryResult
							)
						}
					})

					val responseIOOptionFromCrcContext = responseIOOption

					val responseIO: IO[Either[ErrorResponse, ShrineResponse]] = responseIOOptionFromCrcContext.map(p => {
						p.getOrElse {
							info(s"Query $queryId found but not the results of the original call to the CRC. That call may not have returned from the initial CRC call yet")
							//todo interpret - maybe only an error if it has been sitting around longer than the CRC call timeout
							//don't record this in the adapter database
							Left(ErrorResponse(QueryResultNotAvailable(queryId)))
						}})

					responseIO
				})

				val responseIOEither: IO[Either[ErrorResponse, ShrineResponse ]] = res.getOrElse {
					//don't record this in the adapter database
					debug(s"Query $queryId not found in the Shrine DB")
					IO(Left(ErrorResponse(QueryNotFound(queryId))))
				}

				responseIOEither
			}}
		} catch {
			case NonFatal(x) => IO(Left(ErrorResponse(CouldNotRetrieveQueryFromCrc(queryId, x))))
		}
	}
	def selectQueuedQueryIds: IO[Seq[QueryResultStatus]] = AdapterQueryHistoryDb.db.selectQueuedQueries()

	def storeErrorFromShrineIO(oldQueryResult:QueryResultStatus, problem:Problem): IO[Unit] = {
		val authenticationInfo = AuthenticationInfo.noPassword(oldQueryResult.username,oldQueryResult.domain)
		val newErrorQueryResult = QueryResult.errorResult(Some(problem.description),problem.summary,problem)

		AdapterQueryHistoryDb.db.selectQueryHistoryByQueryIdIO(oldQueryResult.networkQueryId).map(p => p.map(_.toShrineQuery)).flatMap(qh => {
			qh.fold{
				IO(error(s"storeErrorFromShrine No ShrineQuery in the database for ${oldQueryResult.networkQueryId}. This should not happen."))
			}{ shrineQuery: ShrineQuery =>
				val queryDefinition: I2b2QueryDefinition = I2b2QueryDefinition(shrineQuery.name, shrineQuery.queryDefinition.expr)
				val maybeOldResultsIO: IO[Option[ShrineQueryResult]] = AdapterQueryHistoryDb.db.findResultsForIO(oldQueryResult.networkQueryId)

				maybeOldResultsIO.flatMap(maybeOldResults => {
					//maybeOldResults is only used to find errors in breakdowns
					val failedBreakdownTypes: Seq[ResultOutputType] = maybeOldResults.map(_.breakdowns.map(_.resultType)).getOrElse(Seq.empty)
					AdapterQueryHistoryDb.db.storeResultsIO(authenticationInfo, shrineQuery.i2b2MasterQueryId, oldQueryResult.networkQueryId, queryDefinition, Seq(newErrorQueryResult), Seq(newErrorQueryResult), failedBreakdownTypes, Map.empty, Map.empty)
				})
			}
		})
	}
}

object QueuedQueryInterrogator {
	def apply(config:Config): QueuedQueryInterrogator = {
		val shrineConfig = config.getConfig("shrine")

		val adapterConfig = shrineConfig.getConfig("adapter")

		val crcEndpoint: EndpointConfig = adapterConfig.getConfigured("crcEndpoint",EndpointConfig(_))
		val crcPoster: Poster = Poster(crcEndpoint)

		val breakdownTypes: Set[ResultOutputType] = ResultOutputType.breakdownTypes

		val crcHiveCredentials: HiveCredentials = shrineConfig.getConfigured("hiveCredentials", HiveCredentials(_, HiveCredentials.CRC))

		val doObfuscation = adapterConfig.getBoolean("setSizeObfuscation")

		val obfuscator:Obfuscator = adapterConfig.getConfigured("obfuscation",Obfuscator(_))

		val queuedQueryTimeToLive:Duration = adapterConfig.get("queuedQueryTimeToLive",Duration(_))

		new QueuedQueryInterrogator(
			crcPoster,
			crcHiveCredentials,
			doObfuscation,
			breakdownTypes,
			obfuscator,
			queuedQueryTimeToLive
		)
	}
}

case class QueryNotFound(queryId:Long) extends AbstractProblem(ProblemSources.Adapter) {
	override def logLevel: Level = Level.WARN
	override def summary: String = s"Query not found"
	override def description:String = s"No query with id $queryId found on ${stamp.host.getHostName}"
}

case class QueryResultNotAvailable(queryId:Long) extends AbstractProblem(ProblemSources.Adapter) {
	override def logLevel: Level = Level.DEBUG
	override def summary: String = s"Query $queryId found but its results are not available yet."
	override def description:String = s"Query $queryId found but its results are not available yet on ${stamp.host.getHostName} The call to the CRC may not have completed."
}

/**
 * Indicates that something went wrong, usually a networking problem, when the SHRINE adapter asked the i2b2 CRC
 * about a result.
 *
 * SHRINE might have made several attempts to send requests the i2b2 CRC before reporting this problem and giving
 * up on retrieving the result.
 */
case class CouldNotRetrieveQueryFromCrc(queryId:Long,x: Throwable) extends AbstractProblem(ProblemSources.Adapter) {
	override def logLevel: Level = Level.WARN
	override def summary: String = s"Could not retrieve query $queryId from the CRC"
	override def description:String = s"Unhandled exception while retrieving query $queryId from the CRC on ${stamp.host.getHostName}"
	override def throwable: Option[Throwable] = Some(x)
}

case class NoResultsForQueuedQuery(responseText: String, override val throwable: Option[Throwable]) extends AbstractProblem(ProblemSources.Adapter) {
	override def logLevel: Level = Level.WARN
	override val summary: String = "No results returned from CRC for a queued query."
	override val description = "The CRC returned a response with status but no results when asked for an update on a QUEUED query."
	override def detailsText: Option[String] = Some(s"Response from CRC was $responseText")
}

case class DoneButHasNoResultsForQueuedQuery(responseText: String) extends AbstractProblem(ProblemSources.Adapter) {
	override def logLevel: Level = Level.WARN
	override val summary: String = "The CRC reports a DONE status but has no results for a queued query"
	override val description = "The CRC returned a response with status DONE but no results when asked for an update on a QUEUED query."
	override def detailsText: Option[String] = Some(s"Response from CRC was $responseText")
}

case class NoQueryInstanceFromCrc(queryId:Long,rqiResponse: ReadQueryInstancesResponse) extends AbstractProblem(ProblemSources.Adapter) {
	override def logLevel: Level = Level.WARN
	override def summary: String = s"Query not found"
	override def description:String = s"No queryInstance for $queryId found on ${stamp.host.getHostName}"
	override def detailsText: Option[String] = Some(s"Response from CRC was $rqiResponse")
}
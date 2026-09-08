package net.shrine.adapter

import cats.effect.IO
import ch.qos.logback.classic.Level
import fs2.Stream
import net.shrine.adapter.dao.QueryResultStatus
import net.shrine.adapter.i2b2Protocol.{ErrorResponse, ReadInstanceResultsResponse, ReadQueryInstancesResponse, ReadQueryResultResponse, ShrineResponse}
import net.shrine.config.{ConfigExtensions, ConfigSource}
import net.shrine.http4s.catsio.RepeatedIOTask
import net.shrine.log.Loggable
import net.shrine.problem.{AbstractProblem, JsonProblemDigest, ProblemSources, RawProblem}
import net.shrine.protocol.version.{DateStamp, NodeKey, QueryId}
import net.shrine.protocol.i2b2.AuthenticationInfo
import net.shrine.protocol.version.v2.{ResultMetadata, ResultStatus, UpdateCrcQueuedResult, UpdateCrcQueuedResultWithError, UpdateCrcQueuedResultWithProgress}

import scala.concurrent.duration.FiniteDuration
import scala.util.control.NonFatal
import cats.effect.Temporal
import net.shrine.hub.mom.ShrineMomClient

/**
  * Poll for results from QUEUED queries and send those results to the hub when found.
  *
  * @since 1.26.5.1
 */

object QueuedQueriesPoller extends Loggable {
  import cats.effect.unsafe.implicits.global

  val queuedQueryTimeToLive:FiniteDuration = ConfigSource.config.getFiniteDuration("shrine.adapter.queuedQueryTimeToLive")

  private val queuedQueryPollInterval:FiniteDuration = ConfigSource.config.getFiniteDuration("shrine.adapter.queuedQueryPollInterval")

  private val queuedQueryRestTimeBeforePolling:FiniteDuration = ConfigSource.config.getFiniteDuration("shrine.adapter.queuedQueryRestTimeBeforePolling")

  private val queuedQueryCrcPollHttpCallTimeout:FiniteDuration = ConfigSource.config.getFiniteDuration("shrine.adapter.queuedQueryCrcPollHttpCallTimeout")

  val crcRunQueryTimeLimit:FiniteDuration = ConfigSource.config.getFiniteDuration("shrine.adapter.crcRunQueryTimeLimit")

  private val scheduler: RepeatedIOTask = RepeatedIOTask.scheduleIndefinitely(
    initialDelay = queuedQueryRestTimeBeforePolling,
    interval = queuedQueryPollInterval,
    task = () => checkForQueuedQueries(),
    name = "QueuedQueriesPoller"
  )

  def startIO(): IO[Unit] = scheduler.startIO()

  def stopIO(): IO[Unit] = scheduler.stopIO()

  private lazy val readQueryResultAdapter:QueuedQueryInterrogator = QueuedQueryInterrogator(ConfigSource.config)

  /**
    * @return IO[true] if any queued queries are found, false if not
    */
  private def checkForQueuedQueries():IO[Unit] = {
    //Check the database for not completed, not running queries
    val queuedQueriesIO: IO[Seq[QueryResultStatus]] = IO(info(s"Starting checkForQueuedQueries"))
    .flatMap(_ => readQueryResultAdapter.selectQueuedQueryIds)

    // Filter out queries that were very recently QUEUED
    val oldEnoughIO: IO[Seq[QueryResultStatus]] = queuedQueriesIO.map{ queuedQueries =>
      info(s"Found ${queuedQueries.size} queries to check: ${queuedQueries.mkString(", ")}")
      val now = System.currentTimeMillis()
      queuedQueries.filter{ queuedQuery =>
        //Wait at least polling time plus the CRC time. queuedQuery.timestamp.getTime can be the start time
        (now - queuedQuery.timestamp.getTime) > queuedQueryRestTimeBeforePolling.toMillis + crcRunQueryTimeLimit.toMillis
      }
    }
    //Ask the CRC for an update
    oldEnoughIO.flatMap{ oldEnough: Seq[QueryResultStatus] =>
      val oldQueryIds = oldEnough.map(_.networkQueryId).distinct

      val oldEnoughAndDistinct: Seq[QueryResultStatus] = oldQueryIds.flatMap { id =>
        oldEnough.collectFirst { case q: QueryResultStatus if q.networkQueryId == id => q } }

      info(s"Found ${oldEnoughAndDistinct.size} queries old enough to check: ${oldEnoughAndDistinct.mkString(", ")}")

      val askCrcIOs: Seq[IO[Unit]] = oldEnoughAndDistinct.map{ queuedQuery:QueryResultStatus =>
        debug(s"Will ask the CRC about query ${queuedQuery.networkQueryId}")
        val auth = AuthenticationInfo.noPassword(queuedQuery.username,queuedQuery.domain)
        readQueryResultAdapter.askCrc(
          queuedQuery.networkQueryId,
          queuedQueryCrcPollHttpCallTimeout,
          auth
        ).flatMap{ interpretAndMaybeSendAnUpdate(queuedQuery,_) }.handleErrorWith{
          case NonFatal(x) =>
            //trap NonFatal exceptions, log, and move on to the next query
            //this will handle bad behavior from the CRC, just stumbling on the problematic query and continuing
            IO(error(s"Caught exception in QueuedQueriesPoller while checking on query ${queuedQuery.networkQueryId}",x))
        }
      }
      //todo SHRINE2020-1310
      //todo after the fs2 and cats IO upgrade, shuffle the list of CRC IOs (or do a sort inspired by exponential back-off)
      //todo then race the clock - stop querying just after the next polling interval starts.
      //todo then change the default polling interval back to 30 seconds - shrine.adapter.queuedQueryRestTimeBeforePolling
      val itOfStreams = askCrcIOs.map(Stream.eval)
      val seqResultStatus = itOfStreams.foldLeft[Stream[IO,Unit]](Stream.empty){ (soFar,next) =>
        soFar.append(next)
      }.compile.drain

      //this polling worked - sleep a bit then keep going
      seqResultStatus.flatMap(_ => IO.unit)
    }.handleErrorWith{
      case NonFatal(x) =>
        error(s"Caught exception in QueuedQueriesPoller.checkForQueuedQueries",x)
        //something went wrong - sleep a bit then keep going so long as there is no fatal exception
        IO.unit
    }
  }

  private def interpretAndMaybeSendAnUpdate(
                                     oldQueryResult:QueryResultStatus,
                                     crcResponse:Either[ErrorResponse, ShrineResponse]
                                   ):IO[Unit] = {
    val queryId = new QueryId(oldQueryResult.networkQueryId)

    info(s"$queryId's response is $crcResponse")
    val maybeUpdateFromCrcIO: IO[Option[UpdateCrcQueuedResult]] = crcResponse.fold({ errorResponse: ErrorResponse =>
      readQueryResultAdapter.storeErrorFromShrineIO(oldQueryResult,errorResponse.problem).map(_ => { //Give the CRC one chance - See SHRINE-3327
        errorResponse.problem match {
          case qrna:QueryResultNotAvailable =>
            error(s"Not reporting $qrna to the hub. If it is recurring please set the status for this query to FINISHED in the ADAPTER.ADAPTER_QUERY_RESULT table")
            None       //don't report QueryResultNotAvailable SHRINE-3585
          case rp:RawProblem =>
            val updateWithError = UpdateCrcQueuedResultWithError(queryId = queryId, adapterNodeKey = NodeKey.localNodeKey, problem = JsonProblemDigest(rp), status = ResultStatus.ErrorFromCrc, statusMessage = Option(errorResponse.errorMessage), adapterTime = DateStamp.now, resultMetadata = ResultMetadata(Option(readQueryResultAdapter.obfuscator.obfuscatorParameters)))
            Option(updateWithError)
          case _ => throw new IllegalStateException("Error response problems must be subclasses of RawProblem")
        }
      })
    },{ shrineResponse: ShrineResponse  =>

      info(s"interpretAndMaybeSendAnUpdate shrineResponse is $shrineResponse")

      val maybeUpdateResultFromCrc: Option[UpdateCrcQueuedResult] = shrineResponse match {
        case rqrr:ReadQueryResultResponse =>
          val updateResult: UpdateCrcQueuedResult = rqrr.singleNodeResult.createUpdateCrcQueuedResult(queryId, rqrr.queryId, ResultMetadata(Option(readQueryResultAdapter.obfuscator.obfuscatorParameters)))
          if(updateResult.status.isFinal) {
            updateResult match {
              case e: UpdateCrcQueuedResultWithError => readQueryResultAdapter.storeErrorFromShrineIO(oldQueryResult, e.problem).unsafeRunSync()
              case _ => //do nothing. But the only final status you'll see at this stage is an error
            }
            Option(updateResult)
          }
          else None //only send an update if there's finally a result
        case rirr:ReadInstanceResultsResponse => // this is an intermediate state unless there's an error. If so store it.
          val updateResult = rirr.results.head.createUpdateCrcQueuedResult(queryId, rirr.results.head.resultId,  ResultMetadata(Option(readQueryResultAdapter.obfuscator.obfuscatorParameters)))
          updateResult match {
            case updateResultWithError:UpdateCrcQueuedResultWithError =>
              //If this update is an error then store it.
              // A ReadInstanceResultsResponse - if in error - will likely not be stored
              readQueryResultAdapter.storeErrorFromShrineIO(oldQueryResult, updateResultWithError.problem).unsafeRunSync()
              Option(updateResultWithError)
            case _ => None
          }
        case rqir:ReadQueryInstancesResponse => // this is an intermediate state unless there's an error. If so store it.
          val maybeUpdateResult = rqir.createUpdateResult(queryId, readQueryResultAdapter.obfuscator.obfuscatorParameters)
          maybeUpdateResult.foreach {
            case updateResultWithError:UpdateCrcQueuedResultWithError => readQueryResultAdapter.storeErrorFromShrineIO(oldQueryResult, updateResultWithError.problem).unsafeRunSync()
            case _ =>
          }
          maybeUpdateResult
        case _ => throw new IllegalArgumentException(s"Code does not handle a ${shrineResponse.getClass.getSimpleName} $shrineResponse")
      }
      IO(maybeUpdateResultFromCrc)
    })

    val maybeUpdateIO: IO[Option[UpdateCrcQueuedResult]] = maybeUpdateFromCrcIO.map(maybeUpdateFromCrc => {
      maybeUpdateFromCrc.fold{
        maybeQueryTooOld(oldQueryResult)  //no update from the CRC, so check timeout
      }{
        //still in some flavor of QUEUED
        case progress: UpdateCrcQueuedResultWithProgress => maybeQueryTooOld(oldQueryResult).orElse(Option(progress))
        case update => Option(update)
      }
    })
    maybeUpdateIO.flatMap( maybeUpdate => maybeUpdate.fold(IO.unit)(sendUpdateResultMessageIO))
  }

  private def maybeQueryTooOld(oldQueryResult:QueryResultStatus):Option[UpdateCrcQueuedResult] = {
    val now = System.currentTimeMillis()
    if ((now - oldQueryResult.timestamp.getTime) > queuedQueryTimeToLive.toMillis ) {
      val queryId = new QueryId(oldQueryResult.networkQueryId)
      val problem = QueryQueuedTimeToLiveExceeded(queryId,queuedQueryTimeToLive)

      //store error to the database
      readQueryResultAdapter.storeErrorFromShrineIO(oldQueryResult,problem).unsafeRunSync()
      info(s"should have stored 'too old' error for $queryId")

      Option(UpdateCrcQueuedResultWithError(queryId = queryId, adapterNodeKey = NodeKey.localNodeKey, problem = JsonProblemDigest(problem), status = ResultStatus.ErrorFromCrc, statusMessage = Option(problem.description), adapterTime = DateStamp.now, resultMetadata = ResultMetadata(Option(readQueryResultAdapter.obfuscator.obfuscatorParameters))))
    }
    else None
  }

  private def sendUpdateResultMessageIO(updateResult:UpdateCrcQueuedResult): IO[Unit] = {
    ShrineMomClient.sendToHubIO(updateResult.queryId,updateResult,UpdateCrcQueuedResult,s" updateResult for ${updateResult.queryId} to the hub")
  }
}

//noinspection ScalaFileName
case class QueryQueuedTimeToLiveExceeded(queryId: QueryId, queryTimeToLive:FiniteDuration) extends AbstractProblem(ProblemSources.Adapter) {
  override def logLevel: Level = Level.DEBUG
  override val throwable: Option[Throwable] = None
  override val summary: String = s"Shrine's adapter has given up on this query after $queryTimeToLive"
  override val description = s"Shrine's adapter has polled the CRC for a result from query $queryId for $queryTimeToLive before giving up."
  override def detailsText: None.type = None
}

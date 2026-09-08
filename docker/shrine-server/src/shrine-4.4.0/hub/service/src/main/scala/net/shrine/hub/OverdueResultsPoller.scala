package net.shrine.hub

import cats.effect.IO
import ch.qos.logback.classic.Level
import net.shrine.config.{ConfigExtensions, ConfigSource}
import net.shrine.http4s.catsio.RepeatedIOTask
import net.shrine.hub.data.store.HubDb
import net.shrine.log.Loggable
import net.shrine.problem.{AbstractProblem, ProblemSources}
import net.shrine.protocol.version.v2.{Node, Result, ResultStatus, UpdateResultWithError}
import net.shrine.protocol.version.{DateStamp, NodeId, QueryId}

import scala.concurrent.duration.FiniteDuration

object OverdueResultsPoller extends Loggable {

  private val resultsOverdueAfter: FiniteDuration = ConfigSource.config.getFiniteDuration("shrine.hub.result.resultsOverdueAfter")

  private val overdueResultsCheckPeriod: FiniteDuration = ConfigSource.config.getFiniteDuration("shrine.hub.result.overdueResultsCheckPeriod")

  private val overdueResultsBatchSize:Int = ConfigSource.config.getInt("shrine.hub.result.overdueResultsBatchSize")

  private val resultsDoNotBotherAfter: FiniteDuration = ConfigSource.config.getFiniteDuration("shrine.hub.result.resultsDoNotBotherAfter")

  private val scheduler = RepeatedIOTask.scheduleIndefinitely(
    initialDelay = overdueResultsCheckPeriod,
    interval = overdueResultsCheckPeriod,
    task = () => checkForOverdueResults(),
    name = this.getClass.getSimpleName
  )

  def startIO(): IO[Unit] = {
    scheduler.startIO() *> IO(info(s"${this.getClass.getSimpleName} started"))
  }

  def stopIO(): IO[Unit] = scheduler.stopIO() *> IO(info(s"${this.getClass.getSimpleName} stopped"))

  def checkForOverdueResults(): IO[Unit] = {
    for {
      now <- IO(DateStamp.now.underlying)
      before <- IO(new DateStamp(now - resultsOverdueAfter.toMillis))
      notBefore <- IO(new DateStamp(now - resultsDoNotBotherAfter.toMillis))
      _ <- IO( info(s"Checking for overdue results - results before $before and not before $notBefore"))
      nodes <- HubDb.db.selectLatestNodesIO
      updateCount <- batchProcessResults(0,before, notBefore,nodes.map{ node => node.get.id -> node.get}.toMap)
    } yield info(s"Updated $updateCount results")
  }

  private def batchProcessResults(batchNumber:Int, before:DateStamp, notBefore:DateStamp, nodeIdsToNodes:Map[NodeId, Node]):IO[Int] = {
    import cats.implicits._

    for {
      overdueResultsAndIsLast <- HubDb.db.selectOverdueBatchIO(before,notBefore,batchNumber,overdueResultsBatchSize)
      _ <- IO(debug(s"batch $batchNumber found ${overdueResultsAndIsLast._1.size} overdue results to update. isLast? ${overdueResultsAndIsLast._2}"))
      updateCounts <- overdueResultsAndIsLast._1.map(updateAsOverdue(_, nodeIdsToNodes)).sequence
      otherBatches <- if(overdueResultsAndIsLast._2) IO(0)
      else batchProcessResults(batchNumber +1, before, notBefore, nodeIdsToNodes)
    } yield updateCounts.sum + otherBatches
  }

  private def updateAsOverdue(result:Result, nodeIdsToNodes:Map[NodeId, Node]):IO[Int] = {
    debug(s"$result from ${nodeIdsToNodes(result.adapterNodeId).key.underlying} is overdue")
    val problem = QueryAttemptTimeToLiveExceeded(result.queryId,nodeIdsToNodes(result.adapterNodeId),resultsOverdueAfter)
    val overdueResult = result.toError(
      problem = problem,
      status = ResultStatus.ErrorFromCrc,
      statusMessage = Option(problem.summary),
      crcQueryInstanceId = result.crcQueryInstanceId,
      adapterTime = DateStamp.now,
      resultMetadata = result.resultMetadata
    )
    val overdueUpdate = UpdateResultWithError(overdueResult)

    debug(s"Will update $overdueUpdate")
    HubReceiver.updateResultIO(overdueUpdate).flatMap(_ => IO(1))
  }

}

case class QueryAttemptTimeToLiveExceeded(queryId: QueryId, node:Node, timeToLive:FiniteDuration) extends AbstractProblem(ProblemSources.Hub) {
  override def logLevel: Level = Level.DEBUG
  override val throwable: Option[Throwable] = None
  override val summary: String = s"Shrine's hub has given up on getting a result from ${node.name.underlying} after $timeToLive"
  override val description: String = s"Shrine's hub has given up on getting a result from ${node.name.underlying} for $queryId after $timeToLive"
  override def detailsText: None.type = None
}

package net.shrine.receiver

import cats.effect.IO
import cats.effect.std.{AtomicCell, Dispatcher}
import ch.qos.logback.classic.Level
import net.shrine.adapter.RunQueryInterrogator
import net.shrine.adapter.dao.AdapterQueryHistoryDb
import net.shrine.config.ConfigSource
import net.shrine.hub.data.client.HubClient
import net.shrine.hub.mom.ShrineMomClient
import net.shrine.log.Loggable
import net.shrine.problem.{AbstractProblem, ProblemSources}
import net.shrine.protocol.version.v2.{ResultMetadata, ResultStatus, RunQueryForResult, UpdateQueryAtHub, UpdateQueryAtHubWithNameAndNotes, UpdateQueryAtQep, UpdateResult, UpdateResultWithError}
import net.shrine.protocol.version.{Envelope, JsonText, QueryId, ShrineVersion}
import net.shrine.qep.querydb.QepQueryDb

/**
 * Receives messages no matter what, and passes them to the Dispatcher to process
 */

object Receiver extends Loggable {

  private type Cancel = ShrineMomClient.Cancel
  private val atomicCancelToken: IO[AtomicCell[IO, Option[Cancel]]] = AtomicCell[IO].of(None)

  def startIO(dispatcher: Dispatcher[IO]):IO[Unit] = {
    atomicCancelToken.flatMap(
      _.set(Option(
        ShrineMomClient.receiveUntilStop(HubClient.getLocalNodeIO.map(_.momQueueName),dispatchIO,dispatcher)
      ))
    ) *> IO(info(s"${this.getClass.getSimpleName} started"))
  }

  def stopIO():IO[Unit] = {
    atomicCancelToken.flatMap(
      _.getAndSet(None).flatMap((oc:Option[Cancel]) =>
        oc.map(c=>IO.fromFuture(IO(c()))).getOrElse(IO.unit))) *> IO(info(s"${this.getClass.getSimpleName} stopped"))
  }

  private val runQueryInterrogator: Option[RunQueryInterrogator] = {
    if (ConfigSource.config.getBoolean("shrine.adapter.create")) Some(RunQueryInterrogator(ConfigSource.config))
    else None
  }

  //true to complete, false to not complete
  private def dispatchIO(envelope: Envelope): IO[Boolean] = {

    debug(s"envelope holds a ${envelope.contentsType}")

    envelope.contentsType match {
      //Adapter commands
      case contentsType:String if contentsType == RunQueryForResult.envelopeType =>
        val runQueryForResult: RunQueryForResult = RunQueryForResult.tryRead(new JsonText(envelope.contents)).get
        info(s"Received command to run query ${runQueryForResult.query.id}")
        //if there's an adapter run the query. Otherwise complain.
        runQueryInterrogator.fold {
          val queryId = runQueryForResult.query.id
          val errorUpdate = UpdateResultWithError(runQueryForResult.resultProgress.toError(HasNoAdapter(runQueryForResult.query.id),ResultStatus.ErrorInShrine,
            resultMetadata = runQueryForResult.resultProgress.resultMetadata))
          ShrineMomClient.sendToHubIO(queryId,errorUpdate,UpdateResult,s"$errorUpdate to hub").
            handleErrorWith(t => IO(error(s"Exception while sending misconfigured adapter error update $errorUpdate", t))).
            flatMap(_ => IO(true))
        } { rqa => //should we ever separate the adapter from the qep - can use backpressure from the CRC all the way to here
          rqa.startRunQueryForExpectedResult(runQueryForResult).
            flatMap(_ => IO(true))
        }
      case contentsType:String if contentsType == UpdateQueryAtHub.envelopeType => //this will soon be down to just renaming, gone with the adapter's database
        val updateQuery: UpdateQueryAtHub = UpdateQueryAtHub.tryRead(new JsonText(envelope.contents)).get
        info(s"Received command to update query $updateQuery at the hub")
        val updateSomething: IO[Unit] = updateQuery match {
          case updateQueryName: UpdateQueryAtHubWithNameAndNotes => renameQuery(updateQueryName)
          case _ => throw new IllegalStateException(s"Unexpected UpdateQueryAtHub command type ${updateQuery.getClass.getSimpleName} $updateQuery")
        }
        updateSomething.flatMap(_ => IO(true))
      //QEP commands
      case contentsType:String if contentsType == UpdateQueryAtQep.envelopeType =>
        val updateQueryAtQep = UpdateQueryAtQep.tryRead(new JsonText(envelope.contents)).get
          QepQueryDb.db.updateQepQueryIO(updateQueryAtQep).start. //do in parallel
            flatMap(_ => IO(true))
      case contentsType:String if contentsType == UpdateResult.envelopeType =>
        val updateResult = UpdateResult.tryRead(new JsonText(envelope.contents)).get
        QepQueryDb.db.insertQueryResultIO(updateResult.result).start. //do in parallel
          flatMap(_ => IO(true))
      //something unexpected
      case contentsType:String =>
        IO(error(
          s"""Message of type $contentsType from ${envelope.briefString} not understood by ${ShrineVersion.current}'s downstream nodes. 
             |${envelope.asJsonText.underlying}""".stripMargin
        )).flatMap(_ => IO(true))
    }
  }

  private def renameQuery(updateQueryName: UpdateQueryAtHubWithNameAndNotes): IO[Unit] = runQueryInterrogator.map { _ =>
     AdapterQueryHistoryDb.db.renameQueryIO(
      updateQueryName.queryId.underlying,
      updateQueryName.queryName)
  }.getOrElse(IO.unit)

}

//noinspection ScalaFileName
case class HasNoAdapter(queryId:QueryId) extends AbstractProblem(ProblemSources.Adapter) {
  override def logLevel: Level = Level.WARN
  override val summary: String = "This SHRINE node is not configured to have an adapter."
  override val description = s"Received command to run query $queryId but this node has no shrine adapter."
}
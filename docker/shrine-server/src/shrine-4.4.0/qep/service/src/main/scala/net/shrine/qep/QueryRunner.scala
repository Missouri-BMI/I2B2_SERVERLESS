package net.shrine.qep

import cats.effect.{FiberIO, IO}
import ch.qos.logback.classic.Level
import net.shrine.authentication.pm.User
import net.shrine.config.ConfigSource
import net.shrine.hub.data.client.HubClient
import net.shrine.hub.mom.ShrineMomClient
import net.shrine.log.Loggable
import net.shrine.protocol.version.v2.{QueryProgress, QueryStatus, Researcher, RunQueryAtHub, UpdateQueryAtQepWithError, UpdateQueryAtQepWithStatus}
import net.shrine.qep.audit.{QepAuditDb, QepQueryAuditData}
import net.shrine.qep.querydb.QepQueryDb
import net.shrine.config.ConfigExtensions
import net.shrine.messagequeueservice.CouldNotCompleteMomTaskButOKToRetryException
import net.shrine.problem.{AbstractProblem, ProblemNotYetEncoded, ProblemSources}

object QueryRunner extends Loggable {

  private val collectQepAudit: Boolean = ConfigSource.config.getBoolean("shrine.qep.collectQepAudit")
  val projectId: String = ConfigSource.config.getOption("shrine.hiveCredentials.ontProjectId", _.getString).getOrElse(ConfigSource.config.getString("shrine.i2b2ShrineProjectName"))

  def runQuery(user:User,query: QueryProgress): IO[RunQueryResponse] = {
     for {
      localNode <- HubClient.getLocalNodeIO
      //todo move creating researcher to before creating the query
      //ideally the QEP would try to select the researcher before creating a new one. See SHRINE2020-1104
      // The QEP does not have access to a collection of researchers. When it does this code can be replaced.
      researcher <- IO{
        Researcher.createWithRepeatableId(
          userName = user.username,
          userDomainName = user.domain,
          nodeId = localNode.id
        )}
      qepQueryAuditData <- IO(QepQueryAuditData(
        shrineNodeId = localNode.key.underlying,
        userName = user.username,
        queryId = query.id.underlying,
        queryName = query.queryName
      ))
      _ <- QepQueryDb.db.insertQueryIO(query, researcher)
      _ <- if (collectQepAudit) QepAuditDb.db.insertQepQueryIO(qepQueryAuditData) else IO.unit //todo update to convert form a v2.Query internally
      _ <- sendToHub(query,researcher)
    } yield RunQueryResponse(query.id.underlying.toString, "RECEIVED_BY_QEP") //todo Marc-Danie would this be better with either SentToHub or QepError ?
  }

  private def sendToHub(queryWithId:QueryProgress,researcher: Researcher): IO[FiberIO[Unit]] = {
    val sendAndUpdateDb = for {
      queryToHub <- IO(queryWithId.withStatus(QueryStatus.SentToHub))
      _ <- ShrineMomClient.sendToHubIO(
        subjectId = queryToHub.id,
        envelopeContents = RunQueryAtHub(queryToHub,researcher),
        envelopeContentsCompanion = RunQueryAtHub,
        logString = s"query command ${queryWithId.id}"
      )
      _ <- QepQueryDb.db.updateQepQueryIO(UpdateQueryAtQepWithStatus(queryToHub))
    } yield ()

    sendAndUpdateDb.handleErrorWith{ x: Throwable =>
      error(s"Error caused by ${x.getClass}",x)
      QepQueryDb.db.updateQepQueryIO( x match {
        case rx:CouldNotCompleteMomTaskButOKToRetryException => UpdateQueryAtQepWithError(queryWithId,CouldNotSendQueryToHub(rx))
        case _ => UpdateQueryAtQepWithError(queryWithId,ProblemNotYetEncoded("The QEP encountered an unforeseen problem while sending a start query request to the hub",x))
      } )
    }.start
  }
}

case class CouldNotSendQueryToHub(rx:CouldNotCompleteMomTaskButOKToRetryException) extends AbstractProblem(ProblemSources.Qep){
  override def logLevel: Level = Level.WARN

  override val summary = s"${rx.getLocalizedMessage}."

  override val throwable: Option[CouldNotCompleteMomTaskButOKToRetryException] = Some(rx)

  override val description = s"Could not send the command to start a query to the hub due to ${rx.getLocalizedMessage}"
}

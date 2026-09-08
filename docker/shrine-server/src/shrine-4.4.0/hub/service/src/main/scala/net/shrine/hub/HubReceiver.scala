package net.shrine.hub

import cats.effect.IO
import cats.effect.std.{AtomicCell, Dispatcher}
import ch.qos.logback.classic.Level
import net.shrine.hub.data.store.{HubDb, ItemVersionRaceLostException, QueryRow}
import net.shrine.hub.mom.ShrineMomClient
import net.shrine.log.Loggable
import net.shrine.problem.{AbstractProblem, ProblemSources}
import net.shrine.protocol.version.v2.{Node, Query, QueryProgress, QueryStatus, Researcher, Result, ResultMetadata, ResultProgress, ResultStatus, RunQueryAtHub, RunQueryForResult, UpdateCrcQueuedResult, UpdateNodeSystemSpec, UpdateQueryAtHub, UpdateQueryAtQep, UpdateQueryAtQepWithError, UpdateQueryAtQepWithStatus, UpdateQueryReadyForAdapters, UpdateResult}
import net.shrine.protocol.version.{DateStamp, Envelope, JsonText, NodeId, QueryId, ShrineVersion}

import scala.collection.immutable.Iterable
import scala.util.control.NonFatal

object HubReceiver extends Loggable {

  type Cancel = ShrineMomClient.Cancel
  private val atomicCancelToken: IO[AtomicCell[IO, Option[Cancel]]] = AtomicCell[IO].of(None)

  def startIO(dispatcher: Dispatcher[IO]):IO[Unit] = {
    atomicCancelToken.flatMap(
      _.set(Option(
        ShrineMomClient.receiveUntilStop(HubDb.db.selectTheNetworkIO.map(_.hubQueueName),dispatchIO,dispatcher)
      ))
    ) *> IO(info(s"${this.getClass.getSimpleName} started"))
  }

  def stopIO():IO[Unit] = {
    atomicCancelToken.flatMap(
      _.getAndSet(None).flatMap((oc:Option[Cancel]) =>
        oc.map(c=>IO.fromFuture(IO(c()))).getOrElse(IO.unit))) *>
      IO(info(s"${this.getClass.getSimpleName} stopped"))
  }

  private def dispatchIO(envelope: Envelope):IO[Boolean] = {
    envelope.contentsType match {
      case t: String if t == RunQueryAtHub.envelopeType =>
        val command = RunQueryAtHub.tryRead(new JsonText(envelope.contents)).get
        sendQueryToAdaptersIO(command)
      case t: String if t == UpdateResult.envelopeType =>
        val updateResult = UpdateResult.tryRead(new JsonText(envelope.contents)).get
        updateResultIO(updateResult).flatMap(_ => IO(true))
      case t: String if t == UpdateCrcQueuedResult.envelopeType =>
        val result = UpdateCrcQueuedResult.tryRead(new JsonText(envelope.contents)).get
        updateCrcQueuedResultIO(result).flatMap(_ => IO(true))
      case t: String if t == UpdateQueryAtHub.envelopeType =>
        val queryUpdate = UpdateQueryAtHub.tryRead(new JsonText(envelope.contents)).get
        updateQueryAtHubIO(queryUpdate).flatMap(_ => IO(true))
      case t: String if t == UpdateNodeSystemSpec.envelopeType =>
        val nodeSystemSpec = UpdateNodeSystemSpec.tryRead(new JsonText(envelope.contents)).get
        HubDb.db.updateNodeSystemSpecIO(nodeSystemSpec).flatMap(_ => IO(true))
      case t: String =>
        IO(error(
          s"""Message of type $t from ${envelope.briefString} not understood by ${ShrineVersion.current}'s hub.
             |${envelope.asJsonText.underlying}""".stripMargin
        )).flatMap(_ => IO(true))
    }
  }

  //todo near identical method SHRINE2020-813
  private def updateRawResultIO(result:Result):IO[Unit] = {

    for{
      _ <- IO(info(s"Start updating qep about result $result"))
      _ <- HubDb.db.upsertResultIO(result)
      maybeQuery <- HubDb.db.selectQueryIO(result.queryId)
      _ <- maybeQuery.map{ query =>
        ShrineMomClient.sendToNodeIO(
                                      query.id,
                                      envelopeContents = UpdateResult.createUpdateResult(result),
                                      envelopeContentsCompanion = UpdateResult,
                                      logString = s"result update from ${result.adapterNodeName} for query ${result.queryId} ${result.status}",
                                      nodeId = query.nodeOfOriginId
                                    )
      }.getOrElse{
        error(s"Query ${result.queryId} not found for $result")
        IO.unit
      }
    } yield ()
  }

  //todo near identical method SHRINE2020-813
  //todo delete with SHRINE2020-1365
  private def updateCrcQueuedResultIO(updateResult:UpdateCrcQueuedResult):IO[Unit] = {
    HubDb.db.updateCrcQueuedResultIO(updateResult).
      flatMap { maybeResult =>
        maybeResult.map { result =>
        HubDb.db.selectQueryIO(result.queryId).map{maybeQ:Option[Query] => maybeQ.flatMap((q: Query) => Some((q, result)))}
      }.getOrElse{
          debug(s"Query not found for $updateResult. This is OK for queries QUEUED queries before Shrine 1.26.")
          IO(None)
        }}.
      flatMap { maybeQueryAndResult =>
        maybeQueryAndResult.map{ queryAndResult =>
          val query = queryAndResult._1
          val result = queryAndResult._2
          ShrineMomClient.sendToNodeIO(
            subjectId = result.queryId,
            envelopeContents = UpdateResult.createUpdateResult(result),
            envelopeContentsCompanion = UpdateResult,
            logString = s"result update from ${result.adapterNodeName} for query ${result.queryId} ${result.status}",
            nodeId = query.nodeOfOriginId
          )
        }.getOrElse(IO.unit)
      }
  }
  def updateResultIO(updateResult: UpdateResult): IO[Unit] = {
    HubDb.db.updateResultIO(updateResult).
      flatMap { maybeResult =>
        maybeResult.map { result =>
          HubDb.db.selectQueryIO(result.queryId).map { maybeQ: Option[Query] => maybeQ.flatMap((q: Query) => Option((q, result))) }
        }.getOrElse {
          error(s"Query not found for $updateResult")
          IO(None)
        }
      }.
      flatMap { maybeQueryAndResult =>
        maybeQueryAndResult.map { queryAndResult =>
          val query = queryAndResult._1
          val result = queryAndResult._2
          ShrineMomClient.sendToNodeIO(
            subjectId = result.queryId,
            envelopeContents = UpdateResult.createUpdateResult(result),
            envelopeContentsCompanion = UpdateResult,
            logString = s"result update from ${result.adapterNodeName} for query ${result.queryId} ${result.status}",
            nodeId = query.nodeOfOriginId
          )
        }.getOrElse(IO.unit)
      }
  }

  private def updateQueryAtHubIO(updateQuery:UpdateQueryAtHub):IO[Unit] = {
    HubDb.db.upsertQueryForUpdateIO(updateQuery).flatMap(_ => IO.unit)
  }

  private def sendQueryToAdaptersIO(runQueryAtHub: RunQueryAtHub): IO[Boolean] = {

    val queryReceived:QueryProgress = runQueryAtHub.query.withStatus(QueryStatus.ReceivedAtHub)
    val queryReceivedStateChange = UpdateQueryAtQepWithStatus(queryReceived)

    val queryReadyIO: IO[(Query, Seq[(Node,ResultProgress)])] =
      insertQueryAndResearcher(queryReceived,runQueryAtHub.researcher).
        flatMap{ _ =>
        ShrineMomClient.sendToNodeIO(queryReceivedStateChange.queryId,queryReceivedStateChange,UpdateQueryAtQep,s"${queryReceivedStateChange.toString.take(70)} ",queryReceived.nodeOfOriginId)
      }.
      flatMap{ _ => chooseAdapterNodes}.
      flatMap{ adapterNodes => updateToReadyForAdapters(queryReceived,adapterNodes)}

    val sendToAdaptersIO = queryReadyIO.flatMap { queryReadyAndAdapterNodes =>
      val queryReady = queryReadyAndAdapterNodes._1
      val adapterNodesToResult = queryReadyAndAdapterNodes._2

      for {
        _ <- IO(info(s"Will send query ${runQueryAtHub.query.id} to ${adapterNodesToResult.map(_._1.key.underlying).mkString(", ")}"))
        adaptersUsed <- sendToEveryAdapter(queryReady,adapterNodesToResult,runQueryAtHub)
        _ <- IO(info(s"Sent query ${runQueryAtHub.query.id} to ${adaptersUsed.map(_.key.underlying).mkString(", ")}"))
        queryToAdapters <- IO(queryReady.withStatus(QueryStatus.SentToAdapters))
        _ <- updateQueryState(queryToAdapters)
      } yield true
    }
    //todo worth examining what goes wrong here. If the whole works went off the rails then the query can error. Otherwise it is likely ok to let the results tell the story
    sendToAdaptersIO.handleErrorWith{
      case qaipx:QueryAlreadyInProcessException =>
        debug(s"Query ${qaipx.ivrlx.item.id} is already in progress as a response to a previous message. Further processing bypassed.",qaipx)
        IO(true) //OK to ack. Most likely the Hub is running slow
      case x:NoAdaptersRunQueriesException =>
        for {
          nodes <- HubDb.db.selectLatestNodesIO
          problem = NoAdaptersRunQueriesProblem(x, queryReceived.id, nodes.filter(_.isSuccess).map(_.get).toList)
          updateQueryWithError = UpdateQueryAtQepWithError(runQueryAtHub.query,problem)
          _ <- updateQuery(updateQueryWithError,runQueryAtHub.query.nodeOfOriginId)
        } yield true //OK to ack. No need to look at this message again
      case NonFatal(x) =>  //if anything has gone wrong up to this point, the whole query should be an Error. No results are ever coming
        error(s"Exception while running query ${runQueryAtHub.query.id}", x)
        val problem = HubDispatchOfQueryProblem(x,queryReceived.id)
        val updateQueryWithError = UpdateQueryAtQepWithError(runQueryAtHub.query,problem)
        updateQuery(updateQueryWithError,runQueryAtHub.query.nodeOfOriginId).flatMap(_ => IO(true)) //OK to ack. Hub should not see it again anyway
    }
  }

  private def chooseAdapterNodes: IO[Seq[Node]] = {
    HubDb.db.selectLatestNodesIO.map(_.map(_.get)).
      flatMap{nodes =>
        val adapterNodes = nodes.filter(_.sendQueries)
        if(adapterNodes.isEmpty) IO.raiseError(NoAdaptersRunQueriesException(nodes))
        else IO(adapterNodes.toSeq)
      }
  }

  private def sendToEveryAdapter(queryReady:Query, nodesToResultsWithIds: Seq[(Node, ResultProgress)], runQueryAtHub: RunQueryAtHub):IO[List[Node]] = {

    val sendToEachAdapter: List[IO[Node]] = nodesToResultsWithIds.map { adapterNodeAndResultProgress =>

      val adapterNode = adapterNodeAndResultProgress._1
      val resultWithId = adapterNodeAndResultProgress._2
      val runQueryForResult = RunQueryForResult(
        query = queryReady,
        researcher = runQueryAtHub.researcher,
        node = adapterNode,
        resultProgress = resultWithId.withStatus(ResultStatus.SentToAdapter, None, None, DateStamp.now, resultMetadata = resultWithId.resultMetadata)
      )

      val sendAndUpdateIO = for {
        _ <- ShrineMomClient.sendToNodeIO(
                                            subjectId = runQueryForResult.query.id,
                                            envelopeContents = runQueryForResult,
                                            envelopeContentsCompanion = RunQueryForResult,
                                            logString = s"run query command for ${queryReady.id} to ${adapterNode.momQueueName}",
                                            queueName = adapterNode.momQueueName
                                          )
        _ <- IO(info("Completed sendToNodeIO adapterNode"))
        _ <- updateRawResultIO(runQueryForResult.resultProgress) //todo use an UpdateResult instead SHRINE2020-1362, then remove updateRawResultIO
      } yield adapterNode

      sendAndUpdateIO.handleErrorWith{
        case NonFatal(x) =>
          for{
            _ <- updateRawResultIO(resultWithId.toErrorBeforeCrc( //todo use an UpdateResult instead SHRINE2020-1362, then remove updateRawResultIO
              problem = HubDispatchOfQueryProblem(x,resultWithId.queryId,Some(adapterNode)),
              resultMetadata = resultWithId.resultMetadata,
              adapterTime = DateStamp.now
              )
            )
          } yield adapterNode
      }
    }.toList

    import cats.implicits._
    sendToEachAdapter.parSequence
  }

  private def insertQueryAndResearcher[Q <: Query](query:Q, researcher:Researcher): IO[Unit] = {
    HubDb.db.upsertQueryUpdateAndResearcher(query,researcher).
      flatMap{ maybeQuery: Option[Query] =>
        //if no queries are inserted then the query already exists in the database and is already in process
        if(maybeQuery.isEmpty) throw QueryAlreadyInProcessException(query.id)

        IO.unit
      }.handleErrorWith{
        //If this query has already been seen at the hub, be done and complete the message
        case ivrlx:ItemVersionRaceLostException[_,_,_]
          if ivrlx.item.isInstanceOf[Query] => //"if" clause to deal with erasure
          throw QueryAlreadyInProcessException(ivrlx.asInstanceOf[ItemVersionRaceLostException[QueryId,Query,QueryRow]])
    }
  }

  private def updateToReadyForAdapters(queryReceived: Query, adapterNodes:Seq[Node]): IO[(Query, Seq[(Node, ResultProgress)])] = {
    //todo check previous state
    val queryReady = queryReceived.withStatus(QueryStatus.ReadyForAdapters)
    val resultProgresses = adapterNodes.map{adapterNode =>
      Result.create(queryReady, adapterNode, resultMetadata = ResultMetadata(obfuscatingParameters = None))
    }
    val update = UpdateQueryReadyForAdapters(queryReady,resultProgresses)

    HubDb.db.upsertQueryReadyForAdaptersIO(update).flatMap { _ =>
      ShrineMomClient.sendToNodeIO(
        update.queryId,
        update,
        UpdateQueryAtQep,
        s"$update",
        queryReady.nodeOfOriginId)
    }.flatMap{_ => IO((queryReady,adapterNodes.zip(update.resultProgresses)))}
  }

  private def updateQueryState(query:Query): IO[Unit] = {
    val queryStateChanged = UpdateQueryAtQepWithStatus(query)
    updateQuery(queryStateChanged,query.nodeOfOriginId)
  }

  def updateQuery(updateQuery: UpdateQueryAtQep, nodeId:NodeId): IO[Unit] = {
    HubDb.db.upsertQueryForUpdateIO(updateQuery).
    flatMap{ _ =>
      ShrineMomClient.sendToNodeIO(
        updateQuery.queryId,
        updateQuery,
        UpdateQueryAtQep,
        s"$updateQuery",
        nodeId
      )
    }
  }

  private case class NoAdaptersRunQueriesException(nodes:Iterable[Node])
    extends Exception(s"No adapters are configured to run queries of ${nodes.map(_.key).mkString(", ")} ")

  case class QueryAlreadyInProcessException(queryId: QueryId,ivrlx:ItemVersionRaceLostException[QueryId,Query,QueryRow])
    extends Exception(s"Query $queryId is already in the database, already in process",ivrlx)

  private object QueryAlreadyInProcessException{
    def apply(queryId: QueryId): QueryAlreadyInProcessException =
      new QueryAlreadyInProcessException(queryId, null)

    def apply(ivrlx: ItemVersionRaceLostException[QueryId, Query, QueryRow]): QueryAlreadyInProcessException =
      new QueryAlreadyInProcessException(ivrlx.item.id, ivrlx)
  }
}

case class HubDispatchOfQueryProblem(x: Throwable, queryId: QueryId, adapter:Option[Node] = None) extends AbstractProblem(ProblemSources.Hub) {
  override def logLevel: Level = Level.WARN

  override val throwable: Option[Throwable] = Some(x)
  override val summary: String = s"Shrine's hub's dispatcher encountered a problem during its last attempt to send a query to ${adapter.fold("the adapters"){node => s"adapter ${node.key}"}}."
  override val description: String =
    s"""HubDispatcher threw an exception while sending query $queryId to the adapters.""".stripMargin
}

case class NoAdaptersRunQueriesProblem(x: Throwable, queryId: QueryId, knownNodes: List[Node])
  extends AbstractProblem(ProblemSources.Hub) {
  override def logLevel: Level = Level.WARN
  override val summary: String = "No adapters are configured to receive queries."
  override val description: String =
    s"""No adapters are configured for Shrine's hub to send query $queryId to.""".stripMargin
  override def detailsText: Option[String] = Some(if (knownNodes.isEmpty) "The hub's collection of adapter nodes is empty."
    else "The following adapter nodes are known but not configured to receive queries:\n" + knownNodes.map(_.key).mkString(", "))
}
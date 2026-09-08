package net.shrine.messagequeuemiddleware

import cats.effect.IO
import ch.qos.logback.classic.Level
import com.typesafe.config.Config
import io.circe.generic.extras.Configuration
import net.shrine.config.ConfigSource
import net.shrine.hub.data.store.HubDb
import net.shrine.log.Loggable
import net.shrine.messagequeuemiddleware.LocalMessageQueueMiddleware.LocalMessage
import net.shrine.messagequeueservice.{DeliveryAttemptId, Message, Queue}
import net.shrine.problem.{AbstractProblem, ProblemSources}
import net.shrine.protocol.version.v2.{ErrorResult, ResultStatus, RunQueryForResult, UpdateResult}
import net.shrine.protocol.version.{Envelope, JsonText, MomQueueName, QueryId}
import org.http4s.dsl.impl.{->, /, :?, OptionalQueryParamDecoderMatcher}
import org.http4s.dsl.io.{Accepted, Created, GET, InternalServerError, NoContent, NotFound, Ok, PUT, Root, UnprocessableEntity, http4sAcceptedSyntax, http4sCreatedSyntax, http4sInternalServerErrorSyntax, http4sNoContentSyntax, http4sNotFoundSyntax, http4sOkSyntax, http4sUnprocessableEntitySyntax}
import org.http4s.headers.`Content-Type`
import org.http4s.{EntityDecoder, HttpRoutes, QueryParamDecoder, Request, Response, Uri}

import scala.concurrent.duration.Duration
import scala.util.{Failure, Success, Try}
import scala.util.control.NonFatal

/**
  * A web API that provides access to the internal MessageQueue library.
  * Allows client to createQueue, deleteQueue, sendMessage, receiveMessage, getQueues, and sendReceipt
  *
  * Created by yifan on 7/24/17.
  */
case class MessageQueueWebApi() extends Loggable {

  val configPath = "shrine.hub.messagequeue.blockingqWebApi"

  private def webApiConfig: Config = ConfigSource.config.getConfig(configPath)

  private def enabled: Boolean = webApiConfig.getBoolean("enabled")

  if (!enabled) {
    debug(s"MessageQueueWebApi is not enabled. This node is not running a message service.")
  }

  val jsonContentHeader: `Content-Type` = `Content-Type`(org.http4s.MediaType.application.json)

  val service: HttpRoutes[IO] = HttpRoutes.of[IO] {
    implicit val timeOutSecondsQueryParamDecoder: QueryParamDecoder[Duration] =
      QueryParamDecoder[Int].map(i => Duration.create(i,"seconds"))
    object OptionalTimeOutSecondsQueryParamMatcher extends OptionalQueryParamDecoderMatcher[Duration]("timeOutSeconds")

    if (enabled) {
      case _@GET -> Root / "ping" => Ok("pong")
        
      case blockingTestReq@GET -> Root / "parallel" :? OptionalTimeOutSecondsQueryParamMatcher(optionalTimeout)=>
        val timeout:Duration = optionalTimeout.getOrElse(Duration.create(20,"seconds"))
        val startTime = System.currentTimeMillis()
        debug(s"blocking sleep started for $timeout from ${blockingTestReq.uri} ${blockingTestReq.params}")
        scala.concurrent.blocking{Thread.sleep(timeout.toMillis)}
        val endTime = System.currentTimeMillis()
        val reply = s"${endTime - startTime} to complete the blocking sleep"
        debug(reply)
        Ok(reply)

      case createReq@PUT -> Root / "createQueue" / queueName =>
        LocalMessageQueueMiddleware.createQueueIfAbsentIO(MomQueueName(queueName)).flatMap{ localQueue: LocalMessageQueueMiddleware.LocalQueue =>
          val queue = Queue(localQueue.name.underlying)
          val jsonText: String = queue.asJsonString
          Created(jsonText, jsonContentHeader)
        }.handleErrorWith{x => handleGeneralFailure(createReq, x)}

      case getReq@GET -> Root / "getQueue" / queueName =>
        LocalMessageQueueMiddleware.getQueueIO(MomQueueName(queueName)).flatMap{ localQueue: LocalMessageQueueMiddleware.LocalQueue =>
          val queue = Queue(localQueue.name.underlying)
          val jsonText: String = queue.asJsonString
          Ok(jsonText, jsonContentHeader)
        }.handleErrorWith {
          case qdnex: QueueDoesNotExistException => NotFound(qdnex.getMessage)
          case t => handleGeneralFailure(getReq, t)
        }

      case deleteReq@PUT -> Root / "deleteQueue" / queueName =>
        LocalMessageQueueMiddleware.deleteQueueIO(MomQueueName(queueName)).flatMap{ _ => Ok() }.
          handleErrorWith{
            case q: QueueDoesNotExistException => UnprocessableEntity(q.getMessage)
            case x => handleGeneralFailure(deleteReq, x)
        }

      case queuesReq@GET -> Root / "getQueues" =>
        LocalMessageQueueMiddleware.queuesIO.flatMap{ qs: Seq[LocalMessageQueueMiddleware.LocalQueue] =>
          import io.circe.generic.auto._
          import io.circe.syntax._
          implicit val genDevConfig: Configuration = Queue.genDevConfig

          val jsonText: String = qs.map{q => Queue(q.name.underlying)}.asJson.noSpaces
          Ok(jsonText, jsonContentHeader)
        }.handleErrorWith{x => handleGeneralFailure(queuesReq, x)}

      case sendReq@PUT -> Root / "sendMessage" / toQueueName =>
        val messageTextIO: IO[String] = EntityDecoder.decodeText(sendReq)

        val sendIo: IO[Unit] = messageTextIO.flatMap { messageText =>
          info(s"sendMessage entityText length is ${messageText.length} starts with ${messageText.take(90)}")
            val envelopeTry: Try[Envelope] = Envelope.tryRead(new JsonText(messageText))
            envelopeTry match {
              case Success(envelope) =>
                MessageTranslationHandler.translateMessage(MomQueueName(toQueueName), envelope).flatMap{ translatedEnvelope =>
                  LocalMessageQueueMiddleware.getQueueIO (translatedEnvelope.toMomQueueName).flatMap (_.sendIO(translatedEnvelope.envelop.asJsonText.underlying))
                }
              case Failure(_) => LocalMessageQueueMiddleware.getQueueIO(MomQueueName(toQueueName)).flatMap(qIO => qIO.sendIO(messageText))
            }
        }
        sendIo.flatMap{ _ => Accepted()}.
          handleErrorWith{
            case qx: QueueDoesNotExistException => UnprocessableEntity(qx.getMessage)
            case x => handleGeneralFailure(sendReq, x)
          }

      case receiveReq@GET -> Root / "receiveMessage" / fromQueueName :? OptionalTimeOutSecondsQueryParamMatcher(optionalTimeout) =>
        val timeout:Duration = optionalTimeout.getOrElse(Duration.create(20,"seconds"))

        val receiveIo: IO[Option[Message]] = LocalMessageQueueMiddleware.getQueueIO(MomQueueName(fromQueueName)).flatMap(_.receiveIO(timeout))
        receiveIo.flatMap{ s => s.fold(NoContent()){ localMessage: Message =>
          val simpleMessage: LocalMessage = localMessage.asInstanceOf[LocalMessage]

          debug(s"receiveIO simpleMessage contents length is ${simpleMessage.contents.length}")

          val jsonText: String = simpleMessage.toJson
          Ok(jsonText, jsonContentHeader)
        }}.
          handleErrorWith{
            case qx: QueueDoesNotExistException => UnprocessableEntity(qx.getMessage)
            case x => handleGeneralFailure(receiveReq, x)
          }

      //todo don't use until after SHRINE-3025
      case receiveReq@GET -> Root / "receiveStream" / fromQueueName  => ???
/*
        try {
          val receiveStream: fs2.Stream[IO, Message] = LocalMessageQueueMiddleware.getQueueIO(fromQueueName).map(_.receiveStream())
          val eventStream = receiveStream.map{ localMessage: Message =>
            val simpleMessage: LocalMessage = localMessage.asInstanceOf[LocalMessage]
            val jsonText: String = simpleMessage.toJson
            ServerSentEvent(jsonText)
          }
          Ok(eventStream, jsonContentHeader)
        } catch {
          case qx: QueueDoesNotExistException => UnprocessableEntity(qx.getMessage)
          case x:Throwable => handleGeneralFailure(receiveReq, x)
        }
*/
      case ackReq@PUT -> Root / "acknowledge" / deliveryAttempt =>
        val deliveryAttemptId: DeliveryAttemptId = new DeliveryAttemptId(deliveryAttempt.toLong)
        val ackIo: IO[Unit] = LocalMessageQueueMiddleware.completeMessage(deliveryAttemptId)
        ackIo.flatMap{ _ => Ok()}.
          handleErrorWith{
            case qx: MessageDoesNotExistAndCannotBeCompletedException => UnprocessableEntity(qx.getMessage)
            case x => handleGeneralFailure(ackReq, x)
          }
      case x => NotFound(s"The messaging service does not respond to ${x.pathInfo}")
    }
    else {
      case _ =>
        val warningMessage: String =
          """If you intend for this node to serve as this SHRINE network's messaging hub
            |set shrine.hub.messagequeue.blockingqWebApi.enabled to true in your shrine.conf.
            |Do not do this unless you are the hub admin.""".stripMargin
        NotFound(warningMessage)
    }
  }

  private def handleGeneralFailure(req: Request[IO], x: Throwable): IO[Response[IO]] = x match {
    case NonFatal(nfx) =>
      val path: Uri.Path = req.pathInfo
      MessageQueueWebApiServerErrorProblem(nfx, path.toString())
      InternalServerError(
        s"""${getClass.getSimpleName} threw an exception while trying to $path.
           |${getClass.getSimpleName} response: ${x.getMessage} Exception: ${nfx.getClass}""".
          stripMargin)
    case _ => throw x
  }
}

case class MessageQueueWebApiServerErrorProblem(x: Throwable, function: String) extends AbstractProblem(ProblemSources.Hub) {
  override def logLevel: Level = Level.ERROR

  override val throwable: Option[Throwable] = Some(x)
  override val summary: String = "SHRINE cannot use MessageQueueWebApi due to a server error occurred in messageQueueMiddleware."
  override val description: String =
    s"""MessageQueueMiddleware throws an exception while trying to $function,
       |the server's response is: ${x.getMessage} from ${x.getClass}.""".stripMargin
}

case class ProtocolTranslationProblem(queryId: QueryId, fromVersion: Int, toVersion: Int)
  extends AbstractProblem(ProblemSources.Hub) {
  override def logLevel: Level = Level.WARN
  override val summary: String = "Could not translate the protocol message."
  override val description: String =
    s"""Could not translate the protocol message from version $fromVersion to version $toVersion for query ${queryId.underlying}.""".stripMargin
}
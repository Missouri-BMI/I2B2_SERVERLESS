package net.shrine.messagequeueclient

import java.util.concurrent.ConcurrentHashMap
import cats.effect.IO
import ch.qos.logback.classic.Level
import com.typesafe.config.Config
import fs2.Stream
import net.shrine.config.{ConfigExtensions, ConfigSource}
import net.shrine.http4s.catsio.LazyIO
import net.shrine.http4s.client.Http4sHttpClient
import net.shrine.log.Loggable
import net.shrine.messagequeueservice._
import net.shrine.problem.{AbstractProblem, ProblemSources}
import net.shrine.protocol.version.MomQueueName
import org.http4s.{EntityDecoder, Request, Status, Uri}

import scala.annotation.unused
import scala.concurrent.duration.Duration
import scala.jdk.CollectionConverters.ConcurrentMapHasAsScala
import scala.language.postfixOps
import scala.util.control.NonFatal

/**
  * A simple MessageQueueWebClient that uses MessageQueueWebApi to createQueue,
  * deleteQueue, sendMessage, receiveMessage, getQueues, and sendReceipt
  *
  * @author yifan
  * @since 8/10/17
  */
object MessageQueueWebClient extends MessageQueueService with Loggable {

  info("Using MessageQueueWebClient")
  val configPath = "shrine.hub.messagequeue.blockingq"

  val webClientConfig: Config = ConfigSource.config.getConfig(configPath)

  private val receiveWaitTime: Duration = webClientConfig.get("receiveWaitTime", Duration(_))

  val momBaseUri: Uri = ConfigSource.config.buildURI("shrine.shrineHubBaseUrl", "shrine.hub.messagequeue.blockingq.serverUrlPath", Uri.unsafeFromString)

  private val http4sHttpClient = Http4sHttpClient(ConfigSource.config.getConfig("shrine.hub.messagequeue.httpClient"))

  private def filterErrorStatus(uri:Uri, status: Status, bodyString:String): Status = {
    if (status.isSuccess) status
    else {
      throw CouldNotCompleteMomTaskButOKToRetryException(uri, Some(status), Some(bodyString))
      /*
      Survivable status codes discovered during 2.0.0 development, stored for posterity

      //status codes we've observed but which aren't in http4s
      status.code match {
        case 598 => throw CouldNotCompleteMomTaskButOKToRetryException(uri, Some(status), Some(bodyString)) //NetworkReadTimeout
        case 599 => throw CouldNotCompleteMomTaskButOKToRetryException(uri, Some(status), Some(bodyString)) //NetworkConnectTimeout
        case _ => ; //pass through to better-known error codes
      }

      status.responseClass match {
        case x if x == Status.RequestTimeout.responseClass => throw CouldNotCompleteMomTaskButOKToRetryException(uri, Some(status), Some(bodyString))
        case x if x == Status.NotFound.responseClass => throw CouldNotCompleteMomTaskButOKToRetryException(uri, Some(status), Some(bodyString))
        case x if x == Status.InternalServerError.responseClass => throw CouldNotCompleteMomTaskButOKToRetryException(uri, Some(status), Some(bodyString))
        case x if x == Status.ServiceUnavailable.responseClass => throw CouldNotCompleteMomTaskButOKToRetryException(uri, Some(status), Some(bodyString))
        case _ => throw CouldNotCompleteMomTaskDoNotRetryException(uri, Some(status), Some(bodyString))
      }
    */
    }
  }

  private def momFetchAndDecodeIO[A](request:Request[IO])(toA: (Status,String) => IO[A]): IO[A] = {
    info(s"start momFetchAndDecodeIO")
    http4sHttpClient.webFetchAndDecodeIO(request)(toA).handleErrorWith {
      case x:CouldNotCompleteMomTaskButOKToRetryException => throw x
      case NonFatal(x) => throw CouldNotCompleteMomTaskButOKToRetryException(request.uri, None, None, Some(x))
      case x => throw x
/*
Exceptions discovered during 2.0.0 development, stored for posterity

      case tx:TimeoutException => throw CouldNotCompleteMomTaskButOKToRetryException(request.uri, None, None, Some(tx))
      case cx: ConnectException => throw CouldNotCompleteMomTaskButOKToRetryException(request.uri, None, None, Some(cx))
      case ex: EncoderException => throw CouldNotCompleteMomTaskButOKToRetryException(request.uri, None, None, Some(ex))
      case ccx: ClosedChannelException => throw CouldNotCompleteMomTaskButOKToRetryException(request.uri, None, None, Some(ccx))
      case ux:UnknownHostException if(ux.getMessage.contains("Temporary")) => //Found at UCLA with the message "Temporary failure in name resolution"
                                        throw CouldNotCompleteMomTaskButOKToRetryException(request.uri, None, None, Some(ux))
      case ok: CouldNotCompleteMomTaskButOKToRetryException => throw ok
      case notOk: CouldNotCompleteMomTaskDoNotRetryException => throw notOk
      case jt:java.util.concurrent.TimeoutException => throw CouldNotCompleteMomTaskButOKToRetryException(request.uri, None, None, Some(jt))
      case NonFatal(x) => throw CouldNotCompleteMomTaskDoNotRetryException(request.uri,None,None,Some(x))

 */
    }
  }

  @unused
  def ping: IO[String] = {
    val request: Request[IO] = ShrineMessageRequests.pingRequest(momBaseUri)
    momFetchAndDecodeIO[String](request){ (status: Status, bodyString:String) =>
      filterErrorStatus(request.uri,status,bodyString)
      if(status.responseClass == Status.Ok.responseClass) {
        IO(bodyString)
      }
      else {
        error(
          s"""Try to $request , HTTPResponse is a success but it does not contain an expected StatusCode
             |Expected StatusCodes: Ok, Actual StatusCode: $status
             |Body: $bodyString""".stripMargin)
        throw CouldNotCompleteMomTaskButOKToRetryException(request.uri, Some(status), Some(bodyString))
      }
    }
  }

  override def createQueueIfAbsentIO(queueName: MomQueueName): IO[ClientQueue] = {
    val request: Request[IO] = ShrineMessageRequests.createQueueRequest(momBaseUri,queueName)
    momFetchAndDecodeIO[ClientQueue](request){ (status: Status, bodyString:String) =>
      filterErrorStatus(request.uri,status,bodyString)
      if(status.responseClass == Status.Created.responseClass) {
        IO(
          try {
            ClientQueue(Queue.fromJson(bodyString).name)
          } catch {
            case NonFatal(x) =>
              CouldNotInterpretHTTPResponseProblem(x, "create a Queue", queueName, bodyString)
              throw CouldNotCompleteMomTaskButOKToRetryException(request.uri, Some(status), Some(bodyString),Some(x))
          }
        ).map{ queue =>
          info(s"$queue created.")
          queue
        }
      }
      else {
        error(
          s"""Try to create a queue, HTTPResponse is a success but it does not contain an expected StatusCode
             |Expected StatusCodes: Ok, Actual StatusCode: $status
             |Body: $bodyString""".stripMargin)
        throw CouldNotCompleteMomTaskButOKToRetryException(request.uri, Some(status), Some(bodyString))
      }
    }
  }

  //already unrestricted
  override def addReceiverPermissionToQueueIO(queueName: MomQueueName, receiverId: String): IO[Unit] = IO.unit

  //already unrestricted
  override def addSenderPermissionToQueueIO(queueName: MomQueueName, senderId: String): IO[Unit] = IO.unit

  //already unrestricted
  override def removePermissionFromQueueIO(queueName: MomQueueName, receiverId: String): IO[Unit] = IO.unit

  private val queueNamesToQueues: scala.collection.mutable.Map[MomQueueName, IO[ClientQueue]] =
    new ConcurrentHashMap[MomQueueName,IO[ClientQueue]]().asScala

  override def getQueueIO(queueName:MomQueueName):IO[ClientQueue] = {
    queueNamesToQueues.getOrElseUpdate(queueName,{
      LazyIO(s"getQueueIO($queueName)")(networkGetQueueIO(queueName))
    })
  }

  private def networkGetQueueIO(queueName: MomQueueName): IO[ClientQueue] = {
    val request: Request[IO] = ShrineMessageRequests.getQueueRequest(momBaseUri,queueName)
    info(s"Requesting $queueName from $momBaseUri")
    momFetchAndDecodeIO[ClientQueue](request){ (status: Status, bodyString:String) =>
      filterErrorStatus(request.uri,status,bodyString)
      info(s"got $bodyString")
      if(status.responseClass == Status.Ok.responseClass) {
        IO(
          try {
            ClientQueue(Queue.fromJson(bodyString).name)
          } catch {
            case NonFatal(x) =>
              CouldNotInterpretHTTPResponseProblem(x, "get a Queue", queueName, bodyString)
              throw CouldNotCompleteMomTaskButOKToRetryException(request.uri, Some(status), Some(bodyString),Some(x))
          }
        )
      }
      else {
        error(
          s"""Try to get a queue, HTTPResponse is a success but it does not contain an expected StatusCode
             |Expected StatusCodes: Ok, Actual StatusCode: $status
             |Body: $bodyString""".stripMargin)
        throw CouldNotCompleteMomTaskButOKToRetryException(request.uri, Some(status), Some(bodyString))
      }
    }
  }

  private val unit: Unit = ()
  override def deleteQueueIO(queueName: MomQueueName): IO[Unit] = {
    val request: Request[IO] = ShrineMessageRequests.deleteQueueRequest(momBaseUri,queueName)
    momFetchAndDecodeIO[Unit](request){ (status: Status, bodyString:String) =>
      filterErrorStatus(request.uri,status,bodyString)
      if(status.responseClass == Status.Ok.responseClass) {
        IO(unit)
      }
      else {
        error(
          s"""Try to delete a queue, HTTPResponse is a success but it does not contain an expected StatusCode
             |Expected StatusCodes: Ok, Actual StatusCode: $status
             |Body: $bodyString""".stripMargin)
        throw CouldNotCompleteMomTaskButOKToRetryException(request.uri, Some(status), Some(bodyString))
      }
    }
  }

  override def queuesIO: IO[Seq[ClientQueue]] = {
    val request: Request[IO] = ShrineMessageRequests.getQueuesRequest(momBaseUri)
    momFetchAndDecodeIO[Seq[ClientQueue]](request){ (status: Status, bodyString:String) =>
      filterErrorStatus(request.uri,status,bodyString)
      if(status.responseClass == Status.Ok.responseClass) {
        IO(
          try {
           Queue.seqFromJson(bodyString).map(q => ClientQueue(q.name))
          } catch {
            case NonFatal(x) =>
              CouldNotInterpretHTTPResponseProblem(x, "getQueues", bodyString)
              throw CouldNotCompleteMomTaskButOKToRetryException(request.uri, Some(status), Some(bodyString), Some(x))
          }
        )
      }
      else {
        error(
          s"""Try to get all queues, HTTPResponse is a success but it does not contain an expected StatusCode
             |Expected StatusCodes: Ok, Actual StatusCode: $status
             |Body: $bodyString""".stripMargin)
        throw CouldNotCompleteMomTaskButOKToRetryException(request.uri, Some(status), Some(bodyString))
      }
    }
  }

  private def messageOptionFromResponse(status: Status, bodyString:String, uri:Uri): Option[MessageAsJson] = {
    if (status.code == Status.NoContent.code) {
      info(s"No message received from $uri, HTTP Response $status $bodyString")
      None
    } else if (status.code == Status.Ok.code) {
      info(s"Non-empty Message received from $uri")
      try {
        Some(MessageAsJson.tryRead(bodyString).get) //todo better error handling
      } catch {
          case NonFatal(x) =>
            CouldNotInterpretHTTPResponseProblem(x, s"create a Message with ${uri.toString()}", bodyString) //todo take a Uri
            throw x
        }
    } else {
      throw CouldNotCompleteMomTaskButOKToRetryException(uri, Some(status), Some(bodyString))
    }
  } //todo this might be able to survive an UnprocessableEntity code for a retry

  //todo would be really nice to merge ClientQueue with the original Queue, but that needs access to momFetchAndDecodeIO
  case class ClientQueue(rawName:String) extends MomQueue with Loggable {

    val name:MomQueueName = MomQueueName(rawName)

    override def sendIO(contents: String,subject: Long): IO[Unit] = {

      val request: Request[IO] = ShrineMessageRequests.sendMessageRequest(momBaseUri,this,contents)

      debug(s"send to $name  ${contents.take(70)}")

      momFetchAndDecodeIO[Unit](request){ (status: Status, bodyString:String) =>
        filterErrorStatus(request.uri,status,bodyString)
        if(status.responseClass == Status.Accepted.responseClass) {
          info(s"Successfully sent Message ${contents.take(70)} to Queue $name")
          IO(unit)
        }
        else {
          error(
            s"""Try to sendMessage $contents, HTTPResponse is a success but it does not contain an expected StatusCode
               |Expected StatusCodes: StatusCodes.Accepted, Actual StatusCode: $status
               |Body: $bodyString""".stripMargin)
          throw CouldNotCompleteMomTaskButOKToRetryException(request.uri, Some(status), Some(bodyString))
        }
      }
    }

    override def receiveIO(timeout: Duration): IO[Option[Message]] = {

      val request = ShrineMessageRequests.receiveMessageRequest(momBaseUri,this,timeout)
      //todo this might need IO.cede, interrupt-able, or cancellable. Hopefully http client already does that for us SHRINE2020-1632
      momFetchAndDecodeIO[Option[Message]](request){ (status: Status, bodyString:String) =>
        filterErrorStatus(request.uri,status,bodyString)
        IO{messageOptionFromResponse(status,bodyString,request.uri).
          map(msg => MessageQueueClientMessage(msg.deliveryAttemptId, msg.millisecondsToComplete, msg.remainingAttempts, msg.contents))}
      }
    }

    override def receiveStream(): Stream[IO, Message] = {
      //todo switch back for SHRINE-3025
      Stream.repeatEval(receiveIO(receiveWaitTime)).flatMap(om => Stream.emits(om.toList))

      /*
      val request = ShrineMessageProtocol.receiveMessageStreamRequest(momBaseUri,from)
      Http4sHttpClient.webApiStream(request){ response: Response[IO] =>
        response.body.through(ServerSentEvent.decoder[IO]).
          map{sse =>
            val jsonMessage = MessageAsJson.tryRead(sse.data).get //todo better error handling
            MessageQueueClientMessage(jsonMessage.deliveryAttemptId,jsonMessage.contents)
          }
      }*/
    }
  }

  private case class MessageQueueClientMessage private(
                                                deliveryAttemptId: DeliveryAttemptId,
                                                millisecondsToComplete:Long,
                                                remainingAttempts:Int,
                                                messageContent: String
                                              ) extends Message {

    override def contents: String = messageContent

    override def completeIO(): IO[Unit] = {

      val request: Request[IO] = ShrineMessageRequests.completeMessageRequest(momBaseUri,deliveryAttemptId)

      momFetchAndDecodeIO[Unit](request) { (status: Status, bodyString:String) =>
        if (status.responseClass == Status.UnprocessableEntity.responseClass) {
          info(s"Try to completeMessage $messageContent, but message does not exist, $contents")
        }
        filterErrorStatus(request.uri, status,bodyString)
          if (status.responseClass == Status.Ok.responseClass) {
            debug(s"Message ${this.deliveryAttemptId} completed with $status")
            IO(unit)
          }
          else {
            error(
              s"""Try to completeMessage $deliveryAttemptId, HTTPResponse is a success but it does not contain the expected StatusCode
                 | Expected StatusCodes: Status.OK, Actual StatusCodes: $status

                 | Response: $bodyString""".
                stripMargin)
          throw CouldNotCompleteMomTaskButOKToRetryException(
            request.uri,
            status = Some(status),
            contents = Some(bodyString)
          )
        }
      }
    }
  }
}

case class CouldNotInterpretHTTPResponseProblem(x: Throwable, task: String, queueName: Option[MomQueueName], httpResponseString: String) extends AbstractProblem(ProblemSources.Hub) {
  override def logLevel: Level = Level.ERROR
  override val throwable: Option[Throwable] = Some(x)
  override val summary: String = s"Unable to $task due to exception"
  override val description: String = s"Unable to $task from queue $queueName due to exception $x, http response: $httpResponseString"
}

object CouldNotInterpretHTTPResponseProblem {
  def apply(x: Throwable, task: String, httpResponseString: String):CouldNotInterpretHTTPResponseProblem = {
    new CouldNotInterpretHTTPResponseProblem(x,task,None,httpResponseString)
  }

  def apply(x: Throwable, task: String, queueName: MomQueueName, httpResponseString: String): CouldNotInterpretHTTPResponseProblem = {
    new CouldNotInterpretHTTPResponseProblem(x,task,Option(queueName),httpResponseString)
  }
}

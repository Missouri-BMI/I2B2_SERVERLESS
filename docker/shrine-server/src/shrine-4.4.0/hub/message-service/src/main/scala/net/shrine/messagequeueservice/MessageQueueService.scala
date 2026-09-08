package net.shrine.messagequeueservice

import cats.effect.IO
import fs2.Stream
import net.shrine.messagequeuemiddleware.QueueDoesNotExistException
import net.shrine.protocol.version.MomQueueName
import org.http4s.{Status, Uri}

import scala.collection.immutable.Seq
import scala.concurrent.duration.Duration

/**
  * This API mostly imitates AWS SQS' API . See http://docs.aws.amazon.com/sdk-for-java/v1/developer-guide/examples-sqs.html
  *
  * @author david
  * @since 7/18/17
  */
trait MessageQueueService {
  /*
SQS returns CreateQueueResult, which contains queueUrl: String
 */
  def createQueueIfAbsentIO(queueName:MomQueueName): IO[MomQueue]

  def addReceiverPermissionToQueueIO(queueName:MomQueueName,receiverId:String):IO[Unit]

  def addSenderPermissionToQueueIO(queueName:MomQueueName,senderId:String):IO[Unit]

  def removePermissionFromQueueIO(queueName:MomQueueName, receiverId:String):IO[Unit]

  /*
SQS has getQueue, which contains queueUrl: String
 */
  def getQueueIO(queueName:MomQueueName):IO[MomQueue]

  /*
  SQS takes in DeleteMessageRequest, which contains a queueUrl: String and a ReceiptHandle: String
  returns a DeleteMessageResult, toString for debugging
   */
  def deleteQueueIO(queueName:MomQueueName): IO[Unit]

  def deleteQueueIfExistsIO(queueName:MomQueueName): IO[Unit] = {
    deleteQueueIO(queueName).handleErrorWith{case _:QueueDoesNotExistException => IO.unit}
  }

  /*
Returns the names of the queues created on this server.
 */
  def queuesIO: IO[Seq[MomQueue]]
}

//todo if we get to drop backwards compatibility I want to rename this to just Queue
trait MomQueue {
  def name:MomQueueName

  /**
   * @param contents - String contents of the message to send
   * @param subject - Long id of the subject of the contents (usually the Envelope's subject to ensure FIFO order in SQS)
   * @return
   */
  def sendIO(contents:String,subject:Long): IO[Unit]

  /*
SQS ReceiveMessageResult receiveMessage(String queueUrl)
   */
  def receiveIO(timeout:Duration): IO[Option[Message]]

  /*
SQS I don't think has a streaming equivalent in their Java API so use something like
    Stream.repeatEval(receive(from,receiveWaitTime)).flatMap(om => Stream.emits(om.toList))
   */
  def receiveStream():Stream[IO,Message]
}

//This class is now part of the MessageQueueWebClient's wire protocol, not the actual MOM queue
//Do not change - will break backwards compatibility with SHRINE 3.2
case class Queue(name:String) {
  def asJsonString:String = {
    import io.circe.generic.extras.Configuration
    import io.circe.generic.extras.auto.exportEncoder
    import io.circe.syntax.EncoderOps

    implicit val genDevConfig: Configuration = Queue.genDevConfig

    this.asJson.noSpaces
  }
}

object Queue {

  import io.circe.generic.extras.Configuration
  val genDevConfig: Configuration =
    Configuration.default.withDiscriminator("encodedClass")

  def apply(rawName:String):Queue = {
    new Queue(MomQueueName.urlQueueName(rawName))
  }

  def fromJson(jsonString:String):Queue = {
    import io.circe.parser.decode
    import io.circe.generic.extras.auto.exportDecoder

    implicit val genDevConfig: Configuration = Queue.genDevConfig

    decode[Queue](jsonString) match {
      case Right(envelope) => envelope
      case Left(x) => throw x
    }
  }

  def seqFromJson(jsonString:String):Seq[Queue] = {
    import io.circe.parser.decode
    import io.circe.generic.extras.auto.exportDecoder

    implicit val genDevConfig: Configuration = Queue.genDevConfig

    decode[Seq[Queue]](jsonString) match {
      case Right(envelope) => envelope
      case Left(x) => throw x
    }
  }
}

case class CouldNotCompleteMomTaskButOKToRetryException(uri:Uri,
                                                         status:Option[Status] = None,
                                                         contents:Option[String] = None,
                                                         cause:Option[Throwable] = None
                                                        ) extends Exception(s"Could not $uri due to status code $status with message '$contents'",cause.orNull)

case class CouldNotCompleteMomTaskDoNotRetryException(uri:Uri,
                                                       status:Option[Status] = None,
                                                       contents:Option[String] = None,
                                                       cause:Option[Throwable] = None
                                                      ) extends Exception(s"Could not $uri due to status code $status with message '$contents' ",cause.orNull)

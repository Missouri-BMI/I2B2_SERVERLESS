package net.shrine.messagequeueservice

import cats.effect.IO
import net.shrine.crypto.SecureRandomSource
import net.shrine.protocol.version.MomQueueName
import org.http4s.{Method, Request, Uri}

import scala.concurrent.duration.Duration
import scala.util.Try

/**
  * Container for common parts for moving messages around in Shrine's custom message service
  */
object ShrineMessageRequests {

  def pingRequest(momBaseUri: Uri): Request[IO] = Request(
    method = Method.GET,
    uri = momBaseUri /"ping"
  )

  def createQueueRequest(momBaseUri: Uri,queueName:MomQueueName): Request[IO] = Request(
    method = Method.PUT,
    uri = momBaseUri / "createQueue" / s"${queueName.underlying}"
  )

  def getQueueRequest(momBaseUri: Uri,queueName:MomQueueName): Request[IO] = Request(
    method = Method.GET,
    uri = momBaseUri / "getQueue" / s"${queueName.underlying}"
  )

  def deleteQueueRequest(momBaseUri: Uri,queueName:MomQueueName): Request[IO] = Request(
    method = Method.PUT,
    uri = momBaseUri / "deleteQueue" / s"${queueName.underlying}"
  )

  def getQueuesRequest(momBaseUri: Uri): Request[IO] = Request(
    method = Method.GET,
    uri = momBaseUri / "getQueues"
  )

  def sendMessageRequest(momBaseUri: Uri,to:MomQueue,contents:String): Request[IO] = Request(
    method = Method.PUT,
    uri = momBaseUri / "sendMessage" / s"${to.name.underlying}"
  ).withEntity(contents)

  def receiveMessageRequest(momBaseUri: Uri,from:MomQueue,timeout:Duration): Request[IO] = Request[IO](
    method = Method.GET,
    uri = momBaseUri / "receiveMessage" / s"${from.name.underlying}"
      withQueryParam("timeOutSeconds",timeout.toSeconds)
  )

  def receiveMessageStreamRequest(momBaseUri: Uri,from:MomQueue): Request[IO] = Request[IO](
    method = Method.GET,
    uri = momBaseUri / "receiveStream" / s"${from.name.underlying}"
  )

  def completeMessageRequest(momBaseUri: Uri, deliveryAttemptId:DeliveryAttemptId): Request[IO] = Request[IO](
    method = Method.PUT,
    uri = momBaseUri / "acknowledge" / s"${deliveryAttemptId.underlying}"
  )

}

class DeliveryAttemptId(val underlying:Long) extends AnyVal {
  override def toString: String = s"DeliveryAttemptId($underlying)"
}

object DeliveryAttemptId {
  def create():DeliveryAttemptId = new DeliveryAttemptId(SecureRandomSource.nextId())
}

/**
  * For converting between Scala and json
  */
case class MessageAsJson(
                          deliveryAttemptId: DeliveryAttemptId,
                          millisecondsToComplete:Long,
                          remainingAttempts:Int, //todo keep for backwards compatibility with SHRINE 3.2
                          contents: String
                        ) {
  def asJsonText:String = {
    import io.circe.generic.extras.Configuration
    import io.circe.generic.extras.auto.exportEncoder
    import io.circe.syntax.EncoderOps

    implicit val genDevConfig: Configuration = MessageAsJson.genDevConfig

    this.asJson.noSpaces
  }
}

object MessageAsJson {
  import io.circe.generic.extras.Configuration
  val genDevConfig: Configuration =
    Configuration.default.withDiscriminator("encodedClass")

  def tryRead(jsonText: String):Try[MessageAsJson] = Try {
    import io.circe.parser.decode
    import io.circe.generic.extras.auto.exportDecoder
    import io.circe.generic.extras.Configuration

    implicit val genDevConfig: Configuration = MessageAsJson.genDevConfig

    decode[MessageAsJson](jsonText) match {
      case Right(envelope) => envelope
      case Left(x) => throw x //throw errors to pick up in the Try
    }
  }
}

package net.shrine.messagequeuemiddleware

import cats.data.OptionT
import cats.effect.IO
import cats.effect.unsafe.implicits.global
import net.shrine.config.ConfigSource
import net.shrine.messagequeuemiddleware.LocalMessageQueueMiddleware.LocalMessage
import net.shrine.messagequeueservice.{MessageAsJson, Queue, ShrineMessageRequests}
import net.shrine.protocol.version.MomQueueName
import org.http4s.EntityDecoder.text
import org.http4s.dsl.io.{Accepted, Created, NoContent, NotFound, Ok}
import org.http4s.{Method, Request, Response}
import org.junit.jupiter.api.Assertions.{assertEquals, fail}
import org.junit.jupiter.api.Test

import scala.concurrent.duration.Duration
import org.http4s.syntax.literals._

/**
  * Test basic functions of MessageQueueWebApi
  * Created by yifan on 7/27/17.
  */


class MessageQueueWebApiTest { 

  private val expectedQueue: Queue = Queue("testQueueInWebApi")
  private val messageContent = "test Content"

  private val momBaseUri = uri""

  //todo refactor to a common test package
  private def requestResponse(request: Request[IO]): Response[IO] = {

    val responseOptionIo: OptionT[IO, Response[IO]] = MessageQueueWebApi().service.run(request)

    val iorio: IO[Response[IO]] = responseOptionIo.map { r: Response[IO] => r }.fold {
      //todo is there some kind of exception or the like available?
      fail("No response from service")
    } { r: Response[IO] => r
    }
    iorio.unsafeRunSync()
  }

  @Test
  def testReplyToAnyRequestWithNotFoundIfNotEnabled():Unit = {
    val response: Response[IO] = requestResponse(Request(method = Method.GET, uri = uri"/ping"))
    assertEquals(NotFound,response.status)
  }

  val configMap: Map[String, String] = Map(
    "shrine.hub.messagequeue.blockingqWebApi.enabled" -> "true",
    "shrine.hub.messagequeue.blockingq.messageTimeToLive" -> "4 days",
    "shrine.hub.messagequeue.blockingq.messageRedeliveryDelay" -> "3 seconds",
    "shrine.hub.messagequeue.blockingq.messageMaxDeliveryAttempts" -> "2"
  )

  @Test
  def testPing():Unit = {
    ConfigSource.atomicConfig.configForBlock(configMap, "MessageQueueWebApiTest") {
      val response: Response[IO] = requestResponse(Request(method = Method.GET, uri = uri"/ping"))
      assertEquals(Ok,response.status)
      val entityText = response.as[String].unsafeRunSync()
      assertEquals("pong",entityText)
    }
  }

  @Test
  def testParallelTest():Unit = {
    ConfigSource.atomicConfig.configForBlock(configMap, "MessageQueueWebApiTest") {
      val response: Response[IO] = requestResponse(Request(method = Method.GET, uri = uri"/parallel?timeOutSeconds=1"))
      assertEquals(Ok,response.status)
      val entityText = response.as[String].unsafeRunSync()
      assert(entityText.startsWith("1"),s"$entityText should start with 1, but doesn't")
    }
  }

  @Test
  def testReplyToUnknownUrl():Unit = {
    ConfigSource.atomicConfig.configForBlock(configMap, "MessageQueueWebApiTest") {
      val response: Response[IO] = requestResponse(Request(method = Method.GET, uri = uri"/unknownPath"))
      assertEquals(NotFound,response.status)
    }
  }

  @Test
  def testCreateGetDeleteQueues():Unit = {
    ConfigSource.atomicConfig.configForBlock(configMap, "MessageQueueWebApiTest") {

      {
        val response: Response[IO] = requestResponse(ShrineMessageRequests.getQueuesRequest(momBaseUri))
        assertEquals(Ok,response.status)
        val bodyString = response.as[String].unsafeRunSync()
        val queues = Queue.seqFromJson(bodyString)

        assertEquals(Seq.empty,queues)
      }

      {
        val response: Response[IO] = requestResponse(ShrineMessageRequests.createQueueRequest(momBaseUri,MomQueueName("testQueueInWebApi")))
        assertEquals(Created,response.status)
        val bodyString = response.as[String].unsafeRunSync()
        val queue: Queue = Queue.fromJson(bodyString)

        assertEquals(expectedQueue,queue)
      }

      {
        val response: Response[IO] = requestResponse(ShrineMessageRequests.getQueuesRequest(momBaseUri))
        assertEquals(Ok,response.status)
        val bodyString = response.as[String].unsafeRunSync()
        val queues = Queue.seqFromJson(bodyString)

        assertEquals(Seq(expectedQueue),queues)
      }

      {
        val response: Response[IO] = requestResponse(ShrineMessageRequests.getQueueRequest(momBaseUri,MomQueueName("testQueueInWebApi")))
        assertEquals(Ok,response.status)
        val bodyString = response.as[String].unsafeRunSync()
        val queue: Queue = Queue.fromJson(bodyString)

        assertEquals(expectedQueue,queue)
      }

      {
        val response: Response[IO] = requestResponse(ShrineMessageRequests.deleteQueueRequest(momBaseUri,MomQueueName("testQueueInWebApi")))
        assertEquals(Ok,response.status)
      }

      {
        val response: Response[IO] = requestResponse(ShrineMessageRequests.getQueuesRequest(momBaseUri))
        assertEquals(Ok,response.status)
        val bodyString = response.as[String].unsafeRunSync()
        val queues = Queue.seqFromJson(bodyString)

        assertEquals(Seq.empty,queues)
      }
    }
  }

  @Test
  def testSendReceiveAndComplete():Unit = {
    ConfigSource.atomicConfig.configForBlock(configMap, "MessageQueueWebApiTest") {

      val twoSeconds = Duration.create(2,"seconds")
      val queue = {
        val response: Response[IO] = requestResponse(ShrineMessageRequests.createQueueRequest(momBaseUri,MomQueueName("testQueueInWebApi")))
        assertEquals(Created,response.status)
        val bodyString = response.as[String].unsafeRunSync()
        val queue: Queue = Queue.fromJson(bodyString)

        assertEquals(expectedQueue,queue)
        LocalMessageQueueMiddleware.createQueueIfAbsentIO(MomQueueName(queue.name)).unsafeRunSync()
      }

      {
        val response: Response[IO] = requestResponse(ShrineMessageRequests.receiveMessageRequest(momBaseUri,queue,twoSeconds))
        assertEquals(NoContent,response.status)
      }

      {
        val response: Response[IO] = requestResponse(ShrineMessageRequests.sendMessageRequest(momBaseUri,queue,messageContent))
        assertEquals(Accepted,response.status)
      }

      val message = {
        val response: Response[IO] = requestResponse(ShrineMessageRequests.receiveMessageRequest(momBaseUri,queue,twoSeconds))
        assertEquals(Ok,response.status)

        val entityText = response.as[String].unsafeRunSync()
        val message: LocalMessage = MessageAsJson.tryRead(entityText).map(LocalMessage(_)).get
        assertEquals(message.contents,messageContent)

        message
      }

      {
        val response: Response[IO] = requestResponse(ShrineMessageRequests.completeMessageRequest(momBaseUri,message.deliveryAttemptId))
        assertEquals(Ok,response.status)
      }

      {
        val response: Response[IO] = requestResponse(ShrineMessageRequests.receiveMessageRequest(momBaseUri,queue,twoSeconds))
        assertEquals(NoContent,response.status)
      }

      {
        val response: Response[IO] = requestResponse(ShrineMessageRequests.deleteQueueRequest(momBaseUri,queue.name))
        assertEquals(Ok,response.status)
      }
    }
  }

  /* todo turn back on with SHRINE-3025
  "MessageQueueWebApi" should "send, receive via stream, and ack messages in" in {
    ConfigSource.atomicConfig.configForBlock(configMap, "MessageQueueWebApiTest") {

      val twoSeconds = Duration.create(2,"seconds")
      val queue = {
        val response: Response[IO] = requestResponse(ShrineMessageRequests.createQueueRequest(momBaseUri,"testQueueInWebApi"))
        assertEquals(Created)(response.status)
        val bodyString = response.as[String].unsafeRunSync()
        val queue: Queue = Queue.fromJson(bodyString)

        assertEquals(expectedQueue)(queue)
        LocalMessageQueueMiddleware.createQueueIfAbsentIO(queue.name).unsafeRunSync()
      }

      val stream: Stream[IO, LocalMessage] = {
        val response: Response[IO] = requestResponse(ShrineMessageRequests.receiveMessageStreamRequest(momBaseUri,queue))
        assertEquals(Ok)(response.status)

        response.body.
          through(ServerSentEvent.decoder).
          map{sse =>
            MessageAsJson.tryRead(sse.data).map(LocalMessage(_)).get
          }
      }

      val count = 10
      (1 to count).foreach{ i =>
        val response: Response[IO] = requestResponse(ShrineMessageRequests.sendMessageRequest(momBaseUri,queue,messageContent+i))
        assertEquals(Accepted)(response.status)
      }

      stream.map{message =>
        assert(message.contents.startsWith(messageContent))

        val response: Response[IO] = requestResponse(ShrineMessageRequests.completeMessageRequest(momBaseUri,message.deliveryAttemptId))
        assertEquals(Ok)(response.status)
      }.take(count).compile.drain.unsafeRunTimed(twoSeconds)

      {
        val response: Response[IO] = requestResponse(ShrineMessageRequests.deleteQueueRequest(momBaseUri,queue.name))
        assertEquals(Ok)(response.status)
      }
    }
  }

  "MessageQueueWebApi" should "send, receive via stream, and ack messages of very long strings" in {
    ConfigSource.atomicConfig.configForBlock(configMap, "MessageQueueWebApiTest") {

      val twoSeconds = Duration.create(2,"seconds")
      val queue = {
        val response: Response[IO] = requestResponse(ShrineMessageRequests.createQueueRequest(momBaseUri,"testQueueInWebApi"))
        assertEquals(Created)(response.status)
        val entityText = response.as[String].unsafeRunSync()
        val queue: Queue = Queue.fromJson(entityText)

        assertEquals(expectedQueue)(queue)
        LocalMessageQueueMiddleware.createQueueIfAbsentIO(queue.name).unsafeRunSync()
      }

      val stream: Stream[IO, LocalMessage] = {
        val response: Response[IO] = requestResponse(ShrineMessageRequests.receiveMessageStreamRequest(momBaseUri,queue))
        assertEquals(Ok)(response.status)

        response.body.
          through(ServerSentEvent.decoder).
          map{sse =>
            MessageAsJson.tryRead(sse.data).map(LocalMessage(_)).get
          }
      }

      val iters = 14
      val expectedMessages = (1 to iters).map{ i =>
        val number = scala.math.pow(2,i).toInt
        s"$i $number ${"*".*(number)}"
      }

      expectedMessages.foreach{ expected =>
        val response: Response[IO] = requestResponse(ShrineMessageRequests.sendMessageRequest(momBaseUri,queue,expected))
        assertEquals(Accepted)(response.status)
      }

      val expectedMessageStream: Stream[Pure, String] = Stream.emits(expectedMessages)

      val zippedStream = expectedMessageStream.zip(stream)

      zippedStream.map{expectedAndMessage: (String, LocalMessage) =>
        val expected: String = expectedAndMessage._1
        val message = expectedAndMessage._2

        assertEquals(expected.length)(message.contents.length)
        assertEquals(expected)(message.contents)

        val response: Response[IO] = requestResponse(ShrineMessageRequests.completeMessageRequest(momBaseUri,message.deliveryAttemptId))
        assertEquals(Ok)(response.status)
      }.take(iters).compile.drain.unsafeRunTimed(twoSeconds)

      {
        val response: Response[IO] = requestResponse(ShrineMessageRequests.deleteQueueRequest(momBaseUri,queue.name))
        assertEquals(Ok)(response.status)
      }
    }
  }
*/
}
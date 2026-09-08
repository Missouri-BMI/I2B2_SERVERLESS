package net.shrine.messagequeuemiddleware

import java.util.concurrent.TimeUnit
import cats.effect.IO
import fs2.Stream
import net.shrine.config.{ConfigExtensions, ConfigSource}
import net.shrine.crypto.SecureRandomSource
import net.shrine.messagequeuemiddleware.LocalMessageQueueMiddleware.InternalMessage
import net.shrine.messagequeueservice.{Message, MomQueue, Queue}
import net.shrine.protocol.version.MomQueueName
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.{Test, Timeout}

import scala.collection.immutable.Seq
import scala.concurrent.duration.{Duration, DurationInt, FiniteDuration}
import scala.language.postfixOps
import scala.util.Try

/**
  * Test create, delete queue, send, and receive message, getQueueNames, and acknowledge using MessageQueue service
  */
@Timeout(value = 60, unit = TimeUnit.SECONDS)
class LocalMessageQueueMiddlewareTest {
  val waitForQuickMethod: FiniteDuration = FiniteDuration(1,"minute")

  import cats.effect.unsafe.implicits.global

  @Test
  def testSendAndReceiveOneMessage():Unit = {

    val configMap: Map[String, String] = Map("shrine.hub.messagequeue.blockingq.messageTimeToLive" -> "7 seconds",
      "shrine.hub.messagequeue.blockingq.messageRedeliveryDelay" -> "2 seconds")

    ConfigSource.atomicConfig.configForBlock(configMap, "LocalMessageQueueMiddlewareTest") {
      val queueName = MomQueueName("receiveOneMessage")

      assert(LocalMessageQueueMiddleware.queuesIO.unsafeRunSync().isEmpty)

      val queue = LocalMessageQueueMiddleware.createQueueIfAbsentIO(queueName).unsafeRunSync()

      assert(LocalMessageQueueMiddleware.queuesIO.unsafeRunSync() == Seq(queue))

      val testContents = "Test message_receiveOneMessage"

      queue.sendIO(testContents).unsafeRunTimed(waitForQuickMethod)

      queue.receiveIO(1 second).map{ messageOpt =>
        assert(messageOpt.isDefined)
        assert(messageOpt.get.contents == testContents)
        messageOpt.get.completeIO().unsafeRunSync()
      }.unsafeRunSync()

      // receive immediately again, should be no message redelivered yet
      queue.receiveIO( 1 second).map{ noMessage =>
        assert(noMessage.isEmpty)
      }.unsafeRunSync()


      LocalMessageQueueMiddleware.deleteQueueIO(queueName).unsafeRunSync()
      assert(LocalMessageQueueMiddleware.queuesIO.unsafeRunSync().isEmpty)
    }
  }

  @Test
  def testSendAndReceiveSomeMessages():Unit = {

    val queues: Seq[MomQueue] = LocalMessageQueueMiddleware.queuesIO.unsafeRunSync()
    queues.foreach({queue: MomQueue => LocalMessageQueueMiddleware.deleteQueueIO(queue.name).unsafeRunSync()})

    val queueName = MomQueueName("receiveAFewMessages")

    assert(LocalMessageQueueMiddleware.queuesIO.unsafeRunSync().isEmpty)

    val queue = LocalMessageQueueMiddleware.createQueueIfAbsentIO(queueName).unsafeRunSync()

    assert(LocalMessageQueueMiddleware.queuesIO.unsafeRunSync() == Seq(queue))

    val testContents1 = "First test message_receiveAFewMessages"
    queue.sendIO(testContents1).unsafeRunTimed(waitForQuickMethod)
    val message: Option[Message] = queue.receiveIO(1 second).map{ message =>
      assert(message.isDefined)
      assert(message.get.contents == testContents1)
      message
    }.unsafeRunSync()

    message.get.completeIO().unsafeRunTimed(waitForQuickMethod)

    queue.receiveIO(1 second).map{ shouldBeNoMessage =>
      assert(shouldBeNoMessage.isEmpty)
    }.unsafeRunSync()


    val testContents2 = "Second test message_receiveAFewMessages"
    queue.sendIO(testContents2).unsafeRunTimed(waitForQuickMethod)

    val testContents3 = "Third test message_receiveAFewMessages"
    queue.sendIO(testContents3).unsafeRunTimed(waitForQuickMethod)

    val message2: Option[Message] = queue.receiveIO(1 second).map{ message2 =>
      assert(message2.isDefined)
      assert(message2.get.contents == testContents2)
      message2
    }.unsafeRunSync()

    message2.get.completeIO().unsafeRunTimed(waitForQuickMethod)

    val message3: Option[Message] = queue.receiveIO(1 second).map{ message3 =>
      assert(message3.isDefined)
      assert(message3.get.contents == testContents3)
      message3
    }.unsafeRunSync()

    message3.get.completeIO().unsafeRunTimed(waitForQuickMethod)

    queue.receiveIO(1 second).map{ shouldBeNoMessage4 =>
      assert(shouldBeNoMessage4.isEmpty)
    }.unsafeRunSync()

    LocalMessageQueueMiddleware.deleteQueueIO(queueName).unsafeRunSync()
    assert(LocalMessageQueueMiddleware.queuesIO.unsafeRunSync().isEmpty)
  }

  @Test
  def testStreamAFewMessages():Unit = {

    val queues: Seq[MomQueue] = LocalMessageQueueMiddleware.queuesIO.unsafeRunSync()
    queues.foreach({queue: MomQueue => LocalMessageQueueMiddleware.deleteQueueIO(queue.name).unsafeRunSync()})

    val queueName = MomQueueName("streamAFewMessages")

    assert(LocalMessageQueueMiddleware.queuesIO.unsafeRunSync().isEmpty)

    val queue = LocalMessageQueueMiddleware.createQueueIfAbsentIO(queueName).unsafeRunSync()

    assert(LocalMessageQueueMiddleware.queuesIO.unsafeRunSync() == Seq(queue))

    //put a message in the queue to receive before the stream first starts
    val testContents1 = "First test message_receiveAFewMessages"
    queue.sendIO(testContents1).unsafeRunTimed(waitForQuickMethod)

    val messageStream: Stream[IO, Message] = queue.receiveStream()

    val message: Message = messageStream.take(1).map{ message =>
      assert(message.contents == testContents1)
      message
    }.compile.toList.map(_.head).unsafeRunTimed(waitForQuickMethod).get

    message.completeIO().unsafeRunTimed(waitForQuickMethod)

    //send some messages after the IO Stream has been created
    val testContents2 = "Second test message_receiveAFewMessages"
    queue.sendIO(testContents2).unsafeRunTimed(waitForQuickMethod)

    val testContents3 = "Third test message_receiveAFewMessages"
    queue.sendIO(testContents3).unsafeRunTimed(waitForQuickMethod)

    val messages: List[Message] = messageStream.take(2).compile.toList.unsafeRunTimed(waitForQuickMethod).get
    assert(messages.size == 2)

    val message2: Message = messages.head
    assert(message2.contents == testContents2)
    message2.completeIO().unsafeRunTimed(waitForQuickMethod)

    val message3: Message = messages.tail.head
    assert(message3.contents == testContents3)
    message3.completeIO().unsafeRunTimed(waitForQuickMethod)

    LocalMessageQueueMiddleware.deleteQueueIO(queueName).unsafeRunSync()
    assert(LocalMessageQueueMiddleware.queuesIO.unsafeRunSync().isEmpty)
  }

  @Test
  def testCreateAQueueTwice():Unit = {

    val queues: Seq[MomQueue] = LocalMessageQueueMiddleware.queuesIO.unsafeRunSync()
    queues.foreach({queue: MomQueue => LocalMessageQueueMiddleware.deleteQueueIO(queue.name).unsafeRunSync()})

    val queueName = MomQueueName("createSameQueueTwice")
    val queue = LocalMessageQueueMiddleware.createQueueIfAbsentIO(queueName).unsafeRunSync()
    val sameQueue = LocalMessageQueueMiddleware.createQueueIfAbsentIO(queueName).unsafeRunSync()

    assert(queue == sameQueue)
    assert(LocalMessageQueueMiddleware.queuesIO.unsafeRunSync() == Seq(sameQueue))

    val anotherSameQueue = LocalMessageQueueMiddleware.getQueueIO(queueName).unsafeRunSync()
    assert(queue == anotherSameQueue)
    assert(LocalMessageQueueMiddleware.queuesIO.unsafeRunSync() == Seq(sameQueue))

    LocalMessageQueueMiddleware.deleteQueueIO(queueName).unsafeRunSync()
    assert(LocalMessageQueueMiddleware.queuesIO.unsafeRunSync().isEmpty)
  }

  @Test
  def testFailToDeleteAQueueThatDoesNotExist():Unit = {

    val queues: Seq[MomQueue] = LocalMessageQueueMiddleware.queuesIO.unsafeRunSync()
    queues.foreach({queue: MomQueue => LocalMessageQueueMiddleware.deleteQueueIO(queue.name).unsafeRunSync()})

    val queueName = MomQueueName("DeletingNonExistingQueue")
    val deleteQueue = Try{LocalMessageQueueMiddleware.deleteQueueIO(queueName).unsafeRunSync()}
    assert(deleteQueue.isFailure)
    assert(LocalMessageQueueMiddleware.queuesIO.unsafeRunSync().isEmpty)
  }

  @Test
  def testFailToSendToQueueThatDoesNotExist():Unit = {

    val queueName = MomQueueName("SendToNonExistingQueue")
    val queue: MomQueue = LocalMessageQueueMiddleware.createQueueIfAbsentIO(queueName).unsafeRunSync()

    val queues: Seq[MomQueue] = LocalMessageQueueMiddleware.queuesIO.unsafeRunSync()
    queues.foreach({queue: MomQueue => LocalMessageQueueMiddleware.deleteQueueIO(queue.name).unsafeRunSync()})

    assertThrows(classOf[QueueDoesNotExistException],
      () => queue.sendIO("testContent",0L).unsafeRunTimed(waitForQuickMethod))
    assert(LocalMessageQueueMiddleware.queuesIO.unsafeRunSync().isEmpty)
  }

  @Test
  def testFailToReceiveFromQueueThatDoesNotExist():Unit = {

    val queueName = MomQueueName("ReceiveFromNonExistingQueue")
    val queue: MomQueue = LocalMessageQueueMiddleware.createQueueIfAbsentIO(queueName).unsafeRunSync()

    val queues: Seq[MomQueue] = LocalMessageQueueMiddleware.queuesIO.unsafeRunSync()
    queues.foreach({queue: MomQueue => LocalMessageQueueMiddleware.deleteQueueIO(queue.name).unsafeRunSync()})

    assertThrows(classOf[QueueDoesNotExistException], () => queue.receiveIO(1 second).unsafeRunSync())
    assert(LocalMessageQueueMiddleware.queuesIO.unsafeRunSync().isEmpty)
  }

  @Test
  def testFilteredQueueNames():Unit = {
    val queues: Seq[MomQueue] = LocalMessageQueueMiddleware.queuesIO.unsafeRunSync()
    queues.foreach({queue: MomQueue => LocalMessageQueueMiddleware.deleteQueueIO(queue.name).unsafeRunSync()})

    val queueName = "test# Qu%eueFilter"

    assert(Queue(queueName).name == "testQueueFilter")
  }

  @Test
  def testInternalMessageHashCodeEquality():Unit =  {

    val queues: Seq[MomQueue] = LocalMessageQueueMiddleware.queuesIO.unsafeRunSync()
    queues.foreach({queue: MomQueue => LocalMessageQueueMiddleware.deleteQueueIO(queue.name).unsafeRunSync()})

    val queue = LocalMessageQueueMiddleware.createQueueIfAbsentIO(MomQueueName("to")).unsafeRunSync()

    val id: Long = SecureRandomSource.nextId()
    val createdTime = System.currentTimeMillis()
    val message1: InternalMessage = InternalMessage(id, "message1", createdTime, queue, 3)
    val message2: InternalMessage = InternalMessage(id, "message1", createdTime, queue, 1)
    assert(message1 == message2)
    val message3: InternalMessage = InternalMessage(SecureRandomSource.nextId(), "message3", createdTime, queue, 3)
    assert(message1 != message3)

    LocalMessageQueueMiddleware.deleteQueueIO(MomQueueName("to")).unsafeRunSync()
  }

  @Test
  def testSendAgainIfMessageNotCompleted():Unit = {

    val configMap: Map[String, String] = Map("shrine.hub.messagequeue.blockingq.messageTimeToLive" -> "7 seconds",
      "shrine.hub.messagequeue.blockingq.messageRedeliveryDelay" -> "2 seconds",
      "shrine.hub.messagequeue.blockingq.messageMaxDeliveryAttempts" -> "4")

    ConfigSource.atomicConfig.configForBlock(configMap, "LocalMessageQueueMiddlewareTest") {
      val queueName = "receiveOneMessage"

      val messageRedeliveryDelay = ConfigSource.config.get("shrine.hub.messagequeue.blockingq.messageRedeliveryDelay", Duration(_)).toMillis

      assert(LocalMessageQueueMiddleware.queuesIO.unsafeRunSync().isEmpty)

      val queue = LocalMessageQueueMiddleware.createQueueIfAbsentIO(MomQueueName(queueName)).unsafeRunSync()

      assert(LocalMessageQueueMiddleware.queuesIO.unsafeRunSync() == Seq(queue))

      val testContents = "Test message_receiveOneMessage"

      queue.sendIO(testContents).unsafeRunTimed(waitForQuickMethod)

      // first time receive
      queue.receiveIO( 1 second).map{ messageOpt =>
        assert(messageOpt.isDefined)
        assert(messageOpt.get.contents == testContents)
        messageOpt
      }.unsafeRunSync()

      // receive immediately again, should be no message redelivered yet
      // 1 second
      queue.receiveIO(1 second).map{ noMessage =>
        assert(noMessage.isEmpty)
      }.unsafeRunSync()

      // receive after the redelivery delay, should have the redelivered message
      TimeUnit.MILLISECONDS.sleep(messageRedeliveryDelay + 1000)
      // > 2 seconds
      val secondAttemptMessage = queue.receiveIO(1 second).map{ messageOpt =>
        assert(messageOpt.isDefined)
        assert(messageOpt.get.contents == testContents)
        messageOpt
      }.unsafeRunSync()
      secondAttemptMessage.get.completeIO().unsafeRunTimed(waitForQuickMethod)

      // receive after message is completed, should be no message
      queue.receiveIO( 1 second).map{ noMessage =>
        assert(noMessage.isEmpty)
      }.unsafeRunSync()

      LocalMessageQueueMiddleware.deleteQueueIO(MomQueueName(queueName)).unsafeRunSync()
      assert(LocalMessageQueueMiddleware.queuesIO.unsafeRunSync().isEmpty)
    }
  }

  @Test
  def testGiveUpSendingMessageAfterAttempts():Unit = {

    val configMap: Map[String, String] = Map("shrine.hub.messagequeue.blockingq.messageTimeToLive" -> "7 seconds",
      "shrine.hub.messagequeue.blockingq.messageRedeliveryDelay" -> "2 seconds",
      "shrine.hub.messagequeue.blockingq.messageMaxDeliveryAttempts" -> "2")

    ConfigSource.atomicConfig.configForBlock(configMap, "LocalMessageQueueMiddlewareTest") {
      val queueName = "receiveOneMessage"

      //1 attempt
      val messageRedeliveryDelay = ConfigSource.config.get("shrine.hub.messagequeue.blockingq.messageRedeliveryDelay", Duration(_)).toMillis

      assert(LocalMessageQueueMiddleware.queuesIO.unsafeRunSync().isEmpty)

      val queue = LocalMessageQueueMiddleware.createQueueIfAbsentIO(MomQueueName(queueName)).unsafeRunSync()

      assert(LocalMessageQueueMiddleware.queuesIO.unsafeRunSync() == Seq(queue))

      val testContents = "Test message_receiveOneMessage"

      queue.sendIO(testContents).unsafeRunTimed(waitForQuickMethod)

      // first time receive
      queue.receiveIO(1 second).map{ messageOpt =>
        assert(messageOpt.isDefined)
        assert(messageOpt.get.contents == testContents)
        messageOpt
      }.unsafeRunSync()

      // receive immediately again, should be no message redelivered yet
      // 1 second
      queue.receiveIO(1 second).map{ noMessage =>
        assert(noMessage.isEmpty)
      }.unsafeRunSync()

      // receive after the redelivery delay, should have the redelivered message
      TimeUnit.MILLISECONDS.sleep(messageRedeliveryDelay + 1000)
      // > 2 seconds
      queue.receiveIO( 1 second).map{ messageOpt =>
        assert(messageOpt.isDefined)
        assert(messageOpt.get.contents == testContents)
        messageOpt
      }.unsafeRunSync()

      // receive immediately again, should be no message redelivered yet
      queue.receiveIO(1 second).map{ noMessage =>
        assert(noMessage.isEmpty)
      }.unsafeRunSync()

      // > 3 seconds

      // receive after the redelivery delay again, reached maxRedelivery attempt, should be no message
      TimeUnit.MILLISECONDS.sleep(messageRedeliveryDelay + 1000)
      // > 4 seconds
      queue.receiveIO( 1 second).map{ noMessage =>
        assert(noMessage.isEmpty)
      }.unsafeRunSync()

      LocalMessageQueueMiddleware.deleteQueueIO(MomQueueName(queueName)).unsafeRunSync()
      assert(LocalMessageQueueMiddleware.queuesIO.unsafeRunSync().isEmpty)
    }
  }

  @Test
  def testMessageExpires():Unit = {

    val configMap: Map[String, String] = Map("shrine.hub.messagequeue.blockingq.messageTimeToLive" -> "2 seconds",
      "shrine.hub.messagequeue.blockingq.messageRedeliveryDelay" -> "1 second")

    ConfigSource.atomicConfig.configForBlock(configMap, "LocalMessageQueueMiddlewareTest") {
      val queueName = "receiveOneMessage"

      //2 seconds
      val messageTimeToLive = ConfigSource.config.get("shrine.hub.messagequeue.blockingq.messageTimeToLive", Duration(_)).toMillis

      assert(LocalMessageQueueMiddleware.queuesIO.unsafeRunSync().isEmpty)

      val queue = LocalMessageQueueMiddleware.createQueueIfAbsentIO(MomQueueName(queueName)).unsafeRunSync()

      assert(LocalMessageQueueMiddleware.queuesIO.unsafeRunSync() == Seq(queue))

      val testContents = "Test message_receiveOneMessage"

      queue.sendIO(testContents).unsafeRunTimed(waitForQuickMethod)

      // receive after the messageTimeToLive
      TimeUnit.MILLISECONDS.sleep(messageTimeToLive + 1000)
      // > 3 seconds
      queue.receiveIO(1 second).map{ noMessage =>
        assert(noMessage.isEmpty)
      }.unsafeRunSync()


      LocalMessageQueueMiddleware.deleteQueueIO(MomQueueName(queueName)).unsafeRunSync()
      assert(LocalMessageQueueMiddleware.queuesIO.unsafeRunSync().isEmpty)
    }
  }

  @Test
  def testMessageExpiresWithPendingMessage():Unit = {

    val configMap: Map[String, String] = Map("shrine.hub.messagequeue.blockingq.messageTimeToLive" -> "2 seconds",
      "shrine.hub.messagequeue.blockingq.messageRedeliveryDelay" -> "1 second")

    ConfigSource.atomicConfig.configForBlock(configMap, "LocalMessageQueueMiddlewareTest") {
      val queueName = "receiveOneAfterMissingAnotherMessage"

      //2 seconds
      val messageTimeToLive = ConfigSource.config.get("shrine.hub.messagequeue.blockingq.messageTimeToLive", Duration(_)).toMillis

      assert(LocalMessageQueueMiddleware.queuesIO.unsafeRunSync().isEmpty)

      val queue = LocalMessageQueueMiddleware.createQueueIfAbsentIO(MomQueueName(queueName)).unsafeRunSync()

      assert(LocalMessageQueueMiddleware.queuesIO.unsafeRunSync() == Seq(queue))

      val testContents = "Test message_receiveOneMessage that should time out"

      queue.sendIO(testContents).unsafeRunTimed(waitForQuickMethod)
      TimeUnit.MILLISECONDS.sleep(messageTimeToLive/2 )

      val testOtherContents = "Test message_receiveOneMessage that should arrive"
      queue.sendIO(testOtherContents).unsafeRunTimed(waitForQuickMethod)
      TimeUnit.MILLISECONDS.sleep(messageTimeToLive/2 + 100)

      //receive only the second message sent
      queue.receiveIO(1 second).map{ message: Option[Message] =>
        assert(message.isDefined)
        assert(message.exists(_.contents == testOtherContents))
      }.unsafeRunSync()

      queue.receiveIO( 1 second).map{ noMessage =>
        assert(noMessage.isEmpty)
      }.unsafeRunSync()

      LocalMessageQueueMiddleware.deleteQueueIO(MomQueueName(queueName)).unsafeRunSync()
      assert(LocalMessageQueueMiddleware.queuesIO.unsafeRunSync().isEmpty)
    }
  }
}
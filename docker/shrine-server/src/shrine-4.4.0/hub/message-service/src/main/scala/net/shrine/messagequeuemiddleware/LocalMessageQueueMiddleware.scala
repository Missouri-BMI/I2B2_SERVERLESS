package net.shrine.messagequeuemiddleware

import java.util
import java.util.concurrent.{BlockingDeque, LinkedBlockingDeque, ScheduledExecutorService, ScheduledFuture, ScheduledThreadPoolExecutor, TimeUnit, TimeoutException}
import fs2.Stream
import cats.effect.IO
import ch.qos.logback.classic.Level
import net.shrine.config.{ConfigExtensions, ConfigSource}
import net.shrine.crypto.SecureRandomSource
import net.shrine.http4s.catsio.ExecutionContexts
import net.shrine.log.Log
import net.shrine.messagequeueservice.{DeliveryAttemptId, Message, MessageAsJson, MessageQueueService, MomQueue}
import net.shrine.problem.{AbstractProblem, ProblemSources}
import net.shrine.protocol.version.MomQueueName

import scala.annotation.tailrec
import scala.collection.concurrent.{TrieMap, Map => ConcurrentMap}
import scala.concurrent.duration.Duration
import scala.util.control.NonFatal
/**
  * This object is the local version of the Message-Oriented Middleware API, which uses MessageQueue service
  *
  * @author david
  * @since 7/18/17
  */

object LocalMessageQueueMiddleware extends MessageQueueService {

  val configPath = "shrine.hub.messagequeue.blockingq"

  private def config = ConfigSource.config.getConfig(configPath)

  private def messageTimeToLiveInMillis: Long = config.get("messageTimeToLive", Duration(_)).toMillis

  private def messageRedeliveryDelay: Long = config.get("messageRedeliveryDelay", Duration(_)).toMillis

  private def messageMaxDeliveryAttempts: Int = config.getInt("messageMaxDeliveryAttempts")

  // delivery attempt ids to (attempts and futures to cancel)
  private val messageDeliveryAttemptMap: TrieMap[DeliveryAttemptId, (DeliveryAttempt, Option[ScheduledFuture[_]])] = TrieMap.empty

  // message ids to futures to cancel
  private val messageIdsToExpirationCleanupTasks: TrieMap[Long,ScheduledFuture[_]] = TrieMap.empty

  /**
    * Use LocalMessageQueueStopper to stop the MessageQueueMiddleware without unintentionally starting it
    */
  private[messagequeuemiddleware] def stop(): util.List[Runnable] = {
    MessageScheduler.shutDown()
  }

  //todo key should be a Queue instead of a String SHRINE-2308
  //todo rename
  private val blockingQueuePool: ConcurrentMap[MomQueueName, BlockingDeque[InternalMessage]] = TrieMap.empty

  //queue lifecycle
  override def createQueueIfAbsentIO(queueName: MomQueueName): IO[LocalQueue] = IO {
    val proposedQueue = LocalQueue(queueName)
    blockingQueuePool.getOrElseUpdate(proposedQueue.name, new LinkedBlockingDeque[InternalMessage]())
    Log.info(s"created proposedQueue $queueName")
    proposedQueue
  }

  //already unrestricted
  override def addReceiverPermissionToQueueIO(queueName: MomQueueName, receiverId: String): IO[Unit] = IO.unit

  //already unrestricted
  override def addSenderPermissionToQueueIO(queueName: MomQueueName, senderId: String): IO[Unit] = IO.unit

  //already unrestricted
  override def removePermissionFromQueueIO(queueName: MomQueueName, receiverId: String): IO[Unit] = IO.unit

  override def getQueueIO(queueName: MomQueueName): IO[LocalQueue] = IO {
    val proposedQueue = LocalQueue(queueName)

    Log.debug(s"getQueueIO from $queueName - blockingQueuePool contains ${blockingQueuePool.keys}")

    blockingQueuePool.getOrElse(proposedQueue.name, {
      throw QueueDoesNotExistException(LocalQueue(queueName))
    })
    proposedQueue
  }

  override def deleteQueueIO(queueName: MomQueueName): IO[Unit] = IO {
    blockingQueuePool.remove(queueName).getOrElse(throw QueueDoesNotExistException(LocalQueue(queueName)))
  }

  override def queuesIO: IO[Seq[LocalQueue]] = IO {
    blockingQueuePool.keys.map(LocalQueue.apply).toSeq
  }

  private def toMessage(from:LocalQueue, internalMessage: InternalMessage):Message = {
    val deliveryAttemptID = DeliveryAttemptId.create()
    val deliveryAttempt: DeliveryAttempt = DeliveryAttempt(internalMessage, internalMessage.createdTime, from)
    MessageScheduler.scheduleMessageRedelivery(deliveryAttemptID, deliveryAttempt, messageRedeliveryDelay, messageMaxDeliveryAttempts)
    val localMessage = LocalMessage(deliveryAttemptID, messageRedeliveryDelay, internalMessage.remainingAttempts, internalMessage.contents)

    Log.debug(s"${deliveryAttempt.toString.take(70)} scheduled to expire for ${localMessage.toString.take(70)}")

    localMessage
  }

  def completeMessage(deliveryAttemptId: DeliveryAttemptId): IO[Unit] = IO {
    val deliveryAttemptAndFutureTaskOpt: Option[(DeliveryAttempt, Option[ScheduledFuture[_]])] = messageDeliveryAttemptMap.get(deliveryAttemptId)

    deliveryAttemptAndFutureTaskOpt.fold(
      // if message delivery attempt does not exist in the map, then it might be in the queue or expired
      throw MessageDoesNotExistAndCannotBeCompletedException(deliveryAttemptId)
    ){ deliveryAttemptAndFutureTask: (DeliveryAttempt, Option[ScheduledFuture[_]]) =>
      val deliveryAttempt: DeliveryAttempt = deliveryAttemptAndFutureTask._1
      val internalToBeSentMessage: InternalMessage = deliveryAttempt.message
      val queue: LocalQueue = internalToBeSentMessage.toQueue
      // removes all deliveryAttempts of the message from the map and cancels all the scheduled redelivers
      for ((id: DeliveryAttemptId, eachDAandTask: (DeliveryAttempt, Option[ScheduledFuture[_]])) <- messageDeliveryAttemptMap) {
        // internalMessage changes when it is redelivered, but id remains the same
        if (eachDAandTask._1.message.id == internalToBeSentMessage.id) {
          messageDeliveryAttemptMap.remove(id)
          // cancel message redelivery scheduled task
          MessageScheduler.cancelScheduledMessageRedelivery(eachDAandTask._2)
          messageIdsToExpirationCleanupTasks.remove(internalToBeSentMessage.id).foreach(MessageScheduler.cancelExpiredMessageCleanup)
        }
      }
      // removed the message from the queue (if it exists)
      // i.e: if completeMsg is called after it is redelivered, message is back
      // in queue and waiting to be redelivered again
      val blockingQueue = blockingQueuePool.getOrElse(queue.name, throw QueueDoesNotExistException(queue))
      blockingQueue.remove(internalToBeSentMessage)
      Log.debug(s"Message from ${deliveryAttemptAndFutureTask._1.fromQueue} completed")
    }
  }

  case class LocalQueue(name:MomQueueName) extends MomQueue {

    override def sendIO(contents: String,subject: Long = 0L): IO[Unit] = IO { //OK to use 0L - LocalQueue does not use the subject
      val queue = blockingQueuePool.getOrElse(name, throw QueueDoesNotExistException(this))
      // creates a message
      val msgID: Long = SecureRandomSource.nextId()
      val internalMessage: InternalMessage = InternalMessage(msgID, contents, System.currentTimeMillis(), this, messageMaxDeliveryAttempts)
      // schedule future cleanup when the message expires
      MessageScheduler.scheduleExpiredMessageCleanup(this, internalMessage)
      // waiting if necessary for space to become available
      //todo figure out the right IO-way to put things in a queue, probably something from fs2.
      queue.putLast(internalMessage)
      Log.debug(s"After send to $name - blockingQueue contains ${queue.size} messages")
    }
    /**
     * Always do AWS SQS-style long polling.
     * Be sure your code can handle receiving the same message twice.
     *
     * @return Some message before the timeout, or None
     */
    override def receiveIO(timeout: Duration): IO[Option[Message]] = {
      val deadline: Long = System.currentTimeMillis() + timeout.toMillis
      // poll the first message from the blocking deque
      val blockingQueue: BlockingDeque[InternalMessage] = blockingQueuePool.getOrElse(name, throw QueueDoesNotExistException(this))
      IO(Log.debug(s"Before receive from $name - blockingQueue contains ${blockingQueue.size} messages")) *>
        IO.interruptibleMany(Option(
          blockingQueue.pollFirst(deadline - System.currentTimeMillis(), TimeUnit.MILLISECONDS)
        )).
        map{internalMessageOpt =>
          if (internalMessageOpt.isEmpty) Log.debug(s"No message available from queue $name after $timeout")
          internalMessageOpt.map {
            toMessage(this, _)
          }
        }
    }

    //todo this may not be used in current code - keep in case we get a streaming API over http
    override def receiveStream(): Stream[IO, Message] = {
      Log.debug(s"Starting stream from queue $name")

      //things on the client side need to be responsible for endless queues, so
      //I don't think there's a need to stop if the blockingQueue is deleted from the blockingQueuePool
      val blockingQueue: BlockingDeque[InternalMessage] = blockingQueuePool.getOrElse(name, throw QueueDoesNotExistException(this))

      //this doesn't have to be amazingly efficient. It's OK to burn one thread on the call for now.
      //stream out from the blocking queue until it is empty
      //if it is empty, wait for something to show up and stream some more

      //If the message never makes it to the receiver then it is "mostly ok".
      // The delivery attempt will never be completed and the message will be redeilvered soon.
      //todo maybe replace blockingQueue with an FS2 stream
      Stream.repeatEval(
        IO.interruptibleMany(blockingQueue.takeFirst()).map(toMessage(this,_))
      )
    }
  }

  //todo dead letter queue for all messages SHRINE-2261
  case class LocalMessage(deliveryAttemptId: DeliveryAttemptId, millisecondsToComplete:Long, remainingAttempts:Int, contents: String) extends Message {
    def toJson: String = MessageAsJson(deliveryAttemptId,millisecondsToComplete,remainingAttempts,contents).asJsonText

    override def completeIO(): IO[Unit] =
      LocalMessageQueueMiddleware.completeMessage(deliveryAttemptId)
  }

  object LocalMessage {

    def apply(messageAsJson:MessageAsJson): LocalMessage = new LocalMessage(
      deliveryAttemptId = messageAsJson.deliveryAttemptId,
      millisecondsToComplete = messageAsJson.millisecondsToComplete,
      contents = messageAsJson.contents,
      remainingAttempts = messageAsJson.remainingAttempts
    )
  }

  private case class DeliveryAttempt(message: InternalMessage, createdTime: Long, fromQueue: LocalQueue)

  case class InternalMessage(id: Long, contents: String, createdTime: Long, toQueue: LocalQueue, remainingAttempts:Int) {
    // internalMessage changes when it is redelivered, InternalMessage s with different remainingAttempts can be equal.
    override def equals(obj: scala.Any): Boolean = {
      obj match {
        case other: InternalMessage =>
          other.canEqual(this) && other.id == this.id
        case _ => false
      }
    }

    override def hashCode(): Int = {
      id.hashCode()
    }
  }

  private case class MessageRedeliveryRunner(deliveryAttemptID: DeliveryAttemptId, deliveryAttempt: DeliveryAttempt, messageMaxDeliveryAttempts: Long) extends Runnable {
    override def run(): Unit = {
      try {
        messageDeliveryAttemptMap.get(deliveryAttemptID).fold(
                  Log.debug(s"Could not find deliveryAttempt for message ${deliveryAttempt.message.contents} from queue ${deliveryAttempt.fromQueue}")
        ) { deliveryAttemptAndFutureTask: (DeliveryAttempt, Option[ScheduledFuture[_]])  =>
          // get the queue that the message was from, and push message back to the head of the deque
          val blockingQueue = blockingQueuePool.getOrElse(deliveryAttemptAndFutureTask._1.fromQueue.name,
            throw QueueDoesNotExistException(deliveryAttemptAndFutureTask._1.fromQueue))
          // waiting if necessary for space to become available
          val message = deliveryAttemptAndFutureTask._1.message.copy(remainingAttempts = deliveryAttemptAndFutureTask._1.message.remainingAttempts - 1)
          blockingQueue.putFirst(message)
          Log.debug(s"Redelivered message ${message.id} to ${message.toQueue}")
        }
      } catch {
        case i: InterruptedException => Log.error("Scheduled message redelivery was interrupted", i)
        case t: TimeoutException => Log.error(s"Messages can't be redelivered due to timeout", t)
        case e: Throwable => Log.error(s"""${e.getClass.getSimpleName} "${e.getMessage}" caught""", e)
      }
    }
  }

  private case class CleanDeliveryAttemptAndInternalMessageRunner(queue: LocalQueue, messageId: Long, messageTimeToLiveInMillis: Long) extends Runnable {
    override def run(): Unit = {
      val currentTime: Long = System.currentTimeMillis()
      try {
        Log.debug(s"About to clean up outstanding messages. DAMap size: ${messageDeliveryAttemptMap.size}")
        // clean out the ability to cancel this
        messageIdsToExpirationCleanupTasks.remove(messageId)

        // cleans up deliveryAttempt map
        for ((id: DeliveryAttemptId, deliveryAttemptAndFutureTask: (DeliveryAttempt, Option[ScheduledFuture[_]])) <- messageDeliveryAttemptMap){
          if ((currentTime - deliveryAttemptAndFutureTask._1.message.createdTime) >= messageTimeToLiveInMillis) {
            messageDeliveryAttemptMap.remove(id, deliveryAttemptAndFutureTask)
            // cancels its future message redelivery task
            MessageScheduler.cancelScheduledMessageRedelivery(deliveryAttemptAndFutureTask._2)
          }
        }
        Log.debug(s"Outstanding deliveryAttempts that exceed $messageTimeToLiveInMillis milliseconds have been cleaned from the map. " +
          s"DAMap size: ${messageDeliveryAttemptMap.size}")

        val blockingQueue: BlockingDeque[InternalMessage] = blockingQueuePool.getOrElse(queue.name,
          throw QueueDoesNotExistException(queue))

        //remove any message older than messageTimeToLiveMillis
        @tailrec
        def cleanExpired(): Unit = {
          val peeked = Option(blockingQueue.peek())

          if(peeked.exists(currentTime - _.createdTime >= messageTimeToLiveInMillis)) {
            blockingQueue.remove(peeked.get)
            Log.debug(s"${peeked.get.id} removed from $queue because it exceeded time to live $messageTimeToLiveInMillis millis")
            cleanExpired()
          }
        }
        cleanExpired()
      } catch {
        case NonFatal(x) => CleaningUpDeliveryAttemptandInternalMessageProblem(queue, messageTimeToLiveInMillis, x)
        case i: InterruptedException => Log.error("Scheduled expired message cleanup was interrupted", i)
        case t: TimeoutException => Log.error(s"Expired Messages can't be cleaned due to timeout", t)
        case e: Throwable => Log.error(s"""${e.getClass.getSimpleName} "${e.getMessage}" caught""", e)
      }
    }
  }

  private object MessageScheduler {

    import java.util.concurrent.ThreadFactory

    private object LoggingUncaughtExceptionHandler extends Thread.UncaughtExceptionHandler {
      override def uncaughtException(t: Thread, e: Throwable): Unit = {
        Log.error(s"""Thread $t terminated due to ${e.getClass.getSimpleName}, "${e.getMessage}" caught by the default exception handler""", e)
      }
    }

    private class CaughtExceptionsThreadFactory extends ThreadFactory {

      override def newThread(r: Runnable): Thread = {
        val t = new Thread(r)
        t.setDaemon(true)
        t.setUncaughtExceptionHandler(LoggingUncaughtExceptionHandler)
        t
      }
    }

    private val scheduler: ScheduledExecutorService = {
      val s = new ScheduledThreadPoolExecutor(1,new CaughtExceptionsThreadFactory)
      //Remove tasks to clean up expiring messages when messages are delivered.
      //Otherwise they will sit around taking up memory for 4 days.
      s.setRemoveOnCancelPolicy(true)
      s
    }

    def scheduleMessageRedelivery(deliveryAttemptID: DeliveryAttemptId, deliveryAttempt: DeliveryAttempt, messageRedeliveryDelay: Long, messageMaxDeliveryAttempts: Int): Unit = {
      val messageRedeliveryRunner: MessageRedeliveryRunner = MessageRedeliveryRunner(deliveryAttemptID, deliveryAttempt, messageMaxDeliveryAttempts)
      try {
        val remainingAttempts = deliveryAttempt.message.remainingAttempts
        if (remainingAttempts > 0) {
          Log.debug(s"Scheduling message delivery attempt ${(messageMaxDeliveryAttempts - remainingAttempts) +1}, redeliver message in $messageRedeliveryDelay milliseconds.")
          val futureTask: ScheduledFuture[_] = scheduler.schedule(messageRedeliveryRunner, messageRedeliveryDelay, TimeUnit.MILLISECONDS)
          // update the DAMap with new DAID, DA, and scheduled future redelivery task
          messageDeliveryAttemptMap.update(deliveryAttemptID, (deliveryAttempt, Some(futureTask)))
        } else {
          Log.debug(s"Not scheduling message redelivery because $messageMaxDeliveryAttempts attempts have not been acknowledged.")
          // update the DAMap with new DAID, DA, and None (no scheduled future redelivery task)
          messageDeliveryAttemptMap.update(deliveryAttemptID, (deliveryAttempt, None))
        }
      } catch {
        case NonFatal(x) => SchedulingMessageRedeliverySentinelProblem(messageRedeliveryDelay, x)
      }
    }
    def scheduleExpiredMessageCleanup(queue: LocalQueue, messageToBeRemoved: InternalMessage):Unit = {
      val cleanDeliveryAttemptAndInternalMessageRunner: CleanDeliveryAttemptAndInternalMessageRunner = CleanDeliveryAttemptAndInternalMessageRunner(queue, messageToBeRemoved.id, messageTimeToLiveInMillis)
      try {
        Log.debug(s"Starting the sentinel scheduler that cleans outstanding internal message in" +
          s" queue ${messageToBeRemoved.toQueue} exceeds message expiration time: $messageTimeToLiveInMillis")
        messageIdsToExpirationCleanupTasks.update(
          messageToBeRemoved.id,
          scheduler.schedule(cleanDeliveryAttemptAndInternalMessageRunner, messageTimeToLiveInMillis, TimeUnit.MILLISECONDS)
        )
      } catch {
        case NonFatal(x) => SchedulingCleanUpSentinelProblem(queue, messageTimeToLiveInMillis, x) //todo I don't think this needs to be here
      }
    }

    def cancelScheduledMessageRedelivery(futureTask: Option[ScheduledFuture[_]]): Unit = {
      // returns false if the task could not be cancelled, typically because it has already completed normally;
      futureTask.fold(Log.info("No scheduled future task to cancel"))(f => f.cancel(true))
    }

    def cancelExpiredMessageCleanup(futureTask: ScheduledFuture[_]): Unit = {
      // returns false if the task could not be cancelled, typically because it has already completed normally;
      futureTask.cancel(true)
    }

    def shutDown(): util.List[Runnable] = {
      scheduler.shutdownNow()
    }
  }
}

/**
  * If the configuration is such that MessageQueue should have been started use this object to stop it
  */
object LocalMessageQueueStopper {

  def stop(): Unit = {
    //a lot less interesting without MessageQueue - not a big deal to stop schedulers that were never started , maybe nothing to do.
    LocalMessageQueueMiddleware.stop()
  }

}

case class CleaningUpDeliveryAttemptandInternalMessageProblem(queue: LocalMessageQueueMiddleware.LocalQueue, timeOutInMillis: Long, x:Throwable) extends AbstractProblem(ProblemSources.Hub) {
  override def logLevel: Level = Level.ERROR

  override val throwable: Option[Throwable] = Some(x)

  override def summary: String = s"""The Hub encountered an exception while trying to
                                    |cleanup messages that has been outstanding for more
                                    |than $timeOutInMillis milliseconds in queue ${queue.name}. """.stripMargin

  override def description: String = s"""The Hub encountered an exception while trying
                                        |to cleanup messages that has been outstanding
                                        |for more than $timeOutInMillis milliseconds in queue ${queue.name}
                                        |on Thread ${Thread.currentThread().getName}: ${x.getMessage}""".stripMargin
}

case class SchedulingCleanUpSentinelProblem(queue: LocalMessageQueueMiddleware.LocalQueue, timeOutInMillis: Long, x:Throwable) extends AbstractProblem(ProblemSources.Hub) {
  override def logLevel: Level = Level.ERROR
  override val throwable: Option[Throwable] = Some(x)

  override def summary: String = s"""The Hub encountered an exception while trying to
                                    |schedule a sentinel that cleans up outstanding messages
                                    |exceed $timeOutInMillis milliseconds in queue ${queue.name}.""".stripMargin

  override def description: String = s"""The Hub encountered an exception while trying to
                                        |schedule a sentinel that cleans up outstanding messages
                                        |exceed $timeOutInMillis milliseconds  in queue ${queue.name}
                                        |on Thread ${Thread.currentThread().getName}: ${x.getMessage}""".stripMargin
}

case class SchedulingMessageRedeliverySentinelProblem(messageRedeliveryDelay: Long, x:Throwable) extends AbstractProblem(ProblemSources.Hub) {
  override def logLevel: Level = Level.ERROR
  override val throwable: Option[Throwable] = Some(x)

  override def summary: String = s"""The Hub encountered an exception while trying to
                                    |schedule a sentinel that redelivers an incomplete message after $messageRedeliveryDelay milliseconds""".stripMargin

  override def description: String = s"""The Hub encountered an exception while trying to
                                        |schedule a sentinel that redelivers an incomplete message after $messageRedeliveryDelay
                                        |milliseconds on Thread ${Thread.currentThread().getName}: ${x.getMessage}""".stripMargin
}

case class QueueDoesNotExistException(queueName: LocalMessageQueueMiddleware.LocalQueue)
  extends Exception(s"Queue ${queueName.name} not found on server.")

case class MessageDoesNotExistAndCannotBeCompletedException(id: DeliveryAttemptId) extends Exception(
  s"""Message does not exist and cannot be completed!
     |Message might have already been completed or expired!""".stripMargin)

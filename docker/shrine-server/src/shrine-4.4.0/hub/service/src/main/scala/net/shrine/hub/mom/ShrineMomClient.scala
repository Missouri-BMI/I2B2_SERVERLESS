package net.shrine.hub.mom

import java.util.concurrent.TimeUnit
import cats.effect.IO
import ch.qos.logback.classic.Level
import com.typesafe.config.Config
import fs2.Stream
import net.shrine.http4s.catsio.{LazyIO, RetryIO}
import net.shrine.hub.data.client.HubClient
import net.shrine.hub.data.store.HubDb
import net.shrine.log.Loggable
import net.shrine.messagequeueservice.{Message, MessageQueueService, MomQueue}
import net.shrine.problem.{AbstractProblem, ProblemSources}
import net.shrine.protocol.version.v2.{Network, Node}
import net.shrine.protocol.version.{Envelope, EnvelopeContents, EnvelopeContentsCompanion, Id, JsonText, MomQueueName, NodeId, ProtocolVersion}

import scala.concurrent.duration.FiniteDuration
import scala.util.Try
import scala.util.control.NonFatal
import net.shrine.config.{ConfigExtensions, ConfigSource}
import net.shrine.messagequeueclient.{AwsSqsMessageQueueClient, KafkaMessageQueueClient}
import cats.effect.std.Dispatcher

import scala.concurrent.Future

/**
  * Common MOM operations in Shrine.
  */

object ShrineMomClient extends Loggable {

  private lazy val networkIO:IO[Network] = LazyIO("networkIO"){
    info(s"Getting the network")
    if(ConfigSource.config.getBoolean("shrine.hub.create"))
      HubDb.db.selectTheNetworkIO //if we are on the hub machine - or in the network setup tool - get the Network from the database
    else HubClient.getNetworkIO //else get the network from the hub
  }

  //todo consider adding createQueue, createQueues, and deleteQueue, and making serviceIO private
  private[hub] lazy val serviceIO:IO[MessageQueueService] = LazyIO("serviceIO"){
    networkIO.flatMap(serviceIOFromNetwork)
  }

  private[hub] def serviceIOFromNetwork(network:Network):IO[MessageQueueService] = {
    info(s"Network is $network")
    (network.awsSqsConfig, network.kafkaConfig) match {
      case (None, None) =>
        import scala.reflect.runtime.universe.runtimeMirror
        val momClassNameKey = "shrine.hub.messagequeue.implementation"
        val momClassName = ConfigSource.config.getString(momClassNameKey)
        info(s"""Using $momClassName system from ConfigSource "$momClassNameKey"""")
        val classLoaderMirror = runtimeMirror(getClass.getClassLoader)
        val module = classLoaderMirror.staticModule(momClassName)
        IO(classLoaderMirror.reflectModule(module).instance.asInstanceOf[MessageQueueService])

      case (Some(awsSqsConfig), None) => IO(AwsSqsMessageQueueClient(awsSqsConfig))
      case (None, Some(kafkaConfig)) => IO(KafkaMessageQueueClient(kafkaConfig))
      case (Some(_), Some(_)) => IO.raiseError(new IllegalStateException("This network is configured to use both AWS and Kafka"))
    }
  }

  private val hubMomConfig: Config = ConfigSource.config.getConfig("shrine.hub.mom")
  private val retryDelay: FiniteDuration = hubMomConfig.getFiniteDuration("retryDelay")
  private val momSendAttempts:Option[Int] = {
    val attempts = hubMomConfig.getInt("momSendAttempts")
    if (attempts > 0) Option(attempts)
    else None
  }

  private val retryIfTaskThrows: Throwable => Boolean = {case OkToRetry(_) => true}

  /**
    * Uses the hub's database directly to find the node. Only use from the hub.
    */
  private[hub] def sendToNodeIO(
                    subjectId: Id,
                    envelopeContents: EnvelopeContents,
                    envelopeContentsCompanion: EnvelopeContentsCompanion, //todo automatically grab the companion
                    logString: String,
                    nodeId: NodeId
                  ): IO[Unit] = {
    HubDb.db.selectNodeIO(nodeId).flatMap { node: Option[Node] =>
      sendIO(subjectId, envelopeContents, envelopeContentsCompanion, logString, node.get.momQueueName)
    }
  }

  /**
   * Uses the hub's database directly to find the node. Only use from the hub.
   */
  private[hub] def sendToNodeIO(
                                 subjectId: Id,
                                 envelopeContents: EnvelopeContents,
                                 envelopeContentsCompanion: EnvelopeContentsCompanion, //todo automatically grab the companion
                                 logString: String,
                                 queueName: MomQueueName
                               ): IO[Unit] = {
      sendIO(
        subjectId,
        envelopeContents,
        envelopeContentsCompanion,
        logString,
        queueName
      )
  }

  def sendToHubIO(
                   subjectId: Id,
                   envelopeContents: EnvelopeContents,
                   envelopeContentsCompanion: EnvelopeContentsCompanion, //todo automatically grab the companion
                   logString: String
                 ): IO[Unit] = {
    //get the hub's Queue and send the result to the hub
    networkIO.flatMap { network =>
      sendIO(subjectId, envelopeContents, envelopeContentsCompanion, logString, network.hubQueueName)
    }
  }

  private def sendIO(
              subjectId: Id,
              envelopeContents: EnvelopeContents,
              envelopeContentsCompanion: EnvelopeContentsCompanion, //todo automatically grab the companion
              logString: String,
              queueName: MomQueueName
            ): IO[Unit] = {
    val envelope: Envelope = Envelope(envelopeContentsCompanion.envelopeType, subjectId.underlying, envelopeContents.asJsonText.underlying)

    val sIO: IO[Unit] = for {
      _ <- IO(info(s"Will send $logString to $queueName"))
      service <- serviceIO
      queue <- service.getQueueIO(queueName)
      _ <- queue.sendIO(envelope.asJsonText.underlying,envelope.contentsSubject)
      _ <- IO(info(s"Sent $logString to $queueName full message is ${envelope.asJsonText.underlying}"))
    } yield ()

    momSendAttempts.fold{
      RetryIO.keepTrying(task = sIO, delay = retryDelay, retryIfTaskThrows,logString = s"send $subjectId to $queueName until success")
    }{attempts =>
      RetryIO.keepTryingBounded(task = sIO, delay = retryDelay, maxRetries = attempts, retryIfTaskThrows,logString = s"send $subjectId to $queueName attempt $attempts times")
    }
  }

  private def receiveUntilStopIO(queueNameIO: IO[MomQueueName], dispatch: Envelope => IO[Boolean]): IO[Unit] = {

    val queueNameStream: Stream[IO, MomQueueName] = Stream.eval(IO.cede.flatMap(_ => queueNameIO))

    val queueStream: Stream[IO, MomQueue] = queueNameStream.flatMap { queueName =>
      info(s"Dispatcher for $queueName started")
      Stream.eval(serviceIO.flatMap(_.getQueueIO(queueName)))
    }
    val dispatchedStream: Stream[IO, Unit] = queueStream.flatMap { queue: MomQueue => queue.receiveStream() }.
      flatMap((message: Message) => openEnvelopeAndDispatch(message, dispatch))

    val streamToRun: IO[Unit] = RetryIO.keepTrying(
        task = dispatchedStream.compile.drain,
        delay = retryDelay, retryIfTaskThrows,
        logString = s"receiveUntilStop"
      ).map(_ => debug(s"End of receive message stream"))
      .handleErrorWith(t => IO(error("Exception while running dispatch", t)))
    streamToRun
  }

  type Cancel = () => Future[Unit]
  def receiveUntilStop(queueNameIO: IO[MomQueueName], dispatch: Envelope => IO[Boolean],dispatcher: Dispatcher[IO]): Cancel = {
    dispatcher.unsafeRunCancelable(receiveUntilStopIO(queueNameIO, dispatch))
  }

  private def openEnvelopeAndDispatch(message: Message, dispatch: Envelope => IO[Boolean]): Stream[IO, Unit] = {
    debug(s"Message delivery id ${message.deliveryAttemptId} to be dispatched to $dispatch")

    val envelopeTry: Try[Envelope] = Envelope.tryRead(new JsonText(message.contents))

    val envelope = envelopeTry.get
    info(s"Envelope holds a ${envelope.protocolVersion} ${envelope.contentsType}")

    val dispatchIO: IO[Boolean] = envelope.protocolVersion match {
      case v: ProtocolVersion if v == net.shrine.protocol.version.v2.versionId => dispatch(envelope)
      case v: ProtocolVersion if v == net.shrine.protocol.version.v1.versionId =>
        // handle past versions of shrine just like the current case after warning
        warn(s"Envelope contents version $v is not tested with this version of shrine.")
        dispatch(envelope)
      case v: ProtocolVersion =>
        // handle the future case just like the current case after warning
        warn(s"Envelope contents version $v is not tested with this version of shrine. Attempting to read it with ${net.shrine.protocol.version.v2.versionId}")
        dispatch(envelope)
    }

    val timeLimit = FiniteDuration.apply(message.millisecondsToComplete, TimeUnit.MILLISECONDS)
    val shouldCompleteIO: IO[Boolean] = dispatchIO.timeoutTo(timeLimit, {
      IO(warn(s"openEnvelopeAndDispatch of message ${message.deliveryAttemptId} did not complete in $timeLimit")).flatMap(_ => IO(false))
    })

    val completeIO: IO[Unit] = shouldCompleteIO.flatMap { shouldComplete =>
      if (shouldComplete) message.completeIO().map(_ => debug(s"message ${message.deliveryAttemptId} completed"))
      else IO(debug(s"Message ${message.deliveryAttemptId} not completed"))
    }.handleErrorWith[Unit] {
      case NonFatal(t) => IO {
        ExceptionDuringDispatch(t, envelope)
      }
    }.flatMap(_ => IO.cede)

    val concurrentDispatchIO:IO[Unit] = completeIO.start //start processing each message concurrently
      .flatMap(_ => IO.unit) //no need to hang onto the Fibers

    Stream.eval(concurrentDispatchIO)
  }
}

//noinspection ScalaFileName
case class ExceptionDuringDispatch(x: Throwable, envelope: Envelope) extends AbstractProblem(ProblemSources.Commons) {
  override def logLevel: Level = Level.ERROR
  override val throwable: Option[Throwable] = Some(x)
  override val summary: String = s"Exception while dispatching ${envelope.briefString}"
  override val description: String = s"Exception while dispatching ${envelope.briefString}"
}
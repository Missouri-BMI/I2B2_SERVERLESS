package net.shrine.http4s.catsio

import cats.data.NonEmptyList
import cats.effect.IO
import cats.effect.std.AtomicCell
import fs2.Stream
import fs2.concurrent.SignallingRef
import net.shrine.log.Loggable

import scala.concurrent.duration.FiniteDuration
import scala.util.control.NonFatal

/**
 * Execute IO's periodically. Interface loosely based off Monix's Scheduler.
 */

trait RepeatedIOTask {

  /**
   * Stop all executions of the currently scheduled task immediately.
   */
  def stopIO(): IO[Unit]

  /**
   * Start the task with the specified parameters.
   */
  def startIO(): IO[Unit]
}

object RepeatedIOTask extends Loggable {

  /**
   * Execute all IO's in tasks concurrently using the given ContextShift, and wait for all results, returning Unit.
   */

  def startInParallelAndWaitForAll(tasks: IO[_]*): IO[Unit] = {
    info(s"Start of startInParallelAndWaitForAll with ${tasks.size}")
    import cats.implicits.catsSyntaxParallelSequence1
    val result = NonEmptyList.fromList(tasks.toList).fold(IO.unit) {
      _.parSequence *> IO.unit
    }
    info(s"End of startInParallelAndWaitForAll with ${tasks.size}")
    result
  }

  /**
   * Execute task once after the initial delay, and subsequently after interval,
   * until task returns false or the scheduler is stopped by the stop() method.
   */
  def scheduleWithFixedDelay(initialDelay: FiniteDuration,
                             interval: FiniteDuration,
                             task: () => IO[Boolean], //return true to keep going
                             name: String): RepeatedIOTask =
    new RepeatedIOTaskImpl(initialDelay, interval, task, name)

  /**
   * Execute task indefinitely, ignoring output of task, until stop or restart methods are called.
   */
  def scheduleIndefinitely(initialDelay: FiniteDuration,
                           interval: FiniteDuration,
                           task: () => IO[_],
                           name: String): RepeatedIOTask =
    scheduleWithFixedDelay(initialDelay, interval, () => task().flatMap(_ => IO(true)), name)


  /**
   * Scheduler to perform a task repeatedly until the stop condition(s).
   * Will stop when either the stop() method is called, or the task returns false.
   *
   * @param name         The name of the scheduled service.
   * @param initialDelay The time to wait before performing the first execution of task.
   * @param interval     The time to wait between executions of task
   * @param task         The task to be performed. If task should be performed again, return true, else return false.
   */
  private class RepeatedIOTaskImpl(initialDelay: FiniteDuration,
                                   interval: FiniteDuration,
                                   task: () => IO[Boolean],
                                   name: String)
    extends RepeatedIOTask {

    // Should you need to add back the restart capability then you will need to change the stored signalling ref on start.
    // If you decide never to do that then you no longer need the atomic cell, and just need a reference to the signalling ref.
    private val atomicCell: AtomicCell[IO,Option[SignallingRef[IO, Boolean]]] = {
      import cats.effect.unsafe.implicits.global //todo someday go full cats effect and eliminate this import
      AtomicCell[IO].of[Option[SignallingRef[IO, Boolean]]](None).unsafeRunSync()
    }

    override def startIO(): IO[Unit] = {
      val starterIO: IO[Unit] = for{
        _ <- stopIO()
        _ <- IO(info(s"Started $name"))
        _ <- repeatWithSignal()
      } yield ()
      starterIO.start.map(_ => info(s"Ready to start $name"))
    }

    override def stopIO(): IO[Unit] = {
      atomicCell.get.flatMap {maybeSwitch: Option[SignallingRef[IO, Boolean]] =>
        maybeSwitch.map {switch: SignallingRef[IO, Boolean] =>
          for{
            _ <- IO(info(s"About to stop $name"))
            _ <- switch.set(true)
          } yield ()
        }.getOrElse{IO(info(s"$name not running"))}
      }
    }

    private def repeatWithSignal(): IO[Unit] = {
      def wrappedTask(): IO[Unit] = {
        task().flatMap { keepGoing =>
          if (!keepGoing) stopIO()
          else IO.unit
        }.handleErrorWith {
          case NonFatal(x) =>
            error(s"Caught exception in $name", x)
            IO.raiseError(x)
        }
      }

      def repeater(): Stream[IO, Unit] = Stream.repeatEval{
        wrappedTask()
      }.metered(interval) //todo maybe debounce instead

      val switchCreatorIO: IO[SignallingRef[IO, Boolean]] = SignallingRef[IO, Boolean](false).flatMap { s: SignallingRef[IO, Boolean] =>
        atomicCell.set( Option(s))
          .map(_ => s)
      }

      val interruptable: Stream[IO, Unit] = for {
        switch <- Stream.eval(switchCreatorIO)
        _ <- repeater().interruptWhen(switch)
      } yield()

      //todo metered doesn't start until after the initial delay plus its own interval - fix it to respect the initial delay with SHRINE2020-1218
      IO.sleep(initialDelay).flatMap(_ => interruptable.compile.drain) *>
        atomicCell.get.map(_ => info(s"End of $name"))
    }
  }
}

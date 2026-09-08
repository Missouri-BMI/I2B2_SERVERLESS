package net.shrine.http4s.catsio

import cats.effect.IO
import net.shrine.log.Loggable

import scala.concurrent.duration.FiniteDuration
import scala.language.postfixOps
import scala.util.control.NonFatal
import cats.effect.Temporal

/**
  * Enumerate the _actually_ useful ways to retry IOs. 
  */
object RetryIO extends Loggable {

  def keepTrying[A](task: IO[A],
                    delay:FiniteDuration,
                    retryIfTaskThrows: Throwable => Boolean = NonFatal(_),
                    logString:String
                   ): IO[A] = {
    info(s"keepTrying $logString started")
    new IORetry[A](logString,task,None,delay,retryIfTaskThrows).startIO()
  }

  def keepTryingBounded[A](task: IO[A],
                           delay:FiniteDuration,
                           maxRetries: Int,
                           retryIfTaskThrows: Throwable => Boolean = NonFatal(_),
                           logString: String
                          ): IO[A] = {
    info(s"keepTryingBounded $logString started")
    new IORetry[A](logString,task,Option(maxRetries),delay,retryIfTaskThrows).startIO()
  }

  private class IORetry[A](
                            name: String,
                            task: IO[A],
                            maxRetries:Option[Int],
                            delay: FiniteDuration,
                            retryIfTaskThrows: Throwable => Boolean
                          ) extends Loggable {

    def startIO(): IO[A] = {
      for {
        _ <- IO(info(s"Attempting $name"))
        a <- attempt(1)
        _ <- IO(info(s"Completed $name"))
      } yield a
    }

    private def attempt(count: Int): IO[A] = {
      val logString = s"$name attempt $count of ${maxRetries.fold("unlimited")(_.toString)} attempts"

      def again: Boolean = maxRetries.forall(_ >= count)

      IO(info(s"Start of $logString")).flatMap { _ =>
        task.handleErrorWith {
          case x if retryIfTaskThrows(x) => //try again despite survivable exceptions
            info(s"Caught ${x.getClass.getSimpleName} during $logString", x)
            if (again) {
              IO.sleep(delay).flatMap(_ => attempt(count + 1))
            } else IO.raiseError(x)
          case z => //raise this unanticipated exception and give up
            error(s"Caught ${z.getClass.getSimpleName}. Will not attempt $name again.", z)
            IO.raiseError(z)
        }
      }.map{a:A =>
        info(s"$logString succeeded")
        a
      }
    }
  }
}

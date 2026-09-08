package net.shrine.http4s.catsio

import cats.effect.IO
import org.junit.jupiter.api.Test

import scala.concurrent.duration.FiniteDuration

class RealRepeatedIOTaskTest {

  import cats.effect.unsafe.implicits.global

  @Test
  def testReallyIndefinitely(): Unit = {
    object TestTask {
      def printThing(): IO[Unit] = IO {
        println(System.currentTimeMillis())
      }
    }
    val task = RepeatedIOTask.scheduleIndefinitely(
      initialDelay = FiniteDuration(0, "milliseconds"),
      interval = FiniteDuration(500, "milliseconds"),
      task = () => TestTask.printThing(),
      name = "testReallyIndefinitely"
    )

    task.startIO().unsafeRunSync()
    Thread.sleep(5000L)
    task.stopIO().unsafeRunSync()

  }
}

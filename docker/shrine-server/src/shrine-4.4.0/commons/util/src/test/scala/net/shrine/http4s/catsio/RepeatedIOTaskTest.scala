package net.shrine.http4s.catsio

import cats.effect.IO
import cats.effect.testkit.TestControl
import net.shrine.http4s.catsio.RepeatedIOTaskTest.appendTask

import scala.collection.mutable.ListBuffer
import scala.concurrent.duration.FiniteDuration
import org.junit.jupiter.api.Assertions.{assertEquals, assertTrue}
import org.junit.jupiter.api.TestInstance.Lifecycle
import org.junit.jupiter.api.{BeforeEach, Disabled, Test, TestInstance}

@TestInstance(Lifecycle.PER_CLASS)
@Disabled //todo reenable after the cats IO upgrade see SHRINE2020-1439
/**
 * Read https://typelevel.org/cats-effect/docs/core/test-runtime to understand how these tests work
 */
class RepeatedIOTaskTest {

  import cats.effect.unsafe.implicits.global

  val output: ListBuffer[Int] = ListBuffer()

  @BeforeEach
  def resetOut(): Unit = {
     output.clear()
  }

  @Test
  def testNoDelayOnce(): Unit = {
    val task: RepeatedIOTask = RepeatedIOTask.scheduleWithFixedDelay(
      name = "testNoDelayOnce",
      initialDelay = RepeatedIOTaskTest.noDelay,
      interval = RepeatedIOTaskTest.shortDelay,
      task = appendTask(1)(output)
    )

    TestControl.execute(task.startIO()).flatMap { control =>
      for {
        _ <- control.tick
        firstInterval <- control.nextInterval
        _ <- IO(assertEquals(RepeatedIOTaskTest.shortDelay,firstInterval))
        _ <- IO(assertEquals(ListBuffer.empty,output))
        _ <- control.advanceAndTick(RepeatedIOTaskTest.shortDelay)
        _ <- IO(assertEquals(ListBuffer(0),output))
        _ <- control.advanceAndTick(RepeatedIOTaskTest.shortDelay)
        _ <- IO(assertEquals(ListBuffer(0),output))
        results <- control.results
        _ <- IO(assertTrue(results.isDefined))
        _ <- IO(assertTrue(results.get.isSuccess))
      } yield ()
    }.unsafeRunSync()
  }

  @Test
  def testNoDelayMany(): Unit = {
    val howMany = 3

    val task: RepeatedIOTask = RepeatedIOTask.scheduleWithFixedDelay(
      name = "testNoDelayMany",
      initialDelay = RepeatedIOTaskTest.noDelay,
      interval = RepeatedIOTaskTest.shortDelay,
      task = appendTask(howMany)(output)
    )

    TestControl.execute(task.startIO()).flatMap { control =>
      for {
        _ <- control.tick
        firstInterval <- control.nextInterval
        _ <- IO(assertEquals(RepeatedIOTaskTest.shortDelay,firstInterval))
        _ <- IO(assertEquals(ListBuffer.empty,output))
        _ <- control.advanceAndTick(RepeatedIOTaskTest.shortDelay)
        _ <- IO(assertEquals(ListBuffer(0),output))
        _ <- control.advanceAndTick(RepeatedIOTaskTest.shortDelay)
        _ <- IO(assertEquals(ListBuffer(0,1),output))
        _ <- control.advanceAndTick(RepeatedIOTaskTest.shortDelay)
        _ <- IO(assertEquals(ListBuffer(0,1,2),output))
        _ <- control.advanceAndTick(RepeatedIOTaskTest.shortDelay)
        _ <- IO(assertEquals(ListBuffer(0,1,2),output))

        results <- control.results
        _ <- IO(assertTrue(results.isDefined))
        _ <- IO(assertTrue(results.get.isSuccess))
        _ <- IO(assertEquals(ListBuffer(0,1,2),output))
      } yield ()
    }.unsafeRunSync()
  }

  @Test
  def testStop(): Unit = {
    val howMany = 3

    val task: RepeatedIOTask = RepeatedIOTask.scheduleWithFixedDelay(
      name = "testStop",
      initialDelay = RepeatedIOTaskTest.noDelay,
      interval = RepeatedIOTaskTest.shortDelay,
      task = appendTask(howMany)(output)
    )

    TestControl.execute(task.startIO()).flatMap { control =>
      for {
        _ <- control.tick
        firstInterval <- control.nextInterval
        _ <- IO(assertEquals(RepeatedIOTaskTest.shortDelay,firstInterval))
        _ <- IO(assertEquals(ListBuffer.empty,output))
        _ <- control.advanceAndTick(RepeatedIOTaskTest.shortDelay)
        _ <- IO(assertEquals(ListBuffer(0),output))
        _ <- control.advanceAndTick(RepeatedIOTaskTest.shortDelay)
        _ <- IO(assertEquals(ListBuffer(0,1),output))
        _ <- task.stopIO()
        _ <- control.tick
        _ <- control.advanceAndTick(RepeatedIOTaskTest.shortDelay)
        _ <- IO(assertEquals(ListBuffer(0,1),output))

        results <- control.results
        _ <- IO(assertTrue(results.isDefined))
        _ <- IO(assertTrue(results.get.isSuccess))
        _ <- IO(assertEquals(ListBuffer(0,1),output))
      } yield ()
    }.unsafeRunSync()
  }
}

object RepeatedIOTaskTest {
  private val noDelay: FiniteDuration = FiniteDuration(0, "milliseconds")
  private val shortDelay: FiniteDuration = FiniteDuration(500, "milliseconds")

  // Simulate some side-effecting operation
  def appendTask(limit: Integer): ListBuffer[Int] => () => IO[Boolean] = {
    var elem = 0
    l: ListBuffer[Int] => () => IO {
      l += elem
      elem = elem + 1
      elem < limit
    }
  }
}
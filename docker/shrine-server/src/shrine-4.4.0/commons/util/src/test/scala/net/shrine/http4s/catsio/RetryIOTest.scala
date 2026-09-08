package net.shrine.http4s.catsio

import cats.effect.IO
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.{Disabled, Test}

import java.io.{ByteArrayOutputStream, PrintStream}
import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.language.postfixOps
import cats.effect.Temporal

class RetryIOTest {
  import RetryIOTest._

  import cats.effect.unsafe.implicits.global
  @Test
  def testBase(): Unit = {
    assertSucceeds(RetryIO.keepTrying(finalResult,delay = 0 seconds,logString = "testBase"))
  }

  @Test
  def testRetries(): Unit = {
    assertSucceeds(throwNTimes(0, finalResult))
    // Always tries once, then retries up to maxRetries times
    assertTriesNTimes(1, () => assertSucceeds(throwAndRetry(0, 0, 0 seconds)))
    assertTriesNTimes(2, () => assertSucceeds(throwAndRetry(1, 1, 0 seconds)))
    assertTriesNTimes(2, () => assertSucceeds(throwAndRetry(1, 2, 0 seconds)))
    assertTriesNTimes(4, () => assertSucceeds(throwAndRetry(3, 5, 0 seconds)))

    assertTriesNTimes(1, () =>
      assertThrowsRuntimeException{
        throwAndRetry(1, 0, 0 seconds)
    })

    assertTriesNTimes(10, () =>
      assertThrowsRuntimeException {
        throwAndRetry(10, 9, 0 seconds)
    })
  }

  @Test
  def testRetriesWithDelay(): Unit = {
    assertTakesAtLeast(delay * 1, () => assertSucceeds(throwAndRetry(1, 1, delay)))
    assertTakesAtLeast(delay * 2, () => assertSucceeds(throwAndRetry(3, 3, delay)))
    assertTakesAtLeast(delay * 1, () => assertSucceeds(throwAndRetry(1, 3, delay)))

    assertTakesAtLeast(delay * 2, () =>
      assertTriesNTimes(3, () =>
        assertThrowsRuntimeException{
          throwAndRetry(3, 2, delay)
        }
      )
    )
  }

  @Test
  @Disabled
  def testUnretriableException(): Unit = {
    assertThrowsInterruptedException {
      RetryIO.keepTrying(IO.raiseError(new InterruptedException()),delay=0 seconds, logString ="testUnretriableException")
    }

    assertThrowsInterruptedException{
      RetryIO.keepTryingBounded(
        task = throwNTimes(2, finalResult).flatMap(_ => IO.raiseError(new InterruptedException())),
        delay = 0 seconds,
        maxRetries = 2,
        logString = "testUnretriableException"
      )
    }
  }

  @Test
  def testKeepTryingBounded():Unit = {
    assertSucceeds(throwNTimes(0, finalResult))

    assertThrowsRuntimeException{
      RetryIO.keepTryingBounded(
        task = throwNTimes(3, finalResult),
        delay = 1 seconds,
        maxRetries = 2,
        retryIfTaskThrows = retryIfTaskThrows,
        logString = "testKeepTryingBounded"
      )
    }

    RetryIO.keepTryingBounded(task = throwNTimes(2, finalResult), delay = 0 seconds, maxRetries = 2, retryIfTaskThrows,"testKeepTryingBounded").unsafeRunSync()


  }

}

object RetryIOTest {
  import org.junit.Assert

  import cats.effect.unsafe.implicits.global
  val delay: FiniteDuration = 100 milliseconds
  val retryIfTaskThrows: Throwable => Boolean = {case _: RuntimeException => true}

  val counter = new AtomicInteger(0)
  val finalResult: IO[Boolean] = IO{counter.incrementAndGet(); true}

  def throwAndRetry(throws: Int, retries: Int, delay: FiniteDuration)(implicit timer: Temporal[IO]): IO[Boolean] = {
    RetryIO.keepTryingBounded(
      task = throwNTimes(throws, finalResult),
      delay = delay,
      maxRetries = retries,
      retryIfTaskThrows = retryIfTaskThrows,
      logString = "throwAndRetry"
    )
  }

  def assertThrowsRuntimeException(io: IO[Boolean]): Unit = {
    assertThrows(classOf[RuntimeException], () => io.unsafeRunSync())
  }

  def assertThrowsInterruptedException(io: IO[Boolean]): Unit = {
    assertThrows(classOf[InterruptedException], () => io.unsafeRunSync())
  }

  def throwNTimes(n: Int, result: IO[Boolean]): IO[Boolean] = {
    var times = 0
    result.map{ a =>
      if (times < n) {
        times += 1
        throw new RuntimeException()
      } else a
    }
  }

  def assertTakesAtLeast(duration: FiniteDuration, thunk: () => Unit): Unit = {
    val start = System.currentTimeMillis
    thunk()
    val timeTaken = System.currentTimeMillis - start
    Assert.assertTrue(timeTaken >= duration.toMillis)
  }

  def assertTriesNTimes(n: Int, thunk: () => Unit): Unit = {
    counter.set(0)
    thunk()
    Assert.assertEquals(n, counter.get())
    counter.set(0)
  }

  def assertSucceeds(io: IO[Boolean]): Unit = io.map(a => Assert.assertEquals(true, a)).unsafeRunSync()
}
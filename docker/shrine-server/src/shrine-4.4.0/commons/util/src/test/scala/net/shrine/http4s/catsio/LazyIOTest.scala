package net.shrine.http4s.catsio

import cats.effect.IO
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals

import java.util.concurrent.atomic.AtomicInteger

class LazyIOTest {

  import cats.effect.unsafe.implicits.global

  @Test
  def testHappensOnlyOnce() = {
    val counter = new AtomicInteger(0)
    val lazyIO: IO[String] = LazyIO("testSingleLazyEvaluation"){
      counter.incrementAndGet()
      IO("test")
    }

    lazyIO.unsafeRunSync()
    assertEquals(1,counter.get())

    lazyIO.unsafeRunSync()
    assertEquals(1,counter.get())
  }

}

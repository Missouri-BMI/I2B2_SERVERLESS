package net.shrine.http4s.catsio

import org.junit.jupiter.api.{AfterAll, BeforeAll}

import java.io.{ByteArrayOutputStream, PrintStream}

/**
  * Redirect stdout to a print stream accessible directly by tests.
  */
trait LoggingTest {
  final val out: ByteArrayOutputStream = new ByteArrayOutputStream()
  final val originalOut: PrintStream = System.out

  @BeforeAll
  def before(): Unit = {
    System.setOut(new PrintStream(out))
  }

  @AfterAll
  def after(): Unit = {
    System.setOut(originalOut)
  }
}

package net.shrine.log

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.TestInstance.Lifecycle
import org.junit.jupiter.api.{AfterAll, AfterEach, BeforeAll, Test, TestInstance}

import java.io.{ByteArrayOutputStream, PrintStream}

/**
  * @author david
  * @since 8/4/15
  */
@TestInstance(Lifecycle.PER_CLASS)
class CustomPatternLayoutTest extends Loggable {

  val out = new ByteArrayOutputStream
  val originalOut: PrintStream = System.out

  @BeforeAll
  def beforeClass(): Unit = {
    System.setOut(new PrintStream(out))
  }

  @AfterEach
  def afterEach(): Unit = {
    out.reset()
  }

  @AfterAll
  def afterClass(): Unit = {
    System.setOut(originalOut)
  }

  def countMatches(container: String, find: String): Int = {
    val tempString = container.replaceAll(find, "")
    (container.length - tempString.length) / find.length
  }

  val i2b2PasswordString = """<password is_token="false" token_ms_timeout="1800000">kapow</password>"""
  val expectedI2b2PasswordString = """<password is_token="false" token_ms_timeout="1800000">REDACTED</password>"""

  @Test
  def testCensorI2b2Message():Unit = {
    info(i2b2PasswordString)
    val result = out.toString

    assert(result.contains(expectedI2b2PasswordString))
    assertEquals(countMatches(result, expectedI2b2PasswordString), 1)
  }

  @Test
  def testCensorI2b2Throwable():Unit = {
    val exception = new IllegalArgumentException(i2b2PasswordString)

    error("I2B2 sent back some strange xml", exception)
    val result = out.toString

    assert(result.contains(expectedI2b2PasswordString))
    assertEquals(countMatches(result, expectedI2b2PasswordString), 1)
  }

  @Test
  def testCensorI2b2ThrowableWithCause():Unit = {
    val exception = new IllegalArgumentException(i2b2PasswordString)
    exception.initCause(new RuntimeException(i2b2PasswordString))

    error("I2B2 sent back some strange xml", exception)
    val result = out.toString

    assertEquals(countMatches(result, expectedI2b2PasswordString), 2)
  }
}
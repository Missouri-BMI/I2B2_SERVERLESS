package net.shrine.xml

import net.shrine.log.Log
import net.shrine.util.Tries
import org.junit.jupiter.api.Assertions.{assertEquals, assertTrue}
import org.junit.jupiter.api.Test

import scala.util.{Failure, Success, Try}

/**
 * @author clint
 * @since Oct 30, 2012
 */
final class UtilTest {

  @Test
  def testParseXmlTime():Unit = {
    import net.shrine.xml.XmlDateHelper.parseXmlTime
    
    assertTrue(parseXmlTime("").isFailure)
    assertTrue(parseXmlTime(null).isFailure)
    assertTrue(parseXmlTime("asdlaflaksdfhlasdfh12345").isFailure)
    
    val lexicalRep = "2013-11-26T17:22:34.728-05:00"
    
    val Success(parsed) = parseXmlTime(lexicalRep)
    
    assertEquals(parsed.toString,lexicalRep)
    
    val now = XmlDateHelper.now
    
    val Success(parsedNow) = parseXmlTime(now.toString)
    
    assertEquals(parsedNow,now)
  }

  private val x = 123
  
  private val e = new Exception with scala.util.control.NoStackTrace
  
  @Test
  def testSequenceOption():Unit = {
    import Tries.sequence
    
    assertEquals(sequence(Some(Success(x))),Success(Some(x)))
    assertEquals(sequence(Some(Failure(e))),Failure(e))
    
    assertEquals(sequence(None),Success(None))
  }
  
  @Test
  def testSequenceTraversable():Unit = {
    import Tries.sequence
    
    val y = 234
    val z = 43985
    
    assertEquals(sequence(List(Success(x))),Success(List(x)))
    assertEquals(sequence(Seq(Failure(e))),Failure(e))

    assertEquals(sequence(Vector(Success(x), Try(y), Try(z))),Success(Vector(x, y, z)))

    assertEquals(sequence(Vector(Failure(e), Try(y), Try(z))),Failure(e))
    assertEquals(sequence(Vector(Try(x), Failure(e), Try(z))),Failure(e))
    assertEquals(sequence(Vector(Try(x), Try(y), Failure(e))),Failure(e))
    
    val f = new Exception with scala.util.control.NoStackTrace

    assertEquals(sequence(Seq(Try(x), Failure(e), Failure(f))),Failure(e))
    assertEquals(sequence(Seq(Failure(f), Failure(e))),Failure(f))

    assertEquals(sequence(Nil),Success(Nil))
  }
  
  @Test
  def testOptionToTry(): Unit = {
    import Tries.toTry

    //noinspection NotImplementedCode
    assertEquals(toTry(Some(123))(???),Try(123))
    
    val exception = new Exception

    assertEquals(toTry(None)(exception),Failure(exception))
  }
}
package net.shrine.xml

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

import scala.concurrent.duration.FiniteDuration
import scala.xml.Elem

/**
 * @author clint
 * @since Feb 21, 2014
 */
final class DurationEnrichmentsTest {
  import net.shrine.xml.DurationEnrichments._

  import scala.concurrent.duration.{Duration, DurationInt}
  
  val fiveMinutes: FiniteDuration = 5.minutes
  
  val fiveMinutesXml: Elem = <duration><value>5</value><unit>MINUTES</unit></duration>
  
  @Test 
  def testToXml():Unit = {
    assertEquals(fiveMinutes.toXml.toString,fiveMinutesXml.toString)
  }
  
  @Test
  def testFromXml():Unit = {
    assertEquals(Duration.fromXml(fiveMinutesXml).get,fiveMinutes)
  }
  
  @Test
  def testRoundTrip():Unit = {
    assertEquals(Duration.fromXml(fiveMinutes.toXml).get,fiveMinutes)
  }
}
package net.shrine.xml

import org.junit.jupiter.api.Assertions.{assertEquals, assertFalse, assertTrue}
import org.junit.jupiter.api.Test

import javax.xml.datatype.XMLGregorianCalendar

/**
 * @author clint
 * @since Feb 21, 2014
 */
final class XmlGcEnrichmentsTest {
  import net.shrine.xml.XmlGcEnrichments._

  import scala.concurrent.duration.DurationInt
  
  private def toMillis(xmlGc: XMLGregorianCalendar): Long = xmlGc.toGregorianCalendar.getTimeInMillis
  
  private val now = XmlDateHelper.now
  
  private val plusOneSecond = now + 1.second
  
  private val minusOneSecond = now + (-1).second
  
  @Test
  def testPlus():Unit = {
    assertTrue(plusOneSecond.compare(now) > 0)
    
    val delta = toMillis(plusOneSecond) - toMillis(now)
    
    assertEquals(delta,1000L)
  }
  
  @Test
  def testPlusNegative():Unit= {
    assertTrue(minusOneSecond.compare(now) < 0)
    
    val delta = toMillis(minusOneSecond) - toMillis(now)
    
    assertEquals(delta,-1000L)
  }
  
  @Test
  def testComparators():Unit = {
    assertTrue(plusOneSecond > now)
    assertTrue(plusOneSecond > minusOneSecond)
    assertFalse(plusOneSecond > plusOneSecond)
    
    assertTrue(plusOneSecond >= now)
    assertTrue(plusOneSecond >= minusOneSecond)
    assertTrue(plusOneSecond >= plusOneSecond)
    
    assertTrue(minusOneSecond < now)
    assertTrue(minusOneSecond < plusOneSecond)
    assertFalse(minusOneSecond < minusOneSecond)
    
    assertTrue(minusOneSecond <= now )
    assertTrue(minusOneSecond <= plusOneSecond)
    assertTrue(minusOneSecond <= minusOneSecond)
  }
}
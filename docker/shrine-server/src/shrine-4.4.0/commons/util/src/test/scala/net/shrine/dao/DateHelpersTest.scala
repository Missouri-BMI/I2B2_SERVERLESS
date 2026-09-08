package net.shrine.dao

import javax.xml.datatype.XMLGregorianCalendar
import net.shrine.xml.XmlDateHelper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

import java.util.Date

/**
 * @author clint
 * @since Nov 1, 2012
 */
final class DateHelpersTest {
  val xmlNow: XMLGregorianCalendar = XmlDateHelper.now
  
  val javaUtilNow: Date = xmlNow.toGregorianCalendar.getTime
    
  val sqlNow = new java.sql.Timestamp(javaUtilNow.getTime)
  
  @Test
  def testToXmlGc():Unit = {
    val xmlNow = DateHelpers.toXmlGc(sqlNow)
    assertEquals(sqlNow.getTime,xmlNow.toGregorianCalendar.getTime.getTime)
  }
  
  @Test
  def testToTimestamp():Unit = {
    assertEquals(sqlNow,DateHelpers.toTimestamp(xmlNow))
  }
  
  @Test
  def testTimestampXmlGcRoundTrip():Unit = {
    val roundTripped = DateHelpers.toXmlGc(DateHelpers.toTimestamp(xmlNow)) 
    assertEquals(xmlNow,roundTripped)

    def millis(xmlGc: XMLGregorianCalendar): Long = xmlGc.toGregorianCalendar.getTime.getTime
    assertEquals(millis(xmlNow),millis(roundTripped))
  }
}
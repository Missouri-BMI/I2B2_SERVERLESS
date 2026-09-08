package net.shrine.dao

import java.sql.Timestamp

import javax.xml.datatype.XMLGregorianCalendar
import net.shrine.xml.{XmlDateHelper, XmlGcEnrichments}

/**
 * @author clint
 * @date Oct 16, 2012
 */
object DateHelpers {
  def toTimestamp(xmlGc: XMLGregorianCalendar): Timestamp = {
    new java.sql.Timestamp(xmlGc.toGregorianCalendar.getTime.getTime)
  }

  def toTimestamp(milliseconds: Long): Timestamp = {
    new java.sql.Timestamp(milliseconds)
  }

  def toXmlGc(date: java.sql.Timestamp): XMLGregorianCalendar = {
    XmlDateHelper.toXmlGregorianCalendar(new java.util.Date(date.getTime))
  }

  def toXmlGc(milliseconds: Long): XMLGregorianCalendar = {
    XmlDateHelper.toXmlGregorianCalendar(milliseconds)
  }
}
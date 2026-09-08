package net.shrine.xml

import javax.xml.datatype.XMLGregorianCalendar

import scala.concurrent.duration.Duration

/**
 * @author clint
 * @since Dec 21, 2012
 */
object XmlGcEnrichments {
  final implicit class EnrichedXmlGc(val xmlGc: XMLGregorianCalendar) extends AnyVal {
    def +(duration: Duration): XMLGregorianCalendar = {
      val xmlDuration = XmlDateHelper.datatypeFactory.newDuration(duration.toMillis)

      val copy = XmlDateHelper.datatypeFactory.newXMLGregorianCalendar(xmlGc.toXMLFormat)

      copy.add(xmlDuration)

      copy
    }

    def <(other: XMLGregorianCalendar): Boolean = xmlGc.compare(other) < 0

    def >(other: XMLGregorianCalendar): Boolean = xmlGc.compare(other) > 0

    def >=(other: XMLGregorianCalendar): Boolean = xmlGc > other || xmlGc == other

    def <=(other: XMLGregorianCalendar): Boolean = xmlGc < other || xmlGc == other
  }
}

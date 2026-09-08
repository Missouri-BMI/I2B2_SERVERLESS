package net.shrine.adapter.dao.model

import javax.xml.datatype.XMLGregorianCalendar

/**
 * @author clint
 * @date Sep 26, 2013
 */
final case class CountData(
    obfuscatedValue: Long,
    startDate: XMLGregorianCalendar, 
    endDate: XMLGregorianCalendar)
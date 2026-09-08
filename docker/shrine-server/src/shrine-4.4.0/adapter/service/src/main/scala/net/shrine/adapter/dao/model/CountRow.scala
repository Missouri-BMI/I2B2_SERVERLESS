package net.shrine.adapter.dao.model

import javax.xml.datatype.XMLGregorianCalendar

/**
 * @author clint
 * @import net.shrine.adapter.dao.model.ResultRow
date Oct 16, 2012
 * 
 * NB: Can't be final, since Squeryl runs this class through cglib to make a synthetic subclass :(
 */
final case class CountRow(
    id: Int,
    resultId: Int,
    obfuscatedValue: Long,
    creationDate: XMLGregorianCalendar) extends ResultRow(id, resultId) with HasResultId
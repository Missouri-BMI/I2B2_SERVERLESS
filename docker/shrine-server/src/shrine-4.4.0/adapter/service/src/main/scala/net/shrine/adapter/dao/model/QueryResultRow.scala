package net.shrine.adapter.dao.model

import net.shrine.protocol.i2b2.ResultOutputType
import net.shrine.protocol.i2b2.QueryResult.StatusType
import javax.xml.datatype.XMLGregorianCalendar

/**
 * @author clint
 * @date Oct 16, 2012
 * 
 * NB: Can't be final, since Squeryl runs this class through cglib to make a synthetic subclass :(
 */
final case class QueryResultRow(
    id: Int,
    localId: Long,
    queryId: Int,
    resultType: ResultOutputType,
    status: StatusType,
    elapsed: Option[Long], //Will be None for error results
    lastUpdated: XMLGregorianCalendar)
package net.shrine.adapter.i2b2Protocol

import net.shrine.protocol.i2b2.QueryResult

import javax.xml.datatype.XMLGregorianCalendar

/**
 * @author Bill Simons
 * @since 4/2/12
 * @see http://cbmi.med.harvard.edu
 * @see http://chip.org
 *       <p/>
 *       NOTICE: This software comes with NO guarantees whatsoever and is
 *       licensed as Lgpl Open Source
 * @see http://www.gnu.org/licenses/lgpl.html
  *
  * @param endDate - will be None if the query has not yet finished in a ReadQueryInstanceResponse .
  */
final case class QueryInstance (
                                  queryInstanceId: String,
                                  queryMasterId: String,
                                  userId: String,
                                  groupId: String,
                                  startDate: XMLGregorianCalendar,
                                  endDate: Option[XMLGregorianCalendar],
                                  queryStatus:QueryResult.StatusType
                               ){
 
  def withId(newId: String): QueryInstance = this.copy(queryInstanceId = newId)

  override def hashCode: Int =  41 * (41 + queryInstanceId.hashCode) + queryMasterId.hashCode

  override def equals(other: Any): Boolean  = {
    other match {
      case that: QueryInstance =>
        queryInstanceId == that.queryInstanceId &&
        queryMasterId == that.queryMasterId
      case _ => false
    }
  }
}

object QueryInstance {
  def apply(
             queryInstanceId: String,
             queryMasterId: String,
             userId: String,
             groupId: String,
             startDate: XMLGregorianCalendar,
             endDate: XMLGregorianCalendar,
             queryStatus:QueryResult.StatusType
           ): QueryInstance = new QueryInstance(queryInstanceId, queryMasterId, userId, groupId, startDate, Some(endDate),queryStatus:QueryResult.StatusType)
}
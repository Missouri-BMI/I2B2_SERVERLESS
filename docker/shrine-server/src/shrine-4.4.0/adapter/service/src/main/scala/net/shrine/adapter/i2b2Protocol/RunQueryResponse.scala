package net.shrine.adapter.i2b2Protocol

import net.shrine.protocol.i2b2.QueryResult
import net.shrine.protocol.i2b2.query.I2b2QueryDefinition

import javax.xml.datatype.XMLGregorianCalendar

/**
 * @author Bill Simons
 * @since 4/15/11
 * @see http://cbmi.med.harvard.edu
 * 
 * NB: this is a case class to get a structural equality contract in hashCode and equals, mostly for testing
 */
final case class RunQueryResponse(
                                   override val queryId: Long,
                                   override val createDate: XMLGregorianCalendar,
                                   override val userId: String,
                                   override val groupId: String,
                                   override val requestXml: I2b2QueryDefinition,
                                   override val queryInstanceId: Long,
                                   singleNodeResult: QueryResult) extends AbstractRunQueryResponse("runQueryResponse", queryId, createDate, userId, groupId, requestXml, queryInstanceId) {

  override val results: Seq[QueryResult] = Seq(singleNodeResult)
  
  def withResult(res: QueryResult): RunQueryResponse = this.copy(singleNodeResult = res)
}

object RunQueryResponse extends AbstractRunQueryResponse.Companion[RunQueryResponse]
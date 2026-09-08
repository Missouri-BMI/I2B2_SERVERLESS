package net.shrine.adapter.dao.model

import net.shrine.protocol.i2b2.QueryResult

import scala.xml.NodeSeq

/**
 * @author clint
 * @since Oct 16, 2012
 * 
 */
final case class ShrineError(id: Int, resultId: Int, message: String, codec:String, stampText:String, summary:String, digestDescription:String,detailsXml:NodeSeq) extends HasResultId {
  def toQueryResult: QueryResult = {
    QueryResult.errorResult(Option(message), QueryResult.StatusType.Error.name, codec,stampText, summary, digestDescription, detailsXml)
  }
}

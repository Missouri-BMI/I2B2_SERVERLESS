package net.shrine.adapter.i2b2Protocol

import net.shrine.protocol.i2b2.QueryResult

/**
 * @author clint
 * @date Nov 2, 2012
 */
final case class ReadQueryResultResponse(
    override val queryId: Long, singleNodeResult: QueryResult) extends AbstractReadQueryResultResponse("readQueryResultResponse", queryId) {
  
  override def results = Seq(singleNodeResult)
}

object ReadQueryResultResponse
  extends AbstractReadQueryResultResponse.Companion((id, results) => new ReadQueryResultResponse(id, results.head))

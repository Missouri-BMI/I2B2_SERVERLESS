package net.shrine.adapter.i2b2Protocol

import net.shrine.protocol.i2b2.{QueryResult, ResultOutputType}
import net.shrine.xml.XmlUtil

import scala.xml.NodeSeq

/**
 * @author clint
 * @date Dec 4, 2012
 */
abstract class AbstractReadQueryResultResponse(
    rootTagName: String,
    val queryId: Long) extends ShrineResponse with HasQueryResults with NonI2b2ableResponse {

  override def toXml: NodeSeq = XmlUtil.stripWhitespace {
    XmlUtil.renameRootTag(rootTagName) {
      <placeHolder>
        <queryId>{ queryId }</queryId>
        <results>{ results.map(_.toXml) }</results>
      </placeHolder>
    }
  }
}

object AbstractReadQueryResultResponse {
  abstract class Companion[R](makeResponse: (Long, Seq[QueryResult]) => R) {
    def fromXml(breakdownTypes: Set[ResultOutputType])(xml: NodeSeq): R = {
      val queryId = (xml \ "queryId").text.toLong
      val results = (xml \ "results" \ "queryResult").map(QueryResult.fromXml(breakdownTypes))

      makeResponse(queryId, results)
    }
  }
}
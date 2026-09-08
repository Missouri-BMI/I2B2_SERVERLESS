package net.shrine.adapter.i2b2Protocol

import net.shrine.adapter.{DoneButHasNoResultsForQueuedQuery, NoResultsForQueuedQuery}
import net.shrine.protocol.i2b2.QueryResult
import net.shrine.xml.XmlUtil

import scala.util.control.NonFatal
import scala.xml.NodeSeq

/**
 * @author Bill Simons
 * @since 4/13/11
 * @see http://cbmi.med.harvard.edu
 * @see http://chip.org
 *       <p/>
 *       NOTICE: This software comes with NO guarantees whatsoever and is
 *       licensed as Lgpl Open Source
 * @see http://www.gnu.org/licenses/lgpl.html
 *
 * NB: this is a case class to get a structural equality contract in hashCode and equals, mostly for testing
 *
 * !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
 * NOTE: Now that the adapter caches/stores results from the CRC, Instead of an
 * i2b2 instance id, this class now contains the Shrine-generated, network-wide
 * id of a query, which was used to obtain results previously obtained from the
 * CRC from Shrine's datastore.
 * !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
 */
final case class ReadInstanceResultsResponse(
    /*
     * !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
     * NOTE: Now that the adapter caches/stores results from the CRC, Instead of an
     * i2b2 instance id, this class now contains the Shrine-generated, network-wide 
     * id of a query, which is used to obtain results previously obtained from the 
     * CRC from Shrine's datastore.
     * !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
     */
    shrineNetworkQueryId: Long,
    results: Seq[QueryResult]
  ) extends ShrineResponse with HasQueryResults { //AbstractReadInstanceResultsResponse("readInstanceResultsResponse", shrineNetworkQueryId)


  //NB: Set QueryResults' instanceIds to the query id of the enclosing response
  private def resultsWithNetworkQueryId = results.map(_.withInstanceId(shrineNetworkQueryId))

  override protected def i2b2MessageBody: NodeSeq = XmlUtil.stripWhitespace {
    <ns5:response xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:type="ns5:result_responseType">
      <status>
        <condition type="DONE">DONE</condition>
      </status>{resultsWithNetworkQueryId.map(_.toI2b2)}
    </ns5:response>
  }

  override def toXml: NodeSeq = XmlUtil.stripWhitespace {
    XmlUtil.renameRootTag("readInstanceResultsResponse") {
      <placeHolder>
        <shrineNetworkQueryId>
          {shrineNetworkQueryId}
        </shrineNetworkQueryId>
        <queryResults>
          {resultsWithNetworkQueryId.map(_.toXml)}
        </queryResults>
      </placeHolder>
    }
  }
}
object ReadInstanceResultsResponse {
  def fromI2b2(nodeSeq: NodeSeq,shrineNetworkQueryId:Long): Either[ErrorResponse,ReadInstanceResultsResponse] = {
    import net.shrine.protocol.i2b2.ResultOutputType.breakdownTypes

    try {
      val results = (nodeSeq \ "message_body" \ "response" \ "query_result_instance").map(QueryResult.fromI2b2(breakdownTypes))
      Right(ReadInstanceResultsResponse(shrineNetworkQueryId, results))
    } catch {
      case NonFatal(e) =>
        try {
          val condition: NodeSeq = nodeSeq \ "message_body" \ "response" \ "status" \ "condition"
          if (condition.text == "DONE") Left(ErrorResponse(DoneButHasNoResultsForQueuedQuery(XmlUtil.prettyPrint(nodeSeq.head).trim)))
          else Left(ErrorResponse(NoResultsForQueuedQuery(XmlUtil.prettyPrint(nodeSeq.head).trim,Option(e))))
        }
        catch {
          case NonFatal(x) => Left(ErrorResponse(NoResultsForQueuedQuery(XmlUtil.prettyPrint(nodeSeq.head).trim,Option(x))))
        }
    }
  }
}
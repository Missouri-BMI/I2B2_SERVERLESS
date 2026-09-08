package net.shrine.adapter.i2b2Protocol

import net.shrine.protocol.i2b2.{AuthenticationInfo, BaseShrineRequest, RequestType, ResultOutputType, ShrineXmlUnmarshaller}
import net.shrine.xml.{NodeSeqEnrichments, XmlUtil}

import scala.concurrent.duration.Duration
import scala.util.Try
import scala.xml.NodeSeq

/**
 * @author clint
 * @since Nov 2, 2012
 */
final case class ReadQueryResultRequest(
                                        projectId: String,
                                        waitTime: Duration,
                                        authn: AuthenticationInfo,
                                        queryId: Long
                                       ) extends BaseShrineRequest {
  
  override val requestType = RequestType("GetQueryResult")

  override def toXml: NodeSeq = XmlUtil.stripWhitespace(
    <readQueryResult>
      <projectId>{ projectId }</projectId>
      <waitTimeMs>{ waitTime.toMillis }</waitTimeMs>
      { authn.toXml }
      <queryId>{ queryId }</queryId>
    </readQueryResult>)
}

object ReadQueryResultRequest extends ShrineXmlUnmarshaller[ReadQueryResultRequest] with ShrineRequestUnmarshaller {
  override def fromXml(breakdownTypes: Set[ResultOutputType])(xml: NodeSeq): Try[ReadQueryResultRequest] = {
    import NodeSeqEnrichments.Strictness._
    
    for {
      waitTime <- shrineWaitTime(xml)
      authn <- shrineAuthenticationInfo(xml)
      queryId <- xml.withChild("queryId").map(_.text.toLong)
      projectId <- shrineProjectId(xml)
    } yield {
      ReadQueryResultRequest(projectId, waitTime, authn, queryId)
    }
  }
}
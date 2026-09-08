package net.shrine.adapter.i2b2Protocol

import net.shrine.adapter.i2b2Protocol
import net.shrine.protocol.i2b2.serialization.I2b2UnmarshallingHelpers
import net.shrine.protocol.i2b2.{AuthenticationInfo, I2b2XmlUnmarshaller, RequestType, ResultOutputType, ShrineRequest, ShrineXmlUnmarshaller}
import net.shrine.xml.{NodeSeqEnrichments, XmlUtil}

import scala.concurrent.duration.Duration
import scala.util.Try
import scala.xml.NodeSeq

/**
 * @author Bill Simons
 * @since 3/17/11
 * @see http://cbmi.med.harvard.edu
 * @see http://chip.org
 *       <p/>
 *       NOTICE: This software comes with NO guarantees whatsoever and is
 *       licensed as Lgpl Open Source
 * @see http://www.gnu.org/licenses/lgpl.html
 *
 * NB: this is a case class to get a structural equality contract in hashCode and equals, mostly for testing
 */
final case class ReadQueryInstancesRequest(
                                            override val projectId: String,
                                            override val waitTime: Duration,
                                            override val authn: AuthenticationInfo,
                                            i2b2MasterQueryId: Long //todo this should be a different ID - the one from the previous request - rename to i2b2 jargon
                                          ) extends ShrineRequest(projectId, waitTime, authn) with CrcRequest  {

  override val crcRequestType: Option[CrcRequestType] = Option(CrcRequestTypes.MasterRequestType)

  override val requestType: RequestType = RequestType(crcRequestType.get.name)

  override def toXml: NodeSeq = XmlUtil.stripWhitespace {
    <readQueryInstances>
      { headerFragment }
      <queryId>{ i2b2MasterQueryId }</queryId>
    </readQueryInstances>
  }

  protected override def i2b2MessageBody: NodeSeq = XmlUtil.stripWhitespace {
    <message_body>
      { i2b2PsmHeader }
      <ns4:request xsi:type="ns4:master_requestType" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
        <query_master_id>{ i2b2MasterQueryId }</query_master_id>
      </ns4:request>
    </message_body>
  }
}

object ReadQueryInstancesRequest extends I2b2XmlUnmarshaller[ReadQueryInstancesRequest] with ShrineXmlUnmarshaller[ReadQueryInstancesRequest] with ShrineRequestUnmarshaller with I2b2UnmarshallingHelpers {

  override def fromI2b2(breakdownTypes: Set[ResultOutputType])(nodeSeq: NodeSeq): Try[ReadQueryInstancesRequest] = {
    import NodeSeqEnrichments.Strictness._

    for {
      projectId <- i2b2ProjectId(nodeSeq)
      waitTime <- i2b2WaitTime(nodeSeq)
      authn <- i2b2AuthenticationInfo(nodeSeq)
      masterId <- (nodeSeq withChild "message_body" withChild "request" withChild "query_master_id").map(_.text.toLong)
    } yield {
      i2b2Protocol.ReadQueryInstancesRequest(
        projectId,
        waitTime,
        authn,
        masterId)
    }
  }

  override def fromXml(breakdownTypes: Set[ResultOutputType])(xml: NodeSeq): Try[ReadQueryInstancesRequest] = {
    import NodeSeqEnrichments.Strictness._

    for {
      waitTime <- shrineWaitTime(xml)
      authn <- shrineAuthenticationInfo(xml)
      queryId <- xml.withChild("queryId").map(_.text.toLong)
      projectId <- shrineProjectId(xml)
    } yield {
      ReadQueryInstancesRequest(projectId, waitTime, authn, queryId)
    }
  }
}
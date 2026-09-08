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
 *
 * !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
 * NOTE: Now that the adapter caches/stores results from the CRC, Instead of an
 * i2b2 instance id, this class now contains the Shrine-generated, network-wide
 * id of a query, which is used to obtain results previously obtained from the
 * CRC from Shrine's datastore.
 * !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
 */
final case class ReadInstanceResultsRequest(
                                             override val projectId: String,
                                             override val waitTime: Duration,
                                             override val authn: AuthenticationInfo,
                                             /*
                                              * !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
                                              * NOTE: Now that the adapter caches/stores results from the CRC, Instead of an
                                              * i2b2 instance id, this class now contains the Shrine-generated, network-wide
                                              * id of a query, which is used to obtain results previously obtained from the
                                              * CRC from Shrine's datastore.
                                              * !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
                                              *
                                              * But of course if you're using it to make a call to the CRC you need to use the query instant id from the CRC, not the network id.
                                              * In Shrine 2.0.0 and after this is only used to call the CRC.
                                              */
                                             queryInstanceId: Long
                                           ) extends ShrineRequest(projectId, waitTime, authn) with CrcRequest  {

  override val crcRequestType: Option[CrcRequestType] = Option(CrcRequestTypes.InstanceRequestType)

  override val requestType: RequestType = RequestType(crcRequestType.get.name)

  //not used in Shrine anymore
  override def toXml: NodeSeq = XmlUtil.stripWhitespace {
    <readInstanceResults>
      { headerFragment }
      <shrineNetworkQueryId>{ queryInstanceId }</shrineNetworkQueryId>
    </readInstanceResults>
  }

  def withId(id: Long): ReadInstanceResultsRequest = this.copy(queryInstanceId = id)

  def withProject(proj: String): ReadInstanceResultsRequest = this.copy(projectId = proj)

  def withAuthn(ai: AuthenticationInfo): ReadInstanceResultsRequest = this.copy(authn = ai)

  protected override def i2b2MessageBody: NodeSeq = XmlUtil.stripWhitespace {
    <message_body>
      { i2b2PsmHeader }
      <ns4:request xsi:type="ns4:instance_requestType" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
        <query_instance_id>{ queryInstanceId }</query_instance_id>
      </ns4:request>
    </message_body>
  }
}

object ReadInstanceResultsRequest extends I2b2XmlUnmarshaller[ReadInstanceResultsRequest] with ShrineXmlUnmarshaller[ReadInstanceResultsRequest] with ShrineRequestUnmarshaller with I2b2UnmarshallingHelpers {

  override def fromI2b2(breakdownTypes: Set[ResultOutputType])(nodeSeq: NodeSeq): Try[ReadInstanceResultsRequest] = {
    import NodeSeqEnrichments.Strictness._

    for {
      projectId <- i2b2ProjectId(nodeSeq)
      waitTime <- i2b2WaitTime(nodeSeq)
      authn <- i2b2AuthenticationInfo(nodeSeq)
      instanceId <- (nodeSeq withChild "message_body" withChild "request" withChild "query_instance_id").map(_.text.toLong)
    } yield {
      i2b2Protocol.ReadInstanceResultsRequest(
        projectId,
        waitTime,
        authn,
        instanceId)
    }
  }

  override def fromXml(breakdownTypes: Set[ResultOutputType])(xml: NodeSeq): Try[ReadInstanceResultsRequest] = {
    import NodeSeqEnrichments.Strictness._

    for {
      waitTime <- shrineWaitTime(xml)
      authn <- shrineAuthenticationInfo(xml)
      shrineNetworkQueryIdXml <- xml withChild "shrineNetworkQueryId"
      shrineNetworkQueryId <- Try(shrineNetworkQueryIdXml.text.toLong)
      projectId <- shrineProjectId(xml)
    } yield {
      ReadInstanceResultsRequest(projectId, waitTime, authn, shrineNetworkQueryId)
    }
  }
}
package net.shrine.adapter.i2b2Protocol

import net.shrine.protocol.i2b2.serialization.I2b2UnmarshallingHelpers
import net.shrine.protocol.i2b2.{AuthenticationInfo, I2b2XmlUnmarshaller, RequestHeader, RequestType, ResultOutputType, ShrineRequest, ShrineXmlUnmarshaller}
import net.shrine.xml.{NodeSeqEnrichments, XmlUtil}

import scala.concurrent.duration.Duration
import scala.util.Try
import scala.xml.NodeSeq

/**
 * @author clint
 * @since Aug 16, 2012
 */
final case class ReadResultRequest(
  override val projectId: String,
  override val waitTime: Duration,
  override val authn: AuthenticationInfo,
  localResultId: String) extends ShrineRequest(projectId, waitTime, authn) with CrcRequest {

  //NB: Needs to be a TranslatableRequest so that AbstractReadQueryResultAdapter doesn't have to manually 
  //add the correct projectId and credentials to requests (like this one) that it sends to the CRC. 

  def this(header: RequestHeader, localResultId: String) = this(header.projectId, header.waitTime, header.authn, localResultId)

  override val crcRequestType: Option[CrcRequestType] = Option(CrcRequestTypes.ResultRequestType)

  override val requestType: RequestType = RequestType(crcRequestType.get.name)

  //NB: This request is never sent through the broadcaster-aggregator/shrine service, so it doesn't make sense
  //to have it be handled by a ShrineRequestHandler.

  override protected def i2b2MessageBody: NodeSeq = XmlUtil.stripWhitespace {
    <message_body>
      { i2b2PsmHeader }
      <ns4:request xsi:type="ns4:result_requestType" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
        <query_result_instance_id>{ localResultId }</query_result_instance_id>
      </ns4:request>
    </message_body>
  }

  override def toXml: NodeSeq = XmlUtil.stripWhitespace {
    <readResult>
      { headerFragment }
      <resultId>{ localResultId }</resultId>
    </readResult>
  }

  def withAuthn(newAuthn: AuthenticationInfo): ReadResultRequest = this.copy(authn = newAuthn)

  def withProject(newProjectId: String): ReadResultRequest = this.copy(projectId = newProjectId)

}

object ReadResultRequest extends I2b2XmlUnmarshaller[ReadResultRequest] with ShrineXmlUnmarshaller[ReadResultRequest] with ShrineRequestUnmarshaller with I2b2UnmarshallingHelpers {
  override def fromXml(breakdownTypes: Set[ResultOutputType])(xml: NodeSeq): Try[ReadResultRequest] = {
    import NodeSeqEnrichments.Strictness._

    for {
      header <- shrineHeader(xml)
      //NB: This is the LOCAL, NOT NETWORK, resultId
      resultId <- xml.withChild("resultId").map(_.text)
    } yield {
      new ReadResultRequest(header, resultId)
    }
  }

  override def fromI2b2(breakdownTypes: Set[ResultOutputType])(xml: NodeSeq): Try[ReadResultRequest] = {
    import NodeSeqEnrichments.Strictness._

    for {
      header <- i2b2Header(xml)
      //NB: This is the LOCAL, NOT NETWORK, resultId
      resultId <- (xml withChild "message_body" withChild "request" withChild "query_result_instance_id").map(_.text)
    } yield {
      new ReadResultRequest(header, resultId)
    }
  }
}
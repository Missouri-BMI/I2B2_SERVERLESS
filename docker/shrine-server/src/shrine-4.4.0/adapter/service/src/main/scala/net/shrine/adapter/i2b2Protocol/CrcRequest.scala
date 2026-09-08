package net.shrine.adapter.i2b2Protocol

import net.shrine.protocol.i2b2.{ResultOutputType, ShrineRequest}
import net.shrine.xml.XmlUtil

import scala.util.Try
import scala.xml.NodeSeq

/**
 * @author clint
 * @since Aug 15, 2012
 */
trait CrcRequest {
  self: ShrineRequest =>
  def crcRequestType: Option[CrcRequestType] //option hack to support AIM3 protocol - needs psm header, but is not a CRC request

  def i2b2PsmHeaderWithDomain: NodeSeq = makeI2b2PsmHeader(Option(authn.domain))

  def i2b2PsmHeader: NodeSeq = makeI2b2PsmHeader(None)

  private def makeI2b2PsmHeader(domainOption: Option[String] = None): NodeSeq = XmlUtil.stripWhitespace {
    <ns4:psmheader>
      {domainOption match {
      case Some(_) => <user group={authn.domain} login={authn.username}>
        {authn.username}
      </user>
      case None => <user login={authn.username}>
        {authn.username}
      </user>
    }}<patient_set_limit>0</patient_set_limit>
      <estimated_time>0</estimated_time>{crcRequestType.map(rt => <request_type>
      {rt.i2b2RequestType}
    </request_type>).orNull}
    </ns4:psmheader>
  }
}

/**
 * @author clint
 * @since Aug 15, 2012
 */
object CrcRequest extends AbstractI2b2UnmarshallerCompanion[ShrineRequest with CrcRequest](
  Map(
    CrcRequestTypes.InstanceRequestType -> ReadInstanceResultsRequest,
    CrcRequestTypes.MasterRequestType -> ReadQueryInstancesRequest,
    CrcRequestTypes.QueryDefinitionRequestType -> RunQueryRequest,
    CrcRequestTypes.ResultRequestType -> ReadResultRequest)) {

  override def fromI2b2(breakdownTypes: Set[ResultOutputType])(i2b2Request: NodeSeq): Try[ShrineRequest with CrcRequest] = {
    if (isPsmRequest(i2b2Request)) {
      parsePsmRequest(breakdownTypes, i2b2Request)
    } else {
      scala.util.Failure(new Exception(s"Request not understood: $i2b2Request"))
    }
  }
}

object CrcRequestTypes {
  val InstanceRequestType: CrcRequestType = CrcRequestType("InstanceRequestType", "CRC_QRY_getQueryResultInstanceList_fromQueryInstanceId")

  val MasterRequestType: CrcRequestType = CrcRequestType("MasterRequestType", "CRC_QRY_getQueryInstanceList_fromQueryMasterId")

  val QueryDefinitionRequestType: CrcRequestType = CrcRequestType("QueryDefinitionRequestType", "CRC_QRY_runQueryInstance_fromQueryDefinition")

  val ResultRequestType: CrcRequestType = CrcRequestType("ResultRequestType", "CRC_QRY_getResultDocument_fromResultInstanceId")
}
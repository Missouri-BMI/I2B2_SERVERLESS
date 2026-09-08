package net.shrine.adapter.i2b2Protocol

import net.shrine.adapter.i2b2Protocol
import net.shrine.crypto.SecureRandomSource
import net.shrine.protocol.i2b2.serialization.XmlUnmarshaller
import net.shrine.protocol.i2b2.{AuthenticationInfo, BaseShrineRequest, ShrineMessage, ShrineRequest}
import net.shrine.xml.{NodeSeqEnrichments, XmlUtil}

import scala.util.Try
import scala.xml.NodeSeq

/**
 * @author Bill Simons
 * @since 4/5/11
 * @see http://cbmi.med.harvard.edu
 */
//todo only used in test cases. OK to delete when those are cleaned out - Maybe SHRINE-2384
final case class BroadcastMessage(
    requestId: Long, 
    networkAuthn: AuthenticationInfo, 
    request: BaseShrineRequest
                                 ) extends ShrineMessage {

  def withRequestId(id: Long): BroadcastMessage = this.copy(requestId = id)

  def withRequest(req: ShrineRequest): BroadcastMessage = this.copy(request = req)

  override def toXml: NodeSeq = XmlUtil.stripWhitespace {
    <broadcastMessage>
      <requestId>{ requestId }</requestId>
      { networkAuthn.toXml }
      <request>{ request.toXml }</request>
    </broadcastMessage>
  }
}

object BroadcastMessage extends XmlUnmarshaller[Try[BroadcastMessage]] {
  def apply(networkAuthn: AuthenticationInfo, request: BaseShrineRequest): BroadcastMessage = BroadcastMessage(SecureRandomSource.nextId(), networkAuthn, request)

  override def fromXml(xml: NodeSeq): Try[BroadcastMessage] = {
    import NodeSeqEnrichments.Strictness._
    
    for {
      id <- (xml withChild "requestId").map(_.text.toLong)
      authn <- (xml withChild AuthenticationInfo.shrineXmlTagName).flatMap(AuthenticationInfo.fromXml)
      reqXml <- xml withChild "request"
      req <- BaseShrineRequest.fromXml(Set.empty)(reqXml \ "_")
    } yield i2b2Protocol.BroadcastMessage(id, authn, req)
  }
}
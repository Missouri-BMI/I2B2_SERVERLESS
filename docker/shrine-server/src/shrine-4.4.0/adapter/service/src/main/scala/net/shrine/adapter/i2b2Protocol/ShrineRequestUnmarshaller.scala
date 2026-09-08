package net.shrine.adapter.i2b2Protocol

import net.shrine.protocol.i2b2.{AuthenticationInfo, RequestHeader, ResultOutputType, ShrineRequest}
import net.shrine.xml.NodeSeqEnrichments

import scala.concurrent.duration.Duration
import scala.util.Try
import scala.xml.NodeSeq

/**
 * @author Bill Simons
 * @since 3/9/11
 * @see http://cbmi.med.harvard.edu
 * @see http://chip.org
 *       <p/>
 *       NOTICE: This software comes with NO guarantees whatsoever and is
 *       licensed as Lgpl Open Source
 * @see http://www.gnu.org/licenses/lgpl.html
 */
object ShrineRequest {

  private type Unmarshaller[R] = Set[ResultOutputType] => NodeSeq => Try[R]

  def fromXml(breakdownTypes: Set[ResultOutputType])(xml: NodeSeq): Try[ShrineRequest] = {
    for {
      head <- Try(xml.head)
      tagName = head.label
      if shrineUnmarshallers.contains(tagName)
      unmarshal = shrineUnmarshallers(tagName)
      result <- unmarshal(breakdownTypes)(xml)
    } yield result
  }

  private val shrineUnmarshallers: Map[String, Unmarshaller[ShrineRequest]] = Map(
    "readInstanceResults" -> ReadInstanceResultsRequest.fromXml,
    "readQueryInstances" -> ReadQueryInstancesRequest.fromXml,
    "runQuery" -> RunQueryRequest.fromXml,
    "readResult" -> ReadResultRequest.fromXml
  )

  def hasMessageBodySubElement(xml: NodeSeq, tagName: String): Boolean = {
    (xml \ "message_body" \ tagName).nonEmpty
  }
}

/**
 * @author Bill Simons
 * @since 3/30/11
 * @see http://cbmi.med.harvard.edu
 * @see http://chip.org
 *       <p/>
 *       NOTICE: This software comes with NO guarantees whatsoever and is
 *       licensed as Lgpl Open Source
 * @see http://www.gnu.org/licenses/lgpl.html
 */
trait ShrineRequestUnmarshaller {
  final def shrineHeader(xml: NodeSeq): Try[RequestHeader] = {
    for {
      waitTime <- shrineWaitTime(xml)
      authn <- shrineAuthenticationInfo(xml)
      projectId <- shrineProjectId(xml)
    } yield RequestHeader(projectId, waitTime, authn)
  }

  import NodeSeqEnrichments.Strictness._

  final def shrineProjectId(xml: NodeSeq): Try[String] = xml.withChild("projectId").map(_.text)

  final def shrineWaitTime(xml: NodeSeq): Try[Duration] = {
    import scala.concurrent.duration.DurationLong

    xml.withChild("waitTimeMs").map(_.text.toLong.milliseconds)
  }

  final def shrineAuthenticationInfo(xml: NodeSeq): Try[AuthenticationInfo] = {
    xml.withChild(AuthenticationInfo.shrineXmlTagName).flatMap(AuthenticationInfo.fromXml)
  }
}
package net.shrine.adapter.i2b2Protocol

import net.shrine.protocol.i2b2.{I2b2Result, QueryResult, ResultOutputType}
import net.shrine.xml.{MissingChildNodeException, XmlUtil}

import scala.annotation.unused
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}
import scala.xml.NodeSeq

/**
 * @author clint
 * @since Aug 17, 2012
 */
final case class ReadResultResponse(xmlResultId: Long, metadata: QueryResult, data: I2b2Result) extends ShrineResponse {
  override protected def i2b2MessageBody: NodeSeq = XmlUtil.stripWhitespace {
    <ns4:response xsi:type="ns4:crc_xml_result_responseType">
      <status>
        <condition type="DONE">DONE</condition>
      </status>
      { metadata.toI2b2 }
      <crc_xml_result>
        <xml_result_id>{ xmlResultId }</xml_result_id>
        <result_instance_id>{ metadata.resultId }</result_instance_id>
        <xml_value>
          {
            i2b2Escape("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""") +
              i2b2Escape(data.toI2b2String)
          }
        </xml_value>
      </crc_xml_result>
    </ns4:response>
  }

  private def i2b2Escape(xml: String): String = {
    xml.replaceAll("<", "&lt;") //TODO: some >'s should be turned into '&amp;gt;' :(
  }

  //xmlResultId doesn't seem necessary, but I wanted to allow Shrine => I2b2 => Shrine marshalling loops without losing anything.  
  //Maybe this isn't needed? 
  override def toXml: NodeSeq = XmlUtil.stripWhitespace {
    <readResultResponse>
      { metadata.toXml }
      <xmlResultId>{ xmlResultId }</xmlResultId>
      { data.toXml }
    </readResultResponse>
  }
}

object ReadResultResponse {

  val rootTagName = "readResultResponse"

  import net.shrine.xml.NodeSeqEnrichments.Strictness._
  
  def fromXml(breakdownTypes: Set[ResultOutputType])(xml: NodeSeq): Try[ReadResultResponse] = unmarshal(
    xml,
    _.withChild("resultEnvelope").flatMap(I2b2Result.fromXml(breakdownTypes)),
    _.withChild("queryResult").map(QueryResult.fromXml(breakdownTypes)),
    _.withChild("xmlResultId").map(_.text.toLong))

  private[this] def messageBodyXml(x: NodeSeq): Try[NodeSeq] = x.withChild("message_body")

  private[this] def responseXml(x: NodeSeq) = messageBodyXml(x).withChild("response")

  //noinspection RedundantBlock
  private[this] def crcResultXml(nodeSeq: NodeSeq): Try[NodeSeq] = {
    val crcXmlResult: Try[NodeSeq] = responseXml(nodeSeq).withChild("crc_xml_result")
    crcXmlResult.transform({xml:NodeSeq => Success(xml)},{
      x:Throwable => x match {
        case mcnx: MissingChildNodeException => {
          val digForError: Try[ErrorFromCrcException] = for{
            conditionElement <- nodeSeq.withChild("status").withChild("condition")
            conditionType <- conditionElement.attribute("type") if conditionType == "ERROR"
          } yield ErrorFromCrcException(conditionElement.text)

          digForError.transform({interpreted => Failure(interpreted)},{_ => Failure(MissingCrCXmlResultException(nodeSeq,mcnx))})
        }
        case NonFatal(throwable) => Failure(throwable)
      }
    })
  }

  def fromI2b2(xml: NodeSeq, @unused shrineNetworkId:Long): Either[ErrorResponse,ReadResultResponse] = {

    import net.shrine.protocol.i2b2.ResultOutputType.breakdownTypes

    def getEnvelope(x: NodeSeq): Try[I2b2Result] = {
      for {
        escapedXml <- crcResultXml(x).withChild("xml_value").map(_.text)
        unescapedXml = i2b2Unescape(escapedXml)
        envelope <- I2b2Result.fromI2b2String(breakdownTypes)(unescapedXml)
      } yield envelope
    }

    def getXmlResultId(x: NodeSeq): Try[Long] = crcResultXml(x).withChild("xml_result_id").map(_.text.toLong)

    def getQueryResult(x: NodeSeq): Try[QueryResult] = {
      responseXml(x).withChild("query_result_instance").map(QueryResult.fromI2b2(breakdownTypes))
    }
    
    Right[ErrorResponse,ReadResultResponse](unmarshal(
      xml,
      getEnvelope,
      getQueryResult,
      getXmlResultId
    ).get)
  }

  //exposed for testing
  def i2b2Unescape(semiEscapedXml: String): String = {
    semiEscapedXml.replaceAll("&amp;gt;", ">")
  }

  private def unmarshal(
                         nodeSeq: NodeSeq,
                         getData: NodeSeq => Try[I2b2Result],
                         getMetadata: NodeSeq => Try[QueryResult],
                         getXmlResultId: NodeSeq => Try[Long]): Try[ReadResultResponse] = {

    for {
      data <- getData(nodeSeq)
      metadata <- getMetadata(nodeSeq)
      xmlResultId <- getXmlResultId(nodeSeq)
    } yield ReadResultResponse(xmlResultId, metadata, data)
  }
}

case class MissingCrCXmlResultException(x:NodeSeq,cause:Throwable) extends Exception("No crc_xml_result element",cause)

case class ErrorFromCrcException(message:String) extends Exception(s"Error from CRC: $message")
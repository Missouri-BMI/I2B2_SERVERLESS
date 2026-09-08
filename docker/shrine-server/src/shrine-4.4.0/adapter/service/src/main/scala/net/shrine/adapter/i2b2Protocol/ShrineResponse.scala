package net.shrine.adapter.i2b2Protocol

import net.shrine.protocol.i2b2.{ResultOutputType, ShrineMessage}
import net.shrine.protocol.i2b2.serialization.{I2b2Marshaller, XmlUnmarshaller}
import net.shrine.xml.XmlUtil

import scala.util.Try
import scala.xml.NodeSeq

/**
 * @author clint
 * @since Nov 5, 2012
 */
trait ShrineResponse extends ShrineMessage with I2b2Marshaller {
  protected def i2b2MessageBody: NodeSeq

  protected def status: NodeSeq = <status type="DONE">DONE</status>

  //TODO better xmlns strategy
  override def toI2b2: NodeSeq = XmlUtil.stripWhitespace {
    <ns4:response xmlns:ns2="http://www.i2b2.org/xsd/hive/pdo/1.1/" xmlns:ns3="http://www.i2b2.org/xsd/cell/crc/pdo/1.1/" xmlns:ns4="http://www.i2b2.org/xsd/hive/msg/1.1/" xmlns:ns5="http://www.i2b2.org/xsd/cell/crc/psm/1.1/" xmlns:ns6="http://www.i2b2.org/xsd/cell/pm/1.1/" xmlns:ns7="http://sheriff.shrine.net/" xmlns:ns8="http://www.i2b2.org/xsd/cell/crc/psm/querydefinition/1.1/" xmlns:ns9="http://www.i2b2.org/xsd/cell/crc/psm/analysisdefinition/1.1/" xmlns:ns10="http://www.i2b2.org/xsd/cell/ont/1.1/" xmlns:ns11="http://www.i2b2.org/xsd/hive/msg/result/1.1/">
      <message_header>
        <i2b2_version_compatible>1.1</i2b2_version_compatible>
        <hl7_version_compatible>2.4</hl7_version_compatible>
        <sending_application>
          <application_name>SHRINE</application_name>
          <application_version>1.3-compatible</application_version>
        </sending_application>
        <sending_facility>
          <facility_name>SHRINE</facility_name>
        </sending_facility>
        <datetime_of_message>2011-04-08T16:21:12.251-04:00</datetime_of_message>
        <security/>
        <project_id xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:nil="true"/>
      </message_header>
      <response_header>
        <result_status>
          {status}
        </result_status>
      </response_header>
      <message_body>
        {i2b2MessageBody}
      </message_body>
    </ns4:response>
  }
}

/**
 * @author clint
 * @since Nov 5, 2012
 */
object ShrineResponse {
  private def lift(unmarshaller: XmlUnmarshaller[ShrineResponse]): Set[ResultOutputType] => NodeSeq => Try[ShrineResponse] = {
    _ => xml => Try(unmarshaller.fromXml(xml))
  }

  private def lift(unmarshal: Set[ResultOutputType] => NodeSeq => ShrineResponse): Set[ResultOutputType] => NodeSeq => Try[ShrineResponse] = {
    knownTypes => xml => Try(unmarshal(knownTypes)(xml))
  }

  private val unmarshallers = Map[String, Set[ResultOutputType] => NodeSeq => Try[ShrineResponse]](
    "readQueryInstancesResponse" -> lift(ReadQueryInstancesResponse),
    "runQueryResponse" -> RunQueryResponse.fromXml,
    "readQueryResultResponse" -> lift(ReadQueryResultResponse.fromXml _),
    ErrorResponse.rootTagName -> lift(ErrorResponse),
    ReadResultResponse.rootTagName -> ReadResultResponse.fromXml
  )

  def fromXml(breakdownTypes: Set[ResultOutputType])(xml: NodeSeq): Try[ShrineResponse] = {
    xml match {
      case null => scala.util.Failure(new IllegalArgumentException("null xml passed in"))
      case _ => for {
        rootTag <- Try(xml.head)
        rootTagName = rootTag.label
        if unmarshallers.contains(rootTagName)
        unmarshal = unmarshallers(rootTagName)
        unmarshalled <- unmarshal(breakdownTypes)(xml)
      } yield unmarshalled
    }
  }
}
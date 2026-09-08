package net.shrine.adapter.i2b2Protocol

import junit.framework.TestCase
import net.shrine.problem.{TestProblem, XmlProblemDigest}
import net.shrine.util.ShouldMatchersForJUnit
import net.shrine.xml.XmlUtil
import org.junit.Test

import scala.xml.NodeSeq

/**
 * @author clint
 * @since Apr 5, 2013
 */
final class ErrorResponseTest extends TestCase with ShouldMatchersForJUnit {
  val message = "foo"
  val problem = TestProblem(message)

  val resp = ErrorResponse(XmlProblemDigest.create(problem))

  val expectedShrineXml = XmlUtil.stripWhitespace {
    <errorResponse>
      <message>{ message }</message>
      {XmlProblemDigest.create(problem).toXml}
    </errorResponse>
  }

  val expectedI2b2Xml = XmlUtil.stripWhitespace {
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
          <status type="ERROR">{ message }</status>
          {XmlProblemDigest.create(problem).toXml}
        </result_status>
      </response_header>
      <message_body>
      </message_body>
    </ns4:response>
  }

  @Test
  def testToXml(): Unit = doTestToXml(expectedShrineXml, _.toXml)

  @Test
  def testToI2b2(): Unit = doTestToXml(expectedI2b2Xml, _.toI2b2)

  @Test
  def testToXmlRoundTrip(): Unit = doTestRoundTrip(_.toXml, ErrorResponse.fromXml)

  @Test
  def testToI2b2RoundTrip(): Unit = doTestRoundTrip(_.toI2b2, ErrorResponse.fromI2b2)

  @Test
  def testFromXml(): Unit = doTestFromXml(expectedShrineXml, ErrorResponse.fromXml)

  @Test
  def testFromI2b2(): Unit = doTestFromXml(expectedI2b2Xml, ErrorResponse.fromI2b2)

  //NB: See https://open.med.harvard.edu/jira/browse/SHRINE-745

  @Test
  def testFromI2b2AlternateFormat():Unit = {
    val altI2b2Xml = XmlUtil.stripWhitespace {
      <ns5:response xmlns:ns2="http://www.i2b2.org/xsd/hive/pdo/1.1/" xmlns:ns4="http://www.i2b2.org/xsd/cell/crc/psm/1.1/" xmlns:ns3="http://www.i2b2.org/xsd/cell/crc/pdo/1.1/" xmlns:tns="http://axis2.crc.i2b2.harvard.edu" xmlns:ns9="http://www.i2b2.org/xsd/cell/ont/1.1/" xmlns:ns5="http://www.i2b2.org/xsd/hive/msg/1.1/" xmlns:ns6="http://www.i2b2.org/xsd/cell/crc/psm/querydefinition/1.1/" xmlns:ns7="http://www.i2b2.org/xsd/cell/crc/psm/analysisdefinition/1.1/" xmlns:ns10="http://www.i2b2.org/xsd/hive/msg/result/1.1/" xmlns:ns8="http://www.i2b2.org/xsd/cell/pm/1.1/">
        <message_header>
          <i2b2_version_compatible>1.1</i2b2_version_compatible>
          <hl7_version_compatible>2.4</hl7_version_compatible>
          <sending_application>
            <application_name>edu.harvard.i2b2.crc</application_name>
            <application_version>1.5</application_version>
          </sending_application>
          <sending_facility>
            <facility_name>i2b2 Hive</facility_name>
          </sending_facility>
          <receiving_application>
            <application_name>i2b2_QueryTool</application_name>
            <application_version>0.2</application_version>
          </receiving_application>
          <receiving_facility>
            <facility_name>i2b2 Hive</facility_name>
          </receiving_facility>
          <message_control_id>
            <instance_num>1</instance_num>
          </message_control_id>
          <project_id>i2b2</project_id>
        </message_header>
        <response_header>
          <info>Log information</info>
          <result_status>
            <status type="DONE">DONE</status>
            <polling_url interval_ms="100"/>
          </result_status>
        </response_header>
        <message_body>
          <ns4:response xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:type="ns4:crc_xml_result_responseType">
            <status>
              <condition type="ERROR">Query result instance id 3126 not found</condition>
            </status>
          </ns4:response>
        </message_body>
      </ns5:response>
    }
    
    val resp = ErrorResponse.fromI2b2(altI2b2Xml)
    
    resp should not be null
    
    resp.errorMessage should equal("Query result instance id 3126 not found")
  }

  private def doTestFromXml(xml: NodeSeq, deserialize: NodeSeq => ErrorResponse): Unit = {
    intercept[Exception] {
      deserialize(null)
    }

    intercept[Exception] {
      deserialize(<foo></foo>)
    }

    intercept[Exception] {
      //Correct I2b2 XML structure, wrong status type
      deserialize(<ns4:request><response_header><result_status><status type="NUH">{ message }</status></result_status></response_header></ns4:request>)
    }

    val deserialized = deserialize(xml)

    deserialized.problem.detailsXml should equal(resp.problem.detailsXml)

    deserialized.problem should equal(resp.problem)

    deserialized should equal(resp)
  }

  private def doTestToXml(expected: NodeSeq, serialize: ErrorResponse => NodeSeq): Unit = {
    val xml = serialize(resp)

    xml.toString should equal(expected.toString())
  }

  private def doTestRoundTrip(serialize: ErrorResponse => NodeSeq, deserialize: NodeSeq => ErrorResponse): Unit = {
    val unmarshalled = deserialize(serialize(resp))

    unmarshalled should equal(resp)
  }
}
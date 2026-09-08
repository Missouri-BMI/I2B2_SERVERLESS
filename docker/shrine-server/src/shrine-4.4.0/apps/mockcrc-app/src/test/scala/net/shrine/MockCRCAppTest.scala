package net.shrine

import cats.effect.IO
import net.shrine.util.ShouldMatchersForJUnit
import org.http4s.{Method, Request, Response, Uri}
import org.junit.{Ignore, Test}
import org.http4s.dsl.io._
import org.http4s.syntax.literals._
/**
  * Created by marc-danie on 4/12/18.
  */
class MockCRCAppTest extends ShouldMatchersForJUnit{
  import cats.effect.unsafe.implicits.global

  @Test
  def testMockCRCOkResponse: Unit = {

    val req: Request[IO] = Request(method = Method.POST, uri = uri"/mock/i2b2/services/QueryToolService" )
    val response: Response[IO] = MockCRCApp.service.run(req).value.unsafeRunSync().get

    val responseText = response.as[String].map(x => IO(x)).unsafeRunSync().flatMap(y => IO(y)).unsafeRunSync()

    responseText should equal(s"$crcSuccessResponseText")
  }

  @Test
  def testMockCRCOkWithSlashResponse: Unit = {

    val reqWithSlash: Request[IO] = Request(method = Method.POST, uri = uri"/mock/i2b2/services/QueryToolService/" )
    val responseForSlash: Response[IO] = MockCRCApp.service.run(reqWithSlash).value.unsafeRunSync().get
    val responseTextSlash = responseForSlash.as[String].map(x => IO(x)).unsafeRunSync().flatMap(y => IO(y)).unsafeRunSync()

    responseTextSlash should equal(s"$crcSuccessResponseText")
  }

  @Test
  def testMockCRCNotFoundResponse: Unit = {

    val notFoundResponse = "The mock CRC does not respond to Request(method=GET, uri=/mock/i2b2/services/QueryToolService, httpVersion=HTTP/1.1, headers=Headers())"

    val getReq: Request[IO] = Request(method = Method.GET, uri = uri"/mock/i2b2/services/QueryToolService" )
    val response: Response[IO] = MockCRCApp.service.run(getReq).value.unsafeRunSync().get

    val responseText = response.as[String].map(x => IO(x)).unsafeRunSync().flatMap(y => IO(y)).unsafeRunSync()

    responseText should equal(s"$notFoundResponse")

    val otherReq: Request[IO] = Request(method = Method.POST, uri = uri"/path/does/not/exist" )
    val responseNotFoundUrl: Response[IO] = MockCRCApp.service.run(otherReq).value.unsafeRunSync().get

    val responseBadPathText = responseNotFoundUrl.as[String].map(x => IO(x)).unsafeRunSync().flatMap(y => IO(y)).unsafeRunSync()

    val pathNotFoundResponse = "The mock CRC does not respond to Request(method=POST, uri=/path/does/not/exist, httpVersion=HTTP/1.1, headers=Headers())"

    responseBadPathText should equal(s"$pathNotFoundResponse")
  }

    val crcSuccessResponseText: String = """<ns5:response xmlns:ns5="http://www.i2b2.org/xsd/hive/msg/1.1/" xmlns:ns6="http://www.i2b2.org/xsd/cell/crc/psm/analysisdefinition/1.1/" xmlns:ns8="http://www.i2b2.org/xsd/cell/pm/1.1/" xmlns:ns7="http://www.i2b2.org/xsd/cell/crc/psm/querydefinition/1.1/" xmlns:ns9="http://www.i2b2.org/xsd/cell/ont/1.1/" xmlns:ns10="http://www.i2b2.org/xsd/hive/msg/result/1.1/" xmlns:ns2="http://www.i2b2.org/xsd/hive/pdo/1.1/" xmlns:ns4="http://www.i2b2.org/xsd/cell/crc/psm/1.1/" xmlns:ns3="http://www.i2b2.org/xsd/cell/crc/pdo/1.1/">
    |    <message_header>
    |        <i2b2_version_compatible>1.1</i2b2_version_compatible>
    |        <hl7_version_compatible>2.4</hl7_version_compatible>
    |        <sending_application>
    |            <application_name>CRC Cell</application_name>
    |            <application_version>1.7</application_version>
    |        </sending_application>
    |        <sending_facility>
    |            <facility_name>i2b2 Hive</facility_name>
    |        </sending_facility>
    |        <receiving_application>
    |            <application_name>i2b2_QueryTool</application_name>
    |            <application_version>0.2</application_version>
    |        </receiving_application>
    |        <receiving_facility>
    |            <facility_name>i2b2 Hive</facility_name>
    |        </receiving_facility>
    |        <message_control_id>
    |            <instance_num>1</instance_num>
    |        </message_control_id>
    |        <project_id>Demo</project_id>
    |    </message_header>
    |    <response_header>
    |        <info>Log information</info>
    |        <result_status>
    |            <status type="DONE">DONE</status>
    |            <polling_url interval_ms="100"/>
    |        </result_status>
    |    </response_header>
    |    <message_body>
    |        <ns4:response xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:type="ns4:master_instance_result_responseType">
    |            <status>
    |                <condition type="DONE">DONE</condition>
    |            </status>
    |            <query_master>
    |                <query_master_id>31</query_master_id>
    |                <name>Female@14:49:49</name>
    |                <user_id>demo</user_id>
    |                <group_id>Demo</group_id>
    |                <create_date>2018-03-27T03:41:01.060-04:00</create_date>
    |                <request_xml>&lt;?xml version="1.0" encoding="UTF-8" standalone="yes"?>
    |&lt;ns4:query_definition xmlns:ns2="http://www.i2b2.org/xsd/cell/crc/psm/1.1/" xmlns:ns4="http://www.i2b2.org/xsd/cell/crc/psm/querydefinition/1.1/" xmlns:ns3="http://www.i2b2.org/xsd/cell/crc/psm/analysisdefinition/1.1/">
    |    &lt;query_name>Female@14:49:49&lt;/query_name>
    |    &lt;query_timing>ANY&lt;/query_timing>
    |    &lt;specificity_scale>0&lt;/specificity_scale>
    |    &lt;panel>
    |        &lt;panel_number>1&lt;/panel_number>
    |        &lt;panel_timing>ANY&lt;/panel_timing>
    |        &lt;panel_accuracy_scale>0&lt;/panel_accuracy_scale>
    |        &lt;invert>0&lt;/invert>
    |        &lt;total_item_occurrences>1&lt;/total_item_occurrences>
    |        &lt;item>
    |            &lt;hlevel>4&lt;/hlevel>
    |            &lt;item_name>\\i2b2_DEMO\i2b2\Demographics\Gender\Female\&lt;/item_name>
    |            &lt;item_key>\\i2b2_DEMO\i2b2\Demographics\Gender\Female\&lt;/item_key>
    |            &lt;item_icon>LA&lt;/item_icon>
    |            &lt;tooltip>\\i2b2_DEMO\i2b2\Demographics\Gender\Female\&lt;/tooltip>
    |            &lt;class>ENC&lt;/class>
    |            &lt;constrain_by_date/>
    |            &lt;item_is_synonym>false&lt;/item_is_synonym>
    |        &lt;/item>
    |    &lt;/panel>
    |&lt;/ns4:query_definition>
    |</request_xml>
    |            </query_master>
    |            <query_instance>
    |                <query_instance_id>31</query_instance_id>
    |                <query_master_id>31</query_master_id>
    |                <user_id>demo</user_id>
    |                <group_id>Demo</group_id>
    |                <batch_mode>FINISHED</batch_mode>
    |                <start_date>2018-03-27T03:41:01.089-04:00</start_date>
    |                <query_status_type>
    |                    <status_type_id>3</status_type_id>
    |                    <name>FINISHED</name>
    |                    <description>FINISHED</description>
    |                </query_status_type>
    |            </query_instance>
    |            <query_result_instance>
    |                <result_instance_id>34</result_instance_id>
    |                <query_instance_id>31</query_instance_id>
    |                <description>Number of patients for "Female@14:49:49"</description>
    |                <query_result_type>
    |                    <result_type_id>4</result_type_id>
    |                    <name>PATIENT_COUNT_XML</name>
    |                    <display_type>CATNUM</display_type>
    |                    <visual_attribute_type>LA</visual_attribute_type>
    |                    <description>Number of patients</description>
    |                </query_result_type>
    |                <set_size>51</set_size>
    |                <obfuscate_method/>
    |                <start_date>2018-03-27T03:41:01.090-04:00</start_date>
    |                <end_date>2018-03-27T03:41:01.258-04:00</end_date>
    |                <message/>
    |                <query_status_type>
    |                    <status_type_id>3</status_type_id>
    |                    <name>FINISHED</name>
    |                    <description>FINISHED</description>
    |                </query_status_type>
    |            </query_result_instance>
    |        </ns4:response>
    |    </message_body>
    |</ns5:response>""".stripMargin
}


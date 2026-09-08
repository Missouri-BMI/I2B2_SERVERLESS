package net.shrine

/**
  * Created by marc-danie on 3/22/18.
  */

import cats.effect.IO
import org.http4s.{HttpRoutes, Request}
import org.http4s.dsl.impl.{->, /, /:}
import org.http4s.dsl.io.{GET, NotFound, Ok, POST, Root}
import org.http4s.dsl.io.{http4sNotFoundSyntax, http4sOkSyntax}

object MockCRCApp {

  def extractRequestBody(req: Request[IO]): String = {
    import cats.effect.unsafe.implicits.global
    req.as[String].unsafeRunSync()
  }

  def delayedResponse(req: Request[IO]): String = {
    val str: String = extractRequestBody(req)

    val extractWaitTimeRegex ="""(?<=<result_waittime_ms>)(.*)(?=</result_waittime_ms>)""".r
    val waitTimeMs: Option[Long] = extractWaitTimeRegex.findFirstMatchIn(str).map(_.toString).map(_.toLong)

    waitTimeMs.fold{}(x =>
      Thread.sleep(x - 20000)
    )

    completedSuccessCRCResponseText
  }

  val service: HttpRoutes[IO] = HttpRoutes.of[IO] {

    case req@GET -> Root / "ping" => {
      Ok("pong")
    }

    case req@POST -> Root / "mock" / "i2b2" / "services" / "QueryToolService" => {
      val response: String = delayedResponse(req)
      Ok(s"$response")
    }

    case req@POST -> Root / "mock" / "i2b2" / "services" / "QueryToolService" / "" => {
      val response: String = delayedResponse(req)
      Ok(s"$response")
    }
    //simulates going from a QUEUED TO Error state
    //in the error response determined by an optional query parameter
    case req@POST -> Root / "mock" / "i2b2" / "services" / "QueryToolService" / "queued_error"  => {
      val response: String = QueuedNoErrorMsg(req)
      Ok(s"$response")
    }

    case req@POST -> Root / "mock" / "i2b2" / "services" / "QueryToolService" / "queued_error" / "" => {
      val response: String = QueuedNoErrorMsg(req)
      Ok(s"$response")
    }

    //simulates going from a QUEUED TO Error state with some variations (i.e modified or missing fields)
    //in the error response determined by an optional query parameter
    case req@POST -> Root / "mock" / "i2b2" / "services" / "QueryToolService" / "queued_error" / "errorNoMessage" => {
      val response: String = QueuedNoErrorMsg(req)
      Ok(s"$response")
    }

    case req@POST -> Root / "mock" / "i2b2" / "services" / "QueryToolService" / "queued_error" / "errorNoMessage" / "" => {
      val response: String = QueuedNoErrorMsg(req)
      Ok(s"$response")
    }

    case x => NotFound(s"The mock CRC does not respond to $x")
  }

  abstract class QueuedResponse {
    def getQueryResultInstanceList_fromQueryInstanceId: String = {
      queuedQueryResultResponseText
    }
    def getQueryInstanceList_fromQueryMasterId: String

    def apply(request: Request[IO]): String = {
      val str: String = extractRequestBody(request)

      if (str.contains("CRC_QRY_getQueryResultInstanceList_fromQueryInstanceId")) {
        getQueryResultInstanceList_fromQueryInstanceId
      }
      else if (str.contains("CRC_QRY_getQueryInstanceList_fromQueryMasterId")) {
        getQueryInstanceList_fromQueryMasterId
      }
      else {
        queuedResponseText
      }
    }
  }

  object QueuedNoErrorMsg  extends QueuedResponse{

    def getQueryInstanceList_fromQueryMasterId: String = {
      //Reproduces SHRINE-2574
      completedQueuedErrorNoStatusMsgCRCResponseText
    }
  }

  object QueuedMsg  extends QueuedResponse{
    def getQueryInstanceList_fromQueryMasterId: String = {
      completedSuccessCRCResponseText
    }
  }

  val completedSuccessCRCResponseText: String = """<ns5:response xmlns:ns5="http://www.i2b2.org/xsd/hive/msg/1.1/" xmlns:ns6="http://www.i2b2.org/xsd/cell/crc/psm/analysisdefinition/1.1/" xmlns:ns8="http://www.i2b2.org/xsd/cell/pm/1.1/" xmlns:ns7="http://www.i2b2.org/xsd/cell/crc/psm/querydefinition/1.1/" xmlns:ns9="http://www.i2b2.org/xsd/cell/ont/1.1/" xmlns:ns10="http://www.i2b2.org/xsd/hive/msg/result/1.1/" xmlns:ns2="http://www.i2b2.org/xsd/hive/pdo/1.1/" xmlns:ns4="http://www.i2b2.org/xsd/cell/crc/psm/1.1/" xmlns:ns3="http://www.i2b2.org/xsd/cell/crc/pdo/1.1/">
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

  val queuedQueryResultResponseText: String = """<ns5:response xmlns:ns5="http://www.i2b2.org/xsd/hive/msg/1.1/" xmlns:ns6="http://www.i2b2.org/xsd/cell/crc/psm/querydefinition/1.1/" xmlns:ns8="http://www.i2b2.org/xsd/cell/pm/1.1/" xmlns:ns7="http://www.i2b2.org/xsd/cell/crc/psm/analysisdefinition/1.1/" xmlns:ns9="http://www.i2b2.org/xsd/cell/ont/1.1/" xmlns:ns10="http://www.i2b2.org/xsd/hive/msg/result/1.1/" xmlns:ns2="http://www.i2b2.org/xsd/hive/pdo/1.1/" xmlns:ns4="http://www.i2b2.org/xsd/cell/crc/psm/1.1/" xmlns:ns3="http://www.i2b2.org/xsd/cell/crc/pdo/1.1/">
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
            |        <ns4:response xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:type="ns4:result_responseType">
            |            <status>
            |                <condition type="DONE">DONE</condition>
            |            </status>
            |                 </ns4:response>
            |    </message_body>
            |</ns5:response>""".stripMargin

  val completedQueuedErrorNoStatusMsgCRCResponseText: String = """<ns5:response xmlns:ns5="http://www.i2b2.org/xsd/hive/msg/1.1/" xmlns:ns6="http://www.i2b2.org/xsd/cell/crc/psm/querydefinition/1.1/" xmlns:ns8="http://www.i2b2.org/xsd/cell/pm/1.1/" xmlns:ns7="http://www.i2b2.org/xsd/cell/crc/psm/analysisdefinition/1.1/" xmlns:ns9="http://www.i2b2.org/xsd/cell/ont/1.1/" xmlns:ns10="http://www.i2b2.org/xsd/hive/msg/result/1.1/" xmlns:ns2="http://www.i2b2.org/xsd/hive/pdo/1.1/" xmlns:ns4="http://www.i2b2.org/xsd/cell/crc/psm/1.1/" xmlns:ns3="http://www.i2b2.org/xsd/cell/crc/pdo/1.1/">
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
              |        <ns4:response xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:type="ns4:instance_responseType">
              |            <status>
              |                <condition type="DONE">DONE</condition>
              |            </status>
              |            <query_instance>
              |                <query_instance_id>1945</query_instance_id>
              |                <query_master_id>1948</query_master_id>
              |                <user_id>demo</user_id>
              |                <group_id>Demo</group_id>
              |                <batch_mode>ERROR</batch_mode>
              |                <start_date>2018-05-17T12:22:17.013-04:00</start_date>
              |                <end_date>2018-05-17T12:22:18.516-04:00</end_date>
              |                <message/>
              |                <query_status_type>
              |                    <status_type_id>4</status_type_id>
              |                    <name>ERROR</name>
              |                    <description>ERROR</description>
              |                </query_status_type>
              |            </query_instance>
              |        </ns4:response>
              |    </message_body>
              |</ns5:response>""".stripMargin

  val queuedResponseText: String = """<ns5:response xmlns:ns3="http://www.i2b2.org/xsd/cell/crc/pdo/1.1/" xmlns:ns4="http://www.i2b2.org/xsd/cell/crc/psm/1
             |.1/" xmlns:ns2="http://www.i2b2.org/xsd/hive/pdo/1.1/" xmlns:ns10="http://www.i2b2.org/xsd/hive/msg/result/1.1/" xmlns:ns9="http://www.i2b2.org/xsd/cell/on
             |t/1.1/" xmlns:ns7="http://www.i2b2.org/xsd/cell/crc/psm/analysisdefinition/1.1/" xmlns:ns8="http://www.i2b2.org/xsd/cell/pm/1.1/" xmlns:ns6="http://www.i2b
             |2.org/xsd/cell/crc/psm/querydefinition/1.1/" xmlns:ns5="http://www.i2b2.org/xsd/hive/msg/1.1/">
             |  <message_header>
             |    <i2b2_version_compatible>1.1</i2b2_version_compatible>
             |    <hl7_version_compatible>2.4</hl7_version_compatible>
             |    <sending_application>
             |      <application_name>CRC Cell</application_name>
             |      <application_version>1.7</application_version>
             |    </sending_application>
             |    <sending_facility>
             |      <facility_name>i2b2 Hive</facility_name>
             |    </sending_facility>
             |    <receiving_application>
             |      <application_name>i2b2_QueryTool</application_name>
             |      <application_version>0.2</application_version>
             |    </receiving_application>
             |    <receiving_facility>
             |      <facility_name>i2b2 Hive</facility_name>
             |    </receiving_facility>
             |    <message_control_id>
             |      <instance_num>1</instance_num>
             |    </message_control_id>
             |    <project_id>Demo</project_id>
             |  </message_header>
             |  <response_header>
             |    <info>Log information</info>
             |    <result_status>
             |      <status type="DONE">DONE</status>
             |      <polling_url interval_ms="100"/>
             |    </result_status>
             |  </response_header>
             |  <message_body>
             |    <ns4:response xsi:type="ns4:master_instance_result_responseType" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
             |      <status>
             |        <condition type="RUNNING">RUNNING</condition>
             |      </status>
             |      <query_master>
             |        <query_master_id>2919</query_master_id>
             |        <name>Burns (940-949.@09:59:20</name>
             |        <user_id>demo</user_id>
             |        <group_id>Demo</group_id>
             |        <create_date>2018-05-15T13:51:39.941-04:00</create_date> <request_xml>&lt;?xml version="1.0" encoding="UTF-8" standalone="yes"?>
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
             |     </query_master>
             |      <query_instance>
             |        <query_instance_id>2919</query_instance_id>
             |        <query_master_id>2919</query_master_id>
             |        <user_id>demo</user_id>
             |        <group_id>Demo</group_id>
             |        <batch_mode>MEDIUM_QUEUE</batch_mode>
             |        <start_date>2018-05-15T13:52:23.375-04:00</start_date>
             |        <query_status_type>
             |          <status_type_id>7</status_type_id>
             |          <name>MEDIUM_QUEUE</name>
             |          <description>MEDIUM_QUEUE</description>
             |        </query_status_type>
             |      </query_instance>
             |      <query_result_instance>
             |        <result_instance_id>3241</result_instance_id>
             |        <query_instance_id>2919</query_instance_id>
             |        <query_result_type>
             |          <result_type_id>4</result_type_id>
             |          <name>PATIENT_COUNT_XML</name>
             |          <display_type>CATNUM</display_type>
             |          <visual_attribute_type>LA</visual_attribute_type>
             |          <description>Number of patients</description>
             |        </query_result_type>
             |        <set_size>0</set_size>
             |        <obfuscate_method/>
             |        <start_date>2018-05-15T13:52:23.384-04:00</start_date>
             |        <end_date>2018-05-15T13:52:23.440-04:00</end_date>
             |        <message/>
             |        <query_status_type>
             |          <status_type_id>1</status_type_id>
             |          <name>QUEUED</name>
             |          <description>WAITING IN QUEUE TO START PROCESS</description>
             |        </query_status_type>
             |      </query_result_instance>
             |    </ns4:response>
             |  </message_body>
             |</ns5:response>""".stripMargin
}
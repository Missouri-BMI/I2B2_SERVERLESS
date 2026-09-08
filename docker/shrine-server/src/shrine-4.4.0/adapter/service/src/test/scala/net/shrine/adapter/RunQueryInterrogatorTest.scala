package net.shrine.adapter

import cats.effect.IO
import net.shrine.adapter.ObfuscatorTest.within3
import net.shrine.adapter.dao.AdapterQueryHistoryDb
import net.shrine.adapter.i2b2Protocol.{ErrorResponse, HiveCredentials, RawCrcRunQueryResponse, RunQueryRequest, RunQueryResponse}
import net.shrine.adapter.translators.{ExpressionTranslator, QueryDefinitionTranslator}
import net.shrine.config.ConfigSource
import net.shrine.crypto.SealerRevealer
import net.shrine.http4s.client.legacy.{HttpClient, Poster}
import net.shrine.hub.HubLifecycle
import net.shrine.hub.data.store.HubDb
import net.shrine.problem.{TestProblem, XmlProblemDigest}
import net.shrine.protocol.i2b2.{AuthenticationInfo, Credential, DefaultBreakdownResultOutputTypes, QueryResult, ResultOutputType}
import net.shrine.protocol.i2b2.ResultOutputType.PATIENT_COUNT_XML
import net.shrine.protocol.i2b2.query.{I2b2QueryDefinition, OccuranceLimited, Or, Term}
import net.shrine.protocol.version.v2.ResultStatus.{QueuedByCRC, QueuedForManualSubmission}
import net.shrine.protocol.version.{DateStamp, ItemVersion, MomQueueName, NodeId, NodeName, ProtocolVersion, QueryId, ResultId, ShrineVersion}
import net.shrine.protocol.version.v2.{CountResult, Network, Node, ObfuscatingParameters, ResultMetadata, ResultProgress, UpdateResult, UpdateResultWithCount, UpdateResultWithError, UpdateResultWithProgress, VersionInfo}
import net.shrine.util.ShouldMatchersForJUnit
import net.shrine.xml.{MissingChildNodeException, XmlDateHelper, XmlUtil}
import org.junit.{After, Before, Test}

import scala.concurrent.duration.DurationInt

/**
 * @author Bill Simons
 * @author Clint Gilbert
 * @since 4/19/11
 * @see http://cbmi.med.harvard.edu
 */
final class RunQueryInterrogatorTest extends ShouldMatchersForJUnit {
  import cats.effect.unsafe.implicits.global
  private val queryDef = I2b2QueryDefinition("foo", Term("foo", "fooName"))

  private val queryId = 123L
  private val expectedQueryId = 999L
  private val expectedLocalMasterId = queryId.toString
  private val instanceId = 456L
  private val resultId = 42L
  private val projectId = "projectId"
  private val setSize = 17L
  private val userId = "userId"
  private val groupId = "groupId"

  private val justCounts = Set(PATIENT_COUNT_XML)

  private val now = XmlDateHelper.now

  private val countQueryResult = QueryResult(resultId, instanceId, Some(PATIENT_COUNT_XML), setSize, Some(now), Some(now), None, QueryResult.StatusType.Finished, None)

  private val hiveCredentials = HiveCredentials("some-hive-domain", "hive-username", SealerRevealer.seal("hive-password"), "hive-project")

  private val authn = AuthenticationInfo("some-domain", "username", Credential("jksafhkjaf", isToken = false))

  private val altI2b2ErrorXml = XmlUtil.stripWhitespace {
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
  }.toString

  private val otherQueryId: Long = 12345L

  @Test
  def testProcessRawCrcRunQueryResponseCountQueryOnly(): Unit = {
    val outputTypes = Set(PATIENT_COUNT_XML)
    
    val translator = new QueryDefinitionTranslator(ExpressionTranslator(Map("network" -> Set("local1a", "local1b"))))

    val adapter = new RunQueryInterrogator(
      Poster("crc-url", null),
      hiveCredentials,
      translator,
      doObfuscation = false,
      runQueriesImmediately = true,
      breakdownTypes = DefaultBreakdownResultOutputTypes.toSet,
      botCountTimeThresholds = Seq.empty,
      obfuscator = Obfuscator(ObfuscatingParameters(1,1.3,3,10))
    )

    val request = i2b2Protocol.RunQueryRequest(projectId, 1.second, authn, expectedQueryId, outputTypes, queryDef)

    val networkAuthn = AuthenticationInfo("some-domain", "username", Credential("sadasdasdasd", isToken = false))
    
    val rawRunQueryResponse = RawCrcRunQueryResponse(
        queryId = queryId, 
        createDate = XmlDateHelper.now,
        userId = request.authn.username, 
        groupId = request.authn.domain, 
        requestXml = request.queryDefinition, 
        queryInstanceId = otherQueryId,
        singleNodeResults = RawCrcRunQueryResponse.toQueryResultMap(Seq(countQueryResult)))
    
    val resp: RunQueryResponse = adapter.processRawCrcRunQueryResponse(networkAuthn, request, rawRunQueryResponse,queryId)

    resp should not be null
    
    //Validate the response
    
    resp.createDate should not be null
    resp.groupId should be(request.authn.domain)
    resp.userId should be(request.authn.username)
    resp.queryId should be(queryId)
    resp.queryInstanceId should be(otherQueryId)
    resp.requestXml should equal(request.queryDefinition)
    
    (countQueryResult eq resp.singleNodeResult) should be(false)
    within3(resp.singleNodeResult.setSize, countQueryResult.setSize) should be(true)
    
    resp.singleNodeResult.resultType.get should equal(PATIENT_COUNT_XML)
    
    resp.singleNodeResult.breakdowns should equal(Map.empty)
    
    //validate the DB
    
    val expectedNetworkTerm = queryDef.expr.get.asInstanceOf[Term]

    //We should have one row in the shrine_query table, for the query just performed
    val Some(queryHistory) = AdapterQueryHistoryDb.db.selectQueryHistoryByQueryIdIO(expectedQueryId).unsafeRunSync()
    val queryRow = queryHistory.toShrineQuery

    {
      queryRow.dateCreated should not be null
      queryRow.domain should equal(request.authn.domain)
      queryRow.name should equal(queryDef.name)
      queryRow.i2b2MasterQueryId should equal(expectedLocalMasterId)
      queryRow.networkId should equal(expectedQueryId)
      queryRow.username should equal(authn.username)
      queryRow.queryDefinition.expr.get should equal(expectedNetworkTerm)
    }

    //We should have one row in the count_result table, with the right obfuscated value, which is within the expected amount from the original count
    val Some(_) = AdapterQueryHistoryDb.db.selectCountResultsByQueryIdIO(expectedQueryId).unsafeRunSync()

    {
      //countRow.dateCreated should be(null)
    }
  }

  @Test
  def testProcessRawCrcRunQueryResponseWithBreakdownsQueued(): Unit = {

    val translator = new QueryDefinitionTranslator(ExpressionTranslator(Map("network" -> Set("local1a", "local1b"))))

    val adapter = new RunQueryInterrogator(
      Poster("crc-url", null),
      hiveCredentials,
      translator,
      doObfuscation = false,
      runQueriesImmediately = true,
      breakdownTypes = DefaultBreakdownResultOutputTypes.toSet,
      botCountTimeThresholds = Seq.empty,
      obfuscator = Obfuscator(ObfuscatingParameters(1,1.3,3,10))
    )

    val responseXml = """<ns5:response xmlns:ns3="http://www.i2b2.org/xsd/cell/crc/pdo/1.1/" xmlns:ns4="http://www.i2b2.org/xsd/cell/crc/psm/1.1/" xmlns:ns2="http://www.i2b2.org/xsd/hive/pdo/1.1/" xmlns:ns10="http://www.i2b2.org/xsd/hive/msg/result/1.1/" xmlns:ns9="http://www.i2b2.org/xsd/cell/ont/1.1/" xmlns:ns7="http://www.i2b2.org/xsd/cell/crc/psm/querydefinition/1.1/" xmlns:ns8="http://www.i2b2.org/xsd/cell/pm/1.1/" xmlns:ns6="http://www.i2b2.org/xsd/cell/crc/psm/analysisdefinition/1.1/" xmlns:ns5="http://www.i2b2.org/xsd/hive/msg/1.1/">
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
                        |        <condition type="DONE">DONE</condition>
                        |      </status>
                        |      <query_master>
                        |        <query_master_id>6596</query_master_id>
                        |        <name>C50-C-Liver-Breas@15:29:41</name>
                        |        <user_id>demo</user_id>
                        |        <group_id>Demo</group_id>
                        |        <create_date>2021-05-18T22:29:49.000Z</create_date>
                        |        <request_xml>&lt;?xml version=&quot;1.0&quot; encoding=&quot;UTF-8&quot; standalone=&quot;yes&quot;?&gt; &lt;ns4:query_definition xmlns:ns2=&quot;http://www.i2b2.org/xsd/cell/crc/psm/1.1/&quot; xmlns:ns4=&quot;http://www.i2b2.org/xsd/cell/crc/psm/querydefinition/1.1/&quot; xmlns:ns3=&quot;http://www.i2b2.org/xsd/cell/crc/psm/analysisdefinition/1.1/&quot;&gt; &lt;query_name&gt;C50-C-Liver-Breas@15:29:41&lt;/query_name&gt; &lt;query_timing&gt;ANY&lt;/query_timing&gt; &lt;specificity_scale&gt;0&lt;/specificity_scale&gt; &lt;panel&gt; &lt;panel_number&gt;1&lt;/panel_number&gt; &lt;panel_timing&gt;ANY&lt;/panel_timing&gt; &lt;panel_accuracy_scale&gt;100&lt;/panel_accuracy_scale&gt; &lt;invert&gt;0&lt;/invert&gt; &lt;total_item_occurrences&gt;1&lt;/total_item_occurrences&gt; &lt;item&gt; &lt;hlevel&gt;8&lt;/hlevel&gt; &lt;item_name&gt;C50-C50 Malignant neoplasms of breast (C50)&lt;/item_name&gt; &lt;item_key&gt;\\ACT_DX_ICD10_2018\ACT\Diagnosis\ICD10\V2_2018AA\A20098492\A18924118\A20142321\&lt;/item_key&gt; &lt;item_icon&gt;LA&lt;/item_icon&gt; &lt;tooltip&gt;\\ACT_DX_ICD10_2018\ACT\Diagnosis\ICD10\V2_2018AA\A20098492\A18924118\A20142321\&lt;/tooltip&gt; &lt;class&gt;ENC&lt;/class&gt; &lt;constrain_by_date/&gt; &lt;item_is_synonym&gt;false&lt;/item_is_synonym&gt; &lt;/item&gt; &lt;/panel&gt; &lt;panel&gt; &lt;panel_number&gt;2&lt;/panel_number&gt; &lt;panel_timing&gt;ANY&lt;/panel_timing&gt; &lt;panel_accuracy_scale&gt;100&lt;/panel_accuracy_scale&gt; &lt;invert&gt;0&lt;/invert&gt; &lt;total_item_occurrences&gt;1&lt;/total_item_occurrences&gt; &lt;item&gt; &lt;hlevel&gt;3&lt;/hlevel&gt; &lt;item_name&gt;Liver function&lt;/item_name&gt; &lt;item_key&gt;\\ACT_LAB\ACT\Labs\LP40271-6\LP31397-0\&lt;/item_key&gt; &lt;item_icon&gt;LA&lt;/item_icon&gt; &lt;tooltip&gt;\\ACT_LAB\ACT\Labs\LP40271-6\LP31397-0\&lt;/tooltip&gt; &lt;class&gt;ENC&lt;/class&gt; &lt;constrain_by_date/&gt; &lt;item_is_synonym&gt;false&lt;/item_is_synonym&gt; &lt;/item&gt; &lt;item&gt; &lt;hlevel&gt;4&lt;/hlevel&gt; &lt;item_name&gt;Hematocrit&lt;/item_name&gt; &lt;item_key&gt;\\ACT_LAB\ACT\Labs\LP31756-7\LP30786-5\LP45040-0\&lt;/item_key&gt; &lt;item_icon&gt;LA&lt;/item_icon&gt; &lt;tooltip&gt;\\ACT_LAB\ACT\Labs\LP31756-7\LP30786-5\LP45040-0\&lt;/tooltip&gt; &lt;class&gt;ENC&lt;/class&gt; &lt;constrain_by_date/&gt; &lt;item_is_synonym&gt;false&lt;/item_is_synonym&gt; &lt;/item&gt; &lt;/panel&gt; &lt;panel&gt; &lt;panel_number&gt;3&lt;/panel_number&gt; &lt;panel_timing&gt;ANY&lt;/panel_timing&gt; &lt;panel_accuracy_scale&gt;100&lt;/panel_accuracy_scale&gt; &lt;invert&gt;0&lt;/invert&gt; &lt;total_item_occurrences&gt;1&lt;/total_item_occurrences&gt; &lt;item&gt; &lt;hlevel&gt;8&lt;/hlevel&gt; &lt;item_name&gt;Breast, Mammography&lt;/item_name&gt; &lt;item_key&gt;\\ACT_PX_CPT_2018\ACT\Procedures\CPT4\V2_2018AA\A23576389\A23577312\A23575732\&lt;/item_key&gt; &lt;item_icon&gt;LA&lt;/item_icon&gt; &lt;tooltip&gt;\\ACT_PX_CPT_2018\ACT\Procedures\CPT4\V2_2018AA\A23576389\A23577312\A23575732\&lt;/tooltip&gt; &lt;class&gt;ENC&lt;/class&gt; &lt;constrain_by_date/&gt; &lt;item_is_synonym&gt;false&lt;/item_is_synonym&gt; &lt;/item&gt; &lt;/panel&gt; &lt;/ns4:query_definition&gt;</request_xml>
                        |      </query_master>
                        |      <query_instance>
                        |        <query_instance_id>6595</query_instance_id>
                        |        <query_master_id>6596</query_master_id>
                        |        <user_id>demo</user_id>
                        |        <group_id>Demo</group_id>
                        |        <batch_mode>MEDIUM_QUEUE</batch_mode>
                        |        <start_date>2021-05-18T22:29:50.000Z</start_date>
                        |        <query_status_type>
                        |          <status_type_id>10</status_type_id>
                        |          <name>TIMEDOUT</name>
                        |          <description>TIMEDOUT</description>
                        |        </query_status_type>
                        |      </query_instance>
                        |      <query_result_instance>
                        |        <result_instance_id>8653</result_instance_id>
                        |        <query_instance_id>6595</query_instance_id>
                        |        <query_result_type>
                        |          <result_type_id>8</result_type_id>
                        |          <name>PATIENT_AGE_COUNT_XML</name>
                        |          <display_type>CATNUM</display_type>
                        |          <visual_attribute_type>LA</visual_attribute_type>
                        |          <description>Age patient breakdown</description>
                        |        </query_result_type>
                        |        <set_size>0</set_size>
                        |        <start_date>2021-05-18T22:29:50.000Z</start_date>
                        |        <query_status_type>
                        |          <status_type_id>10</status_type_id>
                        |          <name>TIMEDOUT</name>
                        |          <description>TIMEDOUT</description>
                        |        </query_status_type>
                        |      </query_result_instance>
                        |      <query_result_instance>
                        |        <result_instance_id>8654</result_instance_id>
                        |        <query_instance_id>6595</query_instance_id>
                        |        <query_result_type>
                        |          <result_type_id>4</result_type_id>
                        |          <name>PATIENT_COUNT_XML</name>
                        |          <display_type>CATNUM</display_type>
                        |          <visual_attribute_type>LA</visual_attribute_type>
                        |          <description>Number of patients</description>
                        |        </query_result_type>
                        |        <set_size>0</set_size>
                        |        <start_date>2021-05-18T22:29:50.000Z</start_date>
                        |        <end_date>2021-05-18T22:29:50.000Z</end_date>
                        |        <query_status_type>
                        |          <status_type_id>2</status_type_id>
                        |          <name>PROCESSING</name>
                        |          <description>PROCESSING</description>
                        |        </query_status_type>
                        |      </query_result_instance>
                        |    </ns4:response>
                        |  </message_body>
                        |</ns5:response>""".stripMargin

    val outputTypes = Set(PATIENT_COUNT_XML)
    val request = i2b2Protocol.RunQueryRequest(projectId, 1.second, authn, expectedQueryId, outputTypes, queryDef)
    val networkAuthn = AuthenticationInfo("Demo", "username", Credential("sadasdasdasd", isToken = false))

    val response: Either[ErrorResponse, RunQueryResponse] = adapter.runQueryClientClient.parseShrineErrorResponseWithFallback(responseXml,expectedQueryId,adapter.parseShrineResponse(networkAuthn,request))
    val resp = response.getOrElse(fail(s"response is $response"))
    resp should not be null

    (countQueryResult eq resp.singleNodeResult) should be(false)

    //validate the DB

    queryDef.expr.get.asInstanceOf[Term]

    //We should have one row in the shrine_query table, for the query just performed
    val Some(queryHistory) = AdapterQueryHistoryDb.db.selectQueryHistoryByQueryIdIO(expectedQueryId).unsafeRunSync()
    val queryRow = queryHistory.toShrineQuery
    queryRow.dateCreated should not be null
  }

  /*@Test SHRINE-3578
  def testProcessRawCrcRunQueryResponseCountAndBreakdownQuery: Unit = afterCreatingTables {
    val allBreakdownTypes = DefaultBreakdownResultOutputTypes.toSet

    val breakdownTypes = Seq(PATIENT_GENDER_COUNT_XML)

    val outputTypes = Set(PATIENT_COUNT_XML) ++ breakdownTypes

    val translator = new QueryDefinitionTranslator(ExpressionTranslator(Map("network" -> Set("local1a", "local1b"))))

    val request = RunQueryRequest(projectId, 1.second, authn, expectedNetworkQueryId, outputTypes, queryDef)

    val networkAuthn = AuthenticationInfo("some-domain", "username", Credential("sadasdasdasd", false))

    val broadcastMessage = BroadcastMessage(queryId, networkAuthn, request)

    val breakdownQueryResults = breakdownTypes.zipWithIndex.map {
      case (rt, i) =>
        countQueryResult.withId(resultId + i + 1).withResultType(rt)
    }

    val singleNodeResults = toQueryResultMap(countQueryResult +: breakdownQueryResults)

    val rawRunQueryResponse = RawCrcRunQueryResponse(
        queryId = queryId,
        createDate = XmlDateHelper.now,
        userId = request.authn.username,
        groupId = request.authn.domain,
        requestXml = request.queryDefinition,
        queryInstanceId = otherNetworkId,
        singleNodeResults = singleNodeResults)

    //Set up our mock CRC
    val poster = Poster("crc-url", new HttpClient {
      def post(input: String, url: String): HttpResponse = HttpResponse.ok {
        (RunQueryRequest.fromI2b2String(allBreakdownTypes)(input) orElse ReadResultRequest.fromI2b2String(allBreakdownTypes)(input)).get match {
          case runQueryReq: RunQueryRequest => rawRunQueryResponse.toI2b2String
          case readResultReq: ReadResultRequest => ReadResultResponse(xmlResultId = 42L, metadata = breakdownQueryResults.head, data = I2b2ResultEnvelope(PATIENT_GENDER_COUNT_XML, dummyBreakdownData)).toI2b2String
          case _ => sys.error(s"Unknown request: '$input'") //Fail loudly
        }
      }
    })

    val adapter = RunQueryAdapter(
      poster = poster,
      dao = dao,
      hiveCredentials = hiveCredentials,
      conceptTranslator = translator,
      doObfuscation = false,
      runQueriesImmediately = true,
      breakdownTypes = DefaultBreakdownResultOutputTypes.toSet,
      botCountTimeThresholds = Seq.empty,
      obfuscator = Obfuscator(1,1.3,3),
    )

    val resp = adapter.processRawCrcRunQueryResponse(networkAuthn, request, rawRunQueryResponse).asInstanceOf[RunQueryResponse]

    resp should not be (null)

    //Validate the response

    resp.createDate should not be(null)
    resp.groupId should be(request.authn.domain)
    resp.userId should be(request.authn.username)
    resp.queryId should be(queryId)
    resp.queryInstanceId should be(otherNetworkId)
    resp.requestXml should equal(request.queryDefinition)

    (countQueryResult eq resp.singleNodeResult) should be(false)
    within3(resp.singleNodeResult.setSize, countQueryResult.setSize) should be(true)

    resp.singleNodeResult.resultType.get should equal(PATIENT_COUNT_XML)
    resp.singleNodeResult.breakdowns.keySet should equal(Set(PATIENT_GENDER_COUNT_XML))

    val breakdownEnvelope = resp.singleNodeResult.breakdowns.values.head

    breakdownEnvelope.resultType should equal(PATIENT_GENDER_COUNT_XML)
    breakdownEnvelope.data.keySet should equal(dummyBreakdownData.keySet)

    //All breakdowns are obfuscated
    for {
      (key, value) <- breakdownEnvelope.data
    } {
      within3(value, dummyBreakdownData(key)) should be(true)
    }

    //validate the DB

    val expectedNetworkTerm = queryDef.expr.get.asInstanceOf[Term]

    //We should have one row in the shrine_query table, for the query just performed
    val Seq(queryRow) = list(queryRows)

    {
      queryRow.dateCreated should not be (null)
      queryRow.domain should equal(request.authn.domain)
      queryRow.name should equal(queryDef.name)
      queryRow.localId should equal(expectedLocalMasterId)
      queryRow.networkId should equal(expectedNetworkQueryId)
      queryRow.username should equal(authn.username)
      queryRow.queryDefinition.expr.get should equal(expectedNetworkTerm)
    }

    //We should have one row in the count_result table, with the right obfuscated value, which is within the expected amount from the original count
    val Seq(countRow) = list(countResultRows)

    {
      countRow.creationDate should not be (null)
    }

    val breakdownRows @ Seq(xRow, yRow, zRow) = list(breakdownResultRows)

    breakdownRows.map(_.dataKey).toSet should equal(dummyBreakdownData.keySet)
  }*/

  //NB: See https://open.med.harvard.edu/jira/browse/SHRINE-745
  @Test
  def testParseAltErrorXml():Unit = {
    val adapter = RunQueryInterrogator(
      poster = Poster("crc-url", null),
      hiveCredentials = hiveCredentials,
      conceptTranslator = null,
      doObfuscation = false,
      runQueriesImmediately = false,
      breakdownTypes = DefaultBreakdownResultOutputTypes.toSet,
      botCountTimeThresholds = Seq.empty,
      obfuscator = Obfuscator(ObfuscatingParameters(5,6.5,10,10))
    )

    val outputTypes = Set(PATIENT_COUNT_XML)
    val request = i2b2Protocol.RunQueryRequest(projectId, 1.second, authn, expectedQueryId, outputTypes, queryDef)
    val networkAuthn = AuthenticationInfo("Demo", "username", Credential("sadasdasdasd", isToken = false))

    val resp: Either[ErrorResponse, RunQueryResponse] = adapter.runQueryClientClient.parseShrineErrorResponseWithFallback(altI2b2ErrorXml,expectedQueryId,adapter.parseShrineResponse(networkAuthn,request))

    resp should not be null

    resp.left.toOption.get.errorMessage should be("Query result instance id 3126 not found")
  }

  @Test
  def testParseInternalCrcNonStandardErrorXml():Unit = {
    val xml = {
      <ns5:response xmlns:ns2="http://www.i2b2.org/xsd/hive/pdo/1.1/" xmlns:ns4="http://www.i2b2.org/xsd/cell/crc/psm/1.1/" xmlns:ns3="http://www.i2b2.org/xsd/cell/crc/pdo/1.1/" xmlns:tns="http://axis2.crc.i2b2.harvard.edu" xmlns:ns9="http://www.i2b2.org/xsd/cell/ont/1.1/" xmlns:ns5="http://www.i2b2.org/xsd/hive/msg/1.1/" xmlns:ns6="http://www.i2b2.org/xsd/cell/crc/psm/querydefinition/1.1/" xmlns:ns7="http://www.i2b2.org/xsd/cell/crc/psm/analysisdefinition/1.1/" xmlns:ns10="http://www.i2b2.org/xsd/hive/msg/result/1.1/" xmlns:ns8="http://www.i2b2.org/xsd/cell/pm/1.1/">
        <message_header>
          <i2b2_version_compatible>1.1</i2b2_version_compatible>
          <hl7_version_compatible>2.4</hl7_version_compatible>
          <sending_application>
            <application_name>edu.harvard.i2b2.crc</application_name>
            <application_version>1.4</application_version>
          </sending_application>
          <sending_facility>
            <facility_name>i2b2 Hive</facility_name>
          </sending_facility>
          <receiving_application>
            <application_name>i2b2web</application_name>
            <application_version>1.4</application_version>
          </receiving_application>
          <receiving_facility>
            <facility_name>i2b2 Hive</facility_name>
          </receiving_facility>
          <message_control_id>
            <instance_num>1</instance_num>
          </message_control_id>
          <project_id>Demo</project_id>
        </message_header>
        <response_header>
          <info>Log information</info>
          <result_status>
            <status type="ERROR">Message error connecting Project Management cell</status>
            <polling_url interval_ms="100"/>
          </result_status>
        </response_header>
        <message_body>
          <ns4:psmheader>
            <user group="i2b2demo" login="admin">admin</user>
            <patient_set_limit>0</patient_set_limit>
            <estimated_time>0</estimated_time>
            <request_type>CRC_QRY_runQueryInstance_fromQueryDefinition</request_type>
          </ns4:psmheader>
          <ns4:request xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:type="ns4:query_definition_requestType">
            <query_definition>
              <query_name>Age</query_name>
              <specificity_scale>0</specificity_scale>
              <panel name="Panel_31">
                <panel_number>1</panel_number>
                <panel_accuracy_scale>0</panel_accuracy_scale>
                <invert>0</invert>
                <total_item_occurrences>1</total_item_occurrences>
                <item>
                  <hlevel>2</hlevel>
                  <item_name>Age</item_name>
                  <item_key>\\i2b2\i2b2\Demographics\Age\</item_key>
                  <dim_tablename>concept_dimension</dim_tablename>
                  <dim_columnname>concept_path</dim_columnname>
                  <dim_dimcode>\i2b2\Demographics\Age\</dim_dimcode>
                  <dim_columndatatype>T</dim_columndatatype>
                  <facttablecolumn>concept_cd</facttablecolumn>
                  <item_is_synonym>false</item_is_synonym>
                </item>
              </panel>
            </query_definition>
            <result_output_list>
              <result_output priority_index="1" name="PATIENTSET"/>
            </result_output_list>
          </ns4:request>
        </message_body>
      </ns5:response>
    }.toString

    val adapter = RunQueryInterrogator(
      poster = Poster("crc-url", null),
      hiveCredentials = hiveCredentials,
      conceptTranslator = null,
      doObfuscation = false,
      runQueriesImmediately = true,
      breakdownTypes = DefaultBreakdownResultOutputTypes.toSet,
      botCountTimeThresholds = Seq.empty,
      obfuscator = Obfuscator(ObfuscatingParameters(5, 6.5, 10, 10))
    )

    val outputTypes = Set(PATIENT_COUNT_XML)
    val request = i2b2Protocol.RunQueryRequest(projectId, 1.second, authn, expectedQueryId, outputTypes, queryDef)
    val networkAuthn = AuthenticationInfo("Demo", "username", Credential("sadasdasdasd", isToken = false))

    //expect the ugly net.shrine.xml.MissingChildNodeException for this strange response when i2b2's CRC can't talk to its own PM
    val x: MissingChildNodeException = intercept[MissingChildNodeException] {
      val resp: Either[ErrorResponse, RunQueryResponse] = adapter.runQueryClientClient.parseShrineErrorResponseWithFallback(xml, expectedQueryId, adapter.parseShrineResponse(networkAuthn, request))
      resp
    }
    assert(x.message.contains("<message_body xmlns"))
  }

  @Test
  def testTranslateQueryDefinitionXml():Unit = {
    val localTerms = Set("local1a", "local1b")
    val mappings = Map("network" -> localTerms)
    val translator = new QueryDefinitionTranslator(ExpressionTranslator(mappings))

    val adapter = RunQueryInterrogator(
      poster = Poster("crc-url", MockHttpClient),
      hiveCredentials = null,
      conceptTranslator = translator,
      doObfuscation = false,
      runQueriesImmediately = true,
      breakdownTypes = DefaultBreakdownResultOutputTypes.toSet,
      botCountTimeThresholds = Seq.empty,
      obfuscator = Obfuscator(ObfuscatingParameters(5,6.5,10,10))
    )

    val queryDefinition = I2b2QueryDefinition("10-17 years old@14:39:20", OccuranceLimited(1, Term("network", "networkName")))

    val newDef = adapter.conceptTranslator.translate(queryDefinition)

    val expected = I2b2QueryDefinition("10-17 years old@14:39:20", Or(Term("local1a", "networkName"), Term("local1b", "networkName")))

    newDef should equal(expected)
  }

  @Test
  def testRunCountQueryLater(): Unit =  {
    val translator = new QueryDefinitionTranslator(ExpressionTranslator(Map("foo" -> Set("bar"))))

    val adapter = RunQueryInterrogator(
      poster = Poster("crc-url", MockHttpClient),
      hiveCredentials = hiveCredentials,
      conceptTranslator = translator,
      doObfuscation = false,
      runQueriesImmediately = false,
      breakdownTypes = DefaultBreakdownResultOutputTypes.toSet,
      botCountTimeThresholds = Seq.empty,
      obfuscator = Obfuscator(ObfuscatingParameters(5,6.5,10,10))
    )

    val networkAuthn = AuthenticationInfo("network.domain", "nu", Credential("np", isToken = false))

    import scala.concurrent.duration.DurationInt

    val req: RunQueryRequest = i2b2Protocol.RunQueryRequest(projectId, 1.second, networkAuthn, expectedQueryId, Set(PATIENT_COUNT_XML), queryDef)
    val resultReceived:ResultProgress = ResultProgress(queryId = new QueryId(req.networkQueryId), adapterNodeId = new NodeId(0L), adapterNodeName = new NodeName("General Hospital"), resultMetadata = ResultMetadata(Option(adapter.obfuscator.obfuscatorParameters)))

    val output:UpdateResult = adapter.runQueryNowOrLater(resultReceived,req).unsafeRunSync()

    val expectedUpdateResult = UpdateResultWithProgress(
      result = resultReceived.withStatus(QueuedForManualSubmission, Some("Adapter configured to submit queries manually"), resultMetadata =  ResultMetadata(obfuscatingParameters = Option(adapter.obfuscator.obfuscatorParameters))).
        withChangeDate(output.result.versionInfo.changeDate)
    )

    assertResult(expectedUpdateResult)(output)

    val Some(queryHistory) = AdapterQueryHistoryDb.db.selectQueryHistoryByQueryIdIO(expectedQueryId).unsafeRunSync()

    val storedQuery = queryHistory.toShrineQuery
    storedQuery.dateCreated should not be null // :\
    storedQuery.domain should equal(networkAuthn.domain)
    storedQuery.i2b2MasterQueryId should equal((-1L).toString)
    storedQuery.name should equal(queryDef.name)
    storedQuery.networkId should equal(expectedQueryId)
    storedQuery.queryDefinition should equal(queryDef)
    storedQuery.username should equal(networkAuthn.username)
  }

  private def doTestRegularCountQuery(status: QueryResult.StatusType, count: Long, expectedUpdate:UpdateResult ) =  {

    require(!status.isError)

    val countQueryResultToUse = countQueryResult.copy(statusType = status, setSize = count)

    val update: UpdateResult = runTestQuery(justCounts){
      import RawCrcRunQueryResponse.toQueryResultMap

      RawCrcRunQueryResponse(queryId, now, userId, groupId, queryDef, instanceId, toQueryResultMap(Seq(countQueryResultToUse))).toI2b2String
    }.unsafeRunSync()

    val adjustedExpectedUpdate = expectedUpdate.withUntestableFieldsFrom(update)

    assertResult(adjustedExpectedUpdate)(update)

    val Some(savedQuery) = AdapterQueryHistoryDb.db.findResultsForIO(expectedQueryId).unsafeRunSync()

    savedQuery.wasRun should equal(true)

    savedQuery.networkQueryId should equal(expectedQueryId)
    savedQuery.breakdowns should equal(Nil)
    savedQuery.count.creationDate should not be null
    savedQuery.count.localId should equal(countQueryResultToUse.resultId)
    //savedQuery.count.resultId should equal(resultId) TODO: REVISIT
    savedQuery.count.statusType should equal(status)

    if (status.isDone && !status.isError) {
      savedQuery.count.data.get.startDate should not be null
      savedQuery.count.data.get.endDate should not be null

      ObfuscatorTest.within3(savedQuery.count.data.get.obfuscatedValue, count) should be(true)
    } else {
      savedQuery.count.data should be(None)
    }
  }

  @Test
  def testRegularCountQuery(): Unit = {
    val expectedUpdate:UpdateResult = UpdateResultWithCount(
      CountResult(id = new ResultId(7298689623286747044L),
        versionInfo = VersionInfo(new ProtocolVersion(2),
        new ShrineVersion("4.2.0-SNAPSHOT"),
        new ItemVersion(2),
        new DateStamp(1682690764326L),
        new DateStamp(1682690764378L)),
        queryId = new QueryId(999L),
        adapterNodeId = new NodeId(0L),
        adapterNodeName = new NodeName("General Hospital"),
        statusMessage = Option("FINISHED"),
        crcQueryInstanceId = Option(456L), count = 17,
        resultMetadata = ResultMetadata(Option(ObfuscatingParameters(1,1.3,3,10))),
        breakdowns = None
      )
    )

    doTestRegularCountQuery(QueryResult.StatusType.Finished, countQueryResult.setSize,expectedUpdate)
  }

  @Test
  def testRegularCountQueryComesBackProcessing(): Unit = {
    val expectedUpdate: UpdateResult = UpdateResultWithProgress(
      ResultProgress(id = new ResultId(7298689623286747044L),
        versionInfo = VersionInfo(new ProtocolVersion(2),
          new ShrineVersion("4.2.0-SNAPSHOT"),
          new ItemVersion(2), new DateStamp(1682690764326L),
          new DateStamp(1682690764378L)),
        queryId = new QueryId(999L),
        adapterNodeId = new NodeId(0L),
        adapterNodeName = new NodeName("General Hospital"),
        status = QueuedByCRC,
        statusMessage = Option("PROCESSING"),
        crcQueryInstanceId = Option(456L),
        resultMetadata = ResultMetadata(Option(ObfuscatingParameters(1,1.3,3,10)))
      )
    )
    doTestRegularCountQuery(QueryResult.StatusType.Processing, -1L,expectedUpdate)
  }

  @Test
  def testRegularCountQueryComesBackQueued(): Unit = {
    val expectedUpdate: UpdateResult = UpdateResultWithProgress(
      ResultProgress(id = new ResultId(7298689623286747044L),
        versionInfo = VersionInfo(new ProtocolVersion(2),
          new ShrineVersion("4.2.0-SNAPSHOT"),
          new ItemVersion(2),
          new DateStamp(1682690764326L),
          new DateStamp(1682690764378L)),
        queryId = new QueryId(999L),
        adapterNodeId = new NodeId(0L),
        adapterNodeName = new NodeName("General Hospital"),
        status = QueuedByCRC,
        statusMessage = Option("QUEUED"),
        crcQueryInstanceId = Option(456L),
        resultMetadata = ResultMetadata(Option(ObfuscatingParameters(1,1.3,3,10)))
      )
    )
    doTestRegularCountQuery(QueryResult.StatusType.Queued, -1L,expectedUpdate)
  }

  @Test
  def testRegularCountQueryComesBackError(): Unit =  {
    val errorQueryResult = QueryResult.errorResult(Some("some-description"), "some-status-message",TestProblem())
    val outputTypes: Set[ResultOutputType] = justCounts

    val updateIO: IO[UpdateResult] = runTestQuery(outputTypes) {
      import RawCrcRunQueryResponse.toQueryResultMap

      RawCrcRunQueryResponse(queryId, now, userId, groupId, queryDef, instanceId, toQueryResultMap(Seq(errorQueryResult))).toI2b2String
    }
    val update: UpdateResult = updateIO.unsafeRunSync()

    update match {
      case _:UpdateResultWithError => ; //this is expected
      case _ => fail(s"Expected an UpdateResultWithError but got $update")
    }
  }

  @Test
  def testErrorResponsesArePassedThrough(): Unit = {
    val errorResponse = ErrorResponse(XmlProblemDigest.create(TestProblem(summary = "blarg!")))

    val update = runTestQuery(Set(PATIENT_COUNT_XML)) {
      errorResponse.toI2b2String
    }.unsafeRunSync()

    update match {
      case _: UpdateResultWithError => ; //this is expected
      case _ => fail(s"Expected an UpdateResultWithError but got $update")
    }
  }

  private def runTestQuery(outputTypes: Set[ResultOutputType])(i2b2XmlToReturn: => String): IO[UpdateResult] = {
    runTestQuery(outputTypes,MockHttpClient(i2b2XmlToReturn))
  }

  private def runTestQuery(outputTypes: Set[ResultOutputType], httpClient: HttpClient): IO[UpdateResult] = {
    val translator = new QueryDefinitionTranslator(ExpressionTranslator(Map("foo" -> Set("bar"))))

    //NB: Don't obfuscate, for simpler testing
    val adapter = RunQueryInterrogator(
      poster = Poster("crc-url", httpClient),
      hiveCredentials = hiveCredentials,
      conceptTranslator = translator,
      doObfuscation = false,
      runQueriesImmediately = true,
      breakdownTypes = DefaultBreakdownResultOutputTypes.toSet,
      botCountTimeThresholds = Seq.empty,
      obfuscator = Obfuscator(ObfuscatingParameters(1, 1.3, 3, 10))
    )

    import scala.concurrent.duration.DurationInt

    val req = i2b2Protocol.RunQueryRequest(projectId, 1.second, authn, expectedQueryId, outputTypes, queryDef)

    val resultReceived: ResultProgress = ResultProgress(queryId = new QueryId(req.networkQueryId), adapterNodeId = new NodeId(0L), adapterNodeName = new NodeName("General Hospital"), resultMetadata = ResultMetadata(Option(adapter.obfuscator.obfuscatorParameters)))

    adapter.runQueryNowOrLater(resultReceived,req)
  }
  
  val network: Network = Network(
    networkName = "testNetwork",
    hubQueueName = MomQueueName("testHub"),
    adminEmail = "yourname@example.com",
    momId = "testNetwork",
    awsSqsConfig = None,
    kafkaConfig = None
  )

  val node: Node = {
    val thisNodeKey = ConfigSource.config.getString("shrine.nodeKey")

    Node.create(thisNodeKey,thisNodeKey,s"$thisNodeKey.example.co.uk", adminEmail = "email@foo",momId = "testNode1")
  }

  @Before
  def beforeEach(): Unit = {
    AdapterQueryHistoryDb.db.createTables()

    val setUp = for{
      _ <- IO(HubDb.db.createTables())
      _ <- HubDb.db.upsertNetworkIO(network)
      _ <-  HubDb.db.upsertNodeIO(node)
      _ <- HubLifecycle.queuesFromDatabaseIO()
    } yield ()
    setUp.unsafeRunSync()
  }

  @After
  def afterEach(): Unit = {
    AdapterQueryHistoryDb.db.dropTables()

    HubDb.db.dropTables()
  }
}

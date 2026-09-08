package net.shrine.adapter.i2b2Protocol

import net.shrine.problem.{TestProblem, XmlProblemDigest}
import net.shrine.protocol.i2b2.query.{I2b2QueryDefinition, Term}
import net.shrine.protocol.i2b2.{DefaultBreakdownResultOutputTypes, QueryResult, ResultOutputType}
import net.shrine.util.ShouldMatchersForJUnit
import net.shrine.xml.{XmlDateHelper, XmlUtil}
import org.junit.Test

import scala.util.Success
import scala.xml.NodeSeq

/**
 * @author clint
 * @since Nov 5, 2012
 */
//noinspection UnitMethodIsParameterless,NameBooleanParameters,ScalaUnnecessaryParentheses
final class ShrineResponseTest extends ShouldMatchersForJUnit {
  @Test
  def testFromXml: Unit = {
    ShrineResponse.fromXml(DefaultBreakdownResultOutputTypes.toSet)(null: NodeSeq).isFailure should be(true)
    ShrineResponse.fromXml(DefaultBreakdownResultOutputTypes.toSet)(NodeSeq.Empty).isFailure should be(true)

    def roundTrip(response: ShrineResponse): Unit = {
      val unmarshalled = ShrineResponse.fromXml(DefaultBreakdownResultOutputTypes.toSet)(response.toXml)

      unmarshalled.get.getClass should equal(response.getClass)
      unmarshalled should not be (null)
      unmarshalled should equal(Success(response))
    }

    val queryResult1 = QueryResult(
      resultId = 1L,
      instanceId = 2342L,
      resultType = Some(ResultOutputType.PATIENT_COUNT_XML),
      setSize = 123L,
      startDate = None,
      endDate = None,
      description = None,
      statusType = QueryResult.StatusType.Finished,
      statusMessage = None)

    roundTrip(ReadQueryResultResponse(123L, queryResult1))
    roundTrip(ReadQueryInstancesResponse(12345L, "userId", "groupId", Seq.empty))
    roundTrip(RunQueryResponse(38957L, XmlDateHelper.now, "userId", "groupId", I2b2QueryDefinition("foo", Term("bar", "barName")), 2342L, queryResult1))

    roundTrip(ErrorResponse(XmlProblemDigest.create(TestProblem("errorMessage"))))
  }

  @Test
  def testToXml: Unit = {
    val response = new FooResponse

    response.toXmlString should equal("<foo></foo>")
  }

  @Test
  def testToI2b2: Unit = {
    val expected = XmlUtil.stripWhitespace(<ns4:response xmlns:ns2="http://www.i2b2.org/xsd/hive/pdo/1.1/" xmlns:ns3="http://www.i2b2.org/xsd/cell/crc/pdo/1.1/" xmlns:ns4="http://www.i2b2.org/xsd/hive/msg/1.1/" xmlns:ns5="http://www.i2b2.org/xsd/cell/crc/psm/1.1/" xmlns:ns6="http://www.i2b2.org/xsd/cell/pm/1.1/" xmlns:ns7="http://sheriff.shrine.net/" xmlns:ns8="http://www.i2b2.org/xsd/cell/crc/psm/querydefinition/1.1/" xmlns:ns9="http://www.i2b2.org/xsd/cell/crc/psm/analysisdefinition/1.1/" xmlns:ns10="http://www.i2b2.org/xsd/cell/ont/1.1/" xmlns:ns11="http://www.i2b2.org/xsd/hive/msg/result/1.1/">
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
                                                 <status type="DONE">DONE</status>
                                               </result_status>
                                             </response_header>
                                             <message_body>
                                               <foo></foo>
                                             </message_body>
                                           </ns4:response>)

    val response = new FooResponse

    response.toI2b2String should equal(expected.toString())
  }

  private final class FooResponse extends ShrineResponse {
    protected override def i2b2MessageBody: NodeSeq = <foo></foo>

    override def toXml: NodeSeq = i2b2MessageBody
  }
}
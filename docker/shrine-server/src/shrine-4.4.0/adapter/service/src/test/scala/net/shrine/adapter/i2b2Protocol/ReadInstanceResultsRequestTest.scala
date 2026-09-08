package net.shrine.adapter.i2b2Protocol

import net.shrine.protocol.i2b2.{DefaultBreakdownResultOutputTypes, ShrineRequestValidator}
import net.shrine.xml.XmlUtil
import org.junit.Test

import scala.xml.Node

/**
 * @author Bill Simons
 * @since 3/17/11
 * @see http://cbmi.med.harvard.edu
 * @see http://chip.org
 *       <p/>
 *       NOTICE: This software comes with NO guarantees whatsoever and is
 *       licensed as Lgpl Open Source
 * @see http://www.gnu.org/licenses/lgpl.html
 */
final class ReadInstanceResultsRequestTest extends ShrineRequestValidator {
  private val shrineNetworkQueryId = 1105351618885108053L

  override def messageBody: Node = XmlUtil.stripWhitespace {
    <message_body>
      <ns4:psmheader>
        <user login={ username }>{ username }</user>
        <patient_set_limit>0</patient_set_limit>
        <estimated_time>0</estimated_time>
        <request_type>CRC_QRY_getQueryResultInstanceList_fromQueryInstanceId</request_type>
      </ns4:psmheader>
      <ns4:request xsi:type="ns4:instance_requestType" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
        <query_instance_id>{ shrineNetworkQueryId }</query_instance_id>
      </ns4:request>
    </message_body>
  }

  private val readInstanceResultsRequest = XmlUtil.stripWhitespace {
    <readInstanceResults>
      { requestHeaderFragment }
      <shrineNetworkQueryId>{ shrineNetworkQueryId }</shrineNetworkQueryId>
    </readInstanceResults>
  }

  @Test
  override def testFromI2b2():Unit = {
    val translatedRequest = ReadInstanceResultsRequest.fromI2b2(DefaultBreakdownResultOutputTypes.toSet)(request).get

    validateRequestWith(translatedRequest) {
      translatedRequest.queryInstanceId should equal(shrineNetworkQueryId)
    }
  }

  @Test
  override def testShrineRequestFromI2b2(): Unit = {
    val shrineRequest = CrcRequest.fromI2b2(DefaultBreakdownResultOutputTypes.toSet)(request).get

    shrineRequest.isInstanceOf[ReadInstanceResultsRequest] should be(true)
  }

  @Test
  override def testToI2b2():Unit = {
    ReadInstanceResultsRequest(projectId, waitTime, authn, shrineNetworkQueryId).toI2b2 should equal(request)
  }

  @Test
  override def testToXml():Unit = {
    ReadInstanceResultsRequest(projectId, waitTime, authn, shrineNetworkQueryId).toXml should equal(readInstanceResultsRequest)
  }

  @Test
  override def testFromXml():Unit = {
    val actual = ReadInstanceResultsRequest.fromXml(DefaultBreakdownResultOutputTypes.toSet)(readInstanceResultsRequest).get

    validateRequestWith(actual) {
      actual.queryInstanceId should equal(shrineNetworkQueryId)
    }
  }

  @Test
  def testShrineRequestFromXml():Unit = {
    ShrineRequest.fromXml(DefaultBreakdownResultOutputTypes.toSet)(readInstanceResultsRequest).get.isInstanceOf[ReadInstanceResultsRequest] should be(true)
  }
}
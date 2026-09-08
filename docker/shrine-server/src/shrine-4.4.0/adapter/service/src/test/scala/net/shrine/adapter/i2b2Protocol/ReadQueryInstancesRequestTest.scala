package net.shrine.adapter.i2b2Protocol

import net.shrine.protocol.i2b2.{DefaultBreakdownResultOutputTypes, ShrineRequestValidator}
import net.shrine.xml.XmlUtil
import org.junit.Test

import scala.xml.{Node, NodeSeq}

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
final class ReadQueryInstancesRequestTest extends ShrineRequestValidator {
  val queryId = 2422297885846950097L

  override def messageBody: NodeSeq = XmlUtil.stripWhitespace {
    <message_body>
      <ns4:psmheader>
        <user login={ username }>{ username }</user>
        <patient_set_limit>0</patient_set_limit>
        <estimated_time>0</estimated_time>
        <request_type>CRC_QRY_getQueryInstanceList_fromQueryMasterId</request_type>
      </ns4:psmheader>
      <ns4:request xsi:type="ns4:master_requestType" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
        <query_master_id>{ queryId }</query_master_id>
      </ns4:request>
    </message_body>
  }

  val readQueryInstancesRequest: Node = XmlUtil.stripWhitespace {
    <readQueryInstances>
      { requestHeaderFragment }
      <queryId>{ queryId }</queryId>
    </readQueryInstances>
  }

  @Test
  override def testFromI2b2():Unit = {
    val translatedRequest = ReadQueryInstancesRequest.fromI2b2(DefaultBreakdownResultOutputTypes.toSet)(request).get

    validateRequestWith(translatedRequest) {
      translatedRequest.i2b2MasterQueryId should equal(queryId)
    }
  }

  @Test
  override def testShrineRequestFromI2b2(): Unit = {
    val shrineRequest = CrcRequest.fromI2b2(DefaultBreakdownResultOutputTypes.toSet)(request).get

    shrineRequest.isInstanceOf[ReadQueryInstancesRequest] should be(true)
  }

  @Test
  override def testToXml():Unit = {
    ReadQueryInstancesRequest(projectId, waitTime, authn, queryId).toXml should equal(readQueryInstancesRequest)
  }

  @Test
  override def testToI2b2():Unit = {
    ReadQueryInstancesRequest(projectId, waitTime, authn, queryId).toI2b2 should equal(request)
  }

  @Test
  override def testFromXml():Unit = {
    val actual = ReadQueryInstancesRequest.fromXml(DefaultBreakdownResultOutputTypes.toSet)(readQueryInstancesRequest).get

    validateRequestWith(actual) {
      actual.i2b2MasterQueryId should equal(queryId)
    }
  }

  @Test
  def testShrineRequestFromXml():Unit = {
    ShrineRequest.fromXml(DefaultBreakdownResultOutputTypes.toSet)(readQueryInstancesRequest).get.isInstanceOf[ReadQueryInstancesRequest] should be(true)
  }
}
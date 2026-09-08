package net.shrine.adapter.i2b2Protocol

import net.shrine.protocol.i2b2.query.{I2b2QueryDefinition, Term}
import net.shrine.protocol.i2b2.{AuthenticationInfo, Credential, DefaultBreakdownResultOutputTypes, ResultOutputType, ShrineRequest}
import net.shrine.util.ShouldMatchersForJUnit
import net.shrine.xml.XmlUtil
import org.junit.Test

import scala.concurrent.duration.{Duration, DurationLong}

/**
 * @author clint
 * @since Mar 22, 2013
 */
final class ShrineRequestTest extends ShouldMatchersForJUnit {
  @Test
  def testFromXmlThrowsOnBadInput():Unit = {
    intercept[Exception] {
      ShrineRequest.fromXml(DefaultBreakdownResultOutputTypes.toSet)(XmlUtil.loadString("asdasdasddas"))
    }
  }
  
  @Test
  def testFromXml():Unit = {
    val projectId = "salkdjksaljdkla"

    val waitTime: Duration = 98374L.milliseconds
    val userId = "foo-user"
    val authn = AuthenticationInfo("blarg-domain", userId, Credential("sajkhdkjsadh", isToken = true))
    val queryId = 485794359L
    val queryName = "saljkd;salda"
    val outputTypes = ResultOutputType.nonBreakdownTypes.toSet
    val queryDefinition = I2b2QueryDefinition(queryName, Term("oiweruoiewkldfhsofi", "oiweruoiewkldfhsofiName"))
    val localResultId = "aoiduaojsdpaojcmsal"
    
    def doMarshallingRoundTrip(req: ShrineRequest): Unit = {
      val xml = req.toXml
      
      val unmarshalled = ShrineRequest.fromXml(DefaultBreakdownResultOutputTypes.toSet)(xml)
      
      req match {
        case _ => unmarshalled.get should equal(req)
      }
    }
    
    doMarshallingRoundTrip(ReadInstanceResultsRequest(projectId, waitTime, authn, queryId))
    doMarshallingRoundTrip(ReadQueryInstancesRequest(projectId, waitTime, authn, queryId))
    doMarshallingRoundTrip(RunQueryRequest(projectId, waitTime, authn, queryId, outputTypes, queryDefinition))
    doMarshallingRoundTrip(ReadResultRequest(projectId, waitTime, authn, localResultId))
  }
}
package net.shrine.adapter.i2b2Protocol

import net.shrine.protocol.i2b2.{AuthenticationInfo, Credential, DefaultBreakdownResultOutputTypes}
import net.shrine.util.ShouldMatchersForJUnit
import net.shrine.xml.XmlUtil
import org.junit.Test

/**
 * @author clint
 * @since Nov 2, 2012
 */
final class ReadQueryResultRequestTest extends ShouldMatchersForJUnit {

  private val authn = AuthenticationInfo("some-domain", "some-user", Credential("salkfa", isToken = false))

  import scala.concurrent.duration.DurationInt
  
  private val req = ReadQueryResultRequest("some-project-id", 1.second, authn, 123)

  @Test
  def testToXml():Unit = {
    val expected = XmlUtil.stripWhitespace {
      <readQueryResult>
        <projectId>some-project-id</projectId>
        <waitTimeMs>1000</waitTimeMs>
        { authn.toXml }
        <queryId>123</queryId>
      </readQueryResult>
    }.toString

    req.toXmlString should equal(expected)
  }

  @Test
  def testXmlRoundTrip():Unit = {
    ReadQueryResultRequest.fromXml(DefaultBreakdownResultOutputTypes.toSet)(req.toXml).get should equal(req)
  }
}
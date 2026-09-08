package net.shrine.adapter.i2b2Protocol

import junit.framework.TestCase
import net.shrine.protocol.i2b2.{AuthenticationInfo, Credential, RequestHeader}
import net.shrine.util.ShouldMatchersForJUnit
import net.shrine.xml.XmlUtil
import org.junit.Test

/**
 * @author clint
 * @since Aug 16, 2012
 */
final class ShrineRequestUnmarshallerTest extends TestCase with ShouldMatchersForJUnit {
  private val projectId = "jksahjksafhkafkla"
    
  import scala.concurrent.duration.DurationInt
    
  private val waitTime = 12345.milliseconds
  
  private val authn = AuthenticationInfo("some-domain", "some-username", Credential("ksaljfksadlfjklsd", isToken = false))
  
  private val xml = XmlUtil.stripWhitespace(
    <foo>
      <projectId>{ projectId }</projectId>
      <waitTimeMs>{ waitTime.toMillis}</waitTimeMs>
      { authn.toXml }
    </foo>)
  
  final class Foo
    
  private object MockUnmarshaller extends ShrineRequestUnmarshaller
    
  @Test
  def testShrineHeader():Unit = {
    val RequestHeader(actualProjectId, actualWaitTimeMs, actualAuthn) = MockUnmarshaller.shrineHeader(xml).get
    
    actualProjectId should equal(projectId)
    actualWaitTimeMs should equal(waitTime)
    actualAuthn should equal(authn)
  }
  
  @Test
  def testShrineProjectId():Unit = {
    MockUnmarshaller.shrineProjectId(xml).get should equal(projectId)
  }
  
  @Test
  def testShrineWaitTimeMs():Unit = {
    MockUnmarshaller.shrineWaitTime(xml).get should equal(waitTime)
  }
  
  @Test
  def testShrineAuthenticationInfo():Unit = {
    MockUnmarshaller.shrineAuthenticationInfo(xml).get should equal(authn)
  }
}
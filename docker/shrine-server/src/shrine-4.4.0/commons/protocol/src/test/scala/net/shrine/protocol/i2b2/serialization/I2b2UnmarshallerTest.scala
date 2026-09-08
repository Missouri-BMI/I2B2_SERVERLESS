package net.shrine.protocol.i2b2.serialization

import scala.xml.NodeSeq
import org.junit.Test
import net.shrine.util.ShouldMatchersForJUnit
import net.shrine.protocol.i2b2.AuthenticationInfo
import net.shrine.protocol.i2b2.Credential
import net.shrine.protocol.i2b2.RequestHeader
import net.shrine.xml.XmlUtil

/**
 * @author clint
 * @since Aug 16, 2012
 */
final class I2b2UnmarshallerTest extends ShouldMatchersForJUnit {
  private val projectId = "jksahjksafhkafkla"
    
  import scala.concurrent.duration.DurationInt
    
  private val waitTime = 12345.milliseconds
  
  private val authn = AuthenticationInfo("some-domain", "some-username", Credential("ksaljfksadlfjklsd", isToken = false))
  
  private val xml = XmlUtil.stripWhitespace {
    <foo>
      <message_header>
        <project_id>{ projectId }</project_id>
        { authn.toI2b2 }
      </message_header>
      <request_header>
        <result_waittime_ms>{ waitTime.toMillis }</result_waittime_ms>
      </request_header>
    </foo>
  }
  
  final class Foo
    
  private object MockUnmarshaller extends I2b2Unmarshaller[Foo] {
    override def fromI2b2(nodeSeq: NodeSeq): Foo = null
  }
    
  @Test
  def testShrineHeader():Unit = {
    val RequestHeader(actualProjectId, actualWaitTime, actualAuthn) = MockUnmarshaller.i2b2Header(xml).get
    
    actualProjectId should equal(projectId)
    actualWaitTime should equal(waitTime)
    actualAuthn should equal(authn)
  }
  
  @Test
  def testShrineProjectId():Unit = {
    MockUnmarshaller.i2b2ProjectId(xml).get should equal(projectId)
  }
  
  @Test
  def testShrineWaitTimeMs():Unit = {
    MockUnmarshaller.i2b2WaitTime(xml).get should equal(waitTime)
  }
  
  @Test
  def testShrineAuthenticationInfo():Unit = {
    MockUnmarshaller.i2b2AuthenticationInfo(xml).get should equal(authn)
  }
}
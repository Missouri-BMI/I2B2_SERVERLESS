package net.shrine.adapter.i2b2Protocol

import junit.framework.TestCase
import net.shrine.util.ShouldMatchersForJUnit
import org.junit.Test

import scala.xml.NodeSeq

/**
 * @author clint
 * @since Apr 30, 2013
 */
final class NonI2b2ableResponseTest extends TestCase with ShouldMatchersForJUnit {
  object TestResponse extends ShrineResponse with NonI2b2ableResponse {
    def messageBody: NodeSeq = i2b2MessageBody
    
    override def toXml: NodeSeq = ???
  }
  
  @Test
  def testI2b2MessageBody():Unit = {
    intercept[Error] {
      TestResponse.messageBody
    }
  }
  
  @Test
  def testToI2b2():Unit = {
    val testResponseClassName = TestResponse.getClass.getSimpleName
    
    ErrorResponse.fromI2b2(TestResponse.toI2b2).errorMessage.contains(testResponseClassName) should be(true)
  }
}
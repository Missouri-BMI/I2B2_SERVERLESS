package net.shrine.xml

import scala.util.Success
import org.junit.jupiter.api.Assertions.{assertEquals, assertTrue}
import org.junit.jupiter.api.Test

import scala.util.Success
import scala.xml.NodeSeq

/**
 * @author clint
 * @since Feb 3, 2014
 */
final class NodeSeqEnrichmentsTest {
  
  private val xml: NodeSeq = <foo><bar><baz>123</baz><nuh/></bar></foo>
  
  @Test
  def testHelpersChildren():Unit = {
    import net.shrine.xml.NodeSeqEnrichments.Helpers._
    
    assertEquals(<foo/>.children,NodeSeq.Empty)
    
    val xml: NodeSeq = <foo><bar><baz>123</baz><nuh/></bar></foo>
   
    val Seq(childElem) = xml.children
      
    assertEquals(childElem, <bar><baz>123</baz><nuh/></bar>)
    
    assertEquals(xml.children.children,NodeSeq.fromSeq(Seq(<baz>123</baz>, <nuh/>)))
  }
  
  @Test
  def testWithChildNodeSeq():Unit = {
    import net.shrine.xml.NodeSeqEnrichments.Strictness._
    
    assertTrue((xml withChild "glarg").isFailure)
    
    assertTrue((xml withChild "bar" withChild "glarg").isFailure)
    
    val bazAttempt = xml withChild "bar" withChild "baz"
    
    val Success(Seq(bazElem)) = bazAttempt
    
    assertEquals(bazElem, <baz>123</baz>)
  }
}
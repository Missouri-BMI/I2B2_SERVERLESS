package net.shrine.adapter.i2b2Protocol

import net.shrine.util.ShouldMatchersForJUnit
import org.junit.Test

/**
 * @author clint
 * @since Dec 3, 2013
 */
final class NodeIdTest extends ShouldMatchersForJUnit {
  @Test
  def testUnknown():Unit = {
    XmlNodeName.Unknown.name should be("Unknown")
  }
  
  @Test
  def testToXml():Unit = {
    XmlNodeName("foo").toXmlString should equal("<nodeId><name>foo</name></nodeId>")
  }
  
  @Test
  def testFromXml():Unit = {
    XmlNodeName.fromXml("<nodeId><name>foo</name></nodeId>").get should equal(XmlNodeName("foo"))
    
    XmlNodeName.fromXml(Nil).isFailure should be(true)
    
    XmlNodeName.fromXml(<foo/>).isFailure should be(true)
  }
  
  @Test
  def testXmlRoundTrip():Unit = {
    val nodeId = XmlNodeName("foo")
    
    XmlNodeName.fromXml(nodeId.toXml).get should equal(nodeId)
  }
}
package net.shrine.protocol.i2b2

import net.shrine.protocol.i2b2.serialization.{I2b2Marshaller, I2b2Unmarshaller, XmlMarshaller, XmlUnmarshaller}
import org.scalatest.Matchers
import net.shrine.util.ShouldMatchersForJUnit

/**
 * @author clint
 * @since Aug 24, 2012
 */
trait XmlRoundTripper[T <: XmlMarshaller with I2b2Marshaller] { self: ShouldMatchersForJUnit =>
  def doShrineXmlRoundTrip(thing: T, unmarshaller: XmlUnmarshaller[T]): Unit = {
    val xml = thing.toXmlString
    
    val unmarshalled = unmarshaller.fromXml(xml)
    
    unmarshalled should equal(thing)
  }
  
  def doI2b2XmlRoundTrip(thing: T, unmarshaller: I2b2Unmarshaller[T], equalityChecker: (T, T) => Unit = (_ should equal(_))): Unit = {
    val xml = thing.toI2b2String
    
    val unmarshalled = unmarshaller.fromI2b2(xml)
    
    equalityChecker(unmarshalled, thing)
  }
}
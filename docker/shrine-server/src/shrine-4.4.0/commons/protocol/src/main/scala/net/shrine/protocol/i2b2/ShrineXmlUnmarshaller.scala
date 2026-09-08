package net.shrine.protocol.i2b2

import net.shrine.xml.StringEnrichments

import scala.xml.NodeSeq
import scala.util.Try

/**
 * @author clint
 * @since Oct 28, 2014
 */
//todo can be moved when I2b2Result moves to the adapter - when the QEP no longer uses I2b2Result in its database code
trait ShrineXmlUnmarshaller[+T] {
  def fromXml(breakdownTypes: Set[ResultOutputType])(xml: NodeSeq): Try[T]

  import StringEnrichments._
  
  def fromXmlString(breakdownTypes: Set[ResultOutputType])(xmlString: String): Try[T] = {
    xmlString.tryToXml.flatMap(fromXml(breakdownTypes))
  }
}
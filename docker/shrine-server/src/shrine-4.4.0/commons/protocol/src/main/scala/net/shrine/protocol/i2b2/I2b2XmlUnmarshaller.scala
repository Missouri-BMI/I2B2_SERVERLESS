package net.shrine.protocol.i2b2

import net.shrine.xml.StringEnrichments

import scala.xml.NodeSeq
import scala.util.Try

/**
 * @author clint
 * @date Oct 30, 2014
 */
trait I2b2XmlUnmarshaller[+T] {
  def fromI2b2(breakdownTypes: Set[ResultOutputType])(xml: NodeSeq): Try[T]

  import StringEnrichments._
  
  def fromI2b2String(breakdownTypes: Set[ResultOutputType])(xmlString: String): Try[T] = {
    xmlString.tryToXml.flatMap(fromI2b2(breakdownTypes))
  }
}

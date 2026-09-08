package net.shrine.protocol.i2b2.serialization

import xml.NodeSeq
import net.shrine.xml.{StringEnrichments, XmlUtil}

import scala.util.Try

/**
 * @author Bill Simons
 * @date 4/5/11
 * @link http://cbmi.med.harvard.edu
 * @link http://chip.org
 *       <p/>
 *       NOTICE: This software comes with NO guarantees whatsoever and is
 *       licensed as Lgpl Open Source
 * @link http://www.gnu.org/licenses/lgpl.html
 */
trait XmlUnmarshaller[+T] {
  def fromXml(xml: NodeSeq): T

  def fromXml(xmlString: String): T = fromXml(XmlUtil.loadString(xmlString))
  
  import StringEnrichments._
  
  def tryFromXml(xml: NodeSeq): Try[T] = Try(fromXml(xml))
  
  def tryFromXml(xmlString: String): Try[T] = xmlString.tryToXml.map(fromXml)
}
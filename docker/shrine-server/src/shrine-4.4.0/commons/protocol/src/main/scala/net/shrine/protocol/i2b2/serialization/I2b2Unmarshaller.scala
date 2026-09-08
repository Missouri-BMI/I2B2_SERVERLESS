package net.shrine.protocol.i2b2.serialization

import net.shrine.xml.XmlUtil

import scala.xml.NodeSeq


/**
 * @author Bill Simons
 * @date 3/23/11
 * @link http://cbmi.med.harvard.edu
 * @link http://chip.org
 *       <p/>
 *       NOTICE: This software comes with NO guarantees whatsoever and is
 *       licensed as Lgpl Open Source
 * @link http://www.gnu.org/licenses/lgpl.html
 */
trait I2b2Unmarshaller[+T] extends I2b2UnmarshallingHelpers {

  def fromI2b2(xmlString: String): T = fromI2b2(XmlUtil.loadString(xmlString))

  def fromI2b2(nodeSeq: NodeSeq): T
}
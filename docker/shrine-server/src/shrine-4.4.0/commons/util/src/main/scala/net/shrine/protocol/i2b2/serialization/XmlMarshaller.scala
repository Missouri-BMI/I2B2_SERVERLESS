package net.shrine.protocol.i2b2.serialization

import xml.NodeSeq

/**
 * @author Bill Simons
 * @date 3/21/11
 * @link http://cbmi.med.harvard.edu
 * @link http://chip.org
 *       <p/>
 *       NOTICE: This software comes with NO guarantees whatsoever and is
 *       licensed as Lgpl Open Source
 * @link http://www.gnu.org/licenses/lgpl.html
 */
trait XmlMarshaller {
  def toXml: NodeSeq

  def toXmlString: String = toXml.toString
}
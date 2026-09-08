package net.shrine.protocol.i2b2.serialization

import xml.NodeSeq

/**
 * @author Bill Simons
 * @since 3/24/11
 * @see http://cbmi.med.harvard.edu
 * @see http://chip.org
 *       <p/>
 *       NOTICE: This software comes with NO guarantees whatsoever and is
 *       licensed as Lgpl Open Source
 * @see http://www.gnu.org/licenses/lgpl.html
 */
trait I2b2Marshaller {
  def toI2b2: NodeSeq

  def toI2b2String: String = toI2b2.toString()
}
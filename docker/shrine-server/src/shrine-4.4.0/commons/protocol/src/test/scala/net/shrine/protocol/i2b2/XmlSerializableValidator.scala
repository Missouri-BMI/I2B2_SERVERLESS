package net.shrine.protocol.i2b2

/**
 * @author Bill Simons
 * @since 3/28/11
 * @see http://cbmi.med.harvard.edu
 * @see http://chip.org
 *       <p/>
 *       NOTICE: This software comes with NO guarantees whatsoever and is
 *       licensed as Lgpl Open Source
 * @see http://www.gnu.org/licenses/lgpl.html
 */
trait XmlSerializableValidator {
  def testToXml(): Unit

  def testFromXml(): Unit
}
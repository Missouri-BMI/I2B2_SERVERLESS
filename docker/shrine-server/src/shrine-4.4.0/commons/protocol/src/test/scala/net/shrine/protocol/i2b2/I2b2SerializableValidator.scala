package net.shrine.protocol.i2b2

/**
 * @author Bill Simons
 * @date 3/28/11
 * @link http://cbmi.med.harvard.edu
 * @link http://chip.org
 *       <p/>
 *       NOTICE: This software comes with NO guarantees whatsoever and is
 *       licensed as Lgpl Open Source
 * @link http://www.gnu.org/licenses/lgpl.html
 */
trait I2b2SerializableValidator {
  def testToI2b2(): Unit

  def testFromI2b2(): Unit
}

package net.shrine.protocol.i2b2

import net.shrine.util.ShouldMatchersForJUnit

/**
 * @author Bill Simons
 * @date 4/12/11
 * @link http://cbmi.med.harvard.edu
 * @link http://chip.org
 *       <p/>
 *       NOTICE: This software comes with NO guarantees whatsoever and is
 *       licensed as Lgpl Open Source
 * @link http://www.gnu.org/licenses/lgpl.html
 */
trait ShrineResponseValidator extends HasResponse with ShouldMatchersForJUnit with XmlSerializableValidator
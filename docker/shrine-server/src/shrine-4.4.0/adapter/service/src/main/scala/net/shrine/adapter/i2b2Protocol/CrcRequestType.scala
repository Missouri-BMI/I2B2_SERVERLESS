package net.shrine.adapter.i2b2Protocol

/**
 * Simple enum listing the types of CRC requests that Shrine knows how to
 * handle. The enum names are deliberately the same as the jaxb generated class
 * of the request.
 *
 * @author Justin Quan
 * @author clint
 * @since Jun 14, 2010
 * @see http://cbmi.med.harvard.edu
 * @see http://chip.org
 *      <p/>
 *      NOTICE: This software comes with NO guarantees whatsoever and is
 *      licensed as Lgpl Open Source
 * @see http://www.gnu.org/licenses/lgpl.html
 */
final case class CrcRequestType private(name: String, i2b2RequestType: String)

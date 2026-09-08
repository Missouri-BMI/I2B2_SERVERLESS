package net.shrine.protocol.i2b2

import net.shrine.protocol.i2b2.serialization.XmlMarshaller

/**
 * @author clint
 * @since Nov 25, 2013
 */
//only used by BaseShrineRequest and ShrineResponse
trait ShrineMessage extends XmlMarshaller

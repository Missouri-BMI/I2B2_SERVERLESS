package net.shrine.adapter.i2b2Protocol

import net.shrine.protocol.i2b2.XmlUnmarshallers

/**
 * @author clint
 * @since Feb 14, 2014
 */
object BaseShrineResponse extends XmlUnmarshallers.Chained(ShrineResponse.fromXml)

package net.shrine.adapter.i2b2Protocol

import net.shrine.protocol.i2b2.{ShrineRequest, XmlUnmarshallers}

/**
 * @author clint
 * @since Feb 14, 2014
 */
object BaseShrineRequest extends XmlUnmarshallers.Chained(ShrineRequest.fromXml, ReadQueryResultRequest.fromXml)


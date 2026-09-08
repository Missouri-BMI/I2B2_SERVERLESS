package net.shrine.xml

import scala.xml.{Elem, Node, NodeSeq, Text}

/**
 * @author clint
 * @since Sep 30, 2014
 *
 * Helper methods for Options
 */
object OptionEnrichments {
  implicit final class OptionHasToXml[T](val o: Option[T]) extends AnyVal {
    def toXml(enclosingXml: Elem): Node = o.map(v => enclosingXml.copy(child = Seq(Text(String.valueOf(v).trim)))).map(XmlUtil.stripWhitespace).orNull

    def toXml(enclosingXml: Elem, serialize: T => NodeSeq): NodeSeq = o.map(t => enclosingXml.copy(child = serialize(t))).map(XmlUtil.stripWhitespace).orNull

    def toXml(f: T => NodeSeq): NodeSeq = o.map(f).map(x => XmlUtil.stripWhitespace(x.head)).orNull
  }
}

package net.shrine.xml

import scala.util.Try
import scala.xml.NodeSeq

/**
 * @author clint
 * @since Nov 26, 2014
 */
object StringEnrichments {
  final implicit class HasStringEnrichments(val s: String) extends AnyVal {
    def tryToXml: Try[NodeSeq] = Try(XmlUtil.loadString(s))
  }
}

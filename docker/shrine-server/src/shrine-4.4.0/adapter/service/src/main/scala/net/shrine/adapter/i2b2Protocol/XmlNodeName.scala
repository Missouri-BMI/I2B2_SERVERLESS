package net.shrine.adapter.i2b2Protocol

import net.shrine.protocol.i2b2.serialization.{XmlMarshaller, XmlUnmarshaller}
import net.shrine.protocol.version.v2.{Node => ProtocolNode}
import net.shrine.xml.XmlUtil

import scala.util.Try
import scala.xml.{Node, NodeSeq}

/**
 * @author clint
 * @since Nov 1, 2013
 */
final case class XmlNodeName(name: String) extends XmlMarshaller {
  override def toXml: Node = XmlUtil.stripWhitespace {
    import XmlNodeName._

    XmlUtil.renameRootTag(rootTagName) {
      <placeholder>
        <name>{ name }</name>
      </placeholder>
    }
  }
}

object XmlNodeName extends XmlUnmarshaller[Try[XmlNodeName]] {

  val rootTagName = "nodeId"

  val Unknown: XmlNodeName = XmlNodeName("Unknown")

  override def fromXml(xml: NodeSeq): Try[XmlNodeName] = {
    for {
      name <- Try((xml \ "name").text.trim).filter(_.nonEmpty)
    } yield XmlNodeName(name)
  }

  def fromXmlOption(xml:NodeSeq):Try[Option[XmlNodeName]] = Try {
    val nameElement: NodeSeq = xml \ "name"
    val contents = nameElement.text
    if (contents.isEmpty) None
    else Some(XmlNodeName(contents))
  }

  def fromProtocolNode(protocolNode: ProtocolNode):XmlNodeName = {
    XmlNodeName(protocolNode.name.underlying)
  }
}
 
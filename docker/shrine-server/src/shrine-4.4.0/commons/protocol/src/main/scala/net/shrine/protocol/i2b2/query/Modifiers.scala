package net.shrine.protocol.i2b2.query

import net.shrine.xml.{NodeSeqEnrichments, XmlUtil}

import scala.util.{Failure, Success, Try}
import scala.xml.NodeSeq

/**
 * @author clint
 * @since Jul 23, 2014
 */
final case class Modifiers(name: String, appliedPath: String, key: String) {
  def toI2b2: NodeSeq = XmlUtil.stripWhitespace {
    <constrain_by_modifier>
      <modifier_name>{ name }</modifier_name>
      <applied_path>{ appliedPath }</applied_path>
      <modifier_key>{ key }</modifier_key>
    </constrain_by_modifier>
  }

  def toXml: NodeSeq = XmlUtil.stripWhitespace {
    <modifier>
      <name>{ name }</name>
      <appliedPath>{ appliedPath }</appliedPath>
      <key>{ key }</key>
    </modifier>
  }

  def transformKey(f: String => Try[String]): Try[Modifiers] = f(key) match {
    case Success(transformed) => Success(copy(key = transformed))
    case Failure(e) => Failure(e)
  }

  //def transformKey(f: String => String): Modifiers = copy(key = f(key))
}

object Modifiers {
  def fromI2b2(xml: NodeSeq): Try[Modifiers] = unmarshalXml(xml, "modifier_name", "applied_path", "modifier_key")

  def fromXml(xml: NodeSeq): Try[Modifiers] = unmarshalXml(xml, "name", "appliedPath", "key")

  private def unmarshalXml(xml: NodeSeq, nameTagName: String, appliedPathTagName: String, keyTagName: String): Try[Modifiers] = {
    import NodeSeqEnrichments.Strictness._

    def text(attempt: Try[NodeSeq]) = attempt.map(_.text)

    for {
      name <- text(xml withChild nameTagName)
      appliedPath <- text(xml withChild appliedPathTagName)
      key <- text(xml withChild keyTagName)
    } yield Modifiers(name, appliedPath, key)
  }

}
package net.shrine.protocol.i2b2

import net.shrine.config.{ConfigSource, ConfigExtensions}

import xml.NodeSeq
import net.shrine.crypto.SealerRevealer
import net.shrine.protocol.i2b2.serialization.{I2b2Marshaller, I2b2Unmarshaller, XmlMarshaller, XmlUnmarshaller}

import javax.crypto.SealedObject
import scala.concurrent.duration.Duration
import scala.util.Try

/**
 * @author Bill Simons
 * @since 3/9/11
 * @see http://cbmi.med.harvard.edu
 * @see http://chip.org
 *       <p/>
 *       NOTICE: This software comes with NO guarantees whatsoever and is
 *       licensed as Lgpl Open Source
 * @see http://www.gnu.org/licenses/lgpl.html
 *
 * NB: this is a case class to get a structural equality contract in hashCode and equals, mostly for testing
 */
final case class Credential(sealedValue: SealedObject, isToken: Boolean) extends XmlMarshaller with I2b2Marshaller {

  def value: String = SealerRevealer.reveal(sealedValue)

  override def toXml: NodeSeq = <credential isToken={ isToken.toString }>{ value }</credential>

  override def toI2b2: NodeSeq = <password token_ms_timeout={ConfigSource.config.get("shrine.webclient.sessionTimeout", Duration(_)).toMillis.toString}
                                           is_token={ isToken.toString }>{ value }</password>

  override def equals(obj: Any): Boolean = {
    this.canEqual(obj) && {
      val other:Credential = obj.asInstanceOf[Credential]
      this.isToken == other.isToken && this.value == other.value
    }
  }

  override def hashCode(): Int = value.hashCode() ^ isToken.hashCode()

  override def toString: String = s"Credential(REDACTED,$isToken)"
}

object Credential extends I2b2Unmarshaller[Try[Credential]] with XmlUnmarshaller[Try[Credential]] {

  def apply(value:String,isToken:Boolean):Credential = Credential(SealerRevealer.seal(value), isToken)

  override def fromI2b2(xml: NodeSeq): Try[Credential] = parse(xml)(parseI2b2IsToken)

  override def fromXml(xml: NodeSeq): Try[Credential] = parse(xml)(parseShrineIsToken)

  private def parse(xml: NodeSeq)(parseIsToken: NodeSeq => Boolean): Try[Credential] = Try {
    Credential(xml.text, parseIsToken(xml))
  }

  private def parseI2b2IsToken(xml: NodeSeq): Boolean = parseIsToken(xml, "is_token")

  private def parseShrineIsToken(xml: NodeSeq): Boolean = parseIsToken(xml, "isToken")

  private def parseIsToken(xml: NodeSeq, attribute: String): Boolean = {
    val isTokenXml = xml \ ("@" + attribute)

    if (isTokenXml.isEmpty) { false }
    else { isTokenXml.text.toBoolean }
  }

  def safe: Credential = Credential("REDACTED",isToken = false)
}
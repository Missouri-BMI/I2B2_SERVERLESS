package net.shrine.protocol.i2b2

import net.shrine.protocol.i2b2.serialization.{I2b2Marshaller, I2b2Unmarshaller, XmlMarshaller, XmlUnmarshaller}
import net.shrine.protocol.version.v2.Researcher

import xml.{Node, NodeSeq}

import scala.util.Try
import net.shrine.xml.{NodeSeqEnrichments, XmlUtil}

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
 *
 * NB: Exposes a constructor that takes a String, so that JAXRS can automatically unmarshal an instance of this
 * class from a String
 */
final case class AuthenticationInfo(
                                     domain: String,
                                     username: String,
                                     credential: Credential) extends XmlMarshaller with I2b2Marshaller {

  //noinspection ScalaUnusedSymbol
  //NB: For JAXRS
  private def this(other: AuthenticationInfo) = this(other.domain, other.username, other.credential)

  //NB: For JAXRS
  def this(serializedForm: String) = this(AuthenticationInfo.fromHeader(serializedForm))

  override def toI2b2: Node = XmlUtil.stripWhitespace {
    <security>
      <domain>{ domain }</domain>
      <username>{ username }</username>
      { credential.toI2b2 }
    </security>
  }

  def toHeader: String = {
    import AuthenticationInfo.{ headerPrefix => prefix, headerDelimiter => delim }

    prefix + Seq(domain, username, credential.value, credential.isToken.toString).mkString(delim)
  }

  import AuthenticationInfo.shrineXmlTagName

  override def toXml: Node = XmlUtil.renameRootTag(shrineXmlTagName) {
    XmlUtil.stripWhitespace {
      <placeHolder>
        <domain>{ domain }</domain>
        <username>{ username }</username>
        { credential.toXml }
      </placeHolder>
    }
  }
}

object AuthenticationInfo extends I2b2Unmarshaller[Try[AuthenticationInfo]] with XmlUnmarshaller[Try[AuthenticationInfo]] {

  val shrineXmlTagName = "authenticationInfo"

  import NodeSeqEnrichments.Strictness._
    
  override def fromI2b2(xml: NodeSeq): Try[AuthenticationInfo] = {
    for {
      domain <- xml.withChild("domain").map(_.text)
      username <- xml.withChild("username").map(_.text)
      credential <- xml.withChild("password").flatMap(Credential.fromI2b2)
    } yield {
      AuthenticationInfo(domain, username, credential)
    }
  }

  override def fromXml(nodeSeq: NodeSeq): Try[AuthenticationInfo] = {
    for {
      domain <- Try((nodeSeq \ "domain").text.trim)
      username <- Try((nodeSeq \ "username").text.trim)
      credential <- Credential.fromXml(nodeSeq \ "credential")
    } yield AuthenticationInfo(domain, username, credential)
  }

  private[protocol] val headerPrefix = "SHRINE "

  private[protocol] val headerDelimiter = ","

  def fromHeader(header: String): AuthenticationInfo = {
    val Array(_, headerValue) = header.split(headerPrefix)

    val Array(domain, username, password, isTokenStr) = headerValue.split(headerDelimiter)

    new AuthenticationInfo(domain, username, Credential(password, isTokenStr.toBoolean))
  }

  def noPasswordFromResearcher(researcher:Researcher):AuthenticationInfo = AuthenticationInfo(
    domain = researcher.userDomainName.underlying,
    username = researcher.userName.underlying,
    Credential.safe
  )

  def noPassword(username:String,domain:String):AuthenticationInfo = AuthenticationInfo(
    domain = domain,
    username = username,
    credential = Credential.safe
  )
}
package net.shrine.protocol.i2b2

import net.shrine.util.ShouldMatchersForJUnit
import org.junit.Test

/**
 * @author Bill Simons
 * @since 3/10/11
 * @see http://cbmi.med.harvard.edu
 * @see http://chip.org
 *       <p/>
 *       NOTICE: This software comes with NO guarantees whatsoever and is
 *       licensed as Lgpl Open Source
 * @see http://www.gnu.org/licenses/lgpl.html
 */
final class CredentialTest extends ShouldMatchersForJUnit with XmlSerializableValidator with I2b2SerializableValidator {

  @Test
  override def testFromI2b2():Unit = {
    val passwd = "SessionKey:prFsw9A1zZTr2PZpFLh1"

    {
      val cred = Credential.fromI2b2(<password token_ms_timeout="1800000" is_token="true">{ passwd }</password>).get
      cred.value should equal(passwd)
      cred.isToken should equal(true)
    }

    {
      val cred = Credential.fromI2b2(<password token_ms_timeout="1800000" is_token="false">{ passwd }</password>).get
      cred.value should equal(passwd)
      cred.isToken should equal(false)
    }

    {
      val cred = Credential.fromI2b2(<password>{ passwd }</password>).get
      cred.value should equal(passwd)
      cred.isToken should equal(false)
    }
    
    //NB: Toleralte bad input for now
    Credential.fromI2b2(<foo/>).isFailure should be(false)
    Credential.fromI2b2(<bar>asdf</bar>).isFailure should be(false)
  }

  @Test
  override def testToI2b2():Unit = {
    val passwd = "passwd"
    val isToken = false
    val credential = Credential(passwd, isToken)

    credential.toI2b2 should equal(<password token_ms_timeout="20000" is_token={ isToken.toString }>{ passwd }</password>)
  }

  @Test
  override def testToXml():Unit = {
    val credential = Credential("passwd", isToken = false)

    credential.toXml should equal(<credential isToken="false">passwd</credential>)
  }

  @Test
  override def testFromXml():Unit = {
    {
      val credential = Credential.fromXml(<credential isToken="false">passwd</credential>).get

      credential.value should equal("passwd")
      credential.isToken should equal(false)
    }

    {
      val credential = Credential.fromXml(<credential isToken="true">passwd</credential>).get

      credential.isToken should equal(true)
    }
  }

  @Test
  def testEqualsAndHashcode():Unit = {
    val credsTrue = Credential("creds", isToken = true)
    val credsTrue2 = Credential("creds", isToken = true)
    credsTrue should equal(credsTrue2)
    credsTrue2 should equal(credsTrue)
    credsTrue.hashCode should equal(credsTrue2.hashCode())

    val credsFalse = Credential("creds", isToken = false)
    val credsFalse2 = Credential("creds", isToken = false)
    credsFalse should equal(credsFalse2)
    credsFalse2 should equal(credsFalse)
    credsFalse.hashCode should equal(credsFalse2.hashCode())

    credsTrue should not equal credsFalse
    credsFalse should not equal credsTrue
  }
}
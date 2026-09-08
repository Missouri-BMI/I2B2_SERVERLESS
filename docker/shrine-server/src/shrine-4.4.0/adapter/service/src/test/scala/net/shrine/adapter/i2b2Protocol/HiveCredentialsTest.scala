package net.shrine.adapter.i2b2Protocol

import net.shrine.crypto.SealerRevealer
import net.shrine.util.ShouldMatchersForJUnit

/**
 * @author clint
 * @since Oct 4, 2012
 */
final class HiveCredentialsTest extends ShouldMatchersForJUnit {
  def testToAuthenticationInfo(): Unit = {
    val creds = HiveCredentials("domain", "username", SealerRevealer.seal("password"), "project")
    
    val authn = creds.toAuthenticationInfo
    
    authn should not be null
    
    authn.domain should equal(creds.domain)
    authn.username should equal(creds.username)
    authn.credential.value should equal(creds.password)
    authn.credential.isToken should be(right = false)
  }
}
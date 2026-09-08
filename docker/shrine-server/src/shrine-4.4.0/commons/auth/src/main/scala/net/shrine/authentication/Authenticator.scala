package net.shrine.authentication

import net.shrine.authentication.pm.User
import net.shrine.protocol.i2b2.AuthenticationInfo

/**
 * @author clint
 * @since Dec 12, 2013
 */
//todo can be deleted in Shrine 3.2
trait Authenticator {
  def authenticate(authn: AuthenticationInfo, onAuth: Option[User => Unit] = None): AuthenticationResult


}

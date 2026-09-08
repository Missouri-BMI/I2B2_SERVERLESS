package net.shrine.authentication

/**
 * @author clint
 * @date Dec 12, 2013
 */
sealed abstract class AuthenticationResult(val isAuthenticated: Boolean)

object AuthenticationResult {
  final case class Authenticated(domain: String, username: String) extends AuthenticationResult(true)
  
  final case class NotAuthenticated(domain: String, username: String, message: String, cause:Option[Throwable] = None) extends AuthenticationResult(false)
}
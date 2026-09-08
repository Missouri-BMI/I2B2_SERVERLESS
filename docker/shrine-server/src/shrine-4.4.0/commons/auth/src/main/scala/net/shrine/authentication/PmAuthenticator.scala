package net.shrine.authentication

import net.shrine.authentication.pm.{GetUserConfigurationRequest, User}
import net.shrine.http4s.client.legacy.Poster
import net.shrine.protocol.i2b2.AuthenticationInfo

import scala.util.Try
import scala.util.control.NonFatal

/**
 * @author clint
 * @since Feb 26, 2014
 */
//todo remove Authenticator interface in shrine 3.2
final case class PmAuthenticator(pmPoster: Poster) extends Authenticator {

  override def authenticate(authenticationInfo: AuthenticationInfo, onAuth: Option[User => Unit] = None): AuthenticationResult = {
    val httpResponseString = Try {
      val requestString = GetUserConfigurationRequest(authenticationInfo).toI2b2String

      val httpResponse = pmPoster.post(requestString)

      httpResponse.body
    }

    val userFromPm = httpResponseString.flatMap(User.fromI2b2)

    val result = userFromPm.map { user =>
      onAuth.foreach(_(user))
      AuthenticationResult.Authenticated(user.domain, user.username)
    }.recover {
      case NonFatal(e) =>
        val AuthenticationInfo(domain, username, _) = authenticationInfo

        AuthenticationResult.NotAuthenticated(domain, username, s"Failed attempt to certify user", Some(e))
    }

    result.get
  }
}
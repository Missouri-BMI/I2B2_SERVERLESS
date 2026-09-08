package net.shrine.authentication.http4s

import cats.data.{Kleisli, OptionT}
import cats.effect.IO
import net.shrine.authentication.pm.User
import net.shrine.config.{ConfigExtensions, ConfigSource}
import net.shrine.log.Log
import net.shrine.protocol.i2b2.Credential
import org.http4s.server.AuthMiddleware
import org.http4s.{AuthedRequest, AuthedRoutes, Header, Request, Response, Status}
import org.typelevel.ci.CIString

import scala.concurrent.duration.Duration

/**
 * Processes SSO authentication attempts, successful or not. If successful populates a User object
 */
object SSOAuthentication{
  /**
   * Initializes a User object. Given a username, it creates, populates
   * and returns a User object, including its domain and the session
   * timeout value which this method gets from the configuration.
   *
   * @param username:String
   * @return an IO[[User]]
   */
  private def initializeUser(username: String): IO[User] = {

    IO {
      val domain: String = ConfigSource.config.getOption("shrine.webclient.domain", _.getString)
        .getOrElse(ConfigSource.config.getString("shrine.i2b2Domain"))

      val sessionTimeout: Duration = ConfigSource.config.get("shrine.webclient.sessionTimeout", Duration(_))

       User(fullName = username,
        username = username,
        domain = domain,
        credential = Credential.safe,
        params = Map.empty,
        rolesByProject = Map.empty,
        sessionTimeoutMs = Some(sessionTimeout.toMillis.toString)
      )
    }
  }

  /**
   * Http4s AuthMiddleware function
   *
   * After a user has been successfully authenticated by the idP, this method extracts the
   * user's username from the REMOTE_USER request header and calls getAuthUserFromHeader() to obtain
   * a populated User object
   *
   * @return Kleisli function
   */
  private def authUser: Kleisli[IO, Request[IO], Either[SSOIssue, User]] = Kleisli { request: Request[IO] =>
    val remoteUserIdentifier = "REMOTE_USER"

    Log.info(s"Getting $remoteUserIdentifier from request headers")
    val header: Option[Header.Raw] = request.headers.get(CIString(remoteUserIdentifier)).map(_.head)

    // Currently the initializeUser() method populates the full name with the value of the username passed to it.
    // In SSO mode, one could make use of the preexisting AJP_firstName and AJP_lastName headers to construct the full name
    // and pass it to initializeUser()
    // see https://stackoverflow.com/questions/2811769/adding-an-http-header-to-the-request-in-a-servlet-filter
    // see https://jmcardon.github.io/tsec/docs/http4s-auth.html
    // Maybe also we make use of the following header the obtain the value of the domain set in :initializeUser
    // AJP_Shib-Handler: https://shrine-sso-node01.catalyst.harvard.edu/Shibboleth.sso

    header.map { h =>
      Log.info(s"Got $remoteUserIdentifier = ${h.value} from request headers")
      initializeUser(h.value).map(Right(_))
    }.getOrElse {
      Log.error(s"$remoteUserIdentifier was not found in the request headers")
      IO(Left(RemoteUserNotFoundIssue))
    }
  }

  /**
   * "Callback" for authentication failure.
   *
   * This method will log an authentication error and will set the
   * HTTP response status to Status.Unauthorized (401)
   *
   * @return
   */
  private def onAuthFailure: AuthedRoutes[SSOIssue, IO] = Kleisli { req: AuthedRequest[IO, SSOIssue] =>
    // for any requests' auth failure we return 401
    Log.info(s"Responded with status ${Status.Unauthorized} to request ${req.req}")
    OptionT.pure[IO](
        Response[IO](
          status = Status.Unauthorized
        )
      )
  }
  def authMiddleware: AuthMiddleware[IO, User] = AuthMiddleware(authUser, onAuthFailure)

  private sealed trait SSOIssue
  private case object RemoteUserNotFoundIssue extends SSOIssue
}

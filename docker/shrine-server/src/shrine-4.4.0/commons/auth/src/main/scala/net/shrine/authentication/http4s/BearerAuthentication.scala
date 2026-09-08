package net.shrine.authentication.http4s

import java.nio.charset.StandardCharsets
import java.util.Base64

import cats.Applicative
import cats.data.Kleisli
import cats.effect.{IO, Sync}
import cats.implicits.toFunctorOps
import net.shrine.authentication.pm.User
import org.http4s.Credentials.Token
import org.http4s.headers.Authorization
import org.http4s.server.AuthMiddleware
import org.http4s.server.middleware.authentication.challenged
import org.http4s.{AuthedRequest, AuthedRoutes, BasicCredentials, Challenge, EntityDecoder, HttpRoutes, Request}

/**
  * Provides Bearer token Authentication.
  */
object BearerAuthentication {

  val unauthorizedMsg = "Invalid session Id"

  val bearerAuthMiddleware: AuthMiddleware[IO, User] = BearerAuthentication(UserAuthentication.domain, authenticator) apply _ andThen UserAuthentication.addUnauthorizedMsg(unauthorizedMsg)

  /**
    * Validates a token (presumably by comparing it to a
    * hashed value).  A Some value indicates success; None indicates
    * the password failed to validate.
    */
  private type BearerAuthenticator[F[_], A] = Token => F[Option[A]]

  /**
    * Construct authentication middleware that can validate the client-provided
    * plaintext password against something else (like a stored, hashed password).
    * @param realm The realm used for authentication purposes.
    * @param validate Function that validates a plaintext password
    * @return
    */
  def apply[F[_]: Sync, A](
                            realm: String,
                            validate: BearerAuthenticator[F, A]): AuthMiddleware[F, A] =
    challenged(challenge(realm, validate))

  private def challenge[F[_]: Applicative, A](realm: String, validate: BearerAuthenticator[F, A])
  : Kleisli[F, Request[F], Either[Challenge, AuthedRequest[F, A]]] =
    Kleisli { req =>
      validateToken(validate, req).map {
        case Some(authInfo) =>
          Right(AuthedRequest(authInfo, req))
        case None =>
          Left(Challenge("Bearer", realm, Map.empty))
      }
    }

  private def validateToken[F[_], A](validate: BearerAuthenticator[F, A], request: Request[F])(
    implicit F: Applicative[F]): F[Option[A]] = {

    val maybeAuthorizationHeader: Option[Authorization] = request.headers.get(Authorization.name)
      .map(raw => Authorization.parse(raw.head.value).getOrElse(
        //technically this should kick out a 4xx http error, but really it's to the admins to fix
        throw new IllegalStateException(s"No ${Authorization.name} header value present")))

    maybeAuthorizationHeader match {
      case Some(Authorization(Token(scheme, token))) =>
        validate(Token(scheme, token))
      case _ =>
        F.pure(None)
    }
  }
  def authenticator(encodedToken: Token): IO[Option[User]] = {
    import io.circe.generic.auto.exportDecoder
    import org.http4s.circe.jsonOf

    implicit val userSessionTokenDecoder: EntityDecoder[IO, UserToken] = jsonOf[IO, UserToken]

    val decodedToken = Base64.getDecoder.decode(encodedToken.token)
    val decodedTokenStr = new String(decodedToken, StandardCharsets.ISO_8859_1)

    import io.circe.parser.decode
    val userSessionTokenEither = decode[UserToken](decodedTokenStr)

    userSessionTokenEither match {
      case Right(ust) =>
        val basicCredentials = BasicCredentials(ust.username, ust.sessionId)
        BasicAuthentication.authenticator(basicCredentials, isToken = true)
      case Left(_) => IO(None)
    }
  }
}

object BearerAuthMiddleware {

  def apply(service: AuthedRoutes[User, IO]): HttpRoutes[IO] = {

    BearerAuthentication.bearerAuthMiddleware(service)
  }
}

case class UserToken(username: String, sessionId: String) //This is what is received by the server when the user authenticates using their session Id




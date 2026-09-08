package net.shrine.authz

import cats.effect.IO
import net.shrine.config.ConfigSource
import org.http4s._
import org.http4s.dsl.impl.{->, /}
import org.http4s.dsl.io.{GET, NotFound, Ok, Root, http4sNotFoundSyntax, http4sOkSyntax}
import org.typelevel.ci.CIString

/**
 * This service (we like to call it a resource in java/Spring world)
 * implements a single route for logging the user out of SSO (Shibboleth).
 */
object AuthzHttp4sService {

  val service: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req@GET -> Root / "logout" =>
      logout(req)

    case x => NotFound(s"AuthzH4sService does not respond to $x")
  }

  def redirectToUri(uri: String): IO[Response[IO]] = {
    val locationHeader = org.http4s.Header.Raw(CIString("Location"), uri)
    val responseIo = Response[IO]()
      .withStatus(Status.TemporaryRedirect)
      .withHeaders(locationHeader)
    IO(responseIo)
  }

  def logout(req: Request[IO]) ={

    val shibLogoutUrl = ConfigSource.config.getString("shrine.config.authorizer.shibLogoutUrl")

    redirectToUri(shibLogoutUrl)
  }

}

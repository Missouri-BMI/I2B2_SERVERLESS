package net.shrine.authentication.http4s

import cats.data.{Kleisli, NonEmptyList, OptionT}
import cats.effect.IO
import net.shrine.authentication.AuthenticationType
import net.shrine.authentication.pm.User
import net.shrine.config.ConfigSource
import net.shrine.config.ConfigExtensions
import org.http4s.Credentials.Token
import org.http4s.headers.Authorization
import org.http4s.{AuthScheme, AuthedRoutes, Header, HttpRoutes, Response, Status}
import org.typelevel.ci.CIString


object UserAuthentication {
  lazy val domain: String = ConfigSource.config.getOption("shrine.webclient.domain", _.getString).getOrElse(ConfigSource.config.getString("shrine.i2b2Domain"))

  def addUnauthorizedMsg(message: String) =
    Kleisli { resp: Response[IO] =>
      OptionT[IO, Response[IO]] {
        if (resp.status == Status.Unauthorized)
          IO(Some(resp.withEntity(message)))
        else IO(Some(resp))
      }
    }
}

//Allows Either Basic or Bearer Authentication
object AuthMiddlewareSelector {
  def apply(service: AuthedRoutes[User, IO]): HttpRoutes[IO] = Kleisli({ request =>
    val authType: AuthenticationType = ConfigSource.config.get("shrine.qep.authenticationType", {aName =>
      AuthenticationType.namesToAuthenticationTypes.getOrElse(aName, throw new IllegalArgumentException(s"$aName is not a valid authentication type"))
    })
    authType match {
      case AuthenticationType.Sso => SSOAuthentication.authMiddleware(service)(request)
      case AuthenticationType.Pm =>
        val maybeAuthorizationHeader:Option[Authorization] = request.headers.get(Authorization.name)
          .map(raw => Authorization.parse(raw.head.value).getOrElse(
            //technically this should kick out a 4xx http error, but really it's to the admins to fix
            throw new IllegalStateException(s"No ${Authorization.name} header value present")))
        maybeAuthorizationHeader match {
          case Some(Authorization(Token(AuthScheme.Bearer,_))) => BearerAuthMiddleware(service)(request)
          case _ => BasicAuthMiddleware(service)(request)
        }
    }
  })
}

object XRequestedWithMiddleware {

  def apply(service: HttpRoutes[IO]): HttpRoutes[IO] = Kleisli({ request =>

    import org.http4s.headers.`WWW-Authenticate`

    val authHeader: Option[Header.Raw] = request.headers.get(CIString("X-Requested-With")).map(_.head)

    authHeader.find(_.value.equalsIgnoreCase("xmlhttprequest")).map( _ => {
      service (request).map {
        case Status.Unauthorized (resp) => resp.withHeaders (resp.headers.headers.filterNot (header => header.name == `WWW-Authenticate`.headerInstance.name ) )
        case resp => resp
      }
    }).getOrElse(service(request))

  })

}


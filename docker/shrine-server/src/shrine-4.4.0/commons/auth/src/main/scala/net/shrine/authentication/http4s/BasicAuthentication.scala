package net.shrine.authentication.http4s

import cats.effect.IO
import com.typesafe.config.Config
import net.shrine.authentication.pm.{BadUsernameOrPasswordException, GetUserConfigurationRequest, PmUserWithoutProjectException, PmUserWithoutRequiredProjectException, User}
import net.shrine.config.{ConfigExtensions, ConfigSource}
import net.shrine.http4s.client.legacy.{EndpointConfig, Http4sI2b2Client, Poster}
import net.shrine.log.Loggable
import net.shrine.protocol.i2b2.{AuthenticationInfo, Credential}
import org.http4s.server.AuthMiddleware
import org.http4s.server.middleware.authentication.BasicAuth
import org.http4s.{AuthedRoutes, BasicCredentials, HttpRoutes}

import scala.collection.mutable
import scala.concurrent.duration.Duration

object BasicAuthentication {

  val unauthorizedMsg = "Username or password does not match credentials."

  val basicAuthMiddleware: AuthMiddleware[IO, User] = BasicAuth(UserAuthentication.domain, authenticator) apply _ andThen UserAuthentication.addUnauthorizedMsg(unauthorizedMsg)

  def authenticator(userCred: BasicCredentials):IO[Option[User]] ={

    authenticator(userCred, isToken = false)
  }

  def authenticator(userCred: BasicCredentials, isToken: Boolean):IO[Option[User]] ={

    val config: Config = ConfigSource.config
    val userSource:UserSource = config.getString("shrine.authenticate.usersource.type") match {
      case PmUserSource.configName => PmUserSource(config, isToken)
      case ConfigUserSource.configName => ConfigUserSource(config)
      case x => throw new IllegalStateException(s"The config value for 'shrine.authenticate.usersource.type' must be either ${PmUserSource.configName} for actual use or ${ConfigUserSource.configName} for testing. '$x' cannot be used.")
    }

    userSource.authenticateUser(Some(userCred), isToken)
  }

  private case class PmUserSource(config:Config, isToken: Boolean) extends UserSource with Loggable {
    def authenticateUser(userPassOption: Option[BasicCredentials], isToken: Boolean): IO[Option[User]] = {
      IO.blocking { //todo use postIO when available
        val noUser: Option[User] = None
        userPassOption.fold(noUser)(userPass => {
          val requestString = GetUserConfigurationRequest(AuthenticationInfo(UserAuthentication.domain,
            username = userPass.username,
            credential = Credential(userPass.password, isToken = isToken))).toI2b2String
          val httpResponse = pmPoster.post(requestString)

          if (httpResponse.statusCode >= 400) {
            val message = s"HttpResponse status is ${httpResponse.statusCode} from PM via ${pmPoster.url} for ${userPass.username}. Response is $httpResponse"
            warn(message)
            throw new IllegalStateException(message)
          }

          try {
            // user object contains the token in the password field
            val user = User.fromI2b2(httpResponse.body).get
            Some(user)
          } catch {
            case x: BadUsernameOrPasswordException =>
              error(s"PM at ${pmPoster.url} found no (user,password) combination for ${userPass.username}.", x)
              None
            case x: PmUserWithoutProjectException =>
              warn(s"PM at ${pmPoster.url} found no projects for ${userPass.username}.", x)
              None
            case x: PmUserWithoutRequiredProjectException =>
              warn(s"PM at ${pmPoster.url} does not show ${userPass.username} in the required project.", x)
              None
          }
        }
        )
      }
    }

    private lazy val pmPoster: Poster = {
      val pmEndpointConfig: EndpointConfig = EndpointConfig(config.getConfig("shrine.pmEndpoint"))

      //todo for SHRINE-3661 - replace this - maybe need to take the bouncy castle certs and enforce them
      val httpClient = Http4sI2b2Client(pmEndpointConfig)
      Poster(pmEndpointConfig.url.toString, httpClient)
    }
  }

  private object PmUserSource {
    val configName: String = getClass.getSimpleName.dropRight(1)
  }

  //todo is this really dead?
  private case class ConfigUserSource(config:Config) extends UserSource {

    private val prefix = "shrine.authenticate.usersource"

    private val subconfig: Config = config.getConfig(prefix)

    import scala.jdk.CollectionConverters.SetHasAsScala
    private val roles: mutable.Set[String] = subconfig.entrySet().asScala.
      filterNot(x => x.getKey == "type").
      filterNot(x => x.getKey == "domain").
      map(x => x.getKey.take(x.getKey.indexOf(".")))

    private val rolesToUserNames: Map[String, String] = roles.map(x => (
      x,
      subconfig.getConfig(x).getString("username")
    )).toMap

    private val userNamesToPasswords: Map[String, String] = rolesToUserNames.map(x => (
      x._2,
      subconfig.getConfig(x._1).getString("password")
    ))

    lazy val qepUserName: String = rolesToUserNames("qep")
    lazy val researcherUserName: String = rolesToUserNames("researcher")

    def authenticateUser(userPass: Option[BasicCredentials], isToken: Boolean): IO[Option[User]] = IO {

      val noUser:Option[User] = None
      userPass.fold(noUser)(up =>{

        if (userNamesToPasswords(up.username) == up.password) {
          val user: User = User(fullName = up.username,
            username = up.username,
            domain = "domain",
            credential = Credential(up.password,isToken = isToken),
            params = Map.empty,
            rolesByProject = Map(),
            sessionTimeoutMs = Option(ConfigSource.config.get("shrine.webclient.sessionTimeout", Duration(_)).toMillis.toString)
          )
          Some(user)
        }
        else None
      })
    }
  }

  private object ConfigUserSource {
    val configName: String = getClass.getSimpleName.dropRight(1)
  }

  private trait UserSource {

    def authenticateUser(userPass: Option[BasicCredentials], isToken: Boolean): IO[Option[User]]

  }
}

object BasicAuthMiddleware {

  def apply(service: AuthedRoutes[User, IO]): HttpRoutes[IO] = {

    XRequestedWithMiddleware(BasicAuthentication.basicAuthMiddleware(service))
  }
}



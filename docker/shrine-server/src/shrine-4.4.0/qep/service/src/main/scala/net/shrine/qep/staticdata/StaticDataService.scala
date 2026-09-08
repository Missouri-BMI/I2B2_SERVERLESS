package net.shrine.qep.staticdata

import java.net.URL
import cats.effect.IO
import com.typesafe.config.{ConfigException, ConfigRenderOptions}
import io.circe.syntax.EncoderOps
import io.circe.{Json, JsonObject}
import net.shrine.authentication.http4s.AuthMiddlewareSelector
import net.shrine.authentication.pm.User
import net.shrine.config.{ConfigExtensions, ConfigSource}
import net.shrine.log.Log
import net.shrine.util.{Sort, Versions}
import org.http4s.dsl.impl.{->, /, Auth}
import org.http4s.dsl.io.{GET, InternalServerError, NotFound, Ok, Root, http4sInternalServerErrorSyntax, http4sNotFoundSyntax, http4sOkSyntax}
import org.http4s.{AuthedRoutes, HttpRoutes, Request, Response, StaticFile, Status}

import scala.annotation.unused
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

case class StaticDataService() extends Auth {

  private val staticDataInfo: String =
    """
      |The SHRINE static data service.
      |
      |This API retrieves static data from SHRINE.
      |
    """.stripMargin



  private val notFoundService : HttpRoutes[IO] = HttpRoutes.of[IO] {
    case x => NotFound(s"The static data service does not respond too $x")
  }

  private val notAuthedRoute: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case GET -> Root / "ping" => Ok("pong")

    case GET -> Root / "about" => Ok(staticDataInfo)

    case req@GET -> Root / "config" / key => handleKey(key) match {
      case Success(e) => Ok(e)
      case Failure(x) => handleGeneralFailure(req, x)
    }

    case GET -> Root / "webClientConfig" => extractWebClientConfig

    case req@GET -> Root / "webclient" / "logo" => getLogo(req)

    case GET -> Root / "version" => getVersion

    case GET -> Root / "dataDistributionTypes" => getDataDistributionTypes
  }

  private val authedRoute = AuthMiddlewareSelector{
    AuthedRoutes.of[User, IO] {
      case _@GET -> Root / "auth" / "config" as _ => extractShrineConfig

    }
  }

  val service: HttpRoutes[IO] = {
    import cats.implicits._ // SHRINE-3272
    //IDE does not like <+> , but it compiles just fine
    notAuthedRoute <+> authedRoute <+> notFoundService
  }

  private def extractShrineConfig :  IO[Response[IO]] = {
    import io.circe.config.syntax.CirceConfigOps

    val shrineConfig = ConfigSource.config.getConfig("shrine")

    val jsonEither = CirceConfigOps(shrineConfig).as[Json]

    jsonEither match {
      case Right(json) =>
        def filterPasswords(obj: JsonObject): JsonObject = {
            //do not filter out the shrine.webclient.passwordLabel field
            obj.filterKeys(p => !p.contains("password") && !p.equals("passwordLabel")).mapValues(p => {
              p.mapObject(filterPasswords)
            })
        }

        val filteredJson = json.mapObject(filterPasswords)

        Ok(filteredJson.spaces2)

      case Left(e) =>
        val msg = s"An error occurred while retrieving the shrine config $e"
        Log.error(msg, e)
        InternalServerError(msg)
    }
  }

  private def getLogo(req: Request[IO]) :IO[Response[IO]] = {

    val logoUriStrOption: Option[String] = ConfigSource.config.getOption("shrine.webclient.logo", _.getString)
    val defaultLogo = "SHRINE_logo.png"

    logoUriStrOption.map(logoUriStr => {
      staticFile(logoUriStr, req).flatMap(response => {
        if (response.status == Status.NotFound) {
          staticURL(logoUriStr, req)
        }
        else {
          IO(response)
        }
      })
    }).getOrElse(staticResource(defaultLogo, req)).flatMap{
      case e if e.status == Status.NotFound => staticResource(defaultLogo, req)
      case r => IO(r)
    }
  }

  private def extractWebClientConfig: IO[Response[IO]] = {
    import io.circe.generic.auto.exportEncoder
    import io.circe.generic.auto.exportDecoder
    import io.circe.syntax.EncoderOps
    import org.http4s.circe.jsonEncoder
    import io.circe.config.syntax.CirceConfigOps

    val clientConfig = ConfigSource.config.getConfig("shrine.webclient")

    CirceConfigOps(clientConfig).as[WebClientRawConfig] match {
      case Right(conf) => Ok(WebClientConfig(conf).asJson)

      case Left(e) =>
        val msg = s"An error occurred while retrieving the webclient config $e"
        Log.error(msg)
        InternalServerError(msg)
    }
  }

  private def handleKey(key: String): Try[String] = {
    val staticDataConfig = ConfigSource.config.getConfig("shrine.static")
    Try(staticDataConfig.getValue(key).render(ConfigRenderOptions.concise()))
  }

  private def handleGeneralFailure(@unused req: Request[IO], x: Throwable): IO[Response[IO]] = x match {
    case cfe: ConfigException => NotFound(cfe.getMessage)
    case NonFatal(e) => InternalServerError(e.getMessage)
    case _ => throw x
  }

  private def staticResource(resource: String, request: Request[IO]): IO[Response[IO]] = {
    try {
      StaticFile.fromResource(
        name = s"/$resource",
        req = Some(request)
      ).getOrElseF(NotFound(s"Resource not found $resource"))
    }
    catch {
      case e: Throwable =>
        Log.error("Error getting resource", e)
        NotFound(s"Unable to get resource $resource")
    }
  }


  private def staticFile(file: String, request: Request[IO]): IO[Response[IO]] = {
    try {
      StaticFile.fromString(
        url = file,
        req = Some(request)
      ).getOrElseF(NotFound(s"File not found $file"))
    }
    catch {
      case e: Throwable =>
        Log.error("Error getting file", e)
        NotFound(s"Unable to get file $file")
    }
  }

  private def staticURL(url: String, request: Request[IO]): IO[Response[IO]] = {
    try {
      StaticFile.fromURL(
        url = new URL(url),
        req = Some(request)
      ).getOrElseF(NotFound(s"Unable to get URL $url"))
    }
    catch {
      case e: Throwable =>
        Log.error(s"Error getting URL $url", e)
        NotFound(s"Unable to get URL $url")
    }
  }

  private def getVersion: IO[Response[IO]] = {
    import io.circe.syntax.EncoderOps
    import io.circe.generic.auto.exportEncoder

    val response: AppVersion = AppVersion()

    Ok(response.asJson.toString())
  }

  private def getDataDistributionTypes: IO[Response[IO]] = {
    import scala.jdk.CollectionConverters.SetHasAsScala
    import io.circe.generic.auto.exportEncoder

    case class DataDistributionTypes(value: String, description: String)

    val shrineBreakdownOutputConfig = ConfigSource.config.getConfig("shrine.breakdownResultOutputTypes")

    val dataDistributionTypes: Set[(String, DataDistributionTypes)] = shrineBreakdownOutputConfig.root.keySet.asScala.toSet
      .filter(name => shrineBreakdownOutputConfig.getConfig(name).hasPath("description"))
      .map { name: String => {
        val category = if (shrineBreakdownOutputConfig.getConfig(name).hasPath("category")) {
          shrineBreakdownOutputConfig.getConfig(name).getString("category")
        } else {
          "Demographic"
        }
        (category, DataDistributionTypes(name, shrineBreakdownOutputConfig.getConfig(name).getString("description"))
        )
      }
    }

    val dataDistributionTypesByCategory: Map[String, Seq[DataDistributionTypes]] = dataDistributionTypes.toSeq.groupBy(_._1).map(q => {
      (q._1, q._2.map(_._2).toSeq.sortWith((label1,label2) =>{
        Sort.compareAlphaNumerically(label1.description,label2.description) <= 0
      }))
    })

    Ok(dataDistributionTypesByCategory.asJson.spaces2)
  }

}

case class WebClientRawConfig(domain: Option[String],
                              name: Option[String],
                              bannerText: String,
                              shrineUrl: Option[String] = None,
                              siteAdminEmail: Option[String] = None,
                              termsOfUseText: String,
                              unauthorizedMessage: Option[String] = None,
                              usernameLabel: Option[String]= None,
                              passwordLabel: Option[String]= None,
                              defaultNumberOfOntologyChildren: Option[Int] = None,
                              queryFavingInstructions: Option[String]= None,
                              favingIconInstructions: Option[String]= None,
                              favingPlaceholderText: Option[String] = None,
                              nextStepsUrl: Option[String] = None,
                              helpLinks: Map[String, String],
                              ssoLinks: Option[Map[String, String]],
                              ssoLogoutUrl: Option[String],
                           )

case class WebClientConfig(domain: String,
                           name: String,
                           bannerText: String,
                           shrineUrl: Option[String] = None,
                           siteAdminEmail: Option[String] = None,
                           termsOfUseText: String,
                           unauthorizedMessage: Option[String] = None,
                           usernameLabel: Option[String]= None,
                           passwordLabel: Option[String]= None,
                           defaultNumberOfOntologyChildren: Option[Int] = None,
                           queryFavingInstructions: Option[String]= None,
                           favingIconInstructions: Option[String]= None,
                           favingPlaceholderText: Option[String] = None,
                           nextStepsUrl: Option[String] = None,
                           helpLinks: Map[String, String],
                           ssoLinks: Option[Map[String, String]],
                           ssoLogoutUrl: Option[String],
                          )

object WebClientConfig {
  def apply(webClientConfig: WebClientRawConfig): WebClientConfig = {
    val domain: String = webClientConfig.domain.getOrElse(ConfigSource.config.getString("shrine.i2b2Domain"))
    val name: String = webClientConfig.name.getOrElse(ConfigSource.config.getString("shrine.i2b2ShrineProjectName"))
    val bannerText: String = webClientConfig.bannerText

    WebClientConfig(
      domain = domain,
      name= name,
      bannerText = bannerText,
      shrineUrl = webClientConfig.shrineUrl,
      siteAdminEmail = webClientConfig.siteAdminEmail,
      termsOfUseText = webClientConfig.termsOfUseText,
      unauthorizedMessage = webClientConfig.unauthorizedMessage,
      usernameLabel= webClientConfig.usernameLabel,
      passwordLabel = webClientConfig.passwordLabel,
      defaultNumberOfOntologyChildren = webClientConfig.defaultNumberOfOntologyChildren,
      queryFavingInstructions = webClientConfig.queryFavingInstructions,
      favingIconInstructions = webClientConfig.favingIconInstructions,
      favingPlaceholderText = webClientConfig.favingPlaceholderText,
      nextStepsUrl = webClientConfig.nextStepsUrl,
      helpLinks = webClientConfig.helpLinks,
      ssoLinks = webClientConfig.ssoLinks,
      ssoLogoutUrl =  webClientConfig.ssoLogoutUrl
      )
  }
}

case class AppVersion(
                       currentVersion:String,
                       buildDate:String,
                       buildId:String
                     )

object AppVersion {
  def apply(): AppVersion = AppVersion(Versions.version, Versions.buildDate, Versions.scmRevision)
}



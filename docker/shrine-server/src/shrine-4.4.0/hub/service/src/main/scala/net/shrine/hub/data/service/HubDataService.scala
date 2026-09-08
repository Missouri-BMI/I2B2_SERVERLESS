package net.shrine.hub.data.service

import cats.effect.IO
import ch.qos.logback.classic.Level
import net.shrine.config.ConfigSource
import net.shrine.hub.data.store.{HubDatabaseNetworkNotFoundException, HubDb}
import net.shrine.problem.{AbstractProblem, ProblemSources}
import net.shrine.protocol.version.v2.{Network, Node}
import net.shrine.protocol.version.{NodeId, NodeKey}
import org.http4s.dsl.impl.{ ->, /, OptionalQueryParamDecoderMatcher, QueryParamDecoderMatcher}
import org.http4s.dsl.io.{ GET, InternalServerError, NotFound, Ok, Root, http4sInternalServerErrorSyntax, http4sNotFoundSyntax, http4sOkSyntax}
import org.http4s.headers.`Content-Type`
import org.http4s.{HttpRoutes, Request, Response}

import scala.util.control.NonFatal

object HubDataService {

  val jsonContentHeader: `Content-Type` = `Content-Type`(org.http4s.MediaType.application.json)

  object NodeNameMatcher extends QueryParamDecoderMatcher[String]("nodeName")
  object UserDomainNameMatcher extends QueryParamDecoderMatcher[String]("userDomain")
  object AdminEmailMatcher extends QueryParamDecoderMatcher[String]("adminEmail")
  object MomIdMatcher extends QueryParamDecoderMatcher[String]("momId")

  object OptionalNodeNameMatcher extends OptionalQueryParamDecoderMatcher[String]("nodeName")
  object OptionalUserDomainNameMatcher extends OptionalQueryParamDecoderMatcher[String]("userDomain")
  object OptionalMomQueueNameMatcher extends OptionalQueryParamDecoderMatcher[String]("momQueueName")
  object OptionalSendQueriesMatcher extends OptionalQueryParamDecoderMatcher[Boolean]("sendQueries")
  object OptionalAdminEmailMatcher extends OptionalQueryParamDecoderMatcher[String]("adminEmail")
  object OptionalMomIdMatcher extends OptionalQueryParamDecoderMatcher[String]("momId")

  object OptionalNetworkNameMatcher extends OptionalQueryParamDecoderMatcher[String]("networkName")

  val allow500ExceptionEndpoints: Boolean = ConfigSource.config.getBoolean("shrine.hub.allow500ExceptionEndpoints")
  val notAuthedService: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case _@GET -> Root / "ping" => Ok("pong")
    case GET -> Root / "exception" =>
      if(allow500ExceptionEndpoints) throw new RuntimeException("Test throw exception")
      else NotFound("allow500ExceptionEndpoints is not enabled")
    case GET -> Root / "IOException" =>
      if(allow500ExceptionEndpoints) IO{throw new RuntimeException("Test IO(throw exception)")}
      else NotFound("allow500ExceptionEndpoints is not enabled")
    case GET -> Root / "raiseError" =>
      if(allow500ExceptionEndpoints) IO.raiseError(new RuntimeException("Test raiseError"))
      else NotFound("allow500ExceptionEndpoints is not enabled")

    case request@GET -> Root / "node" / nodeKeyOrId =>
      val maybeNodeIO: IO[Option[Node]] = IO{new NodeId(nodeKeyOrId.toLong)}.flatMap(HubDb.db.selectNodeIO).
        handleErrorWith {
        case _:NumberFormatException =>
          val nodeKey = new NodeKey(nodeKeyOrId)
          HubDb.db.selectNodeByKeyIO(nodeKey)
      }
      maybeNodeIO.flatMap(_.fold(
        NotFound(s"No node with id or key of $nodeKeyOrId found")
      ){ node => Ok(node.asJsonText.underlying,jsonContentHeader)
      }).handleErrorWith {
        x: Throwable => handleGeneralFailure(request, x)
      }

    case request@GET -> Root / "network" =>
      useNetworkOrFail(request, (network: Network) => Ok(network.asJsonText.underlying,jsonContentHeader))
  }

  val service: HttpRoutes[IO] = {
    notAuthedService
  }

  private def useNetworkOrFail(request: Request[IO], func: Network => IO[Response[IO]]): IO[Response[IO]] = {
    HubDb.db.selectTheNetworkIO.attempt.flatMap {
      case Left(_: HubDatabaseNetworkNotFoundException) => NotFound(s"No network found. Shrine is misconfigured.")
      case Left(x) => handleGeneralFailure(request, x)
      case Right(network) => func(network)
    }
  }

  private def handleGeneralFailure(req: Request[IO], x: Throwable): IO[Response[IO]] = x match {
    case NonFatal(nfx) =>
      val path = req.pathInfo
      HubServerErrorProblem(nfx, path.toString())
      InternalServerError(
        s"""${getClass.getSimpleName} threw an exception while trying to $path.
           |${getClass.getSimpleName} response: ${x.getMessage} Exception: ${nfx.getClass}""".
          stripMargin)
    case _ => throw x
  }
}

case class HubServerErrorProblem(x: Throwable, function: String) extends AbstractProblem(ProblemSources.Hub) {
  override def logLevel: Level = Level.ERROR

  override val throwable: Option[Throwable] = Some(x)
  override val summary: String = "SHRINE's hub service encountered a problem."
  override val description: String =
    s"""HubDataService throws an exception while trying to $function,
       |the server's response is: ${x.getMessage} from ${x.getClass}.""".stripMargin
}

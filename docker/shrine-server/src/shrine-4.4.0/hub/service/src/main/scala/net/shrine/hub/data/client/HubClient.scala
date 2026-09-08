package net.shrine.hub.data.client


import cats.effect.IO
import com.typesafe.config.Config
import net.shrine.protocol.version.{JsonText, NodeKey}
import net.shrine.protocol.version.v2.{Network, Node}
import org.http4s.{Request, Status, Uri}
import net.shrine.config.{ConfigExtensions, ConfigSource}
import net.shrine.http4s.catsio.{LazyIO, RetryIO}
import net.shrine.http4s.client.Http4sHttpClient
import net.shrine.hub.mom.OkToRetry
import net.shrine.log.Loggable

import scala.concurrent.duration.{Duration, DurationInt}

trait HubClientApi {

  def pingHubIO:IO[String]

  def getNetworkIO: IO[Network]

  def getNodeForKeyIO(nodeKey:NodeKey): IO[Node]

  def getLocalNodeIO:IO[Node]
}

object HubClient extends HubClientApi {

  val hubClient:HubClientApi = if(ConfigSource.config.getBoolean("shrine.hub.client.inProcessForTesting")) HubTestClient
                               else HubHttpClient

  override def pingHubIO: IO[String] = hubClient.pingHubIO

  override def getNetworkIO: IO[Network] = hubClient.getNetworkIO

  override def getNodeForKeyIO(nodeKey: NodeKey): IO[Node] = hubClient.getNodeForKeyIO(nodeKey)

  override def getLocalNodeIO: IO[Node] = hubClient.getLocalNodeIO
}

object HubHttpClient extends HubClientApi with Loggable {
  val configPath = "shrine.hub.client"
  val hubClientConfig: Config = ConfigSource.config.getConfig(configPath)

  val hubClientTimeOut: Duration = hubClientConfig.get("httpClientTimeOut", Duration(_))

  val hubUri: Uri = hubClientConfig.getOption("serverUrl",  p => q => p.get(q, Uri.unsafeFromString)).getOrElse(ConfigSource.config.get("shrine.shrineHubBaseUrl", Uri.unsafeFromString))

  val hubServiceUri: Uri = hubUri / "shrine-api" / "hub"

  private lazy val http4sHttpClient = Http4sHttpClient(hubClientConfig)
  override def pingHubIO: IO[String] = {
    val request = HubServiceRequests.pingRequest(hubServiceUri)
    http4sHttpClient.webFetchAndDecodeIO[String](request) { (status: Status, bodyString: String) =>
      checkForErrorStatus(request, status, bodyString)
      if (status.responseClass == Status.Ok.responseClass) {
        IO(bodyString)
      }
      else {
        error(
          s"""Trying $request. HTTPResponse is a success but it does not contain an expected StatusCode
             |Expected StatusCodes: Ok, Actual StatusCode: $status
             |Body: $bodyString""".stripMargin)
        throw CouldNotCompleteApiTaskButOKToRetryException(request, Some(status), Some(bodyString))
      }
    }
  }

  val getNetworkIO:IO[Network] = LazyIO("getNetworkIO"){
    RetryIO.keepTrying(
      fetchNetworkIO,
      5.seconds,
      {case OkToRetry(_) => true},
      "getNetworkIO from hub"
    )
  }

  private def fetchNetworkIO: IO[Network] = {
    val request: Request[IO] = HubServiceRequests.getNetworkRequest(hubServiceUri)
    http4sHttpClient.webFetchAndDecodeIO[Network](request) { (status: Status, bodyString: String) =>
      checkForErrorStatus(request, status, bodyString)
      if (status.responseClass == Status.Ok.responseClass) {
        IO {
          Network.tryRead(new JsonText(bodyString)).get
        }
      }
      else {
        error(
          s"""Try to get the network. HTTPResponse is a success but it does not contain an expected StatusCode
             |Expected StatusCodes: Ok, Actual StatusCode: $status
             |Body: $bodyString""".stripMargin)
        throw CouldNotCompleteApiTaskButOKToRetryException(request, Some(status), Some(bodyString))
      }
    }
  }

  def getNodeForKeyIO(nodeKey:NodeKey): IO[Node] = {
    info(s"getNodeForKeyIO(${nodeKey.underlying}) called")
    val request: Request[IO] = HubServiceRequests.getNodeRequest(hubServiceUri, nodeKey)
    http4sHttpClient.webFetchAndDecodeIO[Node](request) { (status: Status, bodyString: String) =>
      checkForErrorStatus(request, status, bodyString)
      if (status.responseClass == Status.Ok.responseClass) {
        IO {
          Node.tryRead(new JsonText(bodyString)).get
        }
      }
      else {
        error(
          s"""Try to get a node, HTTPResponse is a success but it does not contain an expected StatusCode
             |Expected StatusCodes: Ok, Actual StatusCode: $status
             |Body: $bodyString""".stripMargin)
        throw CouldNotCompleteApiTaskButOKToRetryException(request, Some(status), Some(bodyString))
      }
    }
  }

  val getLocalNodeIO:IO[Node] = LazyIO("getLocalNodeIO"){
    RetryIO.keepTrying(
      getNodeForKeyIO(NodeKey.localNodeKey),
      5.seconds,
      {case OkToRetry(_) => true},
      s"getLocalNodeIO ${NodeKey.localNodeKey.underlying} from hub"
    )
  }

  def checkForErrorStatus(request:Request[IO], status: Status, bodyString:String): Status = {
    if (status.isSuccess) status
    else {
      //retry for all statuses
      throw CouldNotCompleteApiTaskButOKToRetryException(request, Some(status), Some(bodyString))

      /*
      Survivable status codes discovered during 2.0.0 development, stored for posterity

      //status codes we've observed but which aren't in http4s
      status.code match {
        case 598 => throw CouldNotCompleteApiTaskButOKToRetryException(request, Some(status), Some(bodyString)) //NetworkReadTimeout
        case 599 => throw CouldNotCompleteApiTaskButOKToRetryException(request, Some(status), Some(bodyString)) //NetworkConnectTimeout
        case _ => ; //pass through to better-known error codes
      }

      status.responseClass match {
          //todo retry for 502 here as well
        case x if x == Status.RequestTimeout.responseClass => throw CouldNotCompleteApiTaskButOKToRetryException(request, Some(status), Some(bodyString))
        case x if x == Status.NotFound.responseClass => throw CouldNotCompleteApiTaskDoNotRetryException(request, Some(status), Some(bodyString))
        case _ => throw CouldNotCompleteApiTaskDoNotRetryException(request, Some(status), Some(bodyString))
      }
      */
    }
  }
}

case class CouldNotCompleteApiTaskButOKToRetryException(request: Request[IO],
                                                        status:Option[Status] = None,
                                                        contents:Option[String] = None,
                                                        cause:Option[Throwable] = None
                                                       ) extends
  Exception(s"Could not $request due to status code $status with message '$contents', but another request could work.",cause.orNull)

case class CouldNotCompleteApiTaskDoNotRetryException(request: Request[IO],
                                                      status:Option[Status] = None,
                                                      contents:Option[String] = None,
                                                      cause:Option[Throwable] = None
                                                     ) extends
  Exception(s"Could not $request due to status code $status with message '$contents' . Another request is unlikely to work.",cause.orNull)

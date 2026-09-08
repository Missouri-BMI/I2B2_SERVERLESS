package net.shrine.hub.data.client

import cats.effect.IO
import net.shrine.protocol.version.{NodeId, NodeKey}
import org.http4s.{Method, Request, Uri}

object HubServiceRequests {

  def pingRequest(hubServiceUri: Uri): Request[IO] = Request(
    method = Method.GET,
    uri = hubServiceUri / "ping"
  )

  def getNetworkRequest(hubServiceUri: Uri): Request[IO] = Request(
    method = Method.GET,
    uri = hubServiceUri / "network"
  )

  def getNodeRequest(hubServiceUri: Uri,nodeKey:NodeKey): Request[IO] = Request(
    method = Method.GET,
    uri = hubServiceUri / "node" / s"${nodeKey.underlying}"
  )

  def getNodeRequest(hubServiceUri: Uri,nodeId:NodeId): Request[IO] = Request(
    method = Method.GET,
    uri = hubServiceUri / "node" / s"${nodeId.underlying}"
  )
}

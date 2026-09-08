package net.shrine.hub.data.client

import cats.effect.IO
import net.shrine.hub.data.store.HubDb
import net.shrine.protocol.version.NodeKey
import net.shrine.protocol.version.v2.{Network, Node}

/**
  * An implementation of the Hub Client that does not actually open a connection, for testing
  */

object HubTestClient extends HubClientApi {

  override def pingHubIO: IO[String] = IO("pong")

  override def getNetworkIO: IO[Network] = HubDb.db.selectTheNetworkIO

  override def getNodeForKeyIO(nodeKey: NodeKey): IO[Node] = HubDb.db.selectNodeByKeyIO(nodeKey).map(_.getOrElse(throw new IllegalArgumentException(s"No $nodeKey found.")))

  override def getLocalNodeIO: IO[Node] = getNodeForKeyIO(NodeKey.localNodeKey)
}

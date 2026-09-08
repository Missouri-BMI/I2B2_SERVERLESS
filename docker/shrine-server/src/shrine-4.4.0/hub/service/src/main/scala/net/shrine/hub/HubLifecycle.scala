package net.shrine.hub

import cats.effect.IO
import fs2.Stream
import net.shrine.hub.data.store.HubDb
import net.shrine.hub.mom.ShrineMomClient
import net.shrine.log.Loggable
import net.shrine.protocol.version.v2.{Network, Node}

/**
 * Should only be called from Bootstrap or service test code - to start shrine's legacy MOM system.
 */
object HubLifecycle extends Loggable {

  def initHubFromConfigIfEmptyIO():IO[Unit] = {
    HubDb.db.selectTheNetworkIO.attempt.flatMap {
      case Left(x) => throw x
      case Right(network) => IO(debug(s"${network.networkName} record found. Starting nodes from existing record"))
    }
  }

  def queuesFromDatabaseIO(): IO[Unit] = {

    def setUpQueuesForNodeIO(node: Node, network: Network): IO[Unit] = {
      for {
        shrineMomClient <- ShrineMomClient.serviceIO
        _ <- shrineMomClient.createQueueIfAbsentIO(node.momQueueName)
        _ <- shrineMomClient.addReceiverPermissionToQueueIO(node.momQueueName, node.momId)
        _ <- shrineMomClient.addSenderPermissionToQueueIO(node.momQueueName, network.momId)
        _ <- shrineMomClient.addSenderPermissionToQueueIO(network.hubQueueName, node.momId)
        _ <- IO(info(s"Created ${node.momQueueName}"))
      } yield ()
    }

    def createQueuesForNodes(network: Network, nodes: Seq[Node]): IO[Unit] = {
      val ios: Seq[IO[Unit]] = nodes.map(node => setUpQueuesForNodeIO(node, network))
      val streams: Seq[Stream[IO, Unit]] = ios.map[Stream[IO, Unit]](Stream.eval)
      streams.foldLeft[Stream[IO, Unit]](Stream.empty) { (soFar, next) =>
        soFar.append(next)
      }.compile.drain
    }

    val networkAndNodesIO: IO[(Network, Seq[Node])] = for {
      network <- HubDb.db.selectTheNetworkIO
      nodes <- HubDb.db.selectLatestNodesIO.map(_.toSeq).map(_.map(_.get))
    } yield (network,nodes)

    def createAllQueuesIO(network: Network, nodes: Seq[Node]): IO[Unit] = {
      for {
        shrineMomClient <- ShrineMomClient.serviceIO
        _ <- shrineMomClient.createQueueIfAbsentIO(network.hubQueueName)
        _ <- shrineMomClient.addReceiverPermissionToQueueIO(network.hubQueueName, network.momId)
        _ <- IO(info(s"Created ${network.hubQueueName}"))
        _ <- createQueuesForNodes(network, nodes)
      } yield ()
    }
    networkAndNodesIO.flatMap{networkAndNodes =>
      (networkAndNodes._1.kafkaConfig,networkAndNodes._1.awsSqsConfig) match {
        case (None,None) => createAllQueuesIO(networkAndNodes._1,networkAndNodes._2)
        case _ => IO.unit //if using AWS SQS or Kafka - the queues should already be set up and tomcat shouldn't have permission to do it
      }
    }
  }
}
package net.shrine.receiver

import cats.effect.IO
import net.shrine.config.{ConfigExtensions, ConfigSource}
import net.shrine.http4s.catsio.RepeatedIOTask
import net.shrine.hub.data.client.HubClient
import net.shrine.hub.mom.ShrineMomClient
import net.shrine.log.Loggable
import net.shrine.protocol.version.v2.{NodeSystemSpec, UpdateNodeSystemSpec}

import scala.concurrent.duration.FiniteDuration

object NodeSystemSpecSender extends Loggable {

  def startIO(): IO[Unit] = {
    sendNodeSystemSpec() *> nodeSystemSpecSender.startIO()
  }

  def stopIO(): IO[Unit] = nodeSystemSpecSender.stopIO()

  private def sendNodeSystemSpec(): IO[Unit] = {
    val updateIO: IO[UpdateNodeSystemSpec] = for {
      node <- HubClient.getLocalNodeIO
      //todo other IO results needed for future create method parameters go here
    } yield {
      val nodeSystemSpec = NodeSystemSpec.create(node)
      UpdateNodeSystemSpec(nodeSystemSpec)
    }

    updateIO.flatMap { update: UpdateNodeSystemSpec =>
      ShrineMomClient.sendToHubIO(
        subjectId = update.nodeSystemSpec.id,
        envelopeContents = update,
        envelopeContentsCompanion = UpdateNodeSystemSpec,
        logString = "Update node system spec at hub",
      )
    }.handleErrorWith(x => IO(error("Problem sending nodeSystemSpec", x)))
  }

  private lazy val nodeSystemSpecSender: RepeatedIOTask = {
    RepeatedIOTask.scheduleIndefinitely(
      initialDelay = FiniteDuration(0, "seconds"),
      interval = ConfigSource.config.getFiniteDuration("shrine.updateNodeSystemSpec.interval"),
      task = () => sendNodeSystemSpec(),
      name = "Send NodeSystemSpecs"
    )
  }

}

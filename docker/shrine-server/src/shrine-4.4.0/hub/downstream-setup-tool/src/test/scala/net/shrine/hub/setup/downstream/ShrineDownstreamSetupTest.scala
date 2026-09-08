package net.shrine.hub.setup.downstream

import net.shrine.protocol.version.v2.{Network, Node}
import net.shrine.protocol.version.{MomQueueName, NetworkId, NodeKey}
import org.junit.jupiter.api.Assertions.{assertEquals,assertThrows}
import org.junit.jupiter.api.{AfterEach, BeforeEach, Test}

class ShrineDownstreamSetupTest {
  import cats.effect.unsafe.implicits.global

  @Test
  def testHelp(): Unit = {
    ShrineDownstreamSetup.Help.doIt(Array("help")).unsafeRunSync()

    ShrineDownstreamSetup.commands.foreach{command =>
      ShrineDownstreamSetup.Help.doIt(Array("help",command.name)).unsafeRunSync()
    }
  }
}
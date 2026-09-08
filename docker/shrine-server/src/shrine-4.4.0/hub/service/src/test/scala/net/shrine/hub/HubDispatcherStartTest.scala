package net.shrine.hub

import cats.effect.IO
import fs2.Stream
import net.shrine.hub.data.store.HubDb
import net.shrine.messagequeueservice.MessageQueueService
import net.shrine.protocol.version.v2.{Network, Result, ResultProgress}
import net.shrine.protocol.version.{Envelope, MomQueueName, NodeId, NodeName, QueryId}
import org.junit.{After, Before, Test}
import org.scalatestplus.junit.AssertionsForJUnit

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.{Duration, FiniteDuration}


class HubDispatcherStartTest extends AssertionsForJUnit {
  /*
  @Test
  def testDispatcherReadyToStart():Unit = {
    HubDb.db.upsertNetworkBlocking(network)
    QueueLifecycle.queuesFromDatabase()
    HubDispatcher.restart()

    new HubDispatcherTest().testDispatcherSendAResult()

    HubDispatcher.stop()
  }

  @Test
  def testDispatcherNotReadyToStart():Unit = {
    HubDispatcher.restart()

    HubDispatcher.restart()

    //todo peek at the HubDispatcher's state
  }

  @Test
  def testDispatcherBecomesReadyToStart():Unit = {
    HubDispatcher.restart()

    HubDb.db.upsertNetworkBlocking(network)
    QueueLifecycle.queuesFromDatabase()

    HubDispatcher.restart()

    new HubDispatcherTest().testDispatcherSendAResult()

    HubDispatcher.stop()
  }

  val network = Network(
    networkName = "testNetwork",
    hubQueueName = new MomQueueName("testHub")
  )

  @Before
  def beforeEach(): Unit = {
    HubDb.db.createTables()
    QueueLifecycle.queuesFromDatabase()
  }

  @After
  def afterEach(): Unit = {
    HubDb.db.dropTables()
  }
*/
}

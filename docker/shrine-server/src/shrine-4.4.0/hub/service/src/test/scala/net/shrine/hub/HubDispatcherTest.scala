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



class HubDispatcherTest extends AssertionsForJUnit {
/*
  @Test
  def testDispatcherSendAResult():Unit = {
    val queue = MessageQueueService.service.getQueue(network.hubQueueName.underlying).unsafeRunSync()

    val expectedResult = ResultProgress(
      queryId = new QueryId(1),
      adapterNodeId = new NodeId(2),
      adapterNodeName = new NodeName("test adapter")
    )
    val envelope = Envelope(Result.envelopeType,expectedResult.asJsonText.underlying)

    MessageQueueService.service.send(envelope.asJsonText.underlying,queue).unsafeRunSync()
    implicit val timer = IO.timer(global)
    val pollInterval = Some(Duration("100 milliseconds")).collect { case d: FiniteDuration => d }.get

    val pollTask: IO[Seq[Result]] = IO.sleep(pollInterval).map(x => HubDb.db.selectAllResultsHistory.map(_.get))
    def pollStream: Stream[IO, Seq[Result]] = Stream.eval(pollTask).flatMap{ seq =>
      if (seq.isEmpty) Stream.eval(pollTask)
      else Stream(seq)
    }

    val seq: Option[List[Seq[Result]]] = pollStream.compile.toList.unsafeRunTimed(Duration("10 seconds"))
    assert(Some(List(Seq(expectedResult))) == seq)

    val results: Seq[Result] = HubDb.db.selectAllResultsHistory.map(_.get)
    assert(Seq(expectedResult) == results)
  }

  val network = Network(
    networkName = "testNetwork",
    hubQueueName = new MomQueueName("testHub")
  )

  @Before
  def beforeEach(): Unit = {
    HubDb.db.createTables()

    HubDb.db.upsertNetworkBlocking(network)

    QueueLifecycle.queuesFromDatabase()

    HubDispatcher.restart()
  }

  @After
  def afterEach(): Unit = {
    HubDispatcher.stop()
    HubDb.db.dropTables()
  }
*/
}

package net.shrine.hub

import net.shrine.hub.data.store.HubDb
import net.shrine.hub.mom.ShrineMomClient
import net.shrine.protocol.version.{MomQueueName, NodeKey, NodeName, UserDomainName}
import net.shrine.protocol.version.v2.{Network, Node}
import org.junit.{After, Before, Test}
import org.scalatestplus.junit.AssertionsForJUnit

class HubLifecycleTest extends AssertionsForJUnit {
  import cats.effect.unsafe.implicits.global
  @Test
  def testOneNode():Unit = {

    val expectedNode = Node(
      new NodeName("Test Node"),
      new NodeKey("testNode"),
      new UserDomainName("testnode.domain"),
      adminEmail = "email@foo",
      momId = "testNode",
    )

    val setUpIO = for {
      _ <- HubDb.db.upsertNodeIO(expectedNode)
      _ <- HubLifecycle.queuesFromDatabaseIO()
    } yield ()

    val queue = setUpIO.flatMap(_ => ShrineMomClient.serviceIO.flatMap(_.getQueueIO(expectedNode.momQueueName))).unsafeRunSync()

    assertResult(queue.name)(expectedNode.momQueueName)
  }

  @Test
  def testTwoNodes():Unit = {
    val expectedNode1 = Node(
      new NodeName("Test Node"),
      new NodeKey("testNode"),
      new UserDomainName("testnode.domain"),
      adminEmail = "email@foo",
      momId = "testNode",
    )
    HubDb.db.upsertNodeIO(expectedNode1).unsafeRunSync()

    val expectedNode2 = Node(
      new NodeName("Test Node"),
      new NodeKey("testNode"),
      new UserDomainName("testnode.domain"),
      adminEmail = "email@foo",
      momId = "testNode",
    )
    HubDb.db.upsertNodeIO(expectedNode2).unsafeRunSync()

    HubLifecycle.queuesFromDatabaseIO().unsafeRunSync()

    val queue1 = ShrineMomClient.serviceIO.flatMap(_.getQueueIO(expectedNode1.momQueueName)).unsafeRunSync()
    assertResult(queue1.name)(expectedNode1.momQueueName)

    val queue2 = ShrineMomClient.serviceIO.flatMap(_.getQueueIO(expectedNode1.momQueueName)).unsafeRunSync()
    assertResult(queue2.name)(expectedNode1.momQueueName)
  }

  @Before
  def beforeEach(): Unit = {
    HubDb.db.createTables()

    val expectedNetwork = Network(
      networkName = "testNetwork",
      hubQueueName = MomQueueName("testHub"),
      adminEmail = "yourname@example.com",
      momId = "testNetwork",
      awsSqsConfig = None,
      kafkaConfig = None
    )
    HubDb.db.upsertNetworkIO(expectedNetwork).unsafeRunSync()
  }

  @After
  def afterEach(): Unit = {
    HubDb.db.dropTables()
  }

}

package net.shrine.hub.setup

import net.shrine.hub.data.store.HubDb
import net.shrine.protocol.version.v2.{Network, Node, VersionInfo}
import net.shrine.protocol.version.{DateStamp, ItemVersion, MomQueueName, NetworkId, NodeKey, ProtocolVersion, ShrineVersion}
import org.junit.jupiter.api.Assertions.{assertEquals, assertThrows}
import org.junit.jupiter.api.{AfterEach, BeforeEach, Disabled, Test}

class ShrineNetworkLifecycleTest {
  import cats.effect.unsafe.implicits.global

  val expectedNodeTemplates: Seq[Node] = Seq(
    Node.create(
      name = "Test CTSA",
      key = "shrine-dev-hub",
      userDomainName = "shrine-dev-hub",
      adminEmail = "yourEmail@your.ctsa.edu",
      momQueueName = Option("shrinedevhub"),
      sendQueries = false,
      momId = "arn:aws:iam::CtsaAWSAccountNumber:user/hubUserName"
    ),
    Node.create(
      name = "Famous Hospital 2",
      key = "shrine-dev-node02",
      userDomainName = "shrine-dev-node02",
      adminEmail = "admin@hospital.famousUniversity2.edu",
      momQueueName = Option("shrinedevnode02"),
      momId = "arn:aws:iam::Node2AWSAccountNumber:user/node02UserName",
      versionInfo = VersionInfo(
        protocolVersion = new ProtocolVersion(1),
        shrineVersion = new ShrineVersion("3.3.2"),
        itemVersion = ItemVersion.one,
        createDate = DateStamp.now,
        changeDate = DateStamp.now
      ),
      understandsProtocol = new ProtocolVersion(1)
    ),
    Node.create(
      name = "Famous Hospital 1",
      key = "shrine-dev-node01",
      userDomainName = "shrine-dev-node01",
      adminEmail = "admin@hospital.famousUniversity1.edu",
      momQueueName = Option("shrinedevnode01"),
      momId = "arn:aws:iam::Node1AWSAccountNumber:user/node01UserName",
    )
  ).sortBy(_.key.underlying)

  @Test
  def testHelp(): Unit = {
    ShrineNetworkLifecycle.Help.doIt(Array("help")).unsafeRunSync()

    ShrineNetworkLifecycle.commands.foreach{command =>
      ShrineNetworkLifecycle.Help.doIt(Array("help",command.name)).unsafeRunSync()
    }
  }

  val templateNetwork = Network(
    id = NetworkId.oneNetwork,
    networkName = "SHRINE Dev test network",
    hubQueueName = MomQueueName("hub"),
    adminEmail = "yourEmail@your.ctsa.edu",
    momId = "arn:aws:iam::CtsaAWSAccountNumber:user/hubUserName",
    awsSqsConfig = None,
    kafkaConfig = None
  )

  @Test
  def testCreateNetwork():Unit  = {
    ShrineNetworkLifecycle.executeCommand(Array("createNetwork","src/test/inputs/network.conf"))

    val network = HubDb.db.selectTheNetworkIO.unsafeRunSync()

    val expectedNetwork = templateNetwork.copy(versionInfo = network.versionInfo)
    assertEquals(expectedNetwork,network)

    val nodes: Seq[Node] = HubDb.db.selectLatestNodesIO.unsafeRunSync().map(_.get).toSeq.sortBy(_.key.underlying)

    val expectedNodes = expectedNodeTemplates.zip(nodes).map{nodePair: (Node, Node) =>
      nodePair._1.copy(id = nodePair._2.id,versionInfo = nodePair._2.versionInfo)
    }

    assertEquals(expectedNodes,nodes)
  }

  @Test
  @Disabled //needs a Kafka system to talk to
  def testCreateKafkaNetwork(): Unit = {
    ShrineNetworkLifecycle.executeCommand(Array("createNetwork", "../../docker/scripts/src/main/docker/dev-environments/shrine-hub/network-setup/network.conf"))

    val network = HubDb.db.selectTheNetworkIO.unsafeRunSync()
    val expectedNetwork = templateNetwork.copy(versionInfo = network.versionInfo)

    val nodes: Seq[Node] = HubDb.db.selectLatestNodesIO.unsafeRunSync().map(_.get).toSeq.sortBy(_.key.underlying)

    val expectedNodes = expectedNodeTemplates.zip(nodes).map { nodePair: (Node, Node) =>
      nodePair._1.copy(id = nodePair._2.id, versionInfo = nodePair._2.versionInfo)
    }
  }


  @Test
  def testCreateNetworkTwiceShouldFail():Unit  = {
    ShrineNetworkLifecycle.executeCommand(Array("createNetwork","src/test/inputs/network.conf"))

    assertThrows(classOf[WrongArgumentsException], () =>
      ShrineNetworkLifecycle.executeCommand(Array("createNetwork","src/test/inputs/network.conf"))
    )

    val network = HubDb.db.selectTheNetworkIO.unsafeRunSync()

    val expectedNetwork = templateNetwork.copy(versionInfo = network.versionInfo)

    assertEquals(expectedNetwork,network)

    val nodes: Seq[Node] = HubDb.db.selectLatestNodesIO.unsafeRunSync().map(_.get).toSeq.sortBy(_.key.underlying)

    val expectedNodes = expectedNodeTemplates.zip(nodes).map{nodePair: (Node, Node) =>
      nodePair._1.copy(id = nodePair._2.id,versionInfo = nodePair._2.versionInfo)
    }

    assertEquals(expectedNodes,nodes)
  }

  @Test
  def testModifyNetwork():Unit  = {
    ShrineNetworkLifecycle.executeCommand(Array("createNetwork","src/test/inputs/network.conf"))
    ShrineNetworkLifecycle.executeCommand(Array("modifyNetwork","name=Better-named-network"))

    val network = HubDb.db.selectTheNetworkIO.unsafeRunSync()

    val expectedNetwork = Network(
      id = NetworkId.oneNetwork,
      networkName = "Better-named-network",
      hubQueueName = MomQueueName("hub"),
      adminEmail = "yourEmail@your.ctsa.edu",
      momId = "arn:aws:iam::CtsaAWSAccountNumber:user/hubUserName",
      awsSqsConfig = None,
      kafkaConfig = None,
    ).copy(versionInfo = network.versionInfo)

    assertEquals(expectedNetwork,network)
    assertEquals(2,network.versionInfo.itemVersion.underlying)
  }

  @Test
  def testShowNetworkAndNodes():Unit = {
    ShrineNetworkLifecycle.executeCommand(Array("createNetwork","src/test/inputs/network.conf"))

    ShrineNetworkLifecycle.executeCommand(Array("showNetwork"))
    ShrineNetworkLifecycle.executeCommand(Array("listNodes"))

    val nodeKey = expectedNodeTemplates.head.key
    ShrineNetworkLifecycle.executeCommand(Array("showNode",nodeKey.underlying))
  }

  @Test
  def testCreateNode():Unit = {
    ShrineNetworkLifecycle.executeCommand(Array("createNetwork","src/test/inputs/network.conf"))
    ShrineNetworkLifecycle.executeCommand(Array("createNode","key=h4", "name=Hospital 4", "userDomainName=h4Land", "adminEmail=yourself@h4.edu", "queueName=h4q", "momId=h4IsMe"))

    ShrineNetworkLifecycle.executeCommand(Array("showNode","h4"))

    val node = HubDb.db.selectNodeByKeyIO(new NodeKey("h4")).unsafeRunSync()

    val expectedNode = Node.create(
      name = "Hospital 4",
      key = "h4",
      userDomainName = "h4Land",
      adminEmail = "yourself@h4.edu",
      momQueueName = Option("h4q"),
      momId = "h4IsMe"
    ).copy(id = node.get.id,versionInfo = node.get.versionInfo)

    assertEquals(expectedNode,node.get)
  }

  @Test
  def testModifyNode():Unit = {
    ShrineNetworkLifecycle.executeCommand(Array("createNetwork","src/test/inputs/network.conf"))

    ShrineNetworkLifecycle.executeCommand(Array("modifyNode","shrine-dev-hub", "name=Best CTSA"))

    val nodeOpt = HubDb.db.selectNodeByKeyIO(new NodeKey("shrine-dev-hub")).unsafeRunSync()

    val node = nodeOpt.get
    val expectedNode = Node.create(
      name = "Best CTSA",
      key = "shrine-dev-hub",
      userDomainName = "shrine-dev-hub",
      adminEmail = "yourEmail@your.ctsa.edu",
      momQueueName = Option("shrinedevhub"),
      sendQueries = false,
      momId = "arn:aws:iam::CtsaAWSAccountNumber:user/hubUserName",
      versionInfo =  VersionInfo(
        protocolVersion = new ProtocolVersion(1),
        shrineVersion = new ShrineVersion("3.3.2"),
        itemVersion = ItemVersion.one,
        createDate = node.versionInfo.createDate,
        changeDate = node.versionInfo.changeDate
      ),
    ).copy(id = node.id,versionInfo = node.versionInfo)

    assertEquals(expectedNode,node)
  }

  @Test
  def testRetireRestoreNode():Unit = {
    ShrineNetworkLifecycle.executeCommand(Array("createNetwork","src/test/inputs/network.conf"))
    val beforeNode = HubDb.db.selectNodeByKeyIO(new NodeKey("shrine-dev-node01")).unsafeRunSync().get

    ShrineNetworkLifecycle.executeCommand(Array("retireNode","shrine-dev-node01"))

    val retiredNode = HubDb.db.selectNodeByKeyIO(new NodeKey("shrine-dev-node01")).unsafeRunSync().get
    val expectedNode = beforeNode.copy(versionInfo = retiredNode.versionInfo,sendQueries = false)

    assertEquals(expectedNode,retiredNode)

    ShrineNetworkLifecycle.executeCommand(Array("restoreNode","shrine-dev-node01"))

    val restoredNode = HubDb.db.selectNodeByKeyIO(new NodeKey("shrine-dev-node01")).unsafeRunSync().get
    val expectedRestoredNode = beforeNode.copy(versionInfo = restoredNode.versionInfo,sendQueries = true)

    assertEquals(expectedRestoredNode,restoredNode)
  }

  @Test
  @Disabled //needs a Kafka system to talk to
  def testSwitchMomSystem(): Unit = {
    ShrineNetworkLifecycle.executeCommand(Array("createNetwork", "src/test/inputs/network.conf"))

    val startNetwork = HubDb.db.selectTheNetworkIO.unsafeRunSync()
    val expectedStartNetwork = templateNetwork.copy(versionInfo = startNetwork.versionInfo)
    assertEquals(expectedStartNetwork, startNetwork)

    val startNodes: Seq[Node] = HubDb.db.selectLatestNodesIO.unsafeRunSync().map(_.get).toSeq.sortBy(_.key.underlying)
    val expectedStartNodes = expectedNodeTemplates.zip(startNodes).map { nodePair: (Node, Node) =>
      nodePair._1.copy(id = nodePair._2.id, versionInfo = nodePair._2.versionInfo)
    }
    assertEquals(expectedStartNodes, startNodes)

    ShrineNetworkLifecycle.executeCommand(Array("switchMomSystem", "src/test/inputs/switch-network-to-kafka.conf"))

    val kafkaNetwork = HubDb.db.selectTheNetworkIO.unsafeRunSync()
  }

  @BeforeEach
  def beforeEach(): Unit = {
    HubDb.db.createTables()
  }

  @AfterEach
  def afterEach(): Unit = {
    HubDb.db.dropTables()
  }
}
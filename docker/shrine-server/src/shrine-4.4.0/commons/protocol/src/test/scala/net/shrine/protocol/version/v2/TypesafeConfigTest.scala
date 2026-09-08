package net.shrine.protocol.version.v2

import com.typesafe.config.{Config, ConfigFactory}
import org.junit.Test
import org.scalatestplus.junit.AssertionsForJUnit

/**
  * Tests of reading common currency from typesafe config
  */
class TypesafeConfigTest extends AssertionsForJUnit {

  @Test
  def networkFromTypesafeConfig():Unit = {
    val configString: String =
      s"""network
         |{
         |  name = "Shrine Dev Test Network" //Name of your network
         |  hubQueueName = "hub" //queue used to send messages to the hub, different from the queue used to send messages to a QEP and an adapter colocated with the hub
         |  adminEmail = "yourname@example.com"
         |  momId = "hub"
         |}
       """.stripMargin

    val config: Config = ConfigFactory.parseString(configString)
    val network: Network = Network.networkFromConfig(config)

    assertResult("Shrine Dev Test Network")(network.networkName)
    assertResult("hub")(network.hubQueueName.underlying)
  }

  @Test
  def nodesFromTypesafeConfig():Unit = {
    val configString: String =
      s"""nodes = [
         |        {
         |          name = "Shrine Dev2", //human-readable name for this node
         |          key = "shrineDev2", //machine-friendly key used to identify this node. Never change this.
         |          userDomainName = "shrine-dev2.catalyst", //domain name for users from this node.
         |          queueName = "shrineDev2", //queue used to send messages to the qep and adapter at this node. This field is optional, defautls to the key if not specified
         |          sendQueries = "false" //true to send queries to an adapter at this node. An optional field, true by default.
         |          adminEmail = "yourname@example.com" //the email address for the admin of this node
         |          momId = "shrineDev2"
         |        },
         |        {
         |          name = "Shrine Dev2",
         |          key = "shrineDev2",
         |          userDomainName = "shrine-dev2.catalyst"
         |          adminEmail = "yourname@example.com" //the email address for the admin of this node
         |          momId = "shrineDev2"
         |        }
         |        ]
       """.stripMargin

    val config: Config = ConfigFactory.parseString(configString)
    val nodes: Seq[Node] = Node.nodesFromConfig(config)

    assert(nodes.size == 2)

    assertResult("Shrine Dev2")(nodes.head.name.underlying)
    assertResult("shrineDev2")(nodes.head.key.underlying)
    assertResult("shrine-dev2.catalyst")(nodes.head.userDomainName.underlying)
    assertResult("shrineDev2")(nodes.head.momQueueName.underlying)
    assertResult(false)(nodes.head.sendQueries)
    assertResult("yourname@example.com")(nodes.head.adminEmail)

    assertResult("Shrine Dev2")(nodes(1).name.underlying)
    assertResult("shrineDev2")(nodes(1).key.underlying)
    assertResult("shrine-dev2.catalyst")(nodes(1).userDomainName.underlying)
    assertResult("shrineDev2")(nodes(1).momQueueName.underlying)
    assertResult(true)(nodes(1).sendQueries)
  }

}

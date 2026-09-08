package net.shrine.protocol.version.v2

import net.shrine.protocol.version.{JsonText, MomQueueName, ShrineVersion}
import net.shrine.util.ShouldMatchersForJUnit
import org.junit.Test

class NetworkTest extends ShouldMatchersForJUnit {

  val jsonNetwork: String = V2JsonTest.readJsonFile("/v2/network.json")

  val expectedNetwork = Network(
    networkName = "testNetwork",
    versionInfo = V2JsonTest.staticVersionInfo,
    hubQueueName = MomQueueName("testMomQueueName"),
    adminEmail = "test_email@email.mail",
    momId = "testNetwork",
    awsSqsConfig = None,
    kafkaConfig = None
  )

  @Test
  def testNetworkAgainstV2Json():Unit = {
    val result: Network = Network.tryRead(new JsonText(jsonNetwork)).get
    val rightVersion = result.copy(versionInfo = result.versionInfo.copy(shrineVersion = V2JsonTest.staticVersionInfo.shrineVersion))

    assertResult(expectedNetwork)(rightVersion)
  }
}

package net.shrine.protocol.version.v2

import net.shrine.protocol.version.JsonText
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertEquals

class NodeTest {
  val jsonNode: String = V2JsonTest.readJsonFile("/v2/node.json")
  val expectedNode: Node = V2JsonTest.staticNode

  @Test
  def testNodeAgainstV2Json():Unit = {
    val result: Node = Node.tryRead(new JsonText(jsonNode)).get
    val rightVersion = result.copy(versionInfo = result.versionInfo.copy(shrineVersion = V2JsonTest.staticVersionInfo.shrineVersion))

    assertEquals(expectedNode,rightVersion)
  }
}

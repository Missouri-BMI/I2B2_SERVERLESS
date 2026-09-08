package net.shrine.protocol.version.v2

import net.shrine.protocol.version.{JsonText, ShrineVersion}
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.{Disabled, Test}

class UpdateNodeSystemSpecTest {

  val expectedUpdateNodeSystemVersions: UpdateNodeSystemSpec = UpdateNodeSystemSpec(
    nodeSystemSpec = NodeSystemSpec.create(V2JsonTest.staticNode)
  )

  @Test
  def testUpdateNodeSystemVersionsRoundTrip():Unit = {
    V2JsonTest.testRoundTrip(expectedUpdateNodeSystemVersions.asJsonText.underlying, expectedUpdateNodeSystemVersions, UpdateNodeSystemSpec.tryRead)
  }

  @Test
  def testUpdateNodeSystemVersionsFromJson():Unit = {
    UpdateNodeSystemSpec.tryRead(new JsonText(V2JsonTest.readJsonFile("/v2/updateNodeSystemVersions.json"))).get

    //this update is all about system contexts. Most fields will need some tweak to work for a test, so just reading in the json format should be enough.
  }
}
package net.shrine.protocol.version.v2

import net.shrine.protocol.version.JsonText
import net.shrine.util.ShouldMatchersForJUnit
import org.junit.Test

class ResearcherTest extends ShouldMatchersForJUnit {

  val jsonResearcher: String = V2JsonTest.readJsonFile("/v2/researcher.json")
  val expectedResearcher: Researcher = V2JsonTest.staticResearcher

  @Test
  def testResearcherAgainstV2Json():Unit = {
    val result: Researcher = Researcher.tryRead(new JsonText(jsonResearcher)).get
    val rightVersion = result.copy(versionInfo = result.versionInfo.copy(shrineVersion = V2JsonTest.staticVersionInfo.shrineVersion))

    assertResult(expectedResearcher)(rightVersion)
  }
}

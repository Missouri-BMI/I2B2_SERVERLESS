package net.shrine.protocol.version.v2

import net.shrine.protocol.version.{JsonText, ShrineVersion}
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RunQueryForResultTest {

  val expectedRunQueryForResult: RunQueryForResult = RunQueryForResult(
    query = V2JsonTest.staticQueryProgress,
    researcher = V2JsonTest.staticResearcher,
    node = V2JsonTest.staticNode,
    resultProgress = V2JsonTest.staticResultProgress
  )

  @Test
  def testRunQueryForResultAgainstV2Json():Unit = {
    V2JsonTest.testRoundTrip(expectedRunQueryForResult.asJsonText.underlying, expectedRunQueryForResult, RunQueryForResult.tryRead)
  }

  @Test
  def testRunQueryLoadFromJson():Unit = {
    val expected = expectedRunQueryForResult.copy(
      query = expectedRunQueryForResult.query.asInstanceOf[QueryProgress].copy(versionInfo = expectedRunQueryForResult.query.versionInfo.copy(shrineVersion = new ShrineVersion("4.2.0-SNAPSHOT"))),
      researcher = expectedRunQueryForResult.researcher.copy(versionInfo = expectedRunQueryForResult.query.versionInfo.copy(shrineVersion = new ShrineVersion("4.2.0-SNAPSHOT"))),
      node = expectedRunQueryForResult.node.copy(versionInfo = expectedRunQueryForResult.query.versionInfo.copy(shrineVersion = new ShrineVersion("4.2.0-SNAPSHOT"))),
      resultProgress = expectedRunQueryForResult.resultProgress.copy(versionInfo = expectedRunQueryForResult.query.versionInfo.copy(shrineVersion = new ShrineVersion("4.2.0-SNAPSHOT")))
    )

    val result = RunQueryForResult.tryRead(new JsonText(V2JsonTest.readJsonFile("/v2/runQueryForResult.json"))).get

    assertEquals(expected,result)
  }
}

package net.shrine.protocol.version.v2

import net.shrine.protocol.version.{JsonText, ShrineVersion}
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RunQueryAtHubTest {

  val expectedRunQueryAtHub: RunQueryAtHub = RunQueryAtHub(
    query = V2JsonTest.staticQueryProgress,
    researcher = V2JsonTest.staticResearcher,
  )

  @Test
  def testRunQueryAtHubRoundTrip():Unit = {
    V2JsonTest.testRoundTrip(expectedRunQueryAtHub.asJsonText.underlying, expectedRunQueryAtHub, RunQueryAtHub.tryRead)
  }

  @Test
  def testRunQueryLoadFromJson():Unit = {
    //tests on the build server may have a strange value for ShrineVersion
    val expected: RunQueryAtHub = expectedRunQueryAtHub.copy(
      query = expectedRunQueryAtHub.query.copy(versionInfo = expectedRunQueryAtHub.query.versionInfo.copy(shrineVersion = new ShrineVersion("4.2.0-SNAPSHOT"))),
      researcher = expectedRunQueryAtHub.researcher.copy(versionInfo = expectedRunQueryAtHub.query.versionInfo.copy(shrineVersion = new ShrineVersion("4.2.0-SNAPSHOT"))),
    )

    val result = RunQueryAtHub.tryRead(new JsonText(V2JsonTest.readJsonFile("/v2/runQueryAtHub.json"))).get

    assertEquals(expected,result)
  }
}
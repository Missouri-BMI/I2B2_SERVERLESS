package net.shrine.protocol.version.v2

import net.shrine.problem.{JsonProblemDigest, TestProblem}
import net.shrine.protocol.version.{ItemVersion, JsonText}
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertEquals

class UpdateQueryAtQepTest {

  val jsonUpdateQueryAtQepWithStatus: String = V2JsonTest.readJsonFile("/v2/updateQueryAtQepWithStatus.json")
  val jsonUpdateQueryAtQepWithError: String = V2JsonTest.readJsonFile("/v2/updateQueryAtQepWithError.json")
  val jsonUpdateQueryReadyForAdapters: String = V2JsonTest.readJsonFile("/v2/updateQueryReadyForAdapters.json")

  val expectedUpdateQueryAtQepWithStatus: UpdateQueryAtQepWithStatus = UpdateQueryAtQepWithStatus(V2JsonTest.staticQueryProgress)
  val expectedUpdateQueryAtQepWithError: UpdateQueryAtQepWithError = UpdateQueryAtQepWithError (
    queryId = V2JsonTest.staticQueryProgress.id,
    problemDigest = JsonProblemDigest(TestProblem()),
    expectedItemVersion = ItemVersion.next(ItemVersion.one),
    changeDate = V2JsonTest.staticDateStamp
  )
  val expectedUpdateQueryReadyForAdapters: UpdateQueryReadyForAdapters = UpdateQueryReadyForAdapters (
    query = V2JsonTest.staticQueryProgress,
    resultProgresses = Seq (V2JsonTest.staticResultProgress)
  )

  @Test
  def testUpdateQueryAtQepAgainstV2Json():Unit = {
    V2JsonTest.testRoundTrip(jsonUpdateQueryAtQepWithStatus, expectedUpdateQueryAtQepWithStatus, UpdateQueryAtQep.tryRead)
    V2JsonTest.testRoundTrip(jsonUpdateQueryAtQepWithError, expectedUpdateQueryAtQepWithError, UpdateQueryAtQep.tryRead)

    val result: UpdateQueryReadyForAdapters = UpdateQueryAtQep.tryRead(new JsonText(jsonUpdateQueryReadyForAdapters)).get.asInstanceOf[UpdateQueryReadyForAdapters]
    val rightVersion: UpdateQueryReadyForAdapters = result.copy(resultProgresses = result.resultProgresses
      .map(r => r.copy(versionInfo = r.versionInfo.copy(shrineVersion = V2JsonTest.staticVersionInfo.shrineVersion), queryId = V2JsonTest.staticResultProgress.queryId )))

    assertEquals(expectedUpdateQueryReadyForAdapters,rightVersion)
  }
}
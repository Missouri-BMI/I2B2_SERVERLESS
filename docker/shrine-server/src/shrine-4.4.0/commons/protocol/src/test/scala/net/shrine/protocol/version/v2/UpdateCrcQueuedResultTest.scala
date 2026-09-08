package net.shrine.protocol.version.v2

import net.shrine.problem.{JsonProblemDigest, TestProblem}
import net.shrine.protocol.version.{JsonText, QueryId}
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test


class UpdateCrcQueuedResultTest {
  val jsonUpdateResultWithProgress: String = V2JsonTest.readJsonFile("/v2/updateResultWithProgress.json")
  val jsonUpdateResultWithError: String = V2JsonTest.readJsonFile("/v2/updateResultWithError.json")
  val jsonUpdateResultWithCrcResult: String = V2JsonTest.readJsonFile("/v2/updateResultWithCrcResult.json")

  val expectedUpdateResultWithProgress: UpdateCrcQueuedResultWithProgress = UpdateCrcQueuedResultWithProgress(queryId = new QueryId(V2JsonTest.staticQueryId), adapterNodeKey = V2JsonTest.staticNode.key, status = V2JsonTest.staticResultProgress.status, statusMessage = Some("test status message"), crcQueryInstanceId = None, ResultMetadata(Option(ObfuscatingParameters(5,6.5,10,10))))
  val expectedUpdateResultWithError: UpdateCrcQueuedResultWithError = UpdateCrcQueuedResultWithError(queryId = new QueryId(V2JsonTest.staticQueryId), adapterNodeKey = V2JsonTest.staticNode.key, problem = JsonProblemDigest(TestProblem()), status = V2JsonTest.staticResultProgress.status, statusMessage = Some("test status message"), crcQueryInstanceId = Some(2), resultMetadata = ResultMetadata(Option(ObfuscatingParameters(5,6.5,10,10))))

  val expectedUpdateResultWithCrcResult: UpdateCrcQueuedResultWithCount = UpdateCrcQueuedResultWithCount(queryId = new QueryId(V2JsonTest.staticQueryId), adapterNodeKey = V2JsonTest.staticNode.key, count = 1, crcQueryInstanceId = 2, resultMetadata = ResultMetadata(Option(ObfuscatingParameters(5,6.5,10,10))), status = V2JsonTest.staticResultProgress.status, statusMessage = Some("test status message"))

  def roundTripTest(expectedWrongTime:UpdateCrcQueuedResult, string:String):Unit = {

    val result: UpdateCrcQueuedResult = UpdateCrcQueuedResult.tryRead(new JsonText(string)).get
    val expected = expectedWrongTime.withAdapterTime(result.adapterTime)

    assertEquals(expected,result)
    assertEquals(expected.asJsonText,result.asJsonText)
  }

  @Test
  def testUpdateQueryResultAgainstV2Json():Unit = {
    val jsonUpdateResultWithProgress: String = V2JsonTest.readJsonFile("/v2/updateResultWithProgressAndAdapterTime.json")
    val jsonUpdateResultWithError: String = V2JsonTest.readJsonFile("/v2/updateResultWithErrorAndAdapterTime.json")
    val jsonUpdateResultWithCrcResult: String = V2JsonTest.readJsonFile("/v2/updateResultWithCrcResultAndAdapterTime.json")

    roundTripTest(expectedUpdateResultWithProgress,jsonUpdateResultWithProgress)
    roundTripTest(expectedUpdateResultWithError,jsonUpdateResultWithError)
    roundTripTest(expectedUpdateResultWithCrcResult,jsonUpdateResultWithCrcResult)
  }
}

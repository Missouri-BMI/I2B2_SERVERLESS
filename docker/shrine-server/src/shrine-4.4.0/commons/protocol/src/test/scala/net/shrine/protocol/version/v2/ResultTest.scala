package net.shrine.protocol.version.v2

import net.shrine.problem.{JsonProblemDigest, TestProblem}
import net.shrine.protocol.version.JsonText
import net.shrine.util.ShouldMatchersForJUnit
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertEquals

class ResultTest extends ShouldMatchersForJUnit {

  val jsonResultProgress: String = V2JsonTest.readJsonFile("/v2/resultProgress.json")
  val jsonCrcResult: String = V2JsonTest.readJsonFile("/v2/crcResult.json")
  val jsonErrorResult: String = V2JsonTest.readJsonFile("/v2/errorResult.json")

  val expectedResultProgress: ResultProgress = V2JsonTest.staticResultProgress

  val expectedCrcResult: CountResult = CountResult(id = V2JsonTest.staticResultId,
    versionInfo = V2JsonTest.staticVersionInfo,
    queryId = V2JsonTest.staticQueryProgress.id,
    adapterNodeId = V2JsonTest.staticNodeId,
    adapterNodeName = V2JsonTest.staticNode.name,
    status = ResultStatus.IdAssigned,
    statusMessage = Some("test status message"),
    crcQueryInstanceId = Some(0), count = 0,
    resultMetadata = ResultMetadata(Option(ObfuscatingParameters(5,6.5,10,10))),
    breakdowns = None
  )

  val expectedErrorResult: ErrorResult = ErrorResult(id = V2JsonTest.staticResultId,
    versionInfo = V2JsonTest.staticVersionInfo,
    queryId = V2JsonTest.staticQueryProgress.id,
    adapterNodeId = V2JsonTest.staticNodeId,
    adapterNodeName = V2JsonTest.staticNode.name,
    status = ResultStatus.IdAssigned,
    statusMessage = Some("test ErrorFromCrc message"),
    crcQueryInstanceId = None,
    problemDigest = JsonProblemDigest(TestProblem()),
    resultMetadata = ResultMetadata(Option(ObfuscatingParameters(5,6.5,10,10)))
  )

  def checkWithRightShrineVersionAndQueryId(expectedResult:Result,json: String):Unit = {
    val result: Result = Result.tryRead(new JsonText(json)).get
    val rightVersion = result.withVersionInfoAndQueryId(
      versionInfo = result.versionInfo.copy(shrineVersion = V2JsonTest.staticVersionInfo.shrineVersion),
      queryId = expectedResult.queryId
    )
    assertEquals(expectedResult,rightVersion)
  }

  @Test
  def testResultAgainstV2Json():Unit = {
    checkWithRightShrineVersionAndQueryId(expectedResultProgress,jsonResultProgress)
    checkWithRightShrineVersionAndQueryId(expectedCrcResult,jsonCrcResult)
    checkWithRightShrineVersionAndQueryId(expectedErrorResult,jsonErrorResult)
  }
}

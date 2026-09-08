package net.shrine.protocol.version.v2

import net.shrine.util.ShouldMatchersForJUnit
import org.junit.Test

class QueryTest extends ShouldMatchersForJUnit {

  val expectedQueryProgress: QueryProgress = V2JsonTest.staticQueryProgress
  val expectedQueryError: QueryError = V2JsonTest.staticQueryError

  @Test
  def testQueryAgainstV2Json():Unit = {
    V2JsonTest.testRoundTrip(expectedQueryProgress.asJsonText.underlying, expectedQueryProgress, Query.tryRead)
    V2JsonTest.testRoundTrip(expectedQueryError.asJsonText.underlying, expectedQueryError, Query.tryRead)
  }
}

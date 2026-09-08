package net.shrine.protocol.version.v2

import net.shrine.protocol.version.QueryId
import org.junit.Test

class UpdateQueryAtHubTest {

  val jsonUpdateQueryAtHubWithName: String = V2JsonTest.readJsonFile("/v2/updateQueryAtHubWithNameAndNotes.json")
  val jsonUpdateQueryAtHubWithFaving: String = V2JsonTest.readJsonFile("/v2/updateQueryAtHubWithFaving.json")

  val expectedUpdateQueryAtHubWithName: UpdateQueryAtHubWithNameAndNotes = UpdateQueryAtHubWithNameAndNotes (
    queryId = new QueryId(V2JsonTest.staticQueryId),
    expectedItemVersion = None,
    queryName = "test query name",
    queryNotes = Some("test query notes")
  )

  val expectedUpdateQueryAtHubWithFaving: UpdateQueryAtHubWithFaving = UpdateQueryAtHubWithFaving(
    queryId = new QueryId(V2JsonTest.staticQueryId),
    expectedItemVersion = None,
    faved = true
  )

  @Test
  def testUpdateQueryAtAdapterAgainstV2Json():Unit = {
    V2JsonTest.testRoundTrip(jsonUpdateQueryAtHubWithName, expectedUpdateQueryAtHubWithName, UpdateQueryAtHub.tryRead)
    V2JsonTest.testRoundTrip(jsonUpdateQueryAtHubWithFaving, expectedUpdateQueryAtHubWithFaving, UpdateQueryAtHub.tryRead)
  }
}

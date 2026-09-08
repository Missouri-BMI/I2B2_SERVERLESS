package net.shrine.protocol.version

import net.shrine.protocol.version.v2.V2JsonTest
import org.junit.jupiter.api.Test

class V2EnvelopeTest  {

  val jsonEnvelope: String = V2JsonTest.readJsonFile("/v2/envelope.json")
  val expectedEnvelope: Envelope = Envelope(
    contents = "test contents",
    contentsType = "test contents type",
    contentsSubject = 0,
    protocolVersion = v2.versionId
  )

  @Test
  def testEnvelopeAgainstV2Json():Unit = {
    val resultEnvelope = Envelope.tryRead(new JsonText(jsonEnvelope)).get
    assert(expectedEnvelope.equalsExceptShrineVersion(resultEnvelope))
  }
}

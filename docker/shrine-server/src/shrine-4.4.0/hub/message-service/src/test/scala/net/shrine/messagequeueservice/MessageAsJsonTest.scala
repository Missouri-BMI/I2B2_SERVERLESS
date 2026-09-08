package net.shrine.messagequeueservice

import net.shrine.protocol.version.v2.Node
import net.shrine.protocol.version.{Envelope, JsonText}
import org.junit.jupiter.api.Test

final class MessageAsJsonTest {

  val testTimeoutMilliseconds = 10000L

  @Test
  def testMessageJsonRoundTrip(): Unit = {
    val expectedMessageAsJson: MessageAsJson = MessageAsJson(new DeliveryAttemptId(-43),testTimeoutMilliseconds,3,"test message")
    val theMessageJson = expectedMessageAsJson.asJsonText

    val gotMessageAsJson = MessageAsJson.tryRead(theMessageJson).get
    assert(expectedMessageAsJson == gotMessageAsJson)
  }

  @Test
  def testWithEnvelope(): Unit = {
    val expectedEnvelope = Envelope("testString",0L,"test envelope contents")
    val expectedEnvelopeJson = expectedEnvelope.asJsonText.underlying

    val expectedMessageAsJson: MessageAsJson = MessageAsJson(new DeliveryAttemptId(-43),testTimeoutMilliseconds,3,expectedEnvelopeJson)
    val theMessageJson = expectedMessageAsJson.asJsonText

    val gotMessageAsJson = MessageAsJson.tryRead(theMessageJson).get
    assert(expectedMessageAsJson == gotMessageAsJson)

    val gotEnvelopeAsJson = new JsonText(gotMessageAsJson.contents)
    val gotEnvelope = Envelope.tryRead(gotEnvelopeAsJson).get

    assert(expectedEnvelope == gotEnvelope)
  }

  @Test
  def testWithEnvelopeWithStuff(): Unit = {
    val expectedNode = Node.create(
      name = "testNode",
      key = "testNode",
      userDomainName = "testNodeName",
      adminEmail = "email@foo",
      momId = "testNode"
    )

    val expectedEnvelope = Envelope(Node.envelopeType,0L,expectedNode.asJsonText.underlying)
    val expectedEnvelopeJson = expectedEnvelope.asJsonText.underlying

    val expectedMessageAsJson: MessageAsJson = MessageAsJson(new DeliveryAttemptId(-43),testTimeoutMilliseconds,3,expectedEnvelopeJson)
    val theMessageJson = expectedMessageAsJson.asJsonText

    val gotMessageAsJson = MessageAsJson.tryRead(theMessageJson).get
    assert(expectedMessageAsJson == gotMessageAsJson)

    val gotEnvelopeAsJson = new JsonText(gotMessageAsJson.contents)
    val gotEnvelope = Envelope.tryRead(gotEnvelopeAsJson).get

    assert(expectedEnvelope == gotEnvelope)

    val gotNode = Node.tryRead(new JsonText(gotEnvelope.contents)).get

    assert(expectedNode == gotNode)
  }
}


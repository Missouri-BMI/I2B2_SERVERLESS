package net.shrine.protocol.version

import io.circe.ParsingFailure
import net.shrine.protocol.version.v2.{RunQueryAtHub, RunQueryAtHubTest}
import org.junit.Test
import org.scalatestplus.junit.AssertionsForJUnit

import scala.util.Try

final class EnvelopeTest extends AssertionsForJUnit {

  @Test
  def testEnvelopeJsonRoundTrip():Unit = {
    val exampleContents = ExampleContents("test contents")

    val testContentsJson = exampleContents.asJsonText

    val expectedEnvelope: Envelope = Envelope(ExampleContents.getClass.getSimpleName,0L, testContentsJson.underlying)
    val envelopeJson: JsonText = expectedEnvelope.asJsonText

    val envelope = Envelope.tryRead(envelopeJson).get

    assert(expectedEnvelope == envelope)

    val contentsTry: Try[ExampleContents] = ExampleContents.tryRead(new JsonText(envelope.contents))
    val contents = contentsTry.get

    assert(exampleContents.string == contents.string)
  }

  @Test
  def testEnvelopeFailsWithJunk():Unit = {

    try {
      val envelope = Envelope.tryRead(new JsonText("Not even json, much less a valid envelope")).get
      assert(false, "Should have thrown an exception")

    } catch {
      case pf: ParsingFailure => //expected
    }
  }

  @Test
  def testExpectedProtocolVersion():Unit = {
    val exampleContents = ExampleContents("test contents")

    val testContentsJson = exampleContents.asJsonText

    val expectedEnvelope: Envelope = Envelope(ExampleContents.getClass.getSimpleName,0L, testContentsJson.underlying)

    assert(expectedEnvelope.protocolVersion == ProtocolVersion.current)
    assert(expectedEnvelope.protocolVersion == v2.versionId)
  }

  @Test
  def testEnvelopeRunQueryRoundTrip(): Unit = {
    val exampleContents: RunQueryAtHub = new RunQueryAtHubTest().expectedRunQueryAtHub

    val testContentsJson = exampleContents.asJsonText

    val expectedEnvelope: Envelope = Envelope(RunQueryAtHub.getClass.getSimpleName, 0L, testContentsJson.underlying)
    val envelopeJson: JsonText = expectedEnvelope.asJsonText

    val envelope = Envelope.tryRead(envelopeJson).get

    assert(expectedEnvelope == envelope)

    val contentsTry: Try[RunQueryAtHub] = RunQueryAtHub.tryRead(new JsonText(envelope.contents))
    val contents: RunQueryAtHub = contentsTry.get

    assert(exampleContents == contents)
  }
}

case class ExampleContents(string:String) {
  def asJsonText:JsonText = {
    import io.circe.generic.extras.Configuration
    import io.circe.generic.extras.auto.exportEncoder
    import io.circe.syntax.EncoderOps

    implicit val genDevConfig: Configuration = ExampleContents.genDevConfig

    new JsonText(this.asJson.noSpaces)
  }
}

object ExampleContents {
  import io.circe.generic.extras.Configuration
  val genDevConfig: Configuration =
    Configuration.default.withDiscriminator("encodedClass")

  def tryRead(jsonText:JsonText):Try[ExampleContents] = Try {
    import io.circe.parser.decode
    import io.circe.generic.extras.auto.exportDecoder
    import io.circe.generic.extras.Configuration

    implicit val genDevConfig: Configuration = ExampleContents.genDevConfig

    decode[ExampleContents](jsonText.underlying) match {
      case Right(ec) => ec
      case Left(x) => throw x //throw errors to pick up in the Try
    }
  }
}

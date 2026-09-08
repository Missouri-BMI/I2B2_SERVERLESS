package net.shrine.protocol.version.v2

import io.circe.{Decoder, Encoder}
import net.shrine.protocol.version.{EnvelopeContents, EnvelopeContentsCompanion, JsonText, ProtocolVersion, ValueClass}

import scala.util.Try

/**
 * A command to update system version data from a downstream node
 *
 * @since 4.2
 * @author dwalend
 */

case class UpdateNodeSystemSpec(
                                 nodeSystemSpec: NodeSystemSpec,
                                 protocolVersion: ProtocolVersion = ProtocolVersion.current,
                                ) extends EnvelopeContents {


   def asJsonText: JsonText = {
     //todo this can maybe be fully generic for all the common currency data, in Versioned
     //todo but the generics didn't work out of the box. Ask on the circe gitter channel SHRINE-2848
     import io.circe.generic.extras.Configuration
     import io.circe.generic.extras.auto.exportEncoder
     import io.circe.syntax.EncoderOps

     //noinspection ScalaUnusedSymbol
     implicit val genDevConfig: Configuration = Versioned.genDevConfig
     import io.circe.generic.extras.encoding.UnwrappedEncoder
     //noinspection ScalaUnusedSymbol
     implicit def valueClassEncoder[A <: ValueClass[_]](implicit encodeA: UnwrappedEncoder[A]): Encoder[A] = encodeA

     new JsonText(this.asJson.noSpaces)
   }
 }
object UpdateNodeSystemSpec extends EnvelopeContentsCompanion {

  val envelopeType: String = classOf[UpdateNodeSystemSpec].getSimpleName

  //todo if this can maybe be fully generic for all the common currency data, maybe it can live in Versioned SHRINE-2848
  def tryRead(jsonText:JsonText):Try[UpdateNodeSystemSpec] = Try {
    import io.circe.generic.extras.Configuration
    import io.circe.generic.extras.auto.exportDecoder
    import io.circe.parser.decode

    //noinspection ScalaUnusedSymbol
    implicit val genDevConfig: Configuration = Versioned.genDevConfig
    import io.circe.generic.extras.decoding.UnwrappedDecoder
    //noinspection ScalaUnusedSymbol
    implicit def valueClassDecoder[A <: ValueClass[_]](implicit decodeA: UnwrappedDecoder[A]): Decoder[A] = decodeA

    decode[UpdateNodeSystemSpec](jsonText.underlying) match {
      case Right(envelopeContents) => envelopeContents
      case Left(x) =>
        x.fillInStackTrace()
        throw x //throw errors for now. Eventually do something clever, maybe with circe optics
    }
  }
}
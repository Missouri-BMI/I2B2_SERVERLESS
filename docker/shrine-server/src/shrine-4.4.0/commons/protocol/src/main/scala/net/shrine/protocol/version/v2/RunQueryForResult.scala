package net.shrine.protocol.version.v2

import io.circe.{Decoder, Encoder}
import net.shrine.protocol.version.{EnvelopeContents, EnvelopeContentsCompanion, JsonText, ProtocolVersion, ValueClass}

import scala.util.Try

//resultProgress's state really should be "ToAdapter" todo - assert ? class to capture that constraint?
//researcher's ID had better equal the query's researcher id
//resultProgress's query id had better equal the query's id

case class RunQueryForResult(
                              query: Query,
                              researcher: Researcher,
                              node:Node,
                              resultProgress: ResultProgress,
                              protocolVersion: ProtocolVersion = ProtocolVersion.current
                            ) extends EnvelopeContents {

  def asJsonText:JsonText = {
    //todo this can maybe be fully generic for all the common currency data, in Versioned
    //todo but the generics didn't work out of the box. Ask on the circe gitter channel SHRINE-2848
    import io.circe.generic.extras.Configuration
    import io.circe.generic.extras.auto.exportEncoder
    import io.circe.syntax.EncoderOps

    implicit val genDevConfig: Configuration = Versioned.genDevConfig
    import io.circe.generic.extras.encoding.UnwrappedEncoder
    implicit def valueClassEncoder[A <: ValueClass[_]](implicit encodeA: UnwrappedEncoder[A]): Encoder[A] = encodeA

    new JsonText(this.asJson.noSpaces)
  }
}

object RunQueryForResult extends EnvelopeContentsCompanion {

  val envelopeType: String = classOf[RunQueryForResult].getSimpleName

  //todo if this can maybe be fully generic for all the common currency data, maybe it can live in Versioned SHRINE-2848
  def tryRead(jsonText:JsonText):Try[RunQueryForResult] = Try {
    import io.circe.generic.extras.Configuration
    import io.circe.generic.extras.auto.exportDecoder
    import io.circe.parser.decode

    implicit val genDevConfig: Configuration = Versioned.genDevConfig
    import io.circe.generic.extras.decoding.UnwrappedDecoder
    implicit def valueClassDecoder[A <: ValueClass[_]](implicit decodeA: UnwrappedDecoder[A]): Decoder[A] = decodeA

    decode[RunQueryForResult](jsonText.underlying) match {
      case Right(runQuery) => runQuery
      case Left(x) =>
        x.fillInStackTrace()
        throw x //throw errors for now. Eventually do something clever, maybe with circe optics
    }
  }
}

package net.shrine.protocol.version.v1

import io.circe.{Decoder, Encoder}
import net.shrine.protocol.version.{EnvelopeContents, EnvelopeContentsCompanion, JsonText, ProtocolVersion, QueryId, ValueClass}

import scala.util.Try

/**
  * A command to update something about a pending Query at the Adapter (and Hub)
  *
  * @since 1.26
  * @author dwalend
  */
sealed abstract class UpdateQueryAtAdapter(
                                    val queryId: QueryId,
                                    val protocolVersion: ProtocolVersion = ProtocolVersion.current
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

  def updatedQuery(query:Query):Query
}

object UpdateQueryAtAdapter extends EnvelopeContentsCompanion {

  val envelopeType: String = classOf[UpdateQueryAtAdapter].getSimpleName

  //todo if this can maybe be fully generic for all the common currency data, maybe it can live in Versioned SHRINE-2848
  def tryRead(jsonText:JsonText):Try[UpdateQueryAtAdapter] = Try {
    import io.circe.generic.extras.Configuration
    import io.circe.generic.extras.auto.exportDecoder
    import io.circe.parser.decode

    implicit val genDevConfig: Configuration = Versioned.genDevConfig
    import io.circe.generic.extras.decoding.UnwrappedDecoder
    implicit def valueClassDecoder[A <: ValueClass[_]](implicit decodeA: UnwrappedDecoder[A]): Decoder[A] = decodeA

    decode[UpdateQueryAtAdapter](jsonText.underlying) match {
      case Right(envelopeContents) => envelopeContents
      case Left(x) =>
        x.fillInStackTrace()
        throw x //throw errors for now. Eventually do something clever, maybe with circe optics
    }
  }
}

case class UpdateQueryAtAdapterWithName(
                                 override val queryId:QueryId,
                                 queryName:String
                                 ) extends UpdateQueryAtAdapter(queryId) {

  override def updatedQuery(query: Query): Query = query.withName(queryName)
}

case class UpdateQueryAtAdapterWithFlagging(
                                 override val queryId:QueryId,
                                 flagged: Boolean,
                                 flaggedMessage: Option[String] = None
                               ) extends UpdateQueryAtAdapter(queryId) {

  override def updatedQuery(query: Query): Query = query.withFlagging(flagged,flaggedMessage)
}

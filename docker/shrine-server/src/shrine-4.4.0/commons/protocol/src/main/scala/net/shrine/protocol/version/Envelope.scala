package net.shrine.protocol.version

import io.circe.{Decoder, Encoder}
import net.shrine.log.Log
import net.shrine.util.Versions

import scala.util.Try

/**
  * A json-friendly container for unpacking different messages of known types based on metadata.
  *
  * @author david 
  * @since 9/7/17
  */

//if you ever need to add fields to this case class they must be Options with default values of None to support serializing to and from JSON
case class Envelope(
                     contentsType:String,
                     contentsSubject:Long, 
                     contents:String,
                     protocolVersion:ProtocolVersion = ProtocolVersion.current,
                     shrineVersion: Option[ShrineVersion] = Some(ShrineVersion.current),
                   ) {

  def asJsonText:JsonText = {
    import io.circe.generic.extras.Configuration
    import io.circe.generic.extras.auto.exportEncoder
    import io.circe.syntax.EncoderOps

    implicit val genDevConfig: Configuration = Envelope.genDevConfig
    import io.circe.generic.extras.encoding.UnwrappedEncoder
    implicit def valueClassEncoder[A <: ValueClass[_]](implicit encodeA: UnwrappedEncoder[A]): Encoder[A] = encodeA

    new JsonText(this.asJson.noSpaces)
  }

  def briefString = s"Envelope of $contentsType from $shrineVersion for $contentsSubject $protocolVersion"

  def equalsExceptShrineVersion(envelope:Envelope):Boolean = {
    this == envelope.copy(shrineVersion = this.shrineVersion)
  }
}

case class VersionMismatchException(badVersion:ProtocolVersion) extends Exception(s"Envelope protocol version $badVersion is not compatible with Shrine ${Versions.version}.  ")

object Envelope {

  import io.circe.generic.extras.Configuration
  val genDevConfig: Configuration =
    Configuration.default.withDiscriminator("encodedClass")

  def tryRead(jsonText:JsonText):Try[Envelope] = Try {
    import io.circe.parser.decode
    import io.circe.generic.extras.auto.exportDecoder
    import io.circe.generic.extras.Configuration

    implicit val genDevConfig: Configuration = Envelope.genDevConfig
    import io.circe.generic.extras.decoding.UnwrappedDecoder
    implicit def valueClassDecoder[A <: ValueClass[_]](implicit decodeA: UnwrappedDecoder[A]): Decoder[A] = decodeA

    decode[Envelope](jsonText.underlying) match {
      case Right(envelope) => envelope
      case Left(x) =>
        x.fillInStackTrace()
        Log.warn(s"Unable to parse Envelope json ${jsonText.underlying} ",x)
        throw x //throw errors to pick up in the Try
    }
  }
}
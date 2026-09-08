package net.shrine.protocol.version.v2

import io.circe.{Decoder, Encoder}
import net.shrine.config.ConfigSource
import net.shrine.protocol.version.{JsonText, NodeId, NodeKey, ShrineVersion, ValueClass}

import scala.util.Try

/**
 * Versions of systems at a node in a shrine network.
 *
 * @since 4.2
 * @author dwalend
 */
case class NodeSystemSpec(
                 id:NodeId ,
                 key:NodeKey,
                 versionInfo: VersionInfo = VersionInfo.create,
                 shrineJdk: String,
                 shrineOperatingSystem: String,
                 shrineDatabaseBrand: String,
               ) extends Versioned[NodeId] {
  val shrineVersion: ShrineVersion = versionInfo.shrineVersion

  def equalsExceptMetadata(other:NodeSystemSpec):Boolean = {
    val ignoreSomeVersionInfo = other.versionInfo.copy(
      createDate = this.versionInfo.createDate,
      itemVersion = this.versionInfo.itemVersion,
      changeDate = this.versionInfo.changeDate,
    )
    val withIgnoredVersionInfo = other.copy(
      versionInfo = ignoreSomeVersionInfo
    )

    this == withIgnoredVersionInfo
  }

  def asJsonText:JsonText = {
    //todo this can maybe be fully generic for all the common currency data, in Versioned
    //todo but the generics didn't work out of the box. Ask on the circe gitter channel SHRINE-2848
    //todo see https://tpolecat.github.io/2015/04/29/f-bounds.html for a hint
    import io.circe.generic.extras.Configuration
    import io.circe.generic.extras.auto.exportEncoder
    import io.circe.syntax.EncoderOps

    implicit val genDevConfig: Configuration = Versioned.genDevConfig
    import io.circe.generic.extras.encoding.UnwrappedEncoder
    implicit def valueClassEncoder[A <: ValueClass[_]](implicit encodeA: UnwrappedEncoder[A]): Encoder[A] = encodeA

    new JsonText(this.asJson.noSpaces)
  }
}

object NodeSystemSpec extends VersionedCompanion {

  val envelopeType: String = classOf[Node].getSimpleName


  def create(
              node:Node,
            ): NodeSystemSpec = NodeSystemSpec(
    id = node.id,
    key = node.key,
    shrineJdk = Runtime.version().toString,
    shrineOperatingSystem = System.getProperty("os.name")+" "+System.getProperty("os.version"),
    shrineDatabaseBrand = ConfigSource.config.getString("shrine.shrineDatabaseType")
  )

  //todo if this can maybe be fully generic for all the common currency data, maybe it can live in Versioned SHRINE-2848
  def tryRead(jsonText: JsonText): Try[NodeSystemSpec] = Try {
    import io.circe.generic.extras.Configuration
    import io.circe.generic.extras.auto.exportDecoder
    import io.circe.parser.decode

    implicit val genDevConfig: Configuration = Versioned.genDevConfig
    import io.circe.generic.extras.decoding.UnwrappedDecoder
    implicit def valueClassDecoder[A <: ValueClass[_]](implicit decodeA: UnwrappedDecoder[A]): Decoder[A] = decodeA

    decode[NodeSystemSpec](jsonText.underlying) match {
      case Right(node) => node
      case Left(x) => throw x //throw errors for now. Eventually do something clever, maybe with circe optics
    }
  }
}

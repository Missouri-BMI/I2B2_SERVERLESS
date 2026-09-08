package net.shrine.protocol.version.v1

import net.shrine.protocol.version
import net.shrine.protocol.version.{EnvelopeContents, EnvelopeContentsCompanion, Id, JsonText}

/**
 * A versioned entity stored and transmitted as json
 *
 * @since 1.26
 * @author dwalend
 */
trait Versioned[ItemId <: Id] extends EnvelopeContents {

  def id:ItemId
  def versionInfo:VersionInfo

  override def protocolVersion: version.ProtocolVersion = versionInfo.protocolVersion
}

object Versioned {
  import io.circe.generic.extras.Configuration
  val genDevConfig: Configuration =
    Configuration.default.withDiscriminator("encodedClass")
}

trait VersionedCompanion extends EnvelopeContentsCompanion {

}
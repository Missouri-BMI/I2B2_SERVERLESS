package net.shrine.protocol.version.v2

import net.shrine.protocol.version.{EnvelopeContents, EnvelopeContentsCompanion, Id, ProtocolVersion}

/**
  * A versioned entity stored and transmitted as json
  *
  * @since 1.26
  * @author dwalend
  */
trait Versioned[ItemId <: Id] extends EnvelopeContents {

  def id:ItemId
  def versionInfo:VersionInfo

  override def protocolVersion: ProtocolVersion = versionInfo.protocolVersion
}

object Versioned {
  import io.circe.generic.extras.Configuration
  val genDevConfig: Configuration =
    Configuration.default.withDiscriminator("encodedClass")
}

trait VersionedCompanion extends EnvelopeContentsCompanion {

}
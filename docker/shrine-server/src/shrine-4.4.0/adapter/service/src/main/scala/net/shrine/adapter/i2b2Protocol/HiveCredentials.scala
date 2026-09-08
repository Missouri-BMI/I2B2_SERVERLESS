package net.shrine.adapter.i2b2Protocol

import com.typesafe.config.Config
import net.shrine.config.{ConfigExtensions, ConfigSource}
import net.shrine.crypto.SealerRevealer
import net.shrine.protocol.i2b2.{AuthenticationInfo, Credential}

import javax.crypto.SealedObject

/**
 * @author Bill Simons
 * @since 3/12/12
 * @see http://cbmi.med.harvard.edu
 * @see http://chip.org
 *       <p/>
 *       NOTICE: This software comes with NO guarantees whatsoever and is
 *       licensed as Lgpl Open Source
 * @see http://www.gnu.org/licenses/lgpl.html
 */
final case class HiveCredentials(domain: String, username: String, sealedPassword: SealedObject, projectId: String) {
  def toAuthenticationInfo: AuthenticationInfo = AuthenticationInfo(domain, username, Credential(password, isToken = false))

  def password = SealerRevealer.reveal(sealedPassword)
}

object HiveCredentials {

  sealed case class ProjectIdType(key:String)

  val CRC: ProjectIdType = ProjectIdType("crcProjectId")
  val ONT: ProjectIdType = ProjectIdType("ontProjectId")

  def apply(config:Config, projectIdType: ProjectIdType): HiveCredentials = {
    val domain: String = config.getOption("domain", _.getString).getOrElse(ConfigSource.config.getString("shrine.i2b2Domain"))
    val username: String = config.getString("username")
    val sealedPassword: SealedObject = SealerRevealer.seal(config.getString("password"))

    val projectId: String = config.getOption(projectIdType.key, _.getString).getOrElse(ConfigSource.config.getString("shrine.i2b2ShrineProjectName"))

    new HiveCredentials(domain, username, sealedPassword, projectId)
  }
}
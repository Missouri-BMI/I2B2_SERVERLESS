package net.shrine.crypto

import java.util
import java.util.Map.Entry
import com.typesafe.config.{Config, ConfigValue, ConfigValueType}
import net.shrine.config.ConfigExtensions
import net.shrine.log.Loggable

import javax.crypto.SealedObject
import scala.jdk.CollectionConverters.{ListHasAsScala, SetHasAsScala}

/**
 * @author clint
 * @since Nov 22, 2013
 */

final case class KeyStoreDescriptor(
                                     file: String,
                                     sealedPassword: SealedObject,
                                     privateKeyAlias: Option[String],
                                     caCertAliases: Seq[String],
                                     trustModel: SingleHubModel,
                                     remoteSiteDescriptors: Seq[RemoteSiteDescriptor],
                                     keyStoreFormat: KeyStoreFormat = KeyStoreFormat.Default
                                   )
{
  def password:String = SealerRevealer.reveal(sealedPassword)
}

case class SingleHubModel(isCa: Boolean) {
  val description:String = "Central Certificate Authority"
}

case class RemoteSiteDescriptor(siteAlias: String, keyStoreAlias: Option[String])

object KeyStoreDescriptor extends Loggable {
  object Keys {
    val file            = "file"
    val password        = "password"
    val privateKeyAlias = "privateKeyAlias"
    val keyStoreFormat  = "keyStoreType"
    val caCertAliases   = "caCertAliases"
    val isHub           = "create"
    val downStreamNodes = "downstreamNodes"
    val aliasMap        = "aliasMap"
  }

  def apply(keyStoreConfig: Config, hubConfig: Config, qepConfig: Config): KeyStoreDescriptor = {
    import Keys._

    val tm = {

      if (hubConfig.hasPath(isHub))
        SingleHubModel(hubConfig.getBoolean(isHub))
      else {
        warn(s"Did not specify whether this is the hub or a downStreamNode, assuming it ${if (hubConfig.isEmpty) "isn't" else "is"} because the hub config is ${if (hubConfig.isEmpty) "empty" else "defined"}")
        SingleHubModel(!hubConfig.isEmpty)
      }
    }

    def getRemoteSites: Seq[RemoteSiteDescriptor] = {
      tm match {
        case SingleHubModel(true) => parseRemoteSitesForHub
        case SingleHubModel(false) => parseRemoteSiteFromQep
      }
    }

    def getCaCertAliases: Seq[String] = {

      def isString(cv: ConfigValue) = cv.valueType == ConfigValueType.STRING

      keyStoreConfig.getOption(caCertAliases,_.getList).fold(Seq.empty[ConfigValue])(_.asScala.toList).collect{ case cv if isString(cv) => cvToString(cv) }
    }

    def parseRemoteSitesForHub: Seq[RemoteSiteDescriptor] = {
      val downStreamAliases: util.Set[Entry[String, ConfigValue]] = hubConfig.getConfigOrEmpty(downStreamNodes).entrySet
      downStreamAliases.asScala.map(entry => {
        RemoteSiteDescriptor(entry.getKey, None)}).toList
    }

    def parseRemoteSiteFromQep: Seq[RemoteSiteDescriptor] = {
      val aliases = getCaCertAliases
      assert(aliases.nonEmpty, "There has to be at least one caCertAlias")

      RemoteSiteDescriptor("Hub", Some(aliases.head)) +: Nil
    }

    def getKeyStoreFormat: KeyStoreFormat = {
      val typeOption = keyStoreConfig.getOption(keyStoreFormat,_.getString)

      typeOption.flatMap(KeyStoreFormat.valueOf).getOrElse {
        info(s"Unknown keystore type '${typeOption.getOrElse("")}', allowed types are ${KeyStoreFormat.JKS.name} and ${KeyStoreFormat.PKCS12.name}")

        KeyStoreFormat.Default
      }
    }

    def cvToString(cv: ConfigValue): String = {
      cv.unwrapped.toString
    }

    new KeyStoreDescriptor(
      keyStoreConfig.getString(file),
      SealerRevealer.seal(keyStoreConfig.getString(password)),
      keyStoreConfig.getOption(privateKeyAlias, _.getString),
      getCaCertAliases,
      tm,
      getRemoteSites,
      getKeyStoreFormat
    )
  }
}
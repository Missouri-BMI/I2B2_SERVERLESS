package net.shrine.config

import com.typesafe.config.{Config, ConfigFactory}
import net.shrine.log.Log

import java.io.File
import scala.jdk.javaapi.CollectionConverters.asScala
import scala.util.control.NonFatal

/**
  * @author ty
  * @since  7/22/16
  */
object ConfigSource {

  //config names in order of precedence. qa takes top preference, reference.conf takes bottom
  val overrideConfigName: String = "override"
  val passwordConfigName: String = "password"
  val shrineConfigName: String = "shrine"
  val referenceConfigName: String = "reference"
  val configNames = List(overrideConfigName, passwordConfigName, shrineConfigName, referenceConfigName)

  lazy val atomicConfig: AtomicConfigSource = new AtomicConfigSource ({
    //get each config level without resolving it

    val defaultOverrides = ConfigFactory.defaultOverrides()

    val qaConfig = ConfigFactory.parseResourcesAnySyntax(overrideConfigName)

    def getShrineConfigKeys(config: Config): Set[String] = {
      val baseKey = "shrine"
      if(config.hasPath(baseKey)) asScala(config.getConfig(baseKey).entrySet()).map(_.getKey).toSet
      else Set.empty
    }

    val passwordKeys = Set(
      "hiveCredentials.password",
      "keystore.password",
      "aws.accessKeyId",
      "aws.secretAccessKey",
      "kafka.ssl.truststore.password",
      "kafka.sasl.jaas.password"
    )

    def isPassword(shrineConfigKey: String): Boolean = {
      passwordKeys.contains(shrineConfigKey)
    }

    val passwordConfig = ConfigFactory.parseResourcesAnySyntax(passwordConfigName)
    val nonPasswordConfigInPasswordConfig = getShrineConfigKeys(passwordConfig).filterNot(config => isPassword(config))
    if (nonPasswordConfigInPasswordConfig.nonEmpty) {
      // Temporary handling, TODO early shrine shutdown
      try {
        throw new IllegalArgumentException(s"$passwordConfigName.conf should not contain anything other than " +
          s"expected passwords: ${nonPasswordConfigInPasswordConfig.mkString(", ")}")
      } catch {
        case e: Exception => Log.error("Password config exception caught: " + e);
      }
    }

    //can set the shrine.conf file's path via -Dshrine.conf.file=/path/to/shrine.conf
    val shrineConfig = defaultOverrides.getOption("shrine.conf.file",_.getString)
      .map(fileName => ConfigFactory.parseFile(new File(fileName)))
      .getOrElse(ConfigFactory.parseResourcesAnySyntax(shrineConfigName))

    val passwordConfigInShrineConfig = getShrineConfigKeys(shrineConfig).filter(config => isPassword(config))
    if (passwordConfigInShrineConfig.nonEmpty) {
      // Temporary handling, TODO early shrine shutdown
      try {
        throw new IllegalArgumentException(s"$shrineConfigName.conf should not contain " +
          s"password: ${passwordConfigInShrineConfig.mkString(", ")}")
      } catch {
        case e: Exception => Log.error("Password config exception caught: " + e);
      }
    }

    val referenceConfig = ConfigFactory.parseResourcesAnySyntax(referenceConfigName)

    val finalConfig = defaultOverrides.
      withFallback(qaConfig).
      withFallback(passwordConfig).
      withFallback(shrineConfig).
      withFallback(referenceConfig).
      resolve()
    finalConfig
  })

  def config: Config = {
    try atomicConfig.config
    catch {
      case NonFatal(x) =>
        Log.error(s"Could not load configuration from ${configNames.mkString(".conf, ")}.conf due to ", x)
        throw x
    }
  }

  def configForBlock[T](key: String, value: AnyRef, origin: String)(block: => T): T = {
    atomicConfig.configForBlock(key, value, origin)(block)
  }

  def configForBlock[T](config: Config, origin: String)(block: => T): T = {
    atomicConfig.configForBlock(config, origin)(block)
  }
}

case class ConfigError(throwable: Throwable, path: String) extends Error {
  override def getMessage:String = s"Malformed config file, could not retrieve path '$path'"
}
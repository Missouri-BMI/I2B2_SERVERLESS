package net.shrine.http4s.client.legacy

import java.net.URL

import com.typesafe.config.Config
import net.shrine.config.ConfigSource

import scala.concurrent.duration.FiniteDuration

/**
 * @author clint
 * @since Dec 5, 2013
 */
import net.shrine.config.ConfigExtensions

final case class EndpointConfig(url: URL,
                                timeout: FiniteDuration,
                                concurrentLimit:Int,
                                retryDelay:FiniteDuration,
                                clientConfig:Config
                               )

object EndpointConfig {

  def apply(config: Config): EndpointConfig = {
    val url:URL = config.buildURI("i2b2BaseUrl", "urlPath", new URL(_), Option(ConfigSource.config.getConfig("shrine")))
    val timeout = config.getFiniteDuration("timeout")
    val concurrentLimit = config.getInt("concurrentLimit")
    val retryDelay = config.getFiniteDuration("retryDelay")

    new EndpointConfig(url, timeout,concurrentLimit, retryDelay, config)
  }
}
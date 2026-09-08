package net.shrine

import com.typesafe.config.{Config, ConfigException, ConfigFactory}
import net.shrine.config.ConfigError

import scala.concurrent.duration.{Duration, FiniteDuration}

package object config {

  /**
   * @author dwalend
   * @since July 17, 2015
   *
   * Helper methods for parsing com.typesafe.config.Config objects
   */
  implicit class ConfigExtensions(self: Config) {

    def get[T](key:String,construct:String => T):T = construct(self.getString(key))

    def getConfigured[T](key: String, constructor: Config => T): T = constructor(self.getConfig(key))

    def getOption[T](key: String, extract: Config => (String => T)): Option[T] = {
      if (self.hasPath(key)) Option(extract(self)(key))
      else None
    }

    def getOptionConfigured[T](key: String, constructor: Config => T): Option[T] = {
      getOption(key, _.getConfig).map(constructor)
    }

    def getOptionConfiguredIf[T](key:String,constructor: Config => T,createFlag:String = "create"):Option[T] = {
      if(self.getBoolean(s"$key.$createFlag")) self.getOptionConfigured(key,constructor)
      else None
    }

    def getConfigOrEmpty(key:String): Config = {
      if (self.hasPath(key))
        self.getConfig(key)
      else
        ConfigFactory.empty()
    }

    def getFiniteDuration(key:String):FiniteDuration = {
      self.get(key,Duration(_)) match {
        case f:FiniteDuration => f
        case x => throw new IllegalArgumentException(s"$x must be a FiniteDuration, not a ${x.getClass.getName}")
      }
    }

    def objectForName[T](objectName: String): T = {

      import scala.reflect.runtime.universe
      val runtimeMirror = universe.runtimeMirror(getClass.getClassLoader)
      val module = runtimeMirror.staticModule(objectName)

      val reflectedObj = runtimeMirror.reflectModule(module)
      val obj = reflectedObj.instance

      obj.asInstanceOf[T]
    }

    def getObjectByClassname[T](path: String):T = {
      try {
        objectForName(self.getString(path))
      } catch {
        case cx:ConfigException => throw ConfigError(cx, path)
      }
    }

    def buildURI[T](baseUriKey:String, pathUriKey:String, construct:String => T, baseConfigOption: Option[Config] = None): T = {
      val baseConfig = baseConfigOption.getOrElse(self)
      if(!baseConfig.hasPath(baseUriKey))
        throw new IllegalArgumentException(s"baseUriKey $baseUriKey does not exist")
      else if(!self.hasPath(pathUriKey))
        throw new IllegalArgumentException(s"pathUriKey $pathUriKey does not exist")
      else construct(s"${baseConfig.getString(baseUriKey)}${self.getString(pathUriKey)}")
    }
  }
}
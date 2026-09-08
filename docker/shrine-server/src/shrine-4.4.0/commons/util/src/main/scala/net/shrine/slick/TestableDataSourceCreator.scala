package net.shrine.slick

import java.io.PrintWriter
import java.sql.{Connection, DriverManager}
import java.util.logging.Logger
import javax.naming.InitialContext
import javax.sql.DataSource
import com.typesafe.config.Config
import net.shrine.config.{ConfigExtensions, ConfigSource}
import net.shrine.crypto.SealerRevealer
import net.shrine.log.{Log, Loggable}
import slick.jdbc.JdbcProfile

import javax.crypto.SealedObject

/**
  * @author david 
  * @since 1/26/16
  */
object TestableDataSourceCreator extends Loggable {

  def dataSource(config:Config):DataSource = {

    val dataSourceFrom = config.getString("dataSourceFrom")
    if(dataSourceFrom == "JNDI") {
      val jndiDataSourceName = config.getString("jndiDataSourceName")
      val initialContext:InitialContext = new InitialContext()
      val dataSource = initialContext.lookup(jndiDataSourceName).asInstanceOf[DataSource]

      Log.info(s"data source created for JNDI: $dataSource")

      dataSource
    }
    else if (dataSourceFrom == "dataSourceConfig") {

      val dataSourceConfigConfig = config.getConfig("dataSourceConfig")
      val driverClassName = dataSourceConfigConfig.getString("driverClassName")
      val url = dataSourceConfigConfig.getString("url")

      Log.info(s" creating datasource from dataSourceConfig; URL = $url")

      case class Credentials(username: String,sealedPassword:SealedObject) {
        def password: String = SealerRevealer.reveal(sealedPassword)
      }

      def configToCredentials(config:Config) = Credentials(config.getString("username"),SealerRevealer.seal(config.getString("password")))

      val credentials: Option[Credentials] = if (dataSourceConfigConfig.hasPath("credentials"))
        Some(configToCredentials(dataSourceConfigConfig.getConfig("credentials")))
        else None

      //Creating an instance of the driver to register it.
      Class.forName(driverClassName).getDeclaredConstructor().newInstance()

      //noinspection NotImplementedCode
      object dataSourceConfig extends DataSource {
        override def getConnection: Connection = {
          credentials.fold(DriverManager.getConnection(url))(credentials =>
            DriverManager.getConnection(url,credentials.username,credentials.password))
        }

        override def getConnection(username: String, password: String): Connection = {
          DriverManager.getConnection(url, username, password)
        }

        //unused methods
        override def unwrap[T](iface: Class[T]): T = ???
        override def isWrapperFor(iface: Class[_]): Boolean = ???
        override def setLogWriter(out: PrintWriter): Unit = ???
        override def getLoginTimeout: Int = ???
        override def setLoginTimeout(seconds: Int): Unit = ???
        override def getParentLogger: Logger = ???
        override def getLogWriter: PrintWriter = ???
      }

      dataSourceConfig
    }
    else throw new IllegalArgumentException(s"dataSourceFrom config value must be either JNDI or dataSourceConfig, not $dataSourceFrom")
  }

  def slickDriver(config:Config):JdbcProfile = {
    info(s"slickDriver config from ${config.origin} $config")

    val name = "slickProfileClassName"

    if(config.hasPath(name)) config.getObjectByClassname(name)
    else {
      //figure out the right value from shrineDatabaseType
      val jdbcProfileNames = Map(
        "mysql" -> slick.jdbc.MySQLProfile,
        "oracle" -> slick.jdbc.OracleProfile,
        "sqlserver" -> slick.jdbc.SQLServerProfile,
        "h2" -> slick.jdbc.H2Profile
      )

      val key = ConfigSource.config.getString("shrine.shrineDatabaseType")
      jdbcProfileNames(key)
    }
  }

}

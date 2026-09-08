package net.shrine.email

import java.util.Properties

import scala.collection.JavaConverters.asScalaSet
import com.typesafe.config.Config
import courier.Mailer
import javax.mail.{Authenticator, PasswordAuthentication, Session}
import net.shrine.config.ConfigExtensions

import scala.jdk.CollectionConverters.SetHasAsScala

/**
  * Creates a courier Mailer via shrine.conf, by pulling out all possible properties from https://www.tutorialspoint.com/javamail_api/javamail_api_smtp_servers.htm from an email section of shrine.conf
  *
  * @author david 
  * @since 1.22
  */
object ConfiguredMailer {

  //for testing
  private[email] def createSessionFromConfig(config:Config):Session = {
    //convert the config to a java.util.Properties

    val properties = new Properties()
    val map: Map[String, Object] = config.getConfig("javaxmail").entrySet().asScala.map({ entry =>
      entry.getKey -> entry.getValue.unwrapped()
    }).toMap
    val stringMap:Map[String,String] = map.filter{pair => pair._2.isInstanceOf[String]}.asInstanceOf[Map[String,String]]

    stringMap.foreach{pair =>
      properties.setProperty(pair._1,pair._2)
    }

    def authenticatorFromConfig(config: Config): Authenticator = {
      new javax.mail.Authenticator() {
        override def getPasswordAuthentication = new PasswordAuthentication(config.getString("username"), config.getString("password"))
      }
    }

    val configAuthenticator: Option[Authenticator] = config.getOptionConfigured("authenticator",authenticatorFromConfig)

    configAuthenticator.fold(Session.getDefaultInstance(properties))(
                    authenticator => Session.getDefaultInstance(properties,authenticator))
  }

  def createMailerFromConfig(config:Config):Mailer = {
    val session = createSessionFromConfig(config)

    Mailer(session)
  }
}

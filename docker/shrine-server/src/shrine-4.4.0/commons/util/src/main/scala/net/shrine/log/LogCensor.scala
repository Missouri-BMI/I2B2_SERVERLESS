package net.shrine.log

import java.util

import ch.qos.logback.classic.PatternLayout
import ch.qos.logback.classic.pattern.ThrowableHandlingConverter
import ch.qos.logback.classic.spi.{ILoggingEvent, IThrowableProxy}

import scala.jdk.CollectionConverters.{MapHasAsJava, MapHasAsScala}
import scala.util.matching.Regex

/**
 * Censors out things inappropriate for logs, such as i2b2's password element.
 *
 * @author david 
 * @since 8/4/15
 */
object LogCensor {

  /**
   * Matches for things like
   * <password is_token="false" token_ms_timeout="1800000">kapow</password>
   */
  private val i2b2PasswordRegex: Regex = """(<password.*>).*(</password>)""".r

  /**
   * Matches for things like
   * <set_size>239</set_size>
   */
  private val i2b2SetSizeRegex: Regex = """(<set_size.*>)\d*(</set_size>)""".r

  /**
   * Matches Base64 strings.
   * From http://stackoverflow.com/questions/475074/regex-to-parse-or-validate-base64-data .
   */
  private val base64String = """(?:[A-Za-z0-9+/]{4})*(?:[A-Za-z0-9+/]{2}==|[A-Za-z0-9+/]{3}=)?"""

  /**
   * Matches for things like
   *
   * Authorization: Basic cWVwOnRydXN0bWU=, and Authorization: Basic cWVwOnRydXN0bWU=)
   */
  //noinspection RegExpUnexpectedAnchor
  private val basicAuthRegex: Regex = s"""(Authorization: Basic )$base64String([,)].*)""".r

  /**
    * Matches for json-ish strings from Typsesafe Config like
    *
    * "password" : "demouser",
    *
    * and
    *
    * "qepPassword" : "demouser",
    */
  private val typesafeConfigRegex: Regex = """((?i)password" : ").*(",)""".r

  /**
   * Matches BasicCredentials(username,password)
   */
  private val basicCredentialsRegex: Regex = """(BasicCredentials\(.*,).*(,.*)""".r

  /**
    * Matches UserToken(username,sessionId)
    */
  private val userTokenRegex: Regex = """(UserToken\(.*,).*(\))""".r

  /**
    * Matches UserSessionToken(username,sessionId,Some(19000))
    */
  private val userSessionTokenRegex: Regex = """(UserSessionToken\(.*,).*(,.*)(\)*)""".r

  def censor(target:String):String = {
    val magicRedactedReplacement = "$1REDACTED$2"

    val noIb2Passwords = i2b2PasswordRegex.replaceAllIn(target,magicRedactedReplacement)
    val noIb2Counts = i2b2SetSizeRegex.replaceAllIn(noIb2Passwords,magicRedactedReplacement)
    val noBasicAuth = basicAuthRegex.replaceAllIn(noIb2Counts,magicRedactedReplacement)
    val noBasicCredentials = basicCredentialsRegex.replaceAllIn(noBasicAuth,magicRedactedReplacement)
    val noUserToken = userTokenRegex.replaceAllIn(noBasicCredentials,magicRedactedReplacement)
    val noUserSessionToken = userSessionTokenRegex.replaceAllIn(noUserToken,magicRedactedReplacement)
    typesafeConfigRegex.replaceAllIn(noUserSessionToken,magicRedactedReplacement)
  }
}


class PasswordCensorMessagePatternConverter extends ThrowableHandlingConverter {

  override def convert(event: ILoggingEvent): String = {
    LogCensor.censor(event.getFormattedMessage)
  }
}


class PasswordCensorThrowablePatternConverter extends ThrowableHandlingConverter {

  override def convert(event: ILoggingEvent): String = {
    recursiveAppendCause(Option(event.getThrowableProxy))
  }

  private def recursiveAppendCause(tp: Option[IThrowableProxy]): String = {
    tp.fold("") { information =>
      val className: String = information.getClassName
      val censoredMessage: String = LogCensor.censor(information.getMessage)
      val trace: String = information.getStackTraceElementProxyArray.map(_.getSTEAsString).mkString("\n\t")
      val causeMessage: String = recursiveAppendCause(Option(information.getCause))
      causeMessage match {
        case "" => s"$className: $censoredMessage\n\t$trace\n"
        case msg => s"$className: $censoredMessage\n\t$trace\nCaused by: $msg"
      }
    }
  }
}

class CustomPatternLayout extends PatternLayout {
  override def getDefaultConverterMap: util.Map[String, String] = {
    val baseConverters: Map[String, String] = super.getDefaultConverterMap.asScala.toMap
    (baseConverters ++ CustomPatternLayout.converterRegistry).asJava
  }
}

object CustomPatternLayout extends PatternLayout {
  private val converterRegistry: Map[String, String] = Map(
      "m" -> classOf[PasswordCensorMessagePatternConverter].getName,
      "message" -> classOf[PasswordCensorMessagePatternConverter].getName,
      "throwable" -> classOf[PasswordCensorThrowablePatternConverter].getName,
      "ex" -> classOf[PasswordCensorThrowablePatternConverter].getName
    )
}
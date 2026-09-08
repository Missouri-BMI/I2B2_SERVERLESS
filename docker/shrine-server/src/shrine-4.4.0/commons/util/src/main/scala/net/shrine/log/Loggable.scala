package net.shrine.log

import ch.qos.logback.classic.{Level, Logger}
import org.slf4j.LoggerFactory
import net.shrine.problem.RawProblem

/**
 * Apparently this is how the scala community likes to boilerplate their loggers
 *
 * @author Justin Quan
 * @see http://chip.org
 * Date: 8/8/11
 */
trait Loggable {

  private[this] lazy val internalLogger: Logger = {
    val logger = LoggerFactory.getLogger(this.getClass.getName).asInstanceOf[Logger]
    // Ignore these classes when calculating calling class of log method.
    logger.getLoggerContext.getFrameworkPackages.add("net.shrine.log")
    logger
  }

  //NB: For tests
  private[log] def logger: Logger = internalLogger

  //NB: This used to be a lazy val, but that was confusing Squeryl, so this is now a def
  final def debugEnabled: Boolean = internalLogger.isDebugEnabled

  //NB: This used to be a lazy val, but that was confusing Squeryl, so this is now a def
  final def infoEnabled: Boolean = internalLogger.isInfoEnabled

  def debug(s: => String): Unit = if(debugEnabled) internalLogger.debug(s)
  final def debug(s: => String, e: Throwable): Unit = if(debugEnabled) internalLogger.debug(s, e)

  def info(s: => String): Unit = if(infoEnabled) internalLogger.info(s)
  final def info(s: => String, e: Throwable): Unit = if(infoEnabled) internalLogger.info(s, e)

  def warn(s: => String): Unit = internalLogger.warn(s)
  final def warn(s: => String, e: Throwable): Unit = internalLogger.warn(s, e)

  def error(s: => String): Unit = internalLogger.error(s)
  final def error(s: => String, e: Throwable): Unit = internalLogger.error(s, e)

  def log(problem:RawProblem):Unit =
    internalLogger.log(
      null, //marker - a named object used to enrich log statement, safe to ignore
      problem.getClass.getName,
      Level.toLocationAwareLoggerInteger(problem.logLevel),
      problem.toString,
      null, //argArray - parameters for a filter inside the logger, a feature we do not use
      problem.throwable.orNull
    )

  /**
    * Helper method to trace how long a particular task takes to run.
    *
    */
  def logDuration[T](taskName: String)(log: String => Unit)(f: => T): T = {
    val start = System.currentTimeMillis

    try { f } finally {
      val elapsed = System.currentTimeMillis - start

      log(s"$taskName took $elapsed milliseconds.")
    }
  }
}

/**
 * Simple Log object for when Loggable isn't available.
 *
 * @author dwalend
 * @since 7/20/2015
 */
object Log extends Loggable



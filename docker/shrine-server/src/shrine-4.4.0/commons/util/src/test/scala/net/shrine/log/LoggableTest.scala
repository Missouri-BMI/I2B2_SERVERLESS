package net.shrine.log

import ch.qos.logback.classic.Level
import org.junit.jupiter.api.Assertions.{assertEquals, assertFalse, assertTrue}
import org.junit.jupiter.api.Test

import scala.collection.mutable.{Map => MMap}

/**
 *
 * @author Clint Gilbert
 * @since Oct 11, 2011
 *
 * @see http://cbmi.med.harvard.edu
 *
 * This software is licensed under the LGPL
 * @see http://www.gnu.org/licenses/lgpl.html
 *
 */
final class LoggableTest  {

  import Level.{DEBUG, ERROR, ALL, INFO, WARN}

  @Test
  def testWarn(): Unit = doTest(_.warn, WARN, logMessageIsLazy = false)

  @Test
  def testError(): Unit = doTest(_.error, ERROR, logMessageIsLazy = false)

  private def doTest(log: Loggable => (=> String) => Unit, level: Level, logMessageIsLazy: Boolean): Unit = {
    {
      val loggable = new MockLoggable

      var messageComputed = false

      val otherLevel = if (logMessageIsLazy) higher(level) else not(level)

      loggable.logger.setLevel(otherLevel)

      log(loggable)({ messageComputed = true; "message" })

      {
        val shouldHaveBeenComputed: Boolean = !logMessageIsLazy

        //the log message should NOT have been computed if this log method is lazy
        assertEquals(messageComputed,shouldHaveBeenComputed)
        //but we should have tried to log
        assertTrue(loggable.loggedAt(level))
      }
    }

    {
      val loggable = new MockLoggable

      var messageComputed = false

//      loggable.logger.setLevel(level)

      log(loggable)({ messageComputed = true; "message" })

      //the log message should have been computed 
      assertTrue(messageComputed)
      //we should have tried to log at the desired level
      assertTrue(loggable.loggedAt(level))
      //and at no other levels
      assertFalse(loggable.loggedAt.exists { case (priority, happened) => priority != priority && happened })
    }
  }

  private val priorities: Map[Level, Int] = Seq(DEBUG, INFO, WARN, ERROR, ALL).zipWithIndex.toMap

  private def next(level: Level, adjust: Int => Int): Level = {
    val numericalPriority = priorities(level)
    
    val adjustedPriority = adjust(numericalPriority)

    priorities.find { case (_, index) => index == adjustedPriority }.map { case (p, _) => p }.get
  }

  private def not(level: Level): Level = priorities.keys.find(_ != level).get
  private def higher(level: Level): Level = next(level, _ + 1)

  private final class MockLoggable extends Loggable {
    val loggedAt: MMap[Level, Boolean] = MMap.empty

    override def debug(s: => String): Unit = {
      loggedAt(Level.DEBUG) = true
      
      super.debug(s)
    }

    override def info(s: => String): Unit = {
      loggedAt(Level.INFO) = true
      
      super.info(s)
    }

    override def warn(s: => String): Unit = {
      loggedAt(Level.WARN) = true

      super.warn(s)
    }

    override def error(s: => String): Unit = {
      loggedAt(Level.ERROR) = true
      
      super.error(s)
    }
  }
}
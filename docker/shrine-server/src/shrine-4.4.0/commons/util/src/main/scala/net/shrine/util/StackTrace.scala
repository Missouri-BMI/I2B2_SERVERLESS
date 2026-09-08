package net.shrine.util

/**
 * @author clint
 * @since Oct 18, 2012
 */
object StackTrace {
  def stackTraceAsString(e: Throwable): String = {
    val writer = new java.io.StringWriter

    val printWriter = new java.io.PrintWriter(writer)

    try {
      e.printStackTrace(printWriter)
    } finally {
      printWriter.close()
    }

    writer.toString
  }
}
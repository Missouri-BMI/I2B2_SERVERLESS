package net.shrine.adapter

/**
 * @author Andrew McMurry
 * @author clint
 * @date ???
 * @date Nov 21, 2012 (Scala port)
 */
class AdapterException(message: String, cause: Throwable) extends Exception(message, cause) {
  def this() = this("", null)

  def this(message: String) = this(message, null)

  def this(cause: Throwable) = this("", cause)
}

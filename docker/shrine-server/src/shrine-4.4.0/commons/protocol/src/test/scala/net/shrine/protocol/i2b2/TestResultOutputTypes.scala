package net.shrine.protocol.i2b2

/**
 * @author clint
 * @since Oct 24, 2014
 */
object TestResultOutputTypes {
  import ResultOutputType.I2b2Options
  
  val values: Set[ResultOutputType] = Set(
      ResultOutputType("foo", isBreakdown = true, I2b2Options("foo desc", "FOO"), None),
      ResultOutputType("bar", isBreakdown = true, I2b2Options("bar desc", ResultOutputType.defaultDisplayType), None))
}
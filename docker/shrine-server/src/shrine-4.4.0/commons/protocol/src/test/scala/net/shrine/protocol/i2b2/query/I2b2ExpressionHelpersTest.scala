package net.shrine.protocol.i2b2.query

import net.shrine.util.ShouldMatchersForJUnit
import org.junit.Test

/**
 * @author clint
 * @since Dec 17, 2012
 */
final class I2b2ExpressionHelpersTest extends ShouldMatchersForJUnit {
  @Test
  def testIs(): Unit = {
	import ExpressionHelpers.is
	
	is[I2b2Expression](And()) should equal(true)
	is[I2b2Expression](Term("foo", "foo")) should equal(true)
	
	is[ComposeableI2b2Expression[_]](Term("foo", "foo")) should be(false)
	
	is[And](And()) should equal(true)
	is[And](Or()) should equal(false)
	is[Or](And()) should equal(false)
	is[Or](Or()) should equal(true)
	
	is[String]("ksahdksa") should equal(true)
	is[Int]("sakljd") should equal(false)
  }
}
package net.shrine.adapter.i2b2Protocol

import junit.framework.TestCase
import net.shrine.util.ShouldMatchersForJUnit
import org.junit.Test

/**
 * @author clint
 * @since Mar 29, 2013
 */
final class CrcRequestTest extends TestCase with ShouldMatchersForJUnit {
  @Test
  def testFromI2b2BadInput():Unit = {
    CrcRequest.fromI2b2String(Set.empty)("jksahdjkashdjkashdjkashdjksad").isFailure should be(true)

    CrcRequest.fromI2b2(Set.empty)(<foo/>).isFailure should be(true)
  }
}
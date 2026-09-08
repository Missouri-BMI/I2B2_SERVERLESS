package net.shrine.xml

import org.junit.jupiter.api.Assertions.{assertEquals, assertTrue}
import org.junit.jupiter.api.Test

/**
 * @author clint
 * @since Nov 26, 2014
 */
final class StringEnrichmentsTest {
  @Test
  def testTryToXml(): Unit = {
    import net.shrine.xml.StringEnrichments._
    
    assertTrue((null: String).tryToXml.isFailure)
    assertTrue("".tryToXml.isFailure )
    assertTrue("asl;kda;sd".tryToXml.isFailure)
    
    assertEquals("<foo/>".tryToXml.get, <foo/>)
  }
}
package net.shrine.protocol.i2b2.query

import net.shrine.util.ShouldMatchersForJUnit
import org.junit.Test

final class PanelTimingTest extends ShouldMatchersForJUnit {
  @Test
  def testName: Unit = {
    import PanelTiming._

    Any.name should equal("ANY")
    SameVisit.name should equal("SAMEVISIT")
    SameInstanceNum.name should equal("SAMEINSTANCENUM")
  }
  
  @Test
  def testValueOfOrElse: Unit = {
    import PanelTiming._
    
    valueOfOrElse(Any.name)(null) should be(Any)
    valueOfOrElse(SameVisit.name)(null) should be(SameVisit)
    valueOfOrElse(SameInstanceNum.name)(null) should be(SameInstanceNum)
    
    valueOfOrElse("askldjaljkd")(Any) should be(Any)
    valueOfOrElse("askldjaljkd")(SameVisit) should be(SameVisit)
    valueOfOrElse("askldjaljkd")(SameInstanceNum) should be(SameInstanceNum)
  }
}


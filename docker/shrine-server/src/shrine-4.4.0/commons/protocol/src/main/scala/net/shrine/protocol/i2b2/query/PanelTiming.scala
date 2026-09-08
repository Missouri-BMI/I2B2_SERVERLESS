package net.shrine.protocol.i2b2.query

import net.shrine.util.SEnum

/**
 * @author clint
 * @since Sep 22, 2014
 * 
 * An enum to represent allowable values for <panel_timing> elements in
 * i2b2 XML blobs.
 */
final case class PanelTiming private (name: String) extends PanelTiming.Value {
  def isAny: Boolean = this eq PanelTiming.Any

  /**
   * SEnum's equals doesn't play nice with circe deserialization and equality in test code.
   */
  override def equals(other: Any): Boolean = {
    other match {
      case timing: PanelTiming => name == timing.name
      case _ => false
    }
  }
}

object PanelTiming extends SEnum[PanelTiming] {
  val Any: PanelTiming = PanelTiming("ANY")
  val SameVisit: PanelTiming = PanelTiming("SAMEVISIT")
  val SameInstanceNum: PanelTiming = PanelTiming("SAMEINSTANCENUM")
}
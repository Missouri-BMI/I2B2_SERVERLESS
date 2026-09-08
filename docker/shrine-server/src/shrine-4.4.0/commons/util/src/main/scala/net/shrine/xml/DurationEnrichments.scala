package net.shrine.xml

import scala.concurrent.duration.Duration
import scala.util.Try
import scala.xml.{Node, NodeSeq}

/**
 * @author clint
 * @date Feb 20, 2014
 */
object DurationEnrichments {
  implicit final class HasToXml(val d: Duration) extends AnyVal {
    def toXml: Node = XmlUtil.stripWhitespace {
      <duration>
        <value>{ d.length }</value>
        <unit>{ d.unit }</unit>
      </duration>
    }
  }

  implicit final class HasFromXml(val companion: Duration.type) extends AnyVal {
    def fromXml(xml: NodeSeq): Try[Duration] = {
      import NodeSeqEnrichments.Strictness._

      val lengthAttempt = for {
        lengthXml <- xml withChild "value"
        length <- Try(lengthXml.text.toLong)
      } yield length

      val unitAttempt = for {
        unitXml <- xml withChild "unit"
        unitText = unitXml.text.trim
        unit = java.util.concurrent.TimeUnit.valueOf(unitText)
        if unit != null
      } yield unit

      for {
        length <- lengthAttempt
        unit <- unitAttempt
      } yield  Duration(length, unit)
    }
  }
}

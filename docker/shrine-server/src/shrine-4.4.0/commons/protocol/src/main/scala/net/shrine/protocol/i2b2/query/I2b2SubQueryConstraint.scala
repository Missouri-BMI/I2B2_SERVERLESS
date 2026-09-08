package net.shrine.protocol.i2b2.query

import net.shrine.protocol.i2b2.serialization.I2b2Marshaller
import net.shrine.xml.{NodeSeqEnrichments, XmlUtil}

import scala.util.Try
import scala.xml.NodeSeq

/**
 * @author clint
 * @since Sep 25, 2014
 *
 * A class to handle parameters to I2b2's notion of a temporal query
 * (achieved by choosing 'define sequence of events' in the legacy web client)
 */
final case class I2b2SubQueryConstraint(id: String, joinColumn: String, aggregateOperator: String) extends I2b2Marshaller {
  override def toI2b2: NodeSeq = XmlUtil.stripWhitespace {
    //NB: outer tag is "placeholder", and will be renamed to "first_query" or "second_query"
    <placeholder>
      <query_id>{ id }</query_id>
      <join_column>{ joinColumn }</join_column>
      <aggregate_operator>{ aggregateOperator }</aggregate_operator>
    </placeholder>
  }

  import I2b2SubQueryConstraint._

  def toXml(idx: Int): NodeSeq = XmlUtil.stripWhitespace {
    XmlUtil.renameRootTag(rootTagName) {
      <placeholder index={ idx.toString }>
        <id>{ id }</id>
        <joinColumn>{ joinColumn }</joinColumn>
        <aggregateOperator>{ aggregateOperator }</aggregateOperator>
      </placeholder>
    }
  }
}

object I2b2SubQueryConstraint {
  val rootTagName = "i2b2SubQueryConstraint"

  def fromXml(xml: NodeSeq): Try[I2b2SubQueryConstraint] = {
    import NodeSeqEnrichments.Strictness._

    for {
      id <- xml.withChild("id").map(_.text)
      joinColumn <- xml.withChild("joinColumn").map(_.text)
      aggregateOperator <- xml.withChild("aggregateOperator").map(_.text)
    } yield {
      I2b2SubQueryConstraint(id, joinColumn, aggregateOperator)
    }
  }

  def fromI2b2(xml: NodeSeq): Try[I2b2SubQueryConstraint] = {
    import NodeSeqEnrichments.Strictness._

    for {
      id <- xml.withChild("query_id").map(_.text)
      joinColumn <- xml.withChild("join_column").map(_.text)
      aggregateOperator <- xml.withChild("aggregate_operator").map(_.text)
    } yield {
      I2b2SubQueryConstraint(id, joinColumn, aggregateOperator)
    }
  }
}
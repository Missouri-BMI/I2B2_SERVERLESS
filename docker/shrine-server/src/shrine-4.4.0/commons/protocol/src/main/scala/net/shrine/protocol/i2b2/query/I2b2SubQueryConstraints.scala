package net.shrine.protocol.i2b2.query

import net.shrine.protocol.i2b2.serialization.{I2b2Marshaller, XmlMarshaller}
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
final case class I2b2SubQueryConstraints(
                                          operator: String,
                                          first: I2b2SubQueryConstraint,
                                          second: I2b2SubQueryConstraint,
                                          primarySpan: Option[I2b2QuerySpan] = None,
                                          secondarySpan: Option[I2b2QuerySpan] = None
                                        ) extends I2b2Marshaller with XmlMarshaller {

  override def toI2b2: NodeSeq = XmlUtil.stripWhitespace {
    def subQueryToI2b2(name: String, sq: I2b2SubQueryConstraint) = XmlUtil.renameRootTag(name)(sq.toI2b2.head)

    <subquery_constraint>
      { subQueryToI2b2("first_query", first) }
      <operator>{ operator }</operator>
      { subQueryToI2b2("second_query", second) }
      { primarySpan.map(_.toI2b2).orNull }
      { secondarySpan.map(_.toI2b2).orNull }
    </subquery_constraint>
  }

  import I2b2SubQueryConstraints._

  override def toXml: NodeSeq = XmlUtil.stripWhitespace {
    XmlUtil.renameRootTag(rootTagName) {
      <placeholder>
        { first.toXml(0) }
        <operator>{ operator }</operator>
        { second.toXml(1) }
        { primarySpan.map(_.toXml).orNull }
        { secondarySpan.map(_.toXml).orNull }
      </placeholder>
    }
  }
}

object I2b2SubQueryConstraints {
  val rootTagName = "i2b2SubQueryConstraints"

  import NodeSeqEnrichments.Strictness._

  def fromXml(xml: NodeSeq): Try[I2b2SubQueryConstraints] = {
    def subQueryXmlWithIndex(idx: Int): Try[NodeSeq] = {
      import I2b2SubQueryConstraint.{rootTagName => subQueryTagName}
      
      def indexMatches(x: NodeSeq): Boolean = (x \ "@index").text.trim == idx.toString
      
      Try((xml \ subQueryTagName).filter(indexMatches).head)
    }
    
    for {
      operator <- xml.withChild("operator").map(_.text)
      first <- subQueryXmlWithIndex(0).flatMap(I2b2SubQueryConstraint.fromXml)
      second <- subQueryXmlWithIndex(1).flatMap(I2b2SubQueryConstraint.fromXml)
      spans: Seq[I2b2QuerySpan] = (xml \ I2b2QuerySpan.rootTagName).flatMap(I2b2QuerySpan.fromXml(_).toOption)
    } yield {
      val (span1,span2) = spans match {
        case Seq(s1,s2) => (Option(s1),Option(s2))
        case Seq(s) => (Option(s),None)
        case Seq() => (None,None)
        case _ => throw new IllegalArgumentException(s"i2b2 xml supports 0, 1, or 2 span elements, not ${spans.length} $spans")
      }
      I2b2SubQueryConstraints(operator, first, second, span1, span2)
    }
  }
  
  def fromI2b2(xml: NodeSeq): Try[I2b2SubQueryConstraints] = {
    for {
      operator <- xml.withChild("operator").map(_.text)
      first <- xml.withChild("first_query").flatMap(I2b2SubQueryConstraint.fromI2b2)
      second <- xml.withChild("second_query").flatMap(I2b2SubQueryConstraint.fromI2b2)
      spans: Seq[I2b2QuerySpan] = (xml \ "span").flatMap(I2b2QuerySpan.fromI2b2(_).toOption)
    } yield {
      val (span1, span2) = spans match {
        case Seq(s1, s2) => (Option(s1), Option(s2))
        case Seq(s) => (Option(s), None)
        case Seq() => (None, None)
        case _ => throw new IllegalArgumentException(s"i2b2 xml supports 0, 1, or 2 span elements, not ${spans.length} $spans")
      }
      I2b2SubQueryConstraints(operator, first, second, span1, span2)
    }
  }
}


package net.shrine.protocol.i2b2.query

import net.shrine.protocol.version.v2.querydefinition.{ConceptConstraint, DoubleNumberConstraint, FlagConstraint, FlagConstraints, NumberConstraint, SingleNumberConstraint}
import net.shrine.xml.{OptionEnrichments, XmlUtil}

import scala.util.Try
import scala.xml.NodeSeq

/**
 * @author clint
 * @since Dec 1, 2014
 */
final case class ValueConstraint(valueType: String, unit: Option[String], operator: String, value: String) {
  import OptionEnrichments._
  
  def toI2b2: NodeSeq = XmlUtil.stripWhitespace {
    <constrain_by_value>
      <value_type>{ valueType }</value_type>
      { unit.toXml(<value_unit_of_measure/>) }
      <value_operator>{ operator }</value_operator>
      <value_constraint>{ value }</value_constraint>
    </constrain_by_value>
  }

  def toXml: NodeSeq = XmlUtil.stripWhitespace {
    <valueConstraint>
      <valueType>{ valueType }</valueType>
      { unit.toXml(<unitOfMeasure/>) }
      <operator>{ operator }</operator>
      <value>{ value }</value>
    </valueConstraint>
  }

}

object ValueConstraint {
  def fromShrineV2(v2Constraint:ConceptConstraint):ValueConstraint = {

    v2Constraint match {
      case flagConstraint: FlagConstraint => flagConstraint match {
        case FlagConstraints.Low => ValueConstraint(valueType = "FLAG",unit = None,operator = "EQ",value = "L")
        case FlagConstraints.Normal => ValueConstraint(valueType = "FLAG",unit = None,operator = "EQ",value = "@")
        case FlagConstraints.High => ValueConstraint(valueType = "FLAG",unit = None,operator = "EQ",value = "H")
      }
      case oneNumber: SingleNumberConstraint =>
        val operator = oneNumber.operator match {
          case NumberConstraint.GreaterThan => "GT"
          case NumberConstraint.GreaterThanOrEqual => "GE"
          case NumberConstraint.Equal => "EQ"
          case NumberConstraint.LessThanOrEqual => "LE"
          case NumberConstraint.LessThan => "LT"
        }
        ValueConstraint(valueType = "NUMBER",unit = oneNumber.unit,operator = operator,value = oneNumber.value.toString)
      case twoNumber: DoubleNumberConstraint => ValueConstraint("NUMBER",twoNumber.unit,operator = "BETWEEN", value = s"${twoNumber.value1} and ${twoNumber.value2}")
    }
  }

  def fromI2b2(xml: NodeSeq): Try[ValueConstraint] = unmarshalXml(xml, "value_type", "value_unit_of_measure", "value_operator", "value_constraint")

  def fromXml(xml: NodeSeq): Try[ValueConstraint] = unmarshalXml(xml, "valueType", "unitOfMeasure", "operator", "value")

  private def unmarshalXml(xml: NodeSeq, valueTypeTagName: String, unitTagName: String, operatorTagName: String, valueTagName: String): Try[ValueConstraint] = {
    import net.shrine.xml.NodeSeqEnrichments.Strictness._

    def tagValue(tagName: String) = xml.withChild(tagName).map(XmlUtil.trim)
    
    for {
      valueType <- tagValue(valueTypeTagName)
      unit = tagValue(unitTagName).toOption
      operator <- tagValue(operatorTagName)
      value <- tagValue(valueTagName)
    } yield ValueConstraint(valueType, unit, operator, value)
  }

}

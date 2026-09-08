package net.shrine.protocol.version.v2.querydefinition

import io.circe.Codec
import io.circe.generic.extras.semiauto.deriveEnumerationCodec

sealed trait ConceptConstraint

sealed abstract class FlagConstraint extends ConceptConstraint

object FlagConstraints {

  implicit val codec: Codec[FlagConstraint] = deriveEnumerationCodec[FlagConstraint]

  case object Low extends FlagConstraint
  case object Normal extends FlagConstraint
  case object High extends FlagConstraint
}

sealed abstract class NumberConstraint(operator:NumberConstraint.Operator,unit: Option[String]) extends ConceptConstraint

case class SingleNumberConstraint(operator:NumberConstraint.SingleNumberOperator,value:Double,unit: Option[String]) extends NumberConstraint(operator,unit)

case class DoubleNumberConstraint(value1:Double,value2:Double,unit: Option[String]) extends NumberConstraint(NumberConstraint.Between,unit)

object NumberConstraint {
  sealed trait Operator

  sealed trait SingleNumberOperator extends Operator

  implicit val codec: Codec[Operator] = deriveEnumerationCodec[Operator]

  case object GreaterThan extends SingleNumberOperator
  case object GreaterThanOrEqual extends SingleNumberOperator
  case object Equal extends SingleNumberOperator
  case object LessThanOrEqual extends SingleNumberOperator
  case object LessThan extends SingleNumberOperator

  case object Between extends Operator
}
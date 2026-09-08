package net.shrine.qep

import io.circe.Codec
import io.circe.generic.extras.semiauto.deriveEnumerationCodec

sealed abstract class ConstraintType(val name: String)

object ConstraintType {

  implicit val codec: Codec[ConstraintType] = deriveEnumerationCodec[ConstraintType]

  case object FLAG_CONSTRAINT extends ConstraintType(name = "FLAG")

  case object NUMBER_CONSTRAINT extends ConstraintType(name = "NUMBER")

}
sealed trait ConstraintDetails {
  val constraintType: ConstraintType
  val operator: ConstraintOperator
  val value: String
  val unit: Option[String]
}

//todo revisit for SHRINE2020-1361 - can these be case objects?
case class NORMAL_FLAG (constraintType: ConstraintType = ConstraintType.FLAG_CONSTRAINT, operator: ConstraintOperator = ConstraintOperator.EQ_OPERATOR, value: String = "@", unit: Option[String] = None) extends ConstraintDetails
case class LOW_FLAG (constraintType: ConstraintType = ConstraintType.FLAG_CONSTRAINT, operator: ConstraintOperator = ConstraintOperator.EQ_OPERATOR, value: String = "L", unit: Option[String] = None) extends ConstraintDetails
case class HIGH_FLAG (constraintType: ConstraintType = ConstraintType.FLAG_CONSTRAINT, operator: ConstraintOperator = ConstraintOperator.EQ_OPERATOR, value: String = "H", unit: Option[String] = None) extends ConstraintDetails

case class BETWEEN_NUMBER (constraintType: ConstraintType = ConstraintType.NUMBER_CONSTRAINT, operator: ConstraintOperator = ConstraintOperator.BETWEEN_OPERATOR, value: String, unit: Option[String]) extends ConstraintDetails

object BETWEEN_NUMBER {
  def apply(value: Seq[String], unit: Option[String]): BETWEEN_NUMBER  = {

    validateValues(value)
    val valueString = value.mkString(" and ")
    BETWEEN_NUMBER(value = valueString, unit = unit)
  }

  def validateValues(value: Seq[String]): Unit= {

    if(value.isEmpty){
      throw InvalidValueConstraintException(s"No value specified for BETWEEN constraint")
    }

    if(value.size == 1){
      throw InvalidValueConstraintException(s"Not enough values specified for BETWEEN constraint: $value. Expected 2 but found ${value.size}.")
    }

    if(toDoubleOption(value.head).isEmpty){
      throw InvalidValueConstraintException(s"Start value for BETWEEN constraint is not a number: ${value.head}")
    }

    if(toDoubleOption(value(1)).isEmpty){
      throw InvalidValueConstraintException(s"End value for BETWEEN constraint is not a number: ${value(1)}")
    }

    def toDoubleOption(s: String):Option[Double] = {
      try {
        Some(s.toDouble)
      } catch {
        case _: NumberFormatException => None
      }
    }
  }
}

case class SINGLE_NUMBER (constraintType: ConstraintType = ConstraintType.NUMBER_CONSTRAINT, operator: ConstraintOperator, value: String, unit: Option[String]) extends ConstraintDetails
object SINGLE_NUMBER {
  def apply(operator: ConstraintOperator, value: Seq[String], unit: Option[String]): SINGLE_NUMBER  = {

    validateValues(value, operator)
    val valueString = value.mkString(" and ")
    SINGLE_NUMBER(operator = operator, value = valueString, unit = unit)
  }

  def validateValues(value: Seq[String], operator: ConstraintOperator): Unit= {

    if(value.isEmpty){
      throw InvalidValueConstraintException(s"No value specified for ${operator.name}")
    }

    if(value.size > 1){
      throw InvalidValueConstraintException(s"Too many values specified for ${operator.name} constraint: $value. Expected 1 but found ${value.size}.")
    }

    if(toDoubleOption(value.head).isEmpty){
      throw InvalidValueConstraintException(s"Start value for BETWEEN constraint is not a number: ${value.head}")
    }
  }

  def toDoubleOption(s: String):Option[Double] = {
    try {
      Some(s.toDouble)
    } catch {
      case _: NumberFormatException => None
    }
  }
}

object ConstraintDetails {
  //todo maybe rename constraintType to ... bounds?
  def apply(constraintType: String, value: Seq[String], unit: Option[String]):ConstraintDetails = {

    constraintType match {
      case "NORMAL" => NORMAL_FLAG()
      case "LOW" => LOW_FLAG()
      case "HIGH" => HIGH_FLAG()
      case "EQ" => SINGLE_NUMBER(ConstraintOperator.EQ_OPERATOR, value, unit)
      case "LT" => SINGLE_NUMBER(ConstraintOperator.LT_OPERATOR, value, unit)
      case "GT" => SINGLE_NUMBER(ConstraintOperator.GT_OPERATOR, value, unit)
      case "LE" => SINGLE_NUMBER(ConstraintOperator.LE_OPERATOR, value, unit)
      case "GE" => SINGLE_NUMBER(ConstraintOperator.GE_OPERATOR, value, unit)
      case "BETWEEN" => BETWEEN_NUMBER(value, unit)
      case _ => throw InvalidValueConstraintException(s"Invalid constraintType $constraintType. Expected either NORMAL, LOW, HIGH, EQ, LT, GT, LE, GE, or BETWEEN.")
    }
  }
}
sealed abstract class ConstraintOperator(val name: String)

object ConstraintOperator {
  implicit val codec: Codec[ConstraintOperator] = deriveEnumerationCodec[ConstraintOperator]

  case object EQ_OPERATOR extends ConstraintOperator(name = "EQ")

  case object LT_OPERATOR extends ConstraintOperator(name = "LT")

  case object GT_OPERATOR extends ConstraintOperator(name = "GT")

  case object LE_OPERATOR extends ConstraintOperator(name = "LE")

  case object GE_OPERATOR extends ConstraintOperator(name = "GE")

  case object BETWEEN_OPERATOR extends ConstraintOperator(name = "BETWEEN")
}



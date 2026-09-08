package net.shrine.protocol.i2b2.query

import net.shrine.util.ShouldMatchersForJUnit
import net.shrine.xml.XmlUtil
import org.junit.Test

/**
 * @author clint
 * @date Dec 1, 2014
 */
final class ValueConstraintTest extends ShouldMatchersForJUnit {
  private val valueType = "NUMBER"
  private val unit = Option("ng/l")
  private val operator = "LT"
  private val constraintValue = "42"

  private val xml = XmlUtil.stripWhitespace {
    <constrain_by_value>
      <value_type>{ valueType }</value_type>
      <value_unit_of_measure>{ unit.get }</value_unit_of_measure>
      <value_operator>{ operator }</value_operator>
      <value_constraint>{ constraintValue }</value_constraint>
    </constrain_by_value>
  }

  private val shrineXml = XmlUtil.stripWhitespace {
    <valueConstraint>
      <valueType>{ valueType }</valueType>
      <unitOfMeasure>{ unit.get }</unitOfMeasure>
      <operator>{ operator }</operator>
      <value>{ constraintValue }</value>
    </valueConstraint>
  }

  private val json = """{"valueType":"NUMBER","unitOfMeasure":"ng/l","operator":"LT","value":"42"}"""

  private val valueConstraint = ValueConstraint(valueType, unit, operator, constraintValue)

  @Test
  def testToI2b2: Unit = {
    valueConstraint.toI2b2 should equal(xml)
  }

  @Test
  def testToI2b2ByFlag: Unit = {
    val vt = "FLAG"
    val operator = "EQ"
    val v = "H"

    val byFlagXml = XmlUtil.stripWhitespace {
      <constrain_by_value>
        <value_type>{ vt }</value_type>
        <value_operator>{ operator }</value_operator>
        <value_constraint>{ v }</value_constraint>
      </constrain_by_value>
    }

    ValueConstraint(vt, None, operator, v).toI2b2 should equal(byFlagXml)
  }

  @Test
  def testToXml: Unit = {
    valueConstraint.toXml should equal(shrineXml)
  }

  @Test
  def testToXmlByFlag: Unit = {
    val vt = "FLAG"
    val operator = "EQ"
    val v = "H"

    val byFlagXml = XmlUtil.stripWhitespace {
      <valueConstraint>
        <valueType>{ vt }</valueType>
        <operator>{ operator }</operator>
        <value>{ v }</value>
      </valueConstraint>
    }

    ValueConstraint(vt, None, operator, v).toXml should equal(byFlagXml)
  }

  @Test
  def testFromI2b2: Unit = {
    ValueConstraint.fromI2b2(<foo/>).isFailure should be(true)
    ValueConstraint.fromI2b2(null).isFailure should be(true)

    ValueConstraint.fromI2b2(xml).get should equal(valueConstraint)
    
    val vt = "FLAG"
    val operator = "EQ"
    val v = "H"
    
    val byFlagXml = XmlUtil.stripWhitespace {
      <constrain_by_value>
        <value_type>{ vt }</value_type>
        <value_operator>{ operator }</value_operator>
        <value_constraint>{ v }</value_constraint>
      </constrain_by_value>
    }
    
    ValueConstraint.fromI2b2(byFlagXml).get should equal(ValueConstraint(vt, None, operator, v))
  }

  @Test
  def testFromXml: Unit = {
    ValueConstraint.fromXml(<foo/>).isFailure should be(true)
    ValueConstraint.fromXml(null).isFailure should be(true)

    ValueConstraint.fromXml(shrineXml).get should equal(valueConstraint)

    val vt = "FLAG"
    val operator = "EQ"
    val v = "H"

    val byFlagXml = XmlUtil.stripWhitespace {
      <valueConstraint>
        <valueType>{ vt }</valueType>
        <operator>{ operator }</operator>
        <value>{ v }</value>
      </valueConstraint>
    }

    ValueConstraint.fromXml(byFlagXml).get should equal(ValueConstraint(vt, None, operator, v))
  }

  @Test
  def testI2b2XmlRoundTrip: Unit = {
    ValueConstraint.fromI2b2(valueConstraint.toI2b2).get should equal(valueConstraint)
    
    val vt = "FLAG"
    val operator = "EQ"
    val v = "H"
    
    val byFlag = ValueConstraint(vt, None, operator, v)
    
    ValueConstraint.fromI2b2(byFlag.toI2b2).get should equal(byFlag)
  }

  @Test
  def testShrineXmlRoundTrip: Unit = {
    ValueConstraint.fromXml(valueConstraint.toXml).get should equal(valueConstraint)
    
    val vt = "FLAG"
    val operator = "EQ"
    val v = "H"
    
    val byFlag = ValueConstraint(vt, None, operator, v)
    
    ValueConstraint.fromXml(byFlag.toXml).get should equal(byFlag)
  }
}
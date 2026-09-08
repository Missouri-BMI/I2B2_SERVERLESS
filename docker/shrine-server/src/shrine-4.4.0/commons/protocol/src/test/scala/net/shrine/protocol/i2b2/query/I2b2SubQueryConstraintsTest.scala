package net.shrine.protocol.i2b2.query

import net.shrine.util.ShouldMatchersForJUnit
import net.shrine.xml.XmlUtil
import org.junit.Test

import scala.xml.NodeSeq

/**
 * @author clint
 * @date Sep 26, 2014
 */
final class I2b2SubQueryConstraintsTest extends ShouldMatchersForJUnit {
  private val constraint1 = I2b2SubQueryConstraint("foo", "bar", "baz")
  private val constraint2 = I2b2SubQueryConstraint("nuh", "zuh", "glarg")
  private val operator = "aslkdjlkasjd"
  private val spanOperator = "saklfjsklaf"
  private val spanValue = "asdasdsadsadsad"
  private val spanUnits = "ljjhdfs"

  private val constraints = I2b2SubQueryConstraints(operator, constraint1, constraint2, None)
  
  private val span = I2b2QuerySpan(spanOperator, spanValue, spanUnits)
  
  private val constraintsWithSpan = I2b2SubQueryConstraints(operator, constraint1, constraint2, Some(span))

  private val i2b2Xml: NodeSeq = XmlUtil.stripWhitespace {
    <subquery_constraint>
      <first_query>
        <query_id>foo</query_id>
        <join_column>bar</join_column>
        <aggregate_operator>baz</aggregate_operator>
      </first_query>
      <operator>{ operator }</operator>
      <second_query>
        <query_id>nuh</query_id>
        <join_column>zuh</join_column>
        <aggregate_operator>glarg</aggregate_operator>
      </second_query>
    </subquery_constraint>
  }

  private val i2b2XmlWithSpan: NodeSeq = XmlUtil.stripWhitespace {
    <subquery_constraint>
      <first_query>
        <query_id>foo</query_id>
        <join_column>bar</join_column>
        <aggregate_operator>baz</aggregate_operator>
      </first_query>
      <operator>{ operator }</operator>
      <second_query>
        <query_id>nuh</query_id>
        <join_column>zuh</join_column>
        <aggregate_operator>glarg</aggregate_operator>
      </second_query>
      <span>
        <operator>{ spanOperator }</operator>
        <span_value>{ spanValue }</span_value>
        <units>{ spanUnits }</units>
      </span>
    </subquery_constraint>
  }

  private val shrineXml: NodeSeq = XmlUtil.stripWhitespace {
    <i2b2SubQueryConstraints>
      <i2b2SubQueryConstraint index="0">
        <id>foo</id>
        <joinColumn>bar</joinColumn>
        <aggregateOperator>baz</aggregateOperator>
      </i2b2SubQueryConstraint>
      <operator>{ operator }</operator>
      <i2b2SubQueryConstraint index="1">
        <id>nuh</id>
        <joinColumn>zuh</joinColumn>
        <aggregateOperator>glarg</aggregateOperator>
      </i2b2SubQueryConstraint>
    </i2b2SubQueryConstraints>
  }
  
   private val shrineXmlWithSpan: NodeSeq = XmlUtil.stripWhitespace {
    <i2b2SubQueryConstraints>
      <i2b2SubQueryConstraint index="0">
        <id>foo</id>
        <joinColumn>bar</joinColumn>
        <aggregateOperator>baz</aggregateOperator>
      </i2b2SubQueryConstraint>
      <operator>{ operator }</operator>
      <i2b2SubQueryConstraint index="1">
        <id>nuh</id>
        <joinColumn>zuh</joinColumn>
        <aggregateOperator>glarg</aggregateOperator>
      </i2b2SubQueryConstraint>
			<i2b2QuerySpan>
        <operator>{ spanOperator }</operator>
        <value>{ spanValue }</value>
        <units>{ spanUnits }</units>
      </i2b2QuerySpan>
    </i2b2SubQueryConstraints>
  }

  @Test
  def testToI2b2: Unit = {
    constraints.toI2b2 should equal(i2b2Xml)
  }
  
  @Test
  def testToI2b2WithSpan: Unit = {
    constraintsWithSpan.toI2b2 should equal(i2b2XmlWithSpan)
  }

  @Test
  def testToXml: Unit = {
    constraints.toXml should equal(shrineXml)
  }
  
  @Test
  def testToXmlWithSpan: Unit = {
    constraintsWithSpan.toXml should equal(shrineXmlWithSpan)
  }

  @Test
  def testFromI2b2: Unit = {
    import I2b2SubQueryConstraints.fromI2b2

    fromI2b2(null).isFailure should be(true)
    fromI2b2(NodeSeq.Empty).isFailure should be(true)
    fromI2b2(<nuh/>).isFailure should be(true)

    fromI2b2(i2b2Xml).get should equal(constraints)
  }
  
  @Test
  def testFromI2b2WithSpan: Unit = {
    import I2b2SubQueryConstraints.fromI2b2

    fromI2b2(i2b2XmlWithSpan).get should equal(constraintsWithSpan)
  }

  @Test
  def testFromXml: Unit = {
    import I2b2SubQueryConstraints.fromXml

    fromXml(null).isFailure should be(true)
    fromXml(NodeSeq.Empty).isFailure should be(true)
    fromXml(<nuh/>).isFailure should be(true)

    fromXml(shrineXml).get should equal(constraints)
  }
  
  @Test
  def testFromXmlWithSpan: Unit = {
    import I2b2SubQueryConstraints.fromXml

    fromXml(shrineXmlWithSpan).get should equal(constraintsWithSpan)
  }

  @Test
  def testI2b2XmlRoundTrip: Unit = {
    I2b2SubQueryConstraints.fromI2b2(constraints.toI2b2).get should equal(constraints)
  }
  
  @Test
  def testI2b2XmlRoundTripWithSpan: Unit = {
    I2b2SubQueryConstraints.fromI2b2(constraintsWithSpan.toI2b2).get should equal(constraintsWithSpan)
  }

  @Test
  def testShrineXmlRoundTrip: Unit = {
    I2b2SubQueryConstraints.fromXml(constraints.toXml).get should equal(constraints)
  }
  
  @Test
  def testShrineXmlRoundTripWithSpan: Unit = {
    I2b2SubQueryConstraints.fromXml(constraintsWithSpan.toXml).get should equal(constraintsWithSpan)
  }
}
package net.shrine.protocol.i2b2.query

import net.shrine.util.ShouldMatchersForJUnit
import net.shrine.xml.XmlUtil
import org.junit.Test

import scala.xml.NodeSeq

/**
 * @author clint
 * @date Sep 26, 2014
 */
final class I2b2SubQueryConstraintTest extends ShouldMatchersForJUnit {
  private val constraint = I2b2SubQueryConstraint("foo", "bar", "baz")

  private val i2b2Xml: NodeSeq = XmlUtil.stripWhitespace {
    <placeholder>
      <query_id>foo</query_id>
      <join_column>bar</join_column>
      <aggregate_operator>baz</aggregate_operator>
    </placeholder>
  }
  
  private def shrineXml(idx: Int = 0): NodeSeq = XmlUtil.stripWhitespace {
    <i2b2SubQueryConstraint index={ idx.toString }>
      <id>foo</id>
      <joinColumn>bar</joinColumn>
      <aggregateOperator>baz</aggregateOperator>
    </i2b2SubQueryConstraint>
  }

  @Test
  def testToI2b2: Unit = {
    constraint.toI2b2 should equal(i2b2Xml)
  }
  
  @Test
  def testToXml: Unit = {
    constraint.toXml(0) should equal(shrineXml(0))
    constraint.toXml(1) should equal(shrineXml(1))
    constraint.toXml(42) should equal(shrineXml(42))
  }

  @Test
  def testFromI2b2: Unit = {
    import I2b2SubQueryConstraint.fromI2b2
    
    fromI2b2(null).isFailure should be(true)
    fromI2b2(NodeSeq.Empty).isFailure should be(true)
    fromI2b2(<nuh/>).isFailure should be(true)
    
    fromI2b2(i2b2Xml).get should equal(constraint)
  }
  
  @Test
  def testFromXml: Unit = {
    import I2b2SubQueryConstraint.fromXml
    
    fromXml(null).isFailure should be(true)
    fromXml(NodeSeq.Empty).isFailure should be(true)
    fromXml(<nuh/>).isFailure should be(true)
    
    fromXml(shrineXml(0)).get should equal(constraint)
    fromXml(shrineXml(1)).get should equal(constraint)
    fromXml(shrineXml(42)).get should equal(constraint)
  }
  
  @Test
  def testI2b2XmlRoundTrip: Unit = {
    I2b2SubQueryConstraint.fromI2b2(constraint.toI2b2).get should equal(constraint)
  }
  
  @Test
  def testShrineXmlRoundTrip: Unit = {
    I2b2SubQueryConstraint.fromXml(constraint.toXml(0)).get should equal(constraint)
    I2b2SubQueryConstraint.fromXml(constraint.toXml(1)).get should equal(constraint)
    I2b2SubQueryConstraint.fromXml(constraint.toXml(42)).get should equal(constraint)
  }
}
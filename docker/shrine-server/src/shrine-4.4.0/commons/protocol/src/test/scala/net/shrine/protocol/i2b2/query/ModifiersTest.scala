package net.shrine.protocol.i2b2.query

import net.shrine.util.ShouldMatchersForJUnit
import net.shrine.xml.XmlUtil
import org.junit.Test

/**
 * @author clint
 * @since Jul 23, 2014
 */
final class ModifiersTest extends ShouldMatchersForJUnit {
  private val name = "Principal Diagnosis"
  private val appliedPath = """\i2b2\Diagnoses\Circulatory system (390-459)\%"""
  private val k = """\\i2b2_DIAG\Principal Diagnosis\"""

  private val xml = XmlUtil.stripWhitespace {
    <constrain_by_modifier>
      <modifier_name>{ name }</modifier_name>
      <applied_path>{ appliedPath }</applied_path>
      <modifier_key>{ k }</modifier_key>
    </constrain_by_modifier>
  }
  
  private val shrineXml = XmlUtil.stripWhitespace {
    <modifier>
      <name>{ name }</name>
      <appliedPath>{ appliedPath }</appliedPath>
      <key>{ k }</key>
    </modifier>
  }
  
  private val json = """{"name":"Principal Diagnosis","appliedPath":"\\i2b2\\Diagnoses\\Circulatory system (390-459)\\%","key":"\\\\i2b2_DIAG\\Principal Diagnosis\\"}"""

  private val modifiers = Modifiers(name, appliedPath, k)

  @Test
  def testToI2b2: Unit = {
    modifiers.toI2b2 should equal(xml)
  }
  
  @Test
  def testToXml: Unit = {
    modifiers.toXml should equal(shrineXml)
  }

  @Test
  def testFromI2b2: Unit = {
    Modifiers.fromI2b2(<foo/>).isFailure should be(true)
    Modifiers.fromI2b2(null).isFailure should be(true)

    Modifiers.fromI2b2(xml).get should equal(modifiers)
  }
  
  @Test
  def testFromXml: Unit = {
    Modifiers.fromXml(<foo/>).isFailure should be(true)
    Modifiers.fromXml(null).isFailure should be(true)

    Modifiers.fromXml(shrineXml).get should equal(modifiers)
  }

  @Test
  def testI2b2XmlRoundTrip: Unit = {
    Modifiers.fromI2b2(modifiers.toI2b2).get should equal(modifiers)
  }
  
  @Test
  def testShrineXmlRoundTrip: Unit = {
    Modifiers.fromXml(modifiers.toXml).get should equal(modifiers)
  }
}
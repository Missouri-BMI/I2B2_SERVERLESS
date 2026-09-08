package net.shrine.protocol.i2b2

import net.shrine.util.ShouldMatchersForJUnit
import org.junit.Test

/**
 * @author clint
 * @date Oct 24, 2014
 */
final class DefaultBreakdownResultOutputTypesTest extends ShouldMatchersForJUnit {
  import DefaultBreakdownResultOutputTypes._
  
  @Test
  def testBreakdownFlag: Unit =  {
    PATIENT_AGE_COUNT_XML.isBreakdown should be(true)
    PATIENT_RACE_COUNT_XML.isBreakdown should be(true)
    PATIENT_VITALSTATUS_COUNT_XML.isBreakdown should be(true)
    PATIENT_GENDER_COUNT_XML.isBreakdown should be(true)
  }

  @Test
  def testIsError: Unit =  {
    PATIENT_AGE_COUNT_XML.isError should be(false)
    PATIENT_RACE_COUNT_XML.isError should be(false)
    PATIENT_VITALSTATUS_COUNT_XML.isError should be(false)
    PATIENT_GENDER_COUNT_XML.isError should be(false)
  }

  @Test
  def testValues: Unit =  {
    values.toSet should equal(Set(
      PATIENT_AGE_COUNT_XML,
      PATIENT_RACE_COUNT_XML,
      PATIENT_VITALSTATUS_COUNT_XML,
      PATIENT_GENDER_COUNT_XML))
  }
}
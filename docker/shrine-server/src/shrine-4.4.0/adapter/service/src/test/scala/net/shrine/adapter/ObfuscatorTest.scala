package net.shrine.adapter

import org.junit.Test
import net.shrine.protocol.i2b2.QueryResult
import net.shrine.protocol.i2b2.ResultOutputType
import net.shrine.protocol.i2b2.I2b2Result
import net.shrine.util.ShouldMatchersForJUnit
import net.shrine.protocol.i2b2.DefaultBreakdownResultOutputTypes
import net.shrine.protocol.version.v2.ObfuscatingParameters
import org.scalatest.Assertion

/**
 * @author clint
 * @since Oct 22, 2012
 */

object ObfuscatorTest {
  private def within(range: Long)(a: Long, b: Long) = scala.math.abs(b - a) <= range
  def within3: (Long, Long) => Boolean = within(3)
}

final class ObfuscatorTest extends ShouldMatchersForJUnit {



  def assertWithin(range:Long)(a:Long, b:Long): Assertion = {
    assert(ObfuscatorTest.within(range)(a,b),s"$b was not within $range of $a")
  }

  def assertWithin3: (Long, Long) => Assertion = assertWithin(3)
  def assertWithin1: (Long, Long) => Assertion = assertWithin(1)


  def assertWithinOrLessThanClamp(range:Long)(a:Long, b:Long): Assertion = {
    assert(ObfuscatorTest.within(range)(a, b) || b == Obfuscator.LESS_THAN_LOW_LIMIT,
      s"$b was not within $range of $a, and $b is not ${Obfuscator.LESS_THAN_LOW_LIMIT}")
  }

  val obfuscator13: Obfuscator = Obfuscator(ObfuscatingParameters(1,1.3,3,10))
  val obfuscator65: Obfuscator = Obfuscator(ObfuscatingParameters(binSize = 5,stdDev = 6.5,noiseClamp = 10,lowLimit = 10))

  @Test
  def testObfuscateLong():Unit = {
    val l = 12345L

    val obfuscated = obfuscator13.obfuscate(l)

    assertWithin3(l, obfuscated)
  }

  @Test
  def testObfuscateAlreadyObfuscated():Unit = {
    val l = Obfuscator.LESS_THAN_LOW_LIMIT

    val obfuscated = obfuscator65.obfuscate(l)

    obfuscated should be (Obfuscator.LESS_THAN_LOW_LIMIT)
  }


  @Test
  def testObfuscateZero():Unit = {
    val l = 0L

    val obfuscated = obfuscator65.obfuscate(l)

    obfuscated should be (Obfuscator.LESS_THAN_LOW_LIMIT)
  }

  @Test
  def testObfuscateVerySmall():Unit = {
    val l = 3L

    val obfuscated = obfuscator65.obfuscate(l)

    obfuscated should be (Obfuscator.LESS_THAN_LOW_LIMIT)
  }

  @Test
  def testObfuscatePrettySmall():Unit = {
    val l = 7L

    val obfuscated = obfuscator65.obfuscate(l)

    obfuscated should be (Obfuscator.LESS_THAN_LOW_LIMIT)
  }

  @Test
  def testObfuscateSmallish():Unit = {
    val l = 11L

    val obfuscated = obfuscator65.obfuscate(l)

    assertWithinOrLessThanClamp(10L)(l,obfuscated)
  }

  @Test
  def testObfuscateUseful():Unit = {
    val l = 203L

    val obfuscated = obfuscator65.obfuscate(l)

    assertWithin(15L)(l,obfuscated)
  }


  @Test
  def testObfuscateQueryResult():Unit = {
    import DefaultBreakdownResultOutputTypes._
    import ResultOutputType._

    val breakdowns = Map(
        PATIENT_AGE_COUNT_XML -> I2b2Result(PATIENT_AGE_COUNT_XML, Map("x" -> 1, "y" -> 42)),
    		PATIENT_GENDER_COUNT_XML -> I2b2Result(PATIENT_GENDER_COUNT_XML, Map("a" -> 123, "b" -> 456)))

    def queryResult(resultId: Long, setSize: Long) = QueryResult(
      resultId = resultId,
      instanceId = 123L,
      resultType = Some(PATIENT_COUNT_XML),
      setSize = setSize,
      startDate = None,
      endDate = None,
      description = None,
      statusType = QueryResult.StatusType.Finished,
      statusMessage = None,
      breakdowns = breakdowns
    )

    val resultId1 = 12345L

    val setSize1 = 123L

    //No breakdowns
    {
      val noBreakdowns = queryResult(resultId1, setSize1).copy(breakdowns = Map.empty)

      noBreakdowns.setSize should equal(setSize1)
      noBreakdowns.breakdowns should equal(Map.empty)

      val obfuscated = obfuscator13.obfuscate(noBreakdowns)

      assertWithin3(noBreakdowns.setSize, obfuscated.setSize)
      obfuscated.breakdowns should equal(Map.empty)
    }

    //breakdowns
    {
      val QueryResult(_, _, _, obfscSetSize1, _, _, _, _, _, _, obfscBreakdowns) = obfuscator13.obfuscate(queryResult(resultId1, setSize1))

      assertWithin3(setSize1, obfscSetSize1)
      
      breakdowns.keySet should equal(obfscBreakdowns.keySet)
      
      for {
        (resultType, obfscEnv) <- obfscBreakdowns
        env <- breakdowns.get(resultType) 
      } {
        env.data.keySet should equal(obfscEnv.data.keySet)
        
        for {
          (key, value) <- env.data
          obfscValue <- obfscEnv.data.get(key)
        } {
          assertWithin3(value, obfscValue)
        }
      }
    }
  }
}
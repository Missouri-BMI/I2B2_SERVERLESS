package net.shrine.adapter.dao.model

import net.shrine.util.ShouldMatchersForJUnit
import org.junit.Test
import net.shrine.protocol.i2b2.ResultOutputType
import net.shrine.protocol.i2b2.DefaultBreakdownResultOutputTypes

/**
 * @author clint
 * @date Nov 1, 2012
 */
final class BreakdownTest extends ShouldMatchersForJUnit {
  import ResultOutputType._
  
  @Test
  def testFromRows():Unit = {
    DefaultBreakdownResultOutputTypes.values.foreach { resultType =>
      Breakdown.fromRows(resultType, 123L, Seq.empty) should be(None)
    }
    
    ResultOutputType.nonBreakdownTypes.foreach { resultType =>
      intercept[Exception] {
        Breakdown.fromRows(resultType, 456L, Seq.empty)
      }
    }

    //TODO: test handling rows with different resultIds
    
    val rows = Seq(BreakdownResultRow(1, 123, "foo", 43L), BreakdownResultRow(2, 123, "bar", 100L))
    val localResultId = 409836L
    
    DefaultBreakdownResultOutputTypes.values.foreach { resultType =>
      val Some(breakdown) = Breakdown.fromRows(resultType, localResultId, rows)
      
      breakdown.localId should equal(localResultId)
      breakdown.resultId should equal(123)
      breakdown.resultType should equal(resultType)
      breakdown.data should equal(Map("foo" -> 43, "bar" -> 100))
    }
  }
}
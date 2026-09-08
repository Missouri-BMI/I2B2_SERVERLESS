package net.shrine.adapter.dao.model

import net.shrine.util.ShouldMatchersForJUnit
import org.junit.Test
import net.shrine.protocol.i2b2.ResultOutputType
import net.shrine.protocol.i2b2.QueryResult
import net.shrine.xml.{XmlDateHelper, XmlGcEnrichments}

/**
 * @author clint
 * @date Nov 1, 2012
 */
final class CountTest extends ShouldMatchersForJUnit {
  val now = XmlDateHelper.now

  @Test
  def testToQueryResult():Unit = {
    val resultId = 123
    val localResultId = 99999L
    val orig = 42
    val obfsc = 43

    val startDate = now
    val endDate = {
      import XmlGcEnrichments._
      import scala.concurrent.duration.DurationLong

      now + 100L.milliseconds
    }

    Count(1, resultId, localResultId, QueryResult.StatusType.Processing, now, None).toQueryResult should be(None)

    val Some(queryResult) = {
      Count(1, resultId, localResultId, QueryResult.StatusType.Processing, now, Some(CountData(obfsc, startDate, endDate))).toQueryResult
    }

    queryResult.breakdowns.isEmpty should be(true)
    queryResult.description should be(None)
    queryResult.startDate should be(Some(startDate))
    queryResult.endDate should be(Some(endDate))
    queryResult.instanceId should equal(resultId)
    queryResult.isError should be(false)
    queryResult.resultId should equal(localResultId)
    queryResult.resultType should equal(Some(ResultOutputType.PATIENT_COUNT_XML))
    queryResult.setSize should equal(obfsc)
    queryResult.statusMessage should be(None)
    queryResult.statusType should equal(QueryResult.StatusType.Processing)
  }

  import ResultOutputType.PATIENT_COUNT_XML
  import QueryResult.StatusType.{ Finished, Queued, Processing }
  
  @Test
  def testFromRows():Unit = {
    doTestFromRows(Finished)
  }
  
  @Test
  def testFromRowsProcessing():Unit = {
    doTestFromRows(Processing)
  }
  
  @Test
  def testFromRowsQueued():Unit = {
    doTestFromRows(Queued)
  }

  private def doTestFromRows(status: QueryResult.StatusType):Unit = {
    val countRow = CountRow(123, 456, 20L, now)

    val localResultId = 789L

    val elapsed = 100L
    
    val resultRow = QueryResultRow(987, 789L, 1, PATIENT_COUNT_XML, status, Some(elapsed), now)
    
    val count = Count.fromRows(resultRow, Option(countRow))

    count should not be (null)
    count.creationDate should equal(countRow.creationDate)
    count.id should equal(countRow.id)
    count.localId should equal(resultRow.localId)
    count.resultId should equal(countRow.resultId)

    if (resultRow.status.isDone) {
      val Some(countData) = count.data

      countData.obfuscatedValue should equal(countRow.obfuscatedValue)

      countData.startDate should equal(countRow.creationDate)

      import XmlGcEnrichments._
      import scala.concurrent.duration.DurationLong

      countData.endDate should equal(countRow.creationDate + resultRow.elapsed.get.milliseconds)
    } else {
      count.data should be(None)
    }

  }
}
package net.shrine.adapter.dao.model

import net.shrine.problem.{TestProblem, XmlProblemDigest}
import net.shrine.util.ShouldMatchersForJUnit
import org.junit.Test
import net.shrine.protocol.i2b2.ResultOutputType
import net.shrine.protocol.i2b2.QueryResult.StatusType
import net.shrine.protocol.i2b2.I2b2Result
import net.shrine.protocol.i2b2.DefaultBreakdownResultOutputTypes
import net.shrine.protocol.i2b2.query.{I2b2QueryDefinition, Term}
import net.shrine.xml.XmlDateHelper

/**
 * @author clint
 * @since Nov 1, 2012
 */
final class ShrineQueryResultTest extends ShouldMatchersForJUnit {
  import ResultOutputType._
  import DefaultBreakdownResultOutputTypes._

  private val queryId = 999

  private def someId = scala.util.Random.nextInt()

  private def queryResultRow(resultType: ResultOutputType) = QueryResultRow(
    someId,
    someId.toLong,
    queryId,
    resultType,
    if (resultType.isError) StatusType.Error else StatusType.Finished,
    if (resultType.isError) None else Some(100),
    XmlDateHelper.now)

  private val countQueryResultRow = queryResultRow(PATIENT_COUNT_XML)

  private val breakdownQueryResultRow1 = queryResultRow(PATIENT_AGE_COUNT_XML)

  private val breakdownQueryResultRow2 = queryResultRow(PATIENT_GENDER_COUNT_XML)

  private val errorQueryResultRow1 = queryResultRow(ERROR)

  private val errorQueryResultRow2 = queryResultRow(ERROR)

  private val queryName = "some-query"
  private val queryExpr = Term("foo", "fooName")
  private val queryDefinition = I2b2QueryDefinition(queryName,queryExpr)
  private val queryRow = ShrineQuery(123, "48573498739845", 392874L, queryName, "some-user", "some-domain", XmlDateHelper.now, queryDefinition = queryDefinition)
  
  private val resultRows = Seq(countQueryResultRow, breakdownQueryResultRow1, breakdownQueryResultRow2, errorQueryResultRow1, errorQueryResultRow2)

  private val countRow = Option(CountRow(someId, countQueryResultRow.id, 100, XmlDateHelper.now))

  private val breakdownRows = Map(
    PATIENT_AGE_COUNT_XML -> Seq(BreakdownResultRow(someId, breakdownQueryResultRow1.id, "x", 2), BreakdownResultRow(someId, breakdownQueryResultRow1.id, "y", 3)),
    PATIENT_GENDER_COUNT_XML -> Seq(BreakdownResultRow(someId, breakdownQueryResultRow2.id, "a", 10), BreakdownResultRow(someId, breakdownQueryResultRow2.id, "b", 11)))

  private val pd = XmlProblemDigest.create(TestProblem())

  private val errorRows = Seq(ShrineError(someId, errorQueryResultRow1.id, "foo", pd.codec,pd.stampText,pd.summary,pd.description,pd.detailsXml), ShrineError(someId, errorQueryResultRow2.id, "bar", pd.codec,pd.stampText,pd.summary,pd.description,pd.detailsXml))

  @Test
  def testToQueryResults():Unit = {
    val Some(shrineQueryResult) = ShrineQueryResult.fromRows(queryRow, resultRows, countRow, breakdownRows, errorRows)


    val expected = Count.fromRows(countQueryResultRow, countRow).toQueryResult.map(_.withBreakdowns(Map(
      PATIENT_AGE_COUNT_XML -> I2b2Result(PATIENT_AGE_COUNT_XML, Map("x" -> 2, "y" -> 3)),
      PATIENT_GENDER_COUNT_XML -> I2b2Result(PATIENT_GENDER_COUNT_XML, Map("a" -> 10, "b" -> 11)))
    ))

    shrineQueryResult.toQueryResults(false) should equal(expected)
  }

  @Test
  def testFromRows():Unit = {
    ShrineQueryResult.fromRows(null, Nil, countRow, breakdownRows, errorRows) should be(None)

    val Some(shrineQueryResult) = ShrineQueryResult.fromRows(queryRow, resultRows, countRow, breakdownRows, errorRows)

    shrineQueryResult.count should equal(Count.fromRows(countQueryResultRow, countRow))
    shrineQueryResult.errors should equal(errorRows)
    shrineQueryResult.breakdowns.toSet should equal(Set(
   		Breakdown(breakdownQueryResultRow1.id, breakdownQueryResultRow1.localId, PATIENT_AGE_COUNT_XML, Map("x" -> 2L, "y" -> 3L)),
   		Breakdown(breakdownQueryResultRow2.id, breakdownQueryResultRow2.localId, PATIENT_GENDER_COUNT_XML, Map("a" -> 10L, "b" -> 11L))))
  }
}
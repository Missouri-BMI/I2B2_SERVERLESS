package net.shrine.adapter.dao.model

import net.shrine.problem.{TestProblem, XmlProblemDigest}
import net.shrine.util.ShouldMatchersForJUnit
import net.shrine.protocol.i2b2.QueryResult

/**
 * @author clint
 * @since Nov 1, 2012
 */
final class ShrineErrorTest extends ShouldMatchersForJUnit {
  def testToQueryResult():Unit = {
    val message = "something broke"
    val testProblem = TestProblem()
    val tpd = XmlProblemDigest.create(testProblem)
    val error = ShrineError(1, 123, message,tpd.codec,tpd.stampText,tpd.summary,tpd.description,tpd.detailsXml)
    
    error.toQueryResult should equal(QueryResult.errorResult(Some(message), QueryResult.StatusType.Error.name,testProblem))
  }
}
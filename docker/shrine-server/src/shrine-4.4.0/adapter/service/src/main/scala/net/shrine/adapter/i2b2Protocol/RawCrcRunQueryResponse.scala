package net.shrine.adapter.i2b2Protocol

import net.shrine.adapter.i2b2Protocol
import net.shrine.protocol.i2b2.query.I2b2QueryDefinition
import net.shrine.protocol.i2b2.{QueryResult, ResultOutputType}

import javax.xml.datatype.XMLGregorianCalendar

/**
 * @author clint
 * @since Nov 30, 2012
 */
final case class RawCrcRunQueryResponse(
                                         override val queryId: Long,
                                         override val createDate: XMLGregorianCalendar,
                                         override val userId: String,
                                         override val groupId: String,
                                         override val requestXml: I2b2QueryDefinition,
                                         override val queryInstanceId: Long,
                                         singleNodeResults: Map[ResultOutputType, Seq[QueryResult]]) extends AbstractRunQueryResponse("rawCrcRunQueryResponse", queryId, createDate, userId, groupId, requestXml, queryInstanceId) {

  override def results: Seq[QueryResult] = singleNodeResults.values.flatten.toSeq
  
  //NB: Will fail loudly if no PATIENT_COUNT_XML or ERROR QueryResults are present; PATIENT_COUNT_XML results take priority
  def toRunQueryResponse: RunQueryResponse = {
    import ResultOutputType.{ERROR, PATIENT_COUNT_XML}
    
    val queryResult = {
      val relevantQueryResults = singleNodeResults.get(PATIENT_COUNT_XML) orElse singleNodeResults.get(ERROR)
      
      relevantQueryResults.get.head
    }
    
    i2b2Protocol.RunQueryResponse(queryId, createDate, userId, groupId, requestXml, queryInstanceId, queryResult)
  }

  import RawCrcRunQueryResponse._

  //todo only used in SquerylAdapterDaoTest
  def withResults(results: Iterable[QueryResult]): RawCrcRunQueryResponse = this.copy(singleNodeResults = toQueryResultMap(results))
}

object RawCrcRunQueryResponse extends AbstractRunQueryResponse.Companion[RawCrcRunQueryResponse] {
  import ResultOutputType._
  
  def toQueryResultMap(results: Iterable[QueryResult]): Map[ResultOutputType, Seq[QueryResult]] = {
    results.groupBy(_.resultType.getOrElse(ERROR)).view.mapValues(_.toSeq).toMap
  }
}
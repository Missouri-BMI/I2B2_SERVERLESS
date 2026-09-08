package net.shrine.adapter.dao.model

import javax.xml.datatype.XMLGregorianCalendar
import net.shrine.protocol.i2b2.ResultOutputType
import net.shrine.protocol.i2b2.QueryResult
import net.shrine.protocol.i2b2.I2b2Result


/**
 * @author clint
 * @since Oct 16, 2012
 *
 * NB: Named ShrineQueryResult to avoid clashes with net.shrine.protocol.QueryResult
 */
final case class ShrineQueryResult(
  networkQueryId: Long,
  localId: String,
  dateCreated: XMLGregorianCalendar,
  wasRun: Boolean,
  count: Count,
  breakdowns: Seq[Breakdown],
  errors: Seq[ShrineError]) {
  
  val isDone: Boolean = count.statusType.isDone
  
  def wasNotRun: Boolean = !wasRun

  //todo only used in dubious test code
  def toQueryResults(doObfuscation: Boolean): Option[QueryResult] = {
    val countResult = count.toQueryResult.map { countQueryResult =>
      //add breakdowns
      
      val byType: Map[ResultOutputType, Map[String, Long]] = breakdowns.map(b => b.resultType -> b.data).toMap

      countQueryResult.withBreakdowns(byType.map {
        case (resultType, data) => 
          (resultType, I2b2Result(resultType, data))
      })
    }
    
    def firstError = errors.headOption.map(_.toQueryResult)
    
    countResult orElse firstError
  }
}

object ShrineQueryResult {
  def fromRows(queryRow: ShrineQuery, resultRows: Seq[QueryResultRow], countRowOption: Option[CountRow], breakdownRows: Map[ResultOutputType, Seq[BreakdownResultRow]], errorRows: Seq[ShrineError]): Option[ShrineQueryResult] = {
    if(resultRows.isEmpty) {
      None
    } else {
      val resultRowsByType = resultRows.map(r => r.resultType -> r).toMap
      
      val breakdowns = (for {
        (resultType, resultRow) <- resultRowsByType
        rows <- breakdownRows.get(resultType)
        breakdown <- Breakdown.fromRows(resultType, resultRow.localId, rows)
      } yield breakdown).toSeq

      import ResultOutputType.PATIENT_COUNT_XML

      //todo why is this a for comprehension?
      for {
        resultRow <- resultRowsByType.get(PATIENT_COUNT_XML)
        count = Count.fromRows(resultRow, countRowOption)
      } yield ShrineQueryResult(
          networkQueryId = queryRow.networkId,
          localId = queryRow.i2b2MasterQueryId,
          dateCreated = queryRow.dateCreated,
          wasRun = true, //if there are result rows then the query has at least been shown to I2B2
          count = Count.fromRows(resultRow, countRowOption),//todo should probably be (the unused) count from above
          breakdowns = breakdowns,
          errors = errorRows
        )
    }
  }
}
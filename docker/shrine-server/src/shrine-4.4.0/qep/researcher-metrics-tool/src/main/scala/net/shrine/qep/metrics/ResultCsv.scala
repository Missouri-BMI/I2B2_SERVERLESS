package net.shrine.qep.metrics

import com.opencsv.CSVWriter
import net.shrine.audit.{AdapterName, LongQueryId}
import net.shrine.csv.Column
import net.shrine.protocol.version.v2.ResultStatus
import net.shrine.protocol.version.{DateStamp, NodeKey, NodeName, ResultId, UserName}
import net.shrine.qep.querydb.{FullQueryResult, QepQuery, QepQueryDb}

import java.io.File

object ResultCsv {

  def writeResultsCsv(file:File,startDate:DateStamp,endDate:DateStamp,researcher:Option[UserName]):Unit = CommonMaps.writeCsv (file,{ csvWriter: CSVWriter =>
    import cats.effect.unsafe.implicits.global
    val queriesHistory: Seq[QepQuery] = QepQueryDb.db.selectPreviousQueriesHistoryIO(startDate, endDate, researcher.map(_.underlying)).unsafeRunSync()

    val queryIdToHistory: Map[LongQueryId, Seq[QepQuery]] = queriesHistory.groupBy(_.networkId).map { qh => qh._1 -> qh._2.sortBy(_.changeDate) }

    val queryIds: Seq[LongQueryId] = queryIdToHistory.values.map(_.head).toSeq.sortBy(_.dateCreated).map(_.networkId)

    csvWriter.writeNext(Column.header(ResultRow.columns))
    queryIds.foreach { queryId =>
      val queryHistory: Seq[QepQuery] = queryIdToHistory(queryId)
      val resultHistory: Map[AdapterName, Seq[FullQueryResult]] = QepQueryDb.db.selectResultHistoryForQueryIO(queryId).unsafeRunSync()

      val latestResults = resultHistory.map{adapterNameToResults =>
        adapterNameToResults._2.maxBy(_.changeDate)
      }.toSeq
      val queryRow = QueryRow.fromQepDb(queryHistory,latestResults)

      resultHistory.keys.toSeq.sorted.foreach{adapterName =>
        val resultRow = ResultRow.fromQepDb(queryRow,resultHistory(adapterName))
        csvWriter.writeNext(Column.rowForColumns(ResultRow.columns)(resultRow))
      }
    }
  })
}

case class ResultRow(
                      id:ResultId,
                      queryRow: QueryRow,
                      adapterNodeName:NodeName,
                      status:ResultStatus,
                      count:Int,
                      errorCodec: String,
                      errorSummary: String,
                      createDate:DateStamp,
                      resultStatesToTimeInState: Map[ResultStatus, Long]
                    )

object ResultRow {

  val columns: Array[Column[ResultRow]] = {

    val ignoreStatuses: Set[ResultStatus] = Set(
      ResultStatus.QueuedByAdapter, //todo not yet reported to the hub
      ResultStatus.QueuedForManualSubmission, //todo if we ever support this feature add this state back
      ResultStatus.UnknownWhileQueuedByCRC,
      ResultStatus.UnknownInTransit,
      ResultStatus.UnknownFinal,
      ResultStatus.ReadyToSubmit, //todo not yet recorded at the hub
    ) ++ ResultStatus.statuses.filter(_.isFinal)

    val statusOrder: Seq[ResultStatus] = ResultStatus.statuses.filterNot(ignoreStatuses.contains)

    val timeInStateColumns = statusOrder.map(x => Column[ResultRow](s"Time in ${x.statusName} (ms)",
      rr => rr.resultStatesToTimeInState.get(x).fold("")(_.toString)))

    Array[Column[ResultRow]](
      Column("Query Id", _.queryRow.query.id.excelSafeString),
      Column("Query Name", _.queryRow.query.queryName),
      Column("Researcher User Name", _.queryRow.researcherUserName.underlying),
      Column("Researcher Authentication Domain", _.queryRow.researcherDomainName.underlying),
      Column("Result Id", _.id.excelSafeString),
      Column("Adapter Node Name", _.adapterNodeName.underlying),
      Column("Status", _.status.statusName),
      Column("Count", _.count.toString),
      Column("Error Codec", _.errorCodec),
      Column("Error Summary", _.errorSummary),
      Column("Create Date", _.createDate.toDateString),
    ) ++ timeInStateColumns
  }

  def fromQepDb(
             queryRow: QueryRow,
             resultHistory: Seq[FullQueryResult]
           ): ResultRow = {
    val latestResult = resultHistory.last
    val resultStatesAndTimes = resultHistory.map{ r: FullQueryResult => r.status -> r.changeDate }.sortBy(_._2)
    val zippedResultStatesAndDateStamps: Seq[((String, LongQueryId), (String, LongQueryId))] =
      resultStatesAndTimes.zip(resultStatesAndTimes.tail)
    val resultStatesToTimeInState: Map[ResultStatus, LongQueryId] = zippedResultStatesAndDateStamps.map { zippedRsDs =>
      ResultStatus.namesToStatuses(zippedRsDs._2._1) -> (zippedRsDs._2._2 - zippedRsDs._1._2)
    }.toMap

    ResultRow(
      id = new ResultId(latestResult.resultId),
      queryRow = queryRow,
      adapterNodeName = new NodeName(latestResult.adapterNode),
      status = ResultStatus.namesToStatuses(latestResult.status),
      count = if ((ResultStatus.namesToStatuses(latestResult.status) == ResultStatus.ResultFromCRC) && (latestResult.count != -1)) latestResult.count.toInt
              else 0 ,
      errorCodec = latestResult.problemDigest.map(_.codec).getOrElse(""),
      errorSummary = latestResult.problemDigest.map(_.summary).getOrElse(""),
      createDate = new DateStamp(resultHistory.head.changeDate),
      resultStatesToTimeInState = resultStatesToTimeInState,
    )
  }
}
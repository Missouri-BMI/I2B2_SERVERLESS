package net.shrine.hub.metrics

import com.opencsv.CSVWriter
import net.shrine.csv.Column
import net.shrine.hub.data.store.HubDb
import net.shrine.protocol.version.v2.{CountResult, ErrorResult, Node, Result, ResultStatus}
import net.shrine.protocol.version.{DateStamp, NodeId, NodeKey, NodeName, QueryId, ResultId, UserName}

import java.io.File

object ResultCsv {
  import cats.effect.unsafe.implicits.global
  def writeResultsCsv(file:File,startDate:DateStamp,endDate:DateStamp,researcher:Option[UserName]):Unit = CommonMaps.writeCsv (file,{ csvWriter: CSVWriter =>
    val queryIds: Seq[QueryId] = researcher.fold(
      HubDb.db.selectQueryIdsIO(startDate, endDate)
    ) { r =>
      val researcherId = CommonMaps.researcherUserNameToResearchers(r).map(_.id)
      HubDb.db.selectQueryIdsForResearcherIdsIO(startDate, endDate, researcherId)
    }.unsafeRunSync()

    csvWriter.writeNext(Column.header(ResultRow.columns))
    queryIds.foreach{queryId =>
      val queryHistory = HubDb.db.selectQueryHistoryIO(queryId).unsafeRunSync()
      val latestResults = HubDb.db.selectMostRecentResultsForQueryIO(queryId).unsafeRunSync()
      val resultHistories = HubDb.db.selectResultHistoryIO(queryId).unsafeRunSync()

      val queryRow = QueryRow(queryHistory,latestResults)
      val resultHistoriesByNode: Seq[(NodeId, Seq[Result])] = resultHistories.groupBy(_.adapterNodeId).
        toSeq.sortBy { r =>
        CommonMaps.nodeIdToNode(r._2.head.adapterNodeId).name.underlying
      }

      val resultRows = resultHistoriesByNode.map { nodeIdToResults: (NodeId, Seq[Result]) =>
        ResultRow(queryRow, nodeIdToResults._2)
      }
      resultRows.foreach { row => csvWriter.writeNext(Column.rowForColumns(ResultRow.columns)(row)) }
    }
  })
}

case class ResultRow(
                      id:ResultId,
                      queryRow: QueryRow,
                      adapterNodeShortName:NodeKey,
                      adapterNodeName:NodeName,
                      status:ResultStatus,
                      count:Int,
                      errorCodec: String,
                      errorSummary: String,
                      createDate:DateStamp,
                      resultStatesToTimeInState: Map[ResultStatus, Long]
                    )

object ResultRow {

  val columns:Array[Column[ResultRow]] = {

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
      Column("Query Id",_.queryRow.query.id.excelSafeString),
      Column("Query Name",_.queryRow.query.queryName),
      Column("Researcher User Name",_.queryRow.researcherUserName.underlying),
      Column("Researcher Authentication Domain",_.queryRow.researcherDomainName.underlying),
      Column("Result Id",_.id.excelSafeString),
      Column("Adapter Node Key",_.adapterNodeShortName.underlying),
      Column("Adapter Node Name",_.adapterNodeName.underlying),
      Column("Status",_.status.statusName),
      Column("Count",_.count.toString),
      Column("Error Codec",_.errorCodec),
      Column("Error Summary",_.errorSummary),
      Column("Create Date",_.createDate.toDateString),
    ) ++ timeInStateColumns

  }

  def apply(
             queryRow: QueryRow,
             resultHistory: Seq[Result]
           ): ResultRow = {
    val latestResult = resultHistory.head
    val node: Node = CommonMaps.nodeIdToNode(latestResult.adapterNodeId)
    val resultStatesAndTimes = resultHistory.map { r: Result => r.status -> r.versionInfo.changeDate }.sortBy(_._2.underlying)
    val zippedResultStatesAndDateStamps: Seq[((ResultStatus, DateStamp), (ResultStatus, DateStamp))] =
      resultStatesAndTimes.zip(resultStatesAndTimes.tail)
    val resultStatesToTimeInState: Map[ResultStatus, Long] = zippedResultStatesAndDateStamps.map{ zippedRsDs =>
      zippedRsDs._2._1 -> (zippedRsDs._2._2.underlying - zippedRsDs._1._2.underlying)
    }.toMap

    ResultRow(
      id = latestResult.id,
      queryRow = queryRow,
      adapterNodeShortName = node.key,
      adapterNodeName = node.name,
      status = latestResult.status,
      count = latestResult match {
        case countResult: CountResult =>
          if(countResult.count != -1) countResult.count
          else 0
        case _ => 0
      },
      errorCodec = latestResult match {
        case errorResult: ErrorResult => errorResult.problemDigest.codec
        case _ => ""
      },
      errorSummary = latestResult match {
        case errorResult: ErrorResult => errorResult.problemDigest.summary
        case _ => ""
      },
      createDate = latestResult.versionInfo.createDate,
      resultStatesToTimeInState = resultStatesToTimeInState,
    )
  }
}
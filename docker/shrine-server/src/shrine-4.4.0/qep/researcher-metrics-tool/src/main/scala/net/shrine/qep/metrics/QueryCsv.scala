package net.shrine.qep.metrics

import com.opencsv.CSVWriter
import net.shrine.api.ontology.{CodeCategory, OntologyPath}
import net.shrine.audit.LongQueryId
import net.shrine.csv.Column
import net.shrine.protocol.version.{DateStamp, NodeKey, NodeName, QueryId, UserDomainName, UserName}
import net.shrine.protocol.version.v2.{Query, QueryError, QueryStatus, ResultStatus}
import net.shrine.qep.BasicQuery
import net.shrine.qep.querydb.{FullQueryResult, QepQuery, QepQueryDb}

import java.io.File
import scala.xml.XML

object QueryCsv {

  def writeQueryCsv(file:File,startDate:DateStamp,endDate:DateStamp,researcher:Option[UserName]):Unit = CommonMaps.writeCsv (file,{ csvWriter: CSVWriter =>
    import cats.effect.unsafe.implicits.global
    val queriesHistory: Seq[QepQuery] = QepQueryDb.db.selectPreviousQueriesHistoryIO(startDate,endDate,researcher.map(_.underlying)).unsafeRunSync()

    val queryIdToHistory: Map[LongQueryId, Seq[QepQuery]] = queriesHistory.groupBy(_.networkId).map{ qh => qh._1 -> qh._2.sortBy(p => QueryStatus.namesToStatuses(p.status).stage)}

    val queryIdToMostRecentResults: Map[LongQueryId, Seq[FullQueryResult]] = queryIdToHistory.keys.map{ qid =>
      qid -> QepQueryDb.db.selectLatestResultsForQueryIO(qid).unsafeRunSync()
    }.toMap

    val queryIds = queriesHistory.sortBy(_.dateCreated).map(_.networkId).distinct
    csvWriter.writeNext(Column.header(QueryRow.columns))

    queryIds.foreach { qid =>
      val queryHistory: Seq[QepQuery] = queryIdToHistory(qid)
      val latestResults: Seq[FullQueryResult] = queryIdToMostRecentResults(qid)
      val row = QueryRow.fromQepDb(queryHistory, latestResults)
      csvWriter.writeNext(Column.rowForColumns(QueryRow.columns)(row))
    }
  })
}

case class QueryRow (
                      id:QueryId,
                      queryName:String,
                      researcherUserName:UserName,
                      researcherDomainName:UserDomainName,
                      createDate:DateStamp,
                      status:QueryStatus,
                      sitesThatReturnedCounts:Int,
                      totalCount:Int,
                      sitesThatErrored:Int,
                      sitesPending:Int,
                      errorCodec:String,
                      errorSummary:String,
                      queryStatesToTimeInState: Map[QueryStatus, Long],
                      query: Query,
                      queryTerms:Seq[String],
                      queryAsText:String
                    )

object QueryRow{

  val columns: Array[Column[QueryRow]] = {
    val ignoreStatuses: Set[QueryStatus] = Set(
      QueryStatus.UnknownFinal,
      QueryStatus.UnknownInTransit,
      QueryStatus.IdAssigned, //todo not yet recorded at the hub
      QueryStatus.SentToHub, //todo not yet recorded at the hub
    ) ++ QueryStatus.statuses.filter(_.isFinal)

    val statusOrder: Seq[QueryStatus] = QueryStatus.statuses.filterNot(ignoreStatuses.contains)
    val timeInStateColumns = statusOrder.map(x => Column[QueryRow](s"Time in ${x.statusName} (ms)",
      qr => qr.queryStatesToTimeInState.get(x).fold("")(_.toString)))

    Array[Column[QueryRow]](
      Column("Query ID", _.id.excelSafeString),
      Column("Query Name", _.queryName),
      Column("Researcher User Name", _.researcherUserName.underlying),
      Column("Researcher Authentication Domain", _.researcherDomainName.underlying),
      Column("Start Date", _.createDate.toDateString),
      Column("Status", _.status.statusName),
      Column("Sites With Counts", _.sitesThatReturnedCounts.toString),
      Column("Total Patient Count", _.totalCount.toString),
      Column("Sites With Errors", _.sitesThatErrored.toString),
      Column("Sites Still Pending", _.sitesPending.toString),
      Column("Query Error Codec", _.errorCodec),
      Column("Query Error Summary", _.errorSummary),
    ) ++ timeInStateColumns ++ Array[Column[QueryRow]](
      Column("Query Terms", _.queryTerms.mkString(", ")),
      Column("QueryText", _.queryAsText),
      Column("Query Json", _.query.asJsonText.underlying),
    )
  }


  private lazy val codeCategoryMap: Map[OntologyPath, CodeCategory] = Map.empty.withDefaultValue(CodeCategory(""))

  def fromQepDb(queryHistory: Seq[QepQuery], latestResults: Seq[FullQueryResult]): QueryRow = {
    val latestQuery = queryHistory.last

    val countResults = latestResults.filter(_.status == ResultStatus.ResultFromCRC.statusName)
    val errorResults = latestResults.filter(r => r.status == ResultStatus.ErrorInShrine.statusName || r.status == ResultStatus.ErrorFromCrc.statusName)
    val sitesPending = latestResults.size - (errorResults.size + countResults.size)

    val queryStatesAndDateStamps: Seq[(QueryStatus, DateStamp)] =
      queryHistory.map { q: QepQuery => q.v2Query.status -> q.v2Query.versionInfo.changeDate }.sortBy(_._2.underlying)
    val zippedQueryStatesAndDateStamps: Seq[((QueryStatus, DateStamp), (QueryStatus, DateStamp))] =
      queryStatesAndDateStamps.zip(queryStatesAndDateStamps.tail)
    val queryStatesToTimeInState: Map[QueryStatus, Long] = zippedQueryStatesAndDateStamps.map { zippedQsDs =>
      zippedQsDs._2._1 -> (zippedQsDs._2._2.underlying - zippedQsDs._1._2.underlying)
    }.toMap

    val queryTerms = latestQuery.v2Query.queryDefinition.expression.flattenedConcepts
    val queryAsHtml = s"<outerTag>${BasicQuery.fromV2Query(latestQuery.v2Query).htmlQueryText(codeCategoryMap)}</outerTag>"
    val queryAsText = XML.loadString(queryAsHtml).text

    QueryRow(
      id = latestQuery.v2Query.id,
      queryName = latestQuery.queryName,
      researcherUserName = new UserName(latestQuery.userName),
      researcherDomainName = new UserDomainName(latestQuery.userDomain),
      createDate = latestQuery.v2Query.versionInfo.createDate,
      status = latestQuery.v2Query.status,
      errorCodec = latestQuery.v2Query match { //todo does this get updated every time?
        case error: QueryError => error.problemDigest.codec
        case _ => ""
      },
      errorSummary = latestQuery.v2Query match { //todo does this get updated every time?
        case error: QueryError => error.problemDigest.summary
        case _ => ""
      },
      sitesThatReturnedCounts = countResults.size,
      sitesThatErrored = errorResults.size,
      sitesPending = sitesPending,
      totalCount = countResults.map(_.count).filterNot(_ == -1).sum.toInt,
      queryStatesToTimeInState = queryStatesToTimeInState,
      query = latestQuery.v2Query,
      queryTerms = queryTerms.map(_.displayName),
      queryAsText = queryAsText
    )
  }
}

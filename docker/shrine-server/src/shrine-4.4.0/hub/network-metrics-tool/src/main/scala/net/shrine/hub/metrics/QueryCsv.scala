package net.shrine.hub.metrics

import com.opencsv.CSVWriter
import net.shrine.api.ontology.{CodeCategory, OntologyPath}
import net.shrine.csv.Column
import net.shrine.hub.data.store.HubDb
import net.shrine.protocol.version.{DateStamp, NodeKey, NodeName, QueryId, ResearcherId, UserDomainName, UserName}
import net.shrine.protocol.version.v2.{CountResult, ErrorResult, Node, Query, QueryError, QueryStatus, Result}
import net.shrine.qep.BasicQuery

import java.io.File
import scala.xml.XML

object QueryCsv {
  import cats.effect.unsafe.implicits.global
  def writeQueryCsv(file:File,startDate:DateStamp,endDate:DateStamp,researcher:Option[UserName]):Unit = CommonMaps.writeCsv (file,{ csvWriter: CSVWriter =>
    val queryIds: Seq[QueryId] = researcher.fold(
      HubDb.db.selectQueryIdsIO(startDate, endDate)
    ){ r =>
      val researcherIds: Seq[ResearcherId] = CommonMaps.researcherUserNameToResearchers(r).map(_.id)
      HubDb.db.selectQueryIdsForResearcherIdsIO(startDate, endDate, researcherIds)
    }.unsafeRunSync()

    csvWriter.writeNext(Column.header(QueryRow.columns))

    queryIds.foreach { qid =>
      val queryHistory = HubDb.db.selectQueryHistoryIO(qid).unsafeRunSync()
      val latestResults = HubDb.db.selectMostRecentResultsForQueryIO(qid).unsafeRunSync()
      val row = QueryRow(queryHistory, latestResults)
      csvWriter.writeNext(Column.rowForColumns(QueryRow.columns)(row))
    }
  })
}

case class QueryRow (
                      id:QueryId,
                      queryName:String,
                      originShortName:NodeKey,
                      originName:NodeName,
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
      Column("Query ID",_.id.excelSafeString),
      Column("Query Name",_.queryName),
      Column("Origin Node Key",_.originShortName.underlying),
      Column("Origin Node Name",_.originName.underlying),
      Column("Researcher User Name",_.researcherUserName.underlying),
      Column("Researcher Authentication Domain",_.researcherDomainName.underlying),
      Column("Start Date",_.createDate.toDateString),
      Column("Status",_.status.statusName),
      Column("Sites With Counts",_.sitesThatReturnedCounts.toString),
      Column("Total Patient Count",_.totalCount.toString),
      Column("Sites With Errors",_.sitesThatErrored.toString),
      Column("Sites Still Pending",_.sitesPending.toString),
      Column("Query Error Codec",_.errorCodec),
      Column("Query Error Summary",_.errorSummary),
    ) ++ timeInStateColumns ++ Array[Column[QueryRow]](
      Column("Query Terms",_.queryTerms.mkString(", ")),
      Column("QueryText",_.queryAsText),
      Column("Query Json",_.query.asJsonText.underlying),
    )
  }

  private lazy val codeCategoryMap: Map[OntologyPath, CodeCategory] = Map.empty.withDefaultValue(CodeCategory(""))
  def apply(queryHistory: Seq[Query],latestResults:Seq[Result]): QueryRow = {
    val latestQuery = queryHistory.head
    val node: Node = CommonMaps.nodeIdToNode(latestQuery.nodeOfOriginId)
    val researcher = CommonMaps.researcherIdToResearcher(latestQuery.researcherId)
    val countResults: Seq[CountResult] = latestResults.collect{case x:CountResult => x}
    val errorResults = latestResults.collect{case x:ErrorResult => x}
    val sitesPending = latestResults.size - (errorResults.size + countResults.size)

    val queryStatesAndDateStamps: Seq[(QueryStatus, DateStamp)] =
      queryHistory.map { q: Query => q.status -> q.versionInfo.changeDate }.sortBy(_._2.underlying)
    val zippedQueryStatesAndDateStamps: Seq[((QueryStatus, DateStamp), (QueryStatus, DateStamp))] =
      queryStatesAndDateStamps.zip(queryStatesAndDateStamps.tail)
    val queryStatesToTimeInState: Map[QueryStatus, Long] = zippedQueryStatesAndDateStamps.map{ zippedQsDs =>
      zippedQsDs._2._1 -> (zippedQsDs._2._2.underlying - zippedQsDs._1._2.underlying)
    }.toMap

    val queryTerms = latestQuery.queryDefinition.expression.flattenedConcepts
    val queryAsHtml = s"<outerTag>${BasicQuery.fromV2Query(latestQuery).htmlQueryText(codeCategoryMap)}</outerTag>"
    val queryAsText = XML.loadString(queryAsHtml).text

    QueryRow(
      id = latestQuery.id,
      queryName = latestQuery.queryName,
      originShortName = node.key,
      originName = node.name,
      researcherUserName = researcher.userName,
      researcherDomainName = researcher.userDomainName,
      createDate = latestQuery.versionInfo.createDate,
      status = latestQuery.status,
      errorCodec = latestQuery match {
        case error: QueryError => error.problemDigest.codec
        case _ => ""
      },
      errorSummary = latestQuery match {
        case error: QueryError => error.problemDigest.summary
        case _ => ""
      },
      sitesThatReturnedCounts = countResults.size,
      sitesThatErrored = errorResults.size,
      sitesPending = sitesPending,
      totalCount = countResults.map(_.count).filterNot(_ == -1).sum,
      queryStatesToTimeInState = queryStatesToTimeInState,
      query = latestQuery,
      queryTerms = queryTerms.map(_.displayName),
      queryAsText = queryAsText
    )
  }
}

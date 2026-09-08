package net.shrine.hub.metrics

import com.opencsv.CSVWriter
import net.shrine.csv.Column
import net.shrine.hub.data.store.HubDb
import net.shrine.protocol.version.{DateStamp, NodeId, QueryId, ResearcherId, UserName}
import net.shrine.protocol.version.v2.{CountResult, ErrorResult, Node, NodeSystemSpec, Query, QueryError, Result, ResultStatus}

import java.io.File

object NodeCsv {

  def writeNodeCsv(file:File,startDate:DateStamp,endDate:DateStamp,researcher:Option[UserName]):Unit = CommonMaps.writeCsv (file,{ csvWriter: CSVWriter =>
    import cats.effect.unsafe.implicits.global
    val nodes: Seq[Node] = HubDb.db.selectLatestNodesIO.unsafeRunSync().map(_.get).toSeq
    val nodeIdToNodeSystemSpecs: Map[NodeId, NodeSystemSpec] = HubDb.db.selectLatestNodeSystemSpecsIO.unsafeRunSync().
      map(_.get).map(n => n.id -> n ).toMap

    val queryIds: Seq[QueryId] = researcher.fold(
      HubDb.db.selectQueryIdsIO(startDate, endDate)
    ) { r =>
      val researcherIds: Seq[ResearcherId] = CommonMaps.researcherUserNameToResearchers(r).map(_.id)
      HubDb.db.selectQueryIdsForResearcherIdsIO(startDate, endDate, researcherIds)
    }.unsafeRunSync()

    //if memory gets tight batch these requests and roll up the sums in an intermediate structure to feed to the NodeRow
    val queries = HubDb.db.selectQueriesWithIdsIO(queryIds).unsafeRunSync()
    val results = HubDb.db.selectResultsWithQueryIdsIO(queryIds).unsafeRunSync()

    val nodeOfOriginToQueries = queries.groupBy(_.nodeOfOriginId)
    val queryIdToNodeOfOriginId = queries.map(q => q.id -> q.nodeOfOriginId).toMap

    val nodeOfOriginToResults = results.groupBy(r => queryIdToNodeOfOriginId(r.queryId))

    val adapterToResults = results.groupBy(r => r.adapterNodeId)

    val queryIdToQuery: Map[QueryId, Query] = queries.map(q => q.id -> q).toMap
    val adapterToQueries = adapterToResults.map(a2r => a2r._1 -> a2r._2.map(r => queryIdToQuery(r.queryId)))

    csvWriter.writeNext(Column.header(NodeRow.columns))

    nodes.foreach{node =>
      val nodeRow = NodeRow(
        node,
        nodeIdToNodeSystemSpecs.get(node.id),
        nodeOfOriginToQueries.getOrElse(node.id, Seq.empty),
        nodeOfOriginToResults.getOrElse(node.id, Seq.empty),
        adapterToQueries.getOrElse(node.id, Seq.empty),
        adapterToResults.getOrElse(node.id, Seq.empty),
      )
      csvWriter.writeNext(Column.rowForColumns(NodeRow.columns)(nodeRow))
    }
  })
}

case class NodeRow (  
                      node:Node,
                      nodeSystemSpec:Option[NodeSystemSpec],
                      qepQueries:Seq[Query],
                      qepResults:Seq[Result],
                      adapterQueries:Seq[Query],
                      adapterResults:Seq[Result],
                   ) {
  //Adapter
  private def earliestResultDate: Option[DateStamp] = {
    if (adapterResults.nonEmpty) Option(adapterResults.minBy(_.versionInfo.createDate.underlying).versionInfo.createDate)
    else None
  }

  private def latestResultDate: Option[DateStamp] = {
    if (adapterResults.nonEmpty) Option(adapterResults.maxBy(_.versionInfo.createDate.underlying).versionInfo.createDate)
    else None
  }

  private def researchesServedByAdapter: Int = adapterQueries.map(_.researcherId).distinct.size

  private def potentialResultsFromAdapter: Int = adapterResults.size

  private def successfulResultsFromAdapter: Int = adapterResults.count(_.status == ResultStatus.ResultFromCRC)

  private def totalPatientsReportedFromAdapter: Int = adapterResults.collect{case r:CountResult => r.count}.sum

  private def pendingResultsFromAdapter: Int = adapterResults.count(!_.status.isFinal)

  private def errorResultsFromAdapter: Int = adapterResults.count(_.status.isError)

  private lazy val mostFrequentResultErrorAndCountAtAdapter: (String, Int) = {
    val errorResults = adapterResults.collect { case re: ErrorResult => re }
    if (errorResults.nonEmpty) {
      errorResults.groupBy(_.problemDigest.codec)
        .map { cList => cList._1 -> cList._2.size }.maxBy(_._2)
    } else ("", 0)
  }

  private def mostFrequentResultErrorAtAdapter: String = mostFrequentResultErrorAndCountAtAdapter._1

  private def howFrequentMostFrequentResultErrorAtAdapter: Int = mostFrequentResultErrorAndCountAtAdapter._2

  //QEP
  private def earliestQueryDate: Option[DateStamp] = {
    if(qepQueries.nonEmpty) Option(qepQueries.minBy(_.versionInfo.createDate.underlying).versionInfo.createDate)
    else None
  }
  private def latestQueryDate: Option[DateStamp] = {
    if (qepQueries.nonEmpty) Option(qepQueries.maxBy(_.versionInfo.createDate.underlying).versionInfo.createDate)
    else None
  }

  private def researchersAtQep: Int = qepQueries.map(_.researcherId).distinct.size

  private def queriesFromQep: Int = qepQueries.size

  private def queriesPending: Int = qepQueries.count(!_.status.isFinal)
  private def queriesFromQepFullSuccess:Int = qepResults.groupBy(_.queryId).map(rs => rs._2.forall(_.status == ResultStatus.ResultFromCRC)).count(b => b)
  private def queriesFromQepInError: Int = qepQueries.count(_.status.isError)

  private lazy val mostFrequentQueryErrorAndCount: (String, Int) = {
    val errorQueries = qepQueries.collect { case qe: QueryError => qe }
    if(errorQueries.nonEmpty) {
      errorQueries.groupBy(_.problemDigest.codec)
        .map { cList => cList._1 -> cList._2.size }.maxBy(_._2)
    } else ("",0)
  }
  private def mostFrequentQueryError = mostFrequentQueryErrorAndCount._1
  private def howFrequentMostFrequentQueryError = mostFrequentQueryErrorAndCount._2
  private def queriesFromQepAllErrorResults:Int = qepResults.groupBy(_.queryId).map(rs => rs._2.forall(_.status.isError)).count(b => b)

  private def potentialResultsForQep = qepResults.size
  private def successfulResultsForQep: Int = qepResults.count(_.status == ResultStatus.ResultFromCRC)
  private def totalPatientsReportedToQep: Int = qepResults.collect{case r:CountResult => r.count}.sum
  private def pendingResultsForQep: Int = qepQueries.count(!_.status.isFinal)
  private def errorResultsForQep: Int = qepQueries.count(_.status.isError)

  private lazy val mostFrequentResultErrorAndCountToQep: (String, Int) = {
    val errorResults = qepResults.collect { case re: ErrorResult => re }
    if (errorResults.nonEmpty) {
      errorResults.groupBy(_.problemDigest.codec)
        .map { cList => cList._1 -> cList._2.size }.maxBy(_._2)
    } else ("", 0)
  }

  private def mostFrequentResultErrorToQep: String = mostFrequentResultErrorAndCountToQep._1
  private def howFrequentMostFrequentResultErrorToQep: Int = mostFrequentResultErrorAndCountToQep._2
}

object NodeRow{

  val columns: Array[Column[NodeRow]] = Array(
    //Node Identity
    Column("ID", { nr: NodeRow => nr.node.id.excelSafeString }),
    Column("Key", { nr: NodeRow => nr.node.key.underlying }),
    Column("Name", { nr: NodeRow => nr.node.name.underlying}),

    //Adapter
    Column("Earliest Attempt At Adapter Started",{nr: NodeRow => nr.earliestResultDate.map(_.toDateString).getOrElse("")}),
    Column("Latest Attempt At Adapter Started",{nr: NodeRow => nr.latestResultDate.map(_.toDateString).getOrElse("")}),
    Column("Researchers Served By Adapter",{nr: NodeRow => nr.researchesServedByAdapter.toString}),

    Column("Potential Results From Adapter",{nr: NodeRow => nr.potentialResultsFromAdapter.toString}),
    Column("Successful Results From Adapter",{nr: NodeRow => nr.successfulResultsFromAdapter.toString}),
    Column("Total Patients Reported From Adapter",{nr: NodeRow => nr.totalPatientsReportedFromAdapter.toString}),

    Column("Pending Results From Adapter",{nr: NodeRow => nr.pendingResultsFromAdapter.toString}),

    Column("Error Results From Adapter",{nr: NodeRow => nr.errorResultsFromAdapter.toString}),
    Column("Most Frequent Error From Adapter",{nr: NodeRow => nr.mostFrequentResultErrorAtAdapter}),
    Column("Most Frequent Error Count From Adapter",{nr: NodeRow => nr.howFrequentMostFrequentResultErrorAtAdapter.toString}),


    //QEP
    Column("Earliest Query Started",{nr: NodeRow => nr.earliestQueryDate.map(_.toDateString).getOrElse("")}),
    Column("Latest Query Started",{nr: NodeRow => nr.latestQueryDate.map(_.toDateString).getOrElse("")}),
    Column("Researchers at QEP",{nr: NodeRow => nr.researchersAtQep.toString}),
    Column("Queries Started",{nr: NodeRow => nr.queriesFromQep.toString}),
    Column("Results Attempted For Queries",{nr: NodeRow => nr.potentialResultsForQep.toString}),

    Column("Fully Successful Queries",{nr: NodeRow => nr.queriesFromQepFullSuccess.toString}),
    Column("Successful Results For QEP",{nr: NodeRow => nr.successfulResultsForQep.toString}),
    Column("Total Patients Reported To QEP",{nr: NodeRow => nr.totalPatientsReportedToQep.toString}),

    Column("Pending Queries",{nr: NodeRow => nr.queriesPending.toString}),
    Column("Pending Results For QEP",{nr: NodeRow => nr.pendingResultsForQep.toString}),

    Column("Queries in Error",{nr: NodeRow => nr.queriesFromQepInError.toString}),
    Column("Most Frequent Query Error",{nr: NodeRow => nr.mostFrequentQueryError}),
    Column("Most Frequent Query Error Count",{nr: NodeRow => nr.howFrequentMostFrequentQueryError.toString}),

    Column("Queries With All Error Results",{nr: NodeRow => nr.queriesFromQepAllErrorResults.toString}),
    Column("Error Results For QEP",{nr: NodeRow => nr.errorResultsForQep.toString}),
    Column("Most Frequent Result Error For QEP", { nr: NodeRow => nr.mostFrequentResultErrorToQep }),
    Column("Most Frequent Result Error Count For QEP", { nr: NodeRow => nr.howFrequentMostFrequentResultErrorToQep.toString }),

    //Configuration
    Column("User Domain", { nr: NodeRow => nr.node.userDomainName.underlying }),
    Column("Mom Queue", { nr: NodeRow => nr.node.momQueueName.underlying }),
    Column("Mom ID", { nr: NodeRow => nr.node.momId }),
    Column("Admin Email", { nr: NodeRow => nr.node.adminEmail }),
    Column("Send Queries", { nr: NodeRow => nr.node.sendQueries.toString }),
    Column("Protocol", { nr: NodeRow => nr.node.understandsProtocol.underlying.toString }),
    Column("Created", { nr: NodeRow => nr.node.versionInfo.createDate.toDateString }),
    Column("Last Changed", { nr: NodeRow => nr.node.versionInfo.changeDate.toDateString }),
    Column("Version", { nr: NodeRow => nr.node.versionInfo.itemVersion.underlying.toString }),
    Column("Spec Last Changed", { nr: NodeRow => nr.nodeSystemSpec.map(_.versionInfo.changeDate.toDateString).getOrElse("") }),
    Column("Spec Version", { nr: NodeRow => nr.nodeSystemSpec.map(_.versionInfo.itemVersion.underlying.toString).getOrElse("") }),
    Column("SHRINE Version", { nr: NodeRow => nr.nodeSystemSpec.map(_.versionInfo.shrineVersion.underlying).getOrElse("") }),
    Column("SHRINE Tomcat JDK Version", { nr: NodeRow => nr.nodeSystemSpec.map(_.shrineJdk).getOrElse("") }),
    Column("SHRINE Tomcat OS", { nr: NodeRow => nr.nodeSystemSpec.map(_.shrineOperatingSystem).getOrElse("") }),
    Column("SHRINE Database Brand", { nr: NodeRow => nr.nodeSystemSpec.map(_.shrineDatabaseBrand).getOrElse("") }),
  )
}

package net.shrine.qep.metrics

import com.opencsv.CSVWriter
import net.shrine.audit
import net.shrine.audit.LongQueryId
import net.shrine.csv.Column
import net.shrine.qep.querydb.{QepQuery, QepQueryDb}
import net.shrine.protocol.version.{DateStamp, NodeKey, NodeName, ResearcherId, UserDomainName, UserName}
import net.shrine.protocol.version.v2.{Node, Researcher}

import java.io.File

object ResearcherCsv {

  def writeResearcherCsv(file:File,startDate:DateStamp,endDate:DateStamp,researcher:Option[UserName]):Unit = CommonMaps.writeCsv (file, { csvWriter: CSVWriter =>
    import cats.effect.unsafe.implicits.global
    val queriesHistory: Seq[QepQuery] = QepQueryDb.db.selectPreviousQueriesHistoryIO(startDate,endDate,researcher.map(_.underlying)).unsafeRunSync()

    val researcherUserNamesToQueriesHistory: Map[audit.UserName, Map[LongQueryId, Seq[QepQuery]]] = queriesHistory.groupBy(_.userName).map{ userNameAndQueries: (audit.UserName, Seq[QepQuery]) =>
      val queryHistories: Map[LongQueryId, Seq[QepQuery]] = userNameAndQueries._2.groupBy(_.networkId).map{ queryIdToQueryStates: (LongQueryId, Seq[QepQuery]) =>
        queryIdToQueryStates._1 -> queryIdToQueryStates._2.sortBy(_.v2Query.versionInfo.itemVersion.underlying)
      }
      userNameAndQueries._1 -> queryHistories
    }

    //for each researcher id - make a row
    val researcherNameToQueryCountsAndDates = researcherUserNamesToQueriesHistory.toSeq.sortBy(_._1).map{x =>
      (
        x._1,
        x._2.size,
        x._2.minBy(_._2.head.v2Query.versionInfo.createDate.underlying)._2.head.v2Query.versionInfo.createDate,
        x._2.maxBy(_._2.head.v2Query.versionInfo.createDate.underlying)._2.head.v2Query.versionInfo.createDate,
      )
    }

    csvWriter.writeNext(Column.header(ResearcherRow.columns))
    researcherNameToQueryCountsAndDates.map { x =>
      val firstQuery: QepQuery = researcherUserNamesToQueriesHistory(x._1).head._2.head
      val researcher = Researcher(
        id = firstQuery.v2Query.researcherId,
        userName = new UserName(x._1),
        userDomainName = new UserDomainName(firstQuery.userDomain),
        nodeId = firstQuery.v2Query.nodeOfOriginId
      )

      ResearcherRow(
        id = researcher.id,
        userName = researcher.userName,
        userDomainName = researcher.userDomainName,
        createdDate = researcher.versionInfo.createDate, //todo this is going to always be today
        queriesStarted = x._2,
        earliestQuery = x._3,
        latestQuery = x._4,
      )
    }.foreach(row => csvWriter.writeNext(Column.rowForColumns(ResearcherRow.columns)(row)))
  })
}

case class ResearcherRow(
                          id:ResearcherId,
                          userName: UserName,
                          userDomainName: UserDomainName,
                          createdDate:DateStamp,
                          queriesStarted:Int,
                          earliestQuery:DateStamp,
                          latestQuery:DateStamp,
                   )

object ResearcherRow{
  val columns: Array[Column[ResearcherRow]] = Array[Column[ResearcherRow]](
    Column("Researcher Id", _.id.excelSafeString),
    Column("Researcher User Name", _.userName.underlying),
    Column("Researcher Authentication Domain", _.userDomainName.underlying),
    //Bring back with SHRINE2020-1104      "First Query Date",
    Column("Number of Queries Started", _.queriesStarted.toString),
    Column("Earliest Query", _.earliestQuery.toDateString),
    Column("Latest Query", _.latestQuery.toDateString),
  )

  def apply(
             researcher: Researcher,
             node:Node,
             queriesStarted:Int,
             earliestQuery: DateStamp,
             latestQuery: DateStamp
           ): ResearcherRow = {

    ResearcherRow(
      id =  researcher.id,
      userName = researcher.userName,
      userDomainName = researcher.userDomainName,
      createdDate = researcher.versionInfo.createDate,
      queriesStarted = queriesStarted,
      earliestQuery = earliestQuery,
      latestQuery = latestQuery,
    )
  }
}

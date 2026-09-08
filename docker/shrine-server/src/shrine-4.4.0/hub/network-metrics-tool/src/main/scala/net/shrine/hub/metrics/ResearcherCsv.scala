package net.shrine.hub.metrics

import com.opencsv.CSVWriter
import net.shrine.csv.Column
import net.shrine.hub.data.store.HubDb
import net.shrine.protocol.version.{DateStamp, NodeKey, NodeName, QueryId, ResearcherId, UserDomainName, UserName}
import net.shrine.protocol.version.v2.{Node, Researcher}

import java.io.File

object ResearcherCsv {

  def writeResearcherCsv(file:File,startDate:DateStamp,endDate:DateStamp,researcher:Option[UserName]):Unit = CommonMaps.writeCsv (file, { csvWriter: CSVWriter =>
    import cats.effect.unsafe.implicits.global
    val maybeResearchers: Option[Seq[Researcher]] = researcher.map(CommonMaps.researcherUserNameToResearchers(_))

    //select query ids, researcher ids for queries started in the time period
    val allResearcherIdAndQueryIds: Seq[(ResearcherId, QueryId, DateStamp)] = HubDb.db.selectResearcherQueryIdsAndStartTimesIO(startDate, endDate).unsafeRunSync()
    val researcherIdAndQueryIds = maybeResearchers.fold(allResearcherIdAndQueryIds){ researchers: Seq[Researcher] =>
      val researcherIds = researchers.map(_.id)
      allResearcherIdAndQueryIds.filter(rq => researcherIds.contains(rq._1))
    }

    //for each researcher id - make a row
    val researcherIdToQueryCountsAndDates = researcherIdAndQueryIds.groupBy(_._1).map{x =>
      (x._1, x._2.length, x._2.minBy(_._3.underlying)._3,x._2.maxBy(_._3.underlying)._3)
    }

    csvWriter.writeNext(Column.header(ResearcherRow.columns))

    researcherIdToQueryCountsAndDates.map { x =>
      val researcher = CommonMaps.researcherIdToResearcher(x._1)
      val node = CommonMaps.nodeIdToNode(researcher.nodeId)
      ResearcherRow(researcher, node, x._2, x._3, x._4)
    }.foreach(row => csvWriter.writeNext(Column.rowForColumns(ResearcherRow.columns)(row)))
  })
}

case class ResearcherRow(
                          id:ResearcherId,
                          userName: UserName,
                          userDomainName: UserDomainName,
                          createdDate:DateStamp,
                          originShortName:NodeKey,
                          originName:NodeName,
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
    Column("Origin Node Key", _.originShortName.underlying),
    Column("Origin Node Name", _.originName.underlying),
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
      originShortName = node.key,
      originName = node.name,
      queriesStarted = queriesStarted,
      earliestQuery = earliestQuery,
      latestQuery = latestQuery,
    )
  }
}

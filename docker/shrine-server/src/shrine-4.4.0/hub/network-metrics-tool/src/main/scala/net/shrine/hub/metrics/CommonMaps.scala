package net.shrine.hub.metrics

import java.io.{File, FileWriter, PrintWriter}
import com.opencsv.CSVWriter
import net.shrine.hub.data.store.HubDb
import net.shrine.log.Loggable
import net.shrine.protocol.version.{MomQueueName, NodeId, NodeKey, NodeName, ResearcherId, UserDomainName, UserName}
import net.shrine.protocol.version.v2.{Node, Researcher}

object CommonMaps extends Loggable {
  import cats.effect.unsafe.implicits.global
  private lazy val nodes:Seq[Node] = HubDb.db.selectLatestNodesIO.map{_.toSeq.map(_.get)}.unsafeRunSync()
  private lazy val researchers:Seq[Researcher] = HubDb.db.selectAllResearchersIO.unsafeRunSync()

  lazy val nodeIdToNode: Map[NodeId, Node] = nodes.map{ node => node.id -> node}.toMap.withDefault{ nodeId => Node(
    id = nodeId,
    name = new NodeName("Lost node"),
    key = new NodeKey("lostNode"),
    userDomainName = new UserDomainName("lost.node.domain"),
    momQueueName = MomQueueName("LostNodeQueue"),
    adminEmail = "admin@lostnode.com",
    momId = "lostNode",
  )}
  lazy val researcherIdToResearcher: Map[ResearcherId, Researcher] = researchers.map{ researcher => researcher.id -> researcher}.toMap
  lazy val researcherUserNameToResearchers: Map[UserName, Seq[Researcher]] = researchers.groupBy(_.userName)

  def writeCsv(file: File, writeToCsv:CSVWriter => _):Unit = {
    val csvWriter = new CSVWriter(new PrintWriter(new FileWriter(file), true)) //autoFlush = true seems to have no effect. Setting it anyway.
    try {
      writeToCsv(csvWriter)
    } finally {
      csvWriter.close()
    }
  }

}

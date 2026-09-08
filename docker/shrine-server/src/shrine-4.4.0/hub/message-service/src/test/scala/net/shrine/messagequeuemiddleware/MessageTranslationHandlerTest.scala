package net.shrine.messagequeuemiddleware

import net.shrine.hub.data.store.HubDb
import net.shrine.protocol.version.{DateStamp, Envelope, ItemVersion, JsonText, MomQueueName, NodeId, NodeKey, NodeName, ProtocolVersion, QueryId, ResearcherId, ResultId, ShrineVersion, UserDomainName, UserName, v1}
import net.shrine.protocol.version.v2.{Node, ObfuscatingParameters, Query, QueryProgress, Researcher, Result, ResultMetadata, ResultProgress, ResultStatus, RunQueryForResult, UpdateResultWithProgress, VersionInfo}
import org.junit.jupiter.api.Assertions.{assertEquals, fail}
import org.junit.jupiter.api.Test
import cats.effect.unsafe.implicits.global
import net.shrine.protocol.i2b2.ResultOutputType
import net.shrine.protocol.i2b2.query.I2b2QueryDefinition
import net.shrine.protocol.version.v1.{Topic, TopicId}
import net.shrine.protocol.version.v2.querydefinition.QueryDefinition

import scala.xml.NodeSeq

class MessageTranslationHandlerTest {

  @Test
  def testRunQueryForResultOn3xNode():Unit = {
    val newNode = Node(
      new NodeName("Test 4.x Node"),
      new NodeKey("test4xNode"),
      new UserDomainName("test4xnode.domain"),
      momId = "test4xNode",
      adminEmail = "email3x@foo",
    )
    HubDb.db.upsertNodeIO(newNode).unsafeRunSync()

    val legacyNode = new Node(
      versionInfo = VersionInfo(
        protocolVersion = new ProtocolVersion(1),
        shrineVersion = new ShrineVersion("3.3.2"),
        itemVersion = ItemVersion.one,
        createDate = DateStamp.now,
        changeDate = DateStamp.now
      ),
      name= new NodeName("Test 3.x Node"),
      key = new NodeKey("test3xNode"),
      userDomainName = new UserDomainName("test3xnode.domain"),
      momQueueName = MomQueueName("test3xNode"),
      momId = "test3xNode",
      adminEmail = "email4x@foo",
      understandsProtocol = new ProtocolVersion(1)
    )
    HubDb.db.upsertNodeIO(legacyNode).unsafeRunSync()

    val query = Query.create(
      queryDefinition = QueryDefinition.standIn,
      breakdownNames = Seq.empty,
      queryName = "test query",
      queryNotes = Some("test query notes"),
      nodeOfOriginId = new NodeId(1),
      researcherId = new ResearcherId(2),
    )
    HubDb.db.upsertQueryIO(query).unsafeRunSync()

    val researcher = Researcher(
      userName = new UserName("HmsResearcher"),
      userDomainName = new UserDomainName("hmscatalyst"),
      nodeId = legacyNode.id
    )


    val resultId = new ResultId(1L)
    val result = ResultProgress(
      resultId,
      queryId = query.id,
      adapterNodeId  = legacyNode.id,
      adapterNodeName= legacyNode.name,
      resultMetadata = ResultMetadata(Option(ObfuscatingParameters(5, 6.5, 10, 10)))
    )

    val runQueryForResult = RunQueryForResult(query,researcher, legacyNode,result)

    val toQueueName = MomQueueName("test3xNode")
    val runQueryEnvelope: Envelope = Envelope(net.shrine.protocol.version.v2.RunQueryForResult.envelopeType,
      query.id.underlying,
      runQueryForResult.asJsonText.underlying
    )

    val mesResult: TranslatedMessage = MessageTranslationHandler.translateMessage(toQueueName, runQueryEnvelope).unsafeRunSync().asInstanceOf[TranslatedMessage]

    val expectedResultForRunQueryV1: v1.RunQueryForResult = createV1RunQueryForResult(legacyNode, query, researcher)

    val runQueryResultV1: v1.RunQueryForResult = net.shrine.protocol.version.v1.RunQueryForResult.tryRead (new JsonText (mesResult.envelop.contents) ).get
    assertEquals(runQueryResultV1.query.id, expectedResultForRunQueryV1.query.id)
    assertEquals(runQueryResultV1.query.queryName, expectedResultForRunQueryV1.query.queryName)
    assertEquals(runQueryResultV1.query.nodeOfOriginId, expectedResultForRunQueryV1.query.nodeOfOriginId)
    assertEquals(runQueryResultV1.query.queryDefinitionXml, expectedResultForRunQueryV1.query.queryDefinitionXml)
    assertEquals(runQueryResultV1.query.outputTypesXml.replaceAll(" ",""), expectedResultForRunQueryV1.query.outputTypesXml.replaceAll(" ", ""))
    assertEquals(runQueryResultV1.query.status, expectedResultForRunQueryV1.query.status)
    assertEquals(runQueryResultV1.query.researcherId, expectedResultForRunQueryV1.query.researcherId)
    assertEquals(runQueryResultV1.query.topicId, expectedResultForRunQueryV1.query.topicId)
    assertEquals(runQueryResultV1.node.id, expectedResultForRunQueryV1.node.id)
    assertEquals(runQueryResultV1.node.name, expectedResultForRunQueryV1.node.name)
    assertEquals(runQueryResultV1.node.momQueueName, expectedResultForRunQueryV1.node.momQueueName)
    assertEquals(runQueryResultV1.node.key, expectedResultForRunQueryV1.node.key)
    assertEquals(runQueryResultV1.resultProgress.queryId, expectedResultForRunQueryV1.resultProgress.queryId)
    assertEquals(runQueryResultV1.resultProgress.adapterNodeId, expectedResultForRunQueryV1.resultProgress.adapterNodeId)
    assertEquals(runQueryResultV1.resultProgress.adapterNodeName, expectedResultForRunQueryV1.resultProgress.adapterNodeName)
  }

  private def createV1RunQueryForResult(legacyNode: Node, query: QueryProgress, researcher: Researcher) = {
    val version1Info = net.shrine.protocol.version.v1.VersionInfo(
      protocolVersion = new ProtocolVersion(1),
      itemVersion = ItemVersion.one,
      createDate = DateStamp.now,
      changeDate = DateStamp.now
    )

    def countAndFilteredBreakdowns(selectedBreakDowns: Set[ResultOutputType]): NodeSeq = {
      val filteredResultOutputTypes = Seq(ResultOutputType.PATIENT_COUNT_XML) ++ selectedBreakDowns.toSeq.sortBy(_.name)

      <result_output_list>
        {filteredResultOutputTypes.zipWithIndex.map { typeAndIndex => {
          <result_output priority_index={(typeAndIndex._2 + 1).toString} name={typeAndIndex._1.name.toLowerCase}/>
      }
      }}
      </result_output_list>
    }

    val resultOutputTypes: Set[ResultOutputType] = ResultOutputType.fromBreakdownNames(query.breakdownNames)

    val queryV1: v1.Query = net.shrine.protocol.version.v1.Query.create(
      id = query.id.underlying,
      queryDefinitionXml = I2b2QueryDefinition.fromShrineV2(query.queryDefinition, query.id, query.queryName).toI2b2.toString(),
      outputTypesXml = countAndFilteredBreakdowns(resultOutputTypes).toString(),
      queryName = query.queryName,
      topicId = new TopicId(1L),
      nodeOfOriginId = query.nodeOfOriginId,
      researcherId = researcher.id,
      projectName = "SHRINE",
      versionInfo = version1Info
    )

    val nodeV1: v1.Node = new v1.Node(
      id = legacyNode.id,
      name = legacyNode.name,
      key = legacyNode.key,
      userDomainName = legacyNode.userDomainName,
      momQueueName = new MomQueueName(legacyNode.key.underlying),
      adminEmail = legacyNode.adminEmail,
    )

    val researcherV1: v1.Researcher = net.shrine.protocol.version.v1.Researcher.create(researcher.userName.underlying, researcher.userDomainName.underlying, legacyNode.id)
    val resultProgressV1: v1.ResultProgress = net.shrine.protocol.version.v1.Result.create(queryV1, nodeV1)
    val topicV1: Topic = net.shrine.protocol.version.v1.Topic.createCompatibleWithShrine200(
      researcherV1.id, "DefaultName", "Default Description", queryV1.topicId.underlying.toInt)
    val expectedResultForRunQueryV1 = net.shrine.protocol.version.v1.RunQueryForResult(queryV1, researcherV1, nodeV1, topicV1, resultProgressV1, new ProtocolVersion(1))
    expectedResultForRunQueryV1
  }
}

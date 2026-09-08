package net.shrine.protocol.version.v2

import net.shrine.protocol.version.v2.querydefinition.QueryDefinition
import net.shrine.protocol.version.{JsonText, NodeId, NodeName, QueryId, ResearcherId, ResultId, UserDomainName, UserName}
import org.junit.Test
import org.scalatestplus.junit.AssertionsForJUnit

/**
  * Round-trip tests of common currency to and from json
  */

class JsonTest extends AssertionsForJUnit {

  val obfuscatingParams: ObfuscatingParameters = ObfuscatingParameters(binSize = 5,stdDev = 6.5,noiseClamp = 10,lowLimit = 10)

  val crcResult: CountResult = CountResult(id = ResultId.create(), versionInfo = VersionInfo.create, queryId = QueryId.create(), adapterNodeId = NodeId.create(), adapterNodeName = new NodeName("test node"), status = ResultStatus.ResultFromCRC, statusMessage = None, crcQueryInstanceId = Some(3L), count = 2050, resultMetadata = ResultMetadata(Option(ObfuscatingParameters(5,6.5,10,10))), breakdowns = Some(
                                Breakdowns(
                                  Seq("colors" -> Seq(
                                                      "red" -> 300,
                                                      "blue" -> 600,
                                                      "green" -> 30
                              )))))

  @Test
  def testCrcResultJsonRoundTrip():Unit = {
    assertResult(crcResult){Result.tryRead(crcResult.asJsonText).get}
  }

  val resultProgress: ResultProgress = ResultProgress(
    id = ResultId.create(),
    versionInfo = VersionInfo.create,
    queryId = QueryId.create(),
    adapterNodeId = NodeId.create(),
    adapterNodeName = new NodeName("test node"),
    status = ResultStatus.IdAssigned,
    statusMessage = None,
    resultMetadata = ResultMetadata(Option(obfuscatingParams))
  )

  @Test
  def testResultProgressJsonRoundTrip():Unit = {
    assertResult(resultProgress){Result.tryRead(resultProgress.asJsonText).get}
  }

  @Test
  def testResearcherRoundTrip():Unit = {
    val researcher = Researcher(
      userName = new UserName("Isha"),
      userDomainName = new UserDomainName("hmscatalyst"),
      nodeId = NodeId.create()
    )

    assertResult(researcher){Researcher.tryRead(researcher.asJsonText).get}
  }

  @Test
  def testQueryRoundTrip():Unit = {
    val query = Query.create(
      queryDefinition = QueryDefinition.standIn,
      breakdownNames = Seq("Vitality","Age"),
      queryName = "query Name",
      queryNotes = Some("query notes"),
      queryFaved = true,
      nodeOfOriginId = new NodeId(0),
      researcherId = new ResearcherId(1),
    )

    val jsonString: JsonText = query.asJsonText

    val queryBack = Query.tryRead(jsonString).get
    assertResult(query){queryBack}
  }

  @Test
  def testRunQueryForResultRoundTrip():Unit = {
    val node = Node.create("test node","testnode","testnode.edu", adminEmail="email@foo", momId = "testNode")

    val researcher = Researcher(
      userName = new UserName("Isha"),
      userDomainName = new UserDomainName("hmscatalyst"),
      nodeId = node.id
    )

    val query = Query.create(
      queryDefinition = QueryDefinition.standIn,
      breakdownNames = Seq.empty,
      queryName = "query Name",
      queryNotes = Some("query notes"),
      queryFaved = true,
      nodeOfOriginId = node.id,
      researcherId = researcher.id,
    )

    val result = Result.create(query,node, ResultMetadata(Option(obfuscatingParams)))

    val runQueryForResult = RunQueryForResult(query,researcher,node,result)

    assertResult(runQueryForResult){RunQueryForResult.tryRead(runQueryForResult.asJsonText).get}
  }

}
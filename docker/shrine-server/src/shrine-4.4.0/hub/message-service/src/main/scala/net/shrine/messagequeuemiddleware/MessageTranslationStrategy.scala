package net.shrine.messagequeuemiddleware

import cats.effect.IO
import net.shrine.config.ConfigSource
import net.shrine.hub.data.store.HubDb
import net.shrine.log.Log
import net.shrine.protocol.i2b2.ResultOutputType
import net.shrine.protocol.i2b2.query.{I2b2QueryDefinition, ValueConstraint}
import net.shrine.protocol.version.{DateStamp, Envelope, EnvelopeContents, ItemVersion, JsonText, MomQueueName, NodeKey, ProtocolVersion, QueryId, ResultId, ShrineVersion}
import net.shrine.protocol.version.v2.{Breakdowns, ErrorResult, Node, ObfuscatingParameters, Query, QueryProgress, QueryStatus, Researcher, ResultMetadata, ResultProgress, ResultStatus, RunQueryAtHub, RunQueryForResult, UpdateQueryAtHub, UpdateQueryAtHubWithFavAndNotes, UpdateQueryAtHubWithFaving, UpdateQueryAtHubWithName, UpdateQueryAtHubWithNameAndNotes, UpdateQueryAtQep, UpdateQueryAtQepWithError, UpdateQueryAtQepWithStatus, UpdateQueryReadyForAdapters, UpdateResult, UpdateResultWithCount, UpdateResultWithError, UpdateResultWithProgress, VersionInfo}
import net.shrine.protocol.version.v1.{QueryStatuses, UpdateQueryAtAdapter, UpdateQueryAtAdapterWithFlagging, UpdateQueryAtAdapterWithName, Breakdowns => BreakdownsV1, CrcResult => CrcResultV1, ErrorResult => ErrorResultV1, Node => NodeV1, Query => QueryV1, Researcher => ResearcherV1, Result => ResultV1, ResultOutputType => ResultOutputTypeV1, ResultProgress => ResultProgressV1, ResultStatus => ResultStatusV1, ResultStatuses => ResultStatusesV1, RunQueryAtHub => RunQueryAtHubV1, RunQueryForResult => RunQueryForResultV1, Topic => TopicV1, TopicId => TopicIdV1, UpdateQueryAtQep => UpdateQueryAtQepV1, UpdateQueryAtQepWithError => UpdateQueryAtQepWithErrorV1, UpdateQueryAtQepWithStatus => UpdateQueryAtQepWithStatusV1, UpdateResult => UpdateResultV1, UpdateResultWithCrcResult => UpdateResultWithCrcResultV1, UpdateResultWithError => UpdateResultWithErrorV1, VersionInfo => VersionInfoV1}
import net.shrine.protocol.version.v2.querydefinition.{Concept, ConceptConstraint, ConceptGroup, DoubleNumberConstraint, EventAnchor, EventBoundary, EventConstraint, FlagConstraints, NumberConstraint, QueryDefinition, Relationship, SingleNumberConstraint, TimeConstraint, TimeConstraintOperator, TimeConstraintUnit, Timeline}
import net.shrine.xml.XmlUtil

import scala.xml.NodeSeq

abstract class MessageTranslationStrategy(momQueueName: MomQueueName, envelopeContents: EnvelopeContents, adapterNode: Option[Node])  {
  def translateMessage(): IO[TranslatedMessage]
}

class RunQueryForResultV2toV1Strategy(toMomQueueName: MomQueueName, envelopeContents: EnvelopeContents)
  extends MessageTranslationStrategy(toMomQueueName, envelopeContents, None)  {
  def translateMessage(): IO[TranslatedMessage] = {
    val d:  RunQueryForResult = envelopeContents.asInstanceOf[RunQueryForResult]

    def countAndFilteredBreakdowns(selectedBreakDowns: Set[ResultOutputType]): NodeSeq = {
        val filteredResultOutputTypes = Seq(ResultOutputType.PATIENT_COUNT_XML) ++ selectedBreakDowns.toSeq.sortBy(_.name)

        <result_output_list>
          {filteredResultOutputTypes.zipWithIndex.map { typeAndIndex => {
            <result_output priority_index={(typeAndIndex._2 + 1).toString} name={typeAndIndex._1.name.toLowerCase}/>
        }
        }}
        </result_output_list>
      }

      val resultOutputTypes: Set[ResultOutputType] = ResultOutputType.fromBreakdownNames(d.query.breakdownNames)

      val queryV1: QueryV1 = QueryV1.create(
        id= d.query.id.underlying,
        queryDefinitionXml = I2b2QueryDefinition.fromShrineV2(d.query.queryDefinition, d.query.id, d.query.queryName).toI2b2.toString(),
        outputTypesXml = countAndFilteredBreakdowns(resultOutputTypes).toString(),
        queryName = d.query.queryName,
        topicId = new TopicIdV1(1L),
        nodeOfOriginId = d.query.nodeOfOriginId,
        researcherId = d.researcher.id,
        projectName = "SHRINE",
        versionInfo = VersionInfoV1(
          protocolVersion = new ProtocolVersion(1),
          itemVersion = d.query.versionInfo.itemVersion,
          createDate = DateStamp.now,
          changeDate = DateStamp.now
        )
      )

      val nodeV1: NodeV1 = NodeV1(
        id= d.node.id,
        versionInfo = VersionInfoV1(
        protocolVersion = new ProtocolVersion(1),
        itemVersion = d.node.versionInfo.itemVersion,
        createDate = DateStamp.now,
        changeDate = DateStamp.now
      ),
        name = d.node.name,
        key = d.node.key,
        momQueueName = new MomQueueName(d.node.key.underlying),
        userDomainName = d.node.userDomainName,
        adminEmail = d.node.adminEmail
      )

      val researcherV1: ResearcherV1 = ResearcherV1(
        id= d.researcher.id,
        versionInfo = VersionInfoV1(
          protocolVersion = new ProtocolVersion(1),
          itemVersion = d.researcher.versionInfo.itemVersion,
          createDate = DateStamp.now,
          changeDate = DateStamp.now
        ),
        userName = d.researcher.userName,
        userDomainName = d.researcher.userDomainName,
        nodeId = d.node.id
      )
      val resultProgressV1: ResultProgressV1 = ResultV1.create(queryV1, nodeV1)
      val topicV1: TopicV1 = TopicV1.createCompatibleWithShrine200(
        researcherId = d.researcher.id,
        name="DefaultName",
        description="Default Description",
        localId = queryV1.topicId.underlying.toInt
      )

      val runQueryForResultV1 = RunQueryForResultV1(queryV1, researcherV1, nodeV1, topicV1, resultProgressV1, new ProtocolVersion(1))

      val resultEnvelope: Envelope = Envelope(RunQueryForResult.envelopeType, d.query.id.underlying, runQueryForResultV1.asJsonText.underlying,new ProtocolVersion(1), None)

      IO(TranslatedMessage(toMomQueueName,resultEnvelope))
  }
}

class RunQueryForResultNotSupportedV2Strategy(toMomQueueName: MomQueueName, envelopeContents: EnvelopeContents, adapterNode: Node)
  extends MessageTranslationStrategy(toMomQueueName, envelopeContents, Some(adapterNode))  {
  def translateMessage(): IO[TranslatedMessage] = {
    val runQueryForResult:  RunQueryForResult = envelopeContents.asInstanceOf[RunQueryForResult]
    val protocolTranslationProblem = ProtocolTranslationProblem(runQueryForResult.query.id,
      envelopeContents.protocolVersion.underlying, adapterNode.protocolVersion.underlying)

    val resultError: ErrorResult = runQueryForResult.resultProgress.toError(protocolTranslationProblem, ResultStatus.ErrorInShrine, resultMetadata = runQueryForResult.resultProgress.resultMetadata)
    val updateResultError: UpdateResult = UpdateResult.createUpdateResult(resultError)
    val errorEnvelope: Envelope = Envelope (UpdateResult.envelopeType, runQueryForResult.query.id.underlying, updateResultError.asJsonText.underlying)
    IO(TranslatedMessage(toMomQueueName, errorEnvelope))
  }
}

class UpdateResultV1toV2Strategy(toMomQueueName: MomQueueName, envelopeContents: EnvelopeContents, adapterNode: Node)
  extends MessageTranslationStrategy(toMomQueueName, envelopeContents, Some(adapterNode))  {
  def translateMessage(): IO[TranslatedMessage] = {
      val updateResult: UpdateResultV1 = envelopeContents.asInstanceOf[UpdateResultV1]

        val status = ResultStatus.namesToStatuses(updateResult.status.statusName)
        val queryId = new QueryId(updateResult.queryId.underlying)
        //Set to defaults used in SHRINE version 3.x
        val obfuscatingParams = ObfuscatingParameters(binSize=5,stdDev=6.5,noiseClamp=10,lowLimit=10)

        //get the result Id
        MessageTranslationStrategy.lookupResultId(queryId, adapterNode).flatMap(resultId => {
          val newUpdatedResult = ResultProgress(id = resultId, queryId = queryId, adapterNodeId = adapterNode.id, adapterNodeName = adapterNode.name, resultMetadata = ResultMetadata(Option(obfuscatingParams)))
          if (status.isFinal && !status.isError) {
            val updateResultWithCrcResult: UpdateResultWithCrcResultV1 = updateResult.asInstanceOf[UpdateResultWithCrcResultV1]

            //Set to defaults used in SHRINE version 3.x
            val obfuscatingParams = ObfuscatingParameters(binSize=5,stdDev=6.5,noiseClamp=10,lowLimit=10)

            val breakdowns = updateResultWithCrcResult.breakdowns.map(breakdowns => {
              Breakdowns( breakdowns.counts)
            })
            val newUpdatedResultWithCount = newUpdatedResult.toCrcResult(
              count = updateResultWithCrcResult.count,
              crcQueryInstanceId = updateResultWithCrcResult.crcQueryInstanceId,
              statusMessage = updateResultWithCrcResult.statusMessage,
              breakdowns = breakdowns,
              resultMetadata = ResultMetadata(Option(obfuscatingParams))
            )
            val resultProgress = UpdateResult.createUpdateResult(newUpdatedResultWithCount)
            val resultEnvelope: Envelope = Envelope(UpdateResult.envelopeType, updateResult.queryId.underlying, resultProgress.asJsonText.underlying)

            IO(TranslatedMessage(toMomQueueName,resultEnvelope))
          }
          else if(status.isError){
            val updateResultWithError: UpdateResultWithErrorV1 = updateResult.asInstanceOf[UpdateResultWithErrorV1]

            val newUpdatedResultWithError = newUpdatedResult.toErrorFromJsonProblemDigest(
              problem = updateResultWithError.problem,
              status =  ResultStatus.namesToStatuses(updateResultWithError.status.statusName),
              statusMessage = updateResultWithError.statusMessage,
              crcQueryInstanceId = updateResultWithError.crcQueryInstanceId,
              adapterTime= DateStamp.now,
              resultMetadata = ResultMetadata(Option(obfuscatingParams))
            )
            val resultError = UpdateResult.createUpdateResult(newUpdatedResultWithError)
            val resultEnvelope: Envelope = Envelope(UpdateResult.envelopeType, updateResult.queryId.underlying, resultError.asJsonText.underlying)

            IO(TranslatedMessage(toMomQueueName,resultEnvelope))
          }
          else {
            val newUpdatedResultWithStatus = newUpdatedResult.withStatus(status, resultMetadata =  newUpdatedResult.resultMetadata)
            val resultProgress = UpdateResult.createUpdateResult(newUpdatedResultWithStatus)
            val resultEnvelope: Envelope = Envelope(UpdateResult.envelopeType, updateResult.queryId.underlying, resultProgress.asJsonText.underlying)

            IO(TranslatedMessage(toMomQueueName,resultEnvelope))
          }
      })
  }
}

class UpdateResultV2toV1Strategy(toMomQueueName: MomQueueName, envelopeContents: EnvelopeContents, adapterNode: Node)
  extends MessageTranslationStrategy(toMomQueueName, envelopeContents, Some(adapterNode))  {
  def translateMessage(): IO[TranslatedMessage] = {
    val updateResult: UpdateResult = envelopeContents.asInstanceOf[UpdateResult]

    val status: ResultStatusV1 = ResultStatusesV1.namesToStatuses(updateResult.result.status.statusName)
    val queryId = new QueryId(updateResult.result.queryId.underlying)

    MessageTranslationStrategy.lookupResultId(queryId, adapterNode).flatMap(resultId => {
      val version1Info: VersionInfoV1 = VersionInfoV1(
        protocolVersion = new ProtocolVersion(1),
        itemVersion = updateResult.result.versionInfo.itemVersion,
        createDate = DateStamp.now,
        changeDate = DateStamp.now
      )
      if (status.isFinal && !status.isError) {
        val updateResultWithCount: UpdateResultWithCount =
          updateResult.asInstanceOf[UpdateResultWithCount]

        val breakdowns: Option[BreakdownsV1] = updateResultWithCount.result.breakdowns.map(breakdowns => {
          BreakdownsV1(breakdowns.counts)
        })

        val resultProgress2: CrcResultV1 =  CrcResultV1(
          id = resultId,
          versionInfo = version1Info,
          queryId = updateResultWithCount.result.queryId,
          adapterNodeId = updateResultWithCount.result.adapterNodeId,
          adapterNodeName = updateResultWithCount.result.adapterNodeName,
          count = updateResultWithCount.result.count,
          crcQueryInstanceId = updateResultWithCount.result.crcQueryInstanceId,
          resultType = ResultOutputTypeV1.PATIENT_COUNT_XML,
          breakdowns = breakdowns,
          status = ResultStatusesV1.namesToStatuses(updateResultWithCount.result.status.statusName),
          statusMessage = None
        )
        val resultEnvelope: Envelope = Envelope(ResultV1.envelopeType, updateResult.result.queryId.underlying, resultProgress2.asJsonText.underlying, new ProtocolVersion(1))

        IO(TranslatedMessage(toMomQueueName,resultEnvelope))
      }
      else if (status.isError) {
        val updateResultWithError: UpdateResultWithError = updateResult.asInstanceOf[UpdateResultWithError]

        val resultError = ErrorResultV1(
          id = resultId,
          versionInfo= version1Info,
          queryId = updateResultWithError.result.queryId,
          adapterNodeId = updateResultWithError.result.adapterNodeId,
          adapterNodeName  = updateResultWithError.result.adapterNodeName,
          status = ResultStatusesV1.namesToStatuses(updateResultWithError.result.status.statusName),
          statusMessage = updateResultWithError.result.statusMessage,
          problemDigest = updateResultWithError.result.problemDigest
        )

        val resultEnvelope: Envelope = Envelope(ResultV1.envelopeType,
          resultError.queryId.underlying,
          resultError.asJsonText.underlying,
          new ProtocolVersion(1))

        IO(TranslatedMessage(toMomQueueName,resultEnvelope))
      }
      else {
        val updateResultWithProgress: UpdateResultWithProgress = updateResult.asInstanceOf[UpdateResultWithProgress]

        val resultProgress = ResultProgressV1(
          id = resultId,
          versionInfo = version1Info,
          queryId = updateResultWithProgress.result.queryId,
          adapterNodeId = updateResultWithProgress.result.adapterNodeId,
          adapterNodeName= updateResultWithProgress.result.adapterNodeName,
          status = ResultStatusesV1.namesToStatuses(updateResultWithProgress.result.status.statusName),
          statusMessage = updateResultWithProgress.result.statusMessage,
          crcQueryInstanceId = updateResultWithProgress.result.crcQueryInstanceId
        )

        val resultEnvelope: Envelope = Envelope(ResultV1.envelopeType,
          updateResultWithProgress.result.queryId.underlying, resultProgress.asJsonText.underlying, new ProtocolVersion(1))
        IO(TranslatedMessage(toMomQueueName,resultEnvelope))
      }
    })
  }
}


class RunQueryAtHubV1toV2Strategy(toMomQueueName: MomQueueName, envelopeContents: EnvelopeContents)
  extends MessageTranslationStrategy(toMomQueueName, envelopeContents, None)  {
  def translateMessage(): IO[TranslatedMessage] = {
    val d: RunQueryAtHubV1 = envelopeContents.asInstanceOf[RunQueryAtHubV1]
    val version2Info = VersionInfo(
      protocolVersion = ProtocolVersion.current,
      shrineVersion = ShrineVersion.current,
      itemVersion = ItemVersion.one,
      createDate = DateStamp.now,
      changeDate = DateStamp.now
    )

    val resultOutputTypesXml = XmlUtil.loadString(d.query.outputTypesXml)
    val resultOutputTypes = (resultOutputTypesXml \ "result_output").flatMap { breakdownXml =>
      val breakdownName = XmlUtil.trim(breakdownXml \ "@name")

      ResultOutputType.valueOf(ResultOutputType.breakdownTypes)(breakdownName)
    }

    val breakdownNames = resultOutputTypes.filter(_.isBreakdown).map(_.name)
    val query: Query = Query.create(
      id= d.query.id.underlying,
      queryDefinition = TransformV1QueryToV2Query.process(I2b2QueryDefinition.fromI2b2(d.query.queryDefinitionXml).get).get,
      breakdownNames = breakdownNames,
      queryName = d.query.queryName,
      nodeOfOriginId = d.query.nodeOfOriginId,
      researcherId = d.researcher.id,
    )
    val researcher: Researcher = new Researcher(
      id = d.researcher.id,
      userName = d.researcher.userName,
      userDomainName = d.researcher.userDomainName,
      nodeId = d.researcher.nodeId
    )
    val queryProgress: QueryProgress = QueryProgress(
      id = query.id,
      versionInfo= version2Info,
      status = QueryStatus.namesToStatuses(d.query.status.statusName),
      queryDefinition= query.queryDefinition,
      breakdownNames = query.breakdownNames,
      queryName = query.queryName,
      queryNotes = None,
      queryFaved= false,
      nodeOfOriginId = query.nodeOfOriginId,
      researcherId = query.researcherId
    )

    val runQueryAtHub: RunQueryAtHub = RunQueryAtHub(
      queryProgress,
      researcher
    )
    val resultEnvelope: Envelope = Envelope(RunQueryAtHub.envelopeType, d.query.id.underlying, runQueryAtHub.asJsonText.underlying, ProtocolVersion.current)

    IO(TranslatedMessage(toMomQueueName,resultEnvelope))
  }
}

class UpdateQueryAtQepV2toV1Strategy(toMomQueueName: MomQueueName, envelopeContents: EnvelopeContents)
  extends MessageTranslationStrategy(toMomQueueName, envelopeContents, None)  {
  def translateMessage(): IO[TranslatedMessage] = {
    val updateQueryAtQep: UpdateQueryAtQep = envelopeContents.asInstanceOf[UpdateQueryAtQep]
    updateQueryAtQep match {
      case statusUpdate: UpdateQueryAtQepWithStatus =>
        val updateQueryAtQepWithStatus: UpdateQueryAtQepWithStatusV1 =
          net.shrine.protocol.version.v1.UpdateQueryAtQepWithStatus(
            statusUpdate.queryId,
            QueryStatuses.namesToStatuses(statusUpdate.queryStatus.statusName),
            changeDate = DateStamp.now
          )

        val resultEnvelope: Envelope = Envelope(UpdateQueryAtQepV1.envelopeType,
          statusUpdate.queryId.underlying,
          updateQueryAtQepWithStatus.asJsonText.underlying,
          new ProtocolVersion(1)
        )

        IO(TranslatedMessage(toMomQueueName,resultEnvelope))

      case statusUpdate: UpdateQueryReadyForAdapters =>
        val updateQueryReadyForAdapters: net.shrine.protocol.version.v1.UpdateQueryReadyForAdapters = net.shrine.protocol.version.v1.UpdateQueryReadyForAdapters(
          queryId = statusUpdate.queryId,
          changeDate= DateStamp.now,
          resultProgresses = statusUpdate.resultProgresses.map(p => {
            net.shrine.protocol.version.v1.ResultProgress(
              id = p.id,
              versionInfo = net.shrine.protocol.version.v1.VersionInfo(
                protocolVersion = new ProtocolVersion(1),
                itemVersion = p.versionInfo.itemVersion,
                createDate = DateStamp.now,
                changeDate = DateStamp.now
              ),
              queryId = statusUpdate.queryId,
              adapterNodeId = p.adapterNodeId,
              adapterNodeName = p.adapterNodeName,
              status = ResultStatusesV1.namesToStatuses(p.status.statusName),
              statusMessage = p.statusMessage,
              crcQueryInstanceId= p.crcQueryInstanceId
            )})
        )

        val resultEnvelope: Envelope = Envelope(UpdateQueryAtQepV1.envelopeType,
          statusUpdate.queryId.underlying,
          updateQueryReadyForAdapters.asJsonText.underlying,
          new ProtocolVersion(1)
        )

        IO(TranslatedMessage(toMomQueueName,resultEnvelope))

      case errorUpdate: UpdateQueryAtQepWithError =>
        val updateQueryAtQepWithError: UpdateQueryAtQepWithErrorV1 = UpdateQueryAtQepWithErrorV1(
          queryId = errorUpdate.queryId,
          problem= errorUpdate.problemDigest,
        )
        val resultEnvelope: Envelope = Envelope(net.shrine.protocol.version.v1.UpdateQueryAtQep.envelopeType,
          errorUpdate.queryId.underlying,
          updateQueryAtQepWithError.asJsonText.underlying,
          new ProtocolVersion(1)
        )
        IO(TranslatedMessage(toMomQueueName,resultEnvelope))
    }
  }
}

class UpdateQueryAtAdapterWithFlaggingV1toV2Strategy(toMomQueueName: MomQueueName, envelopeContents: EnvelopeContents)
  extends MessageTranslationStrategy(toMomQueueName, envelopeContents, None) {
  def translateMessage(): IO[TranslatedMessage] = {
    val updateQueryAtAdapterWithFlagging: UpdateQueryAtAdapterWithFlagging = envelopeContents.asInstanceOf[UpdateQueryAtAdapterWithFlagging]

    val updateQueryAtHubWithFavAndNotes = UpdateQueryAtHubWithFavAndNotes(
      queryId = updateQueryAtAdapterWithFlagging.queryId,
      expectedItemVersion = None,
      faved = updateQueryAtAdapterWithFlagging.flagged,
      queryNotes = updateQueryAtAdapterWithFlagging.flaggedMessage.getOrElse("")
    )
    val faveAndNotesEnvelop: Envelope = Envelope(UpdateQueryAtHub.envelopeType,
      updateQueryAtHubWithFavAndNotes.queryId.underlying,
      updateQueryAtHubWithFavAndNotes.asJsonText.underlying,
    )
    val faveAndNotesMessage: TranslatedMessage = TranslatedMessage(toMomQueueName,faveAndNotesEnvelop)

    IO(faveAndNotesMessage)
  }
}

class UpdateQueryAtAdapterWithNameV1toV2Strategy(toMomQueueName: MomQueueName, envelopeContents: EnvelopeContents)
  extends MessageTranslationStrategy(toMomQueueName, envelopeContents, None) {
  def translateMessage(): IO[TranslatedMessage] = {
    val updateQueryAtAdapterWithName: UpdateQueryAtAdapterWithName = envelopeContents.asInstanceOf[UpdateQueryAtAdapterWithName]

    val updateQueryAtHubWithName = UpdateQueryAtHubWithName(
      queryId = updateQueryAtAdapterWithName.queryId,
      expectedItemVersion = None,
      queryName = updateQueryAtAdapterWithName.queryName,
    )

    val queryNameEnvelop: Envelope = Envelope(UpdateQueryAtHub.envelopeType,
      updateQueryAtHubWithName.queryId.underlying,
      updateQueryAtHubWithName.asJsonText.underlying,
    )
    val queryNameMessage: TranslatedMessage = TranslatedMessage(toMomQueueName,queryNameEnvelop)

    IO(queryNameMessage)
  }
}

object MessageTranslationStrategy {
  val v1 = new ProtocolVersion(1)
  val v2 = new ProtocolVersion(2)

  def extractMessage(envelope: Envelope): Option[EnvelopeContents] = {
    envelope match {
      case env if env.protocolVersion == v2 && env.contentsType == RunQueryForResult.envelopeType =>  Some(RunQueryForResult.tryRead (new JsonText (envelope.contents) ).get)
      case env if env.protocolVersion == v1 && env.contentsType == UpdateResultV1.envelopeType =>  Some(UpdateResultV1.tryRead(new JsonText(envelope.contents)).get)
      case env if env.protocolVersion == v2 && env.contentsType == UpdateResult.envelopeType =>  Some(UpdateResult.tryRead(new JsonText(envelope.contents)).get)
      case env if env.protocolVersion == v1 && env.contentsType == RunQueryAtHubV1.envelopeType =>  Some(RunQueryAtHubV1.tryRead(new JsonText(envelope.contents)).get)
      case env if env.protocolVersion == v2 && env.contentsType == UpdateQueryAtQep.envelopeType =>  Some(UpdateQueryAtQep.tryRead(new JsonText(envelope.contents)).get)
      case env if env.protocolVersion == v1 && env.contentsType == UpdateQueryAtAdapter.envelopeType =>  Some(UpdateQueryAtAdapter.tryRead(new JsonText(envelope.contents)).get)
      case _ => None
    }
  }

  def getStrategy(envelope: Envelope, toQueueName: MomQueueName): IO[Option[(MessageTranslationStrategy)]] = {
    val envelopeContents = extractMessage(envelope)
    envelopeContents match {
      case Some(runQueryForResult: RunQueryForResult) => HubDb.db.selectLatestNodesIO.flatMap(nodes => {
        val destNodeOpt = nodes.filter(p => p.get.id == runQueryForResult.node.id)
        val timeline = runQueryForResult.query.queryDefinition.topLevelTimelines
        val isMoreThan2EventTimeline = timeline.nonEmpty && timeline.head.subsequent.length > 1
        val hasMultipleTimespansTimeline = timeline.nonEmpty && !isMoreThan2EventTimeline && timeline.head.subsequent.head.secondaryTimeConstraint.nonEmpty

        val hubQueueNameIO = HubDb.db.selectTheNetworkIO.flatMap(network => IO(network.hubQueueName))

        if (destNodeOpt.nonEmpty && destNodeOpt.head.get.protocolVersion.underlying == 1
          && isMoreThan2EventTimeline) {
          Log.error(s"Not able to translate query containing timeline with more than two events with id ${runQueryForResult.query.id}")
          hubQueueNameIO.flatMap(hubQueueName => IO(Some(new RunQueryForResultNotSupportedV2Strategy(hubQueueName, runQueryForResult, destNodeOpt.head.get))))
        }
        else if (destNodeOpt.nonEmpty && destNodeOpt.head.get.protocolVersion.underlying == 1
          && hasMultipleTimespansTimeline) {
          Log.error(s"Not able to translate query containing timeline with more than one time span with id ${runQueryForResult.query.id}")
          hubQueueNameIO.flatMap(hubQueueName => IO(Some(new RunQueryForResultNotSupportedV2Strategy(hubQueueName, runQueryForResult, destNodeOpt.head.get))))
        }
        else if (destNodeOpt.nonEmpty && destNodeOpt.head.get.protocolVersion.underlying == 1) {
          IO(Some(new RunQueryForResultV2toV1Strategy(toQueueName,runQueryForResult)))
        } else {
          IO(None)
        }
      })
      case Some(updateResult: UpdateResultV1) => HubDb.db.selectNodeByKeyIO(updateResult.adapterNodeKey).flatMap(nodeOpt => {
        val destNode = nodeOpt.get
          IO(Some(new UpdateResultV1toV2Strategy(toQueueName,updateResult, destNode)))
        })

      case Some(updateResult: UpdateResult) =>  HubDb.db.selectLatestNodesIO.flatMap(nodes => {
        val momNode = nodes.filter(p => p.get.momQueueName == toQueueName && p.get.protocolVersion.underlying ==1)
        val adapterNode = nodes.filter(p => p.get.id == updateResult.result.adapterNodeId)

        if (momNode.nonEmpty && adapterNode.nonEmpty) {
          IO(Some(new UpdateResultV2toV1Strategy(toQueueName, updateResult, adapterNode.head.get)))
        }
        else{
          IO(None)
        }
      })
      case Some(updateResult: RunQueryAtHubV1) => IO(Some(new RunQueryAtHubV1toV2Strategy(toQueueName, updateResult)))

      case Some(updateQueryAtQep: UpdateQueryAtQep) =>   HubDb.db.selectLatestNodesIO.map(nodes => nodes
        .filter(p => p.get.momQueueName==toQueueName)).flatMap (nodeOpt => {
        val destNode = nodeOpt.head.get
        if(destNode.protocolVersion.underlying == 1) {
          IO(Some(new UpdateQueryAtQepV2toV1Strategy(toQueueName,updateQueryAtQep)))
        }
        else{
          IO(None)
        }
      })
      case Some(updateQueryAtAdapterWithFlagging: UpdateQueryAtAdapterWithFlagging) =>
         IO(Some(new UpdateQueryAtAdapterWithFlaggingV1toV2Strategy(toQueueName, updateQueryAtAdapterWithFlagging)))
      case Some(updateQueryAtAdapterWithName: UpdateQueryAtAdapterWithName) =>
        IO(Some(new UpdateQueryAtAdapterWithNameV1toV2Strategy(toQueueName, updateQueryAtAdapterWithName)))
      case  _ => IO(None)
     }
  }

  def lookupResultId(queryId: QueryId, adapterNode: Node): IO[ResultId] ={
    HubDb.db.selectMostRecentResultsForQueryIO(queryId).map(results => {
      results.filter(_.adapterNodeId == adapterNode.id).head.id
    })
  }
}

object TransformV1QueryToV2Query {
  def process(i2b2QueryDefinition: I2b2QueryDefinition): Option[QueryDefinition] = {
    val conceptGroupsOption: Option[Seq[ConceptGroup]] = convertI2b2PanelsToConceptGroups(i2b2QueryDefinition)

    val timelineConceptGroupList = i2b2QueryDefinition.subQueries.map(subQuery => {
      val timelineConceptGroups: Option[ConceptGroup] = subQuery.expr.map(ex => {
        val conceptGroup: ConceptGroup = I2b2QueryDefinition.toPanels(ex).map(panel => {
          val startDate = panel.start.map(date => new DateStamp(date.toGregorianCalendar.getTime.getTime))
          val endDate = panel.end.map(date => new DateStamp(date.toGregorianCalendar.getTime.getTime))
          val allConcepts = panel.terms.filter(_.isTerm).map(texpr => {
            Concept(texpr.name, texpr.value)
          })

          val conceptGroup = if (panel.isExcluded) ConceptGroup.noneOf(allConcepts) else ConceptGroup.atLeastOneOf(allConcepts)

          val groupWithStartDate = startDate.fold(conceptGroup) { date => conceptGroup.withStartDate(date) }
          val groupWithEndDate = endDate.fold(groupWithStartDate) { date => groupWithStartDate.withEndDate(date) }
          val groupWithOccurs = if (panel.minOccurrences > 1) groupWithEndDate.withOccursAtLeast(panel.minOccurrences) else groupWithEndDate

          groupWithOccurs
        }).head
        conceptGroup
      })

      timelineConceptGroups
    })
    if (timelineConceptGroupList.length == 2) {
      val twoEventTimeline: Timeline = createTwoEventTimeline(i2b2QueryDefinition, timelineConceptGroupList)

      conceptGroupsOption match {
        case Some(conceptGroups) => Some(QueryDefinition.allOf(conceptGroups ++ Seq(twoEventTimeline)))
        case None => Some(QueryDefinition.allOf(Seq(twoEventTimeline)))
      }
    }else{
      conceptGroupsOption match {
        case Some(conceptGroups) => Some(QueryDefinition.allOf(conceptGroups))
        case None => None
      }
    }
  }

  private def convertI2b2PanelsToConceptGroups(i2b2QueryDefinition: I2b2QueryDefinition): Option[Seq[ConceptGroup]]  = {
    val conceptGroupsOption: Option[Seq[ConceptGroup]] = i2b2QueryDefinition.expr.map(ex => {
      I2b2QueryDefinition.toPanels(ex).map(panel => {
        val startDate = panel.start.map(date => new DateStamp(date.toGregorianCalendar.getTime.getTime))
        val endDate = panel.end.map(date => new DateStamp(date.toGregorianCalendar.getTime.getTime))
        val conceptsWithOutConstraints = panel.terms.filter(_.isTerm).map(texpr => {
          Concept(texpr.name, texpr.value)
        })
        val conceptsWithConstraints = panel.termsWithValueConstraints.filter(_.isTerm).map(texpr => {
          val conceptConstraint: Option[ConceptConstraint] = texpr.valueConstraint.flatMap {
            case vCon: ValueConstraint if vCon.valueType == "FLAG" && vCon.value == "L" => Option(FlagConstraints.Low)
            case vCon: ValueConstraint if vCon.valueType == "FLAG" && vCon.value == "@" => Option(FlagConstraints.Normal)
            case vCon: ValueConstraint if vCon.valueType == "FLAG" && vCon.value == "H" => Option(FlagConstraints.High)
            case vCon: ValueConstraint if vCon.valueType == "NUMBER" && vCon.operator == "BETWEEN" =>
              val valueList = vCon.value.split(" and ")
              Option(DoubleNumberConstraint(
                value1 = valueList(0).trim.toDouble,
                value2 = valueList(1).trim.toDouble,
                unit = vCon.unit
              ))
            case vCon: ValueConstraint if vCon.valueType == "NUMBER" =>
              val operatorMap = Map(
                "LT" -> NumberConstraint.LessThan,
                "LE" -> NumberConstraint.LessThanOrEqual,
                "GT" -> NumberConstraint.GreaterThan,
                "GE" -> NumberConstraint.GreaterThanOrEqual,
                "EQ" -> NumberConstraint.Equal
              )
              Option(SingleNumberConstraint(
                operator = operatorMap(vCon.operator),
                value = vCon.value.toDouble,
                unit = vCon.unit
              ))

            case _ => None
          }
          Concept(texpr.term.name, texpr.term.value, conceptConstraint)
        })

        val allConcepts = conceptsWithOutConstraints ++ conceptsWithConstraints
        val conceptGroup = if (panel.isExcluded) ConceptGroup.noneOf(allConcepts) else ConceptGroup.atLeastOneOf(allConcepts)

        val groupWithStartDate = startDate.fold(conceptGroup) { date => conceptGroup.withStartDate(date) }
        val groupWithEndDate = endDate.fold(groupWithStartDate) { date => groupWithStartDate.withEndDate(date) }
        val groupWithOccurs = if (panel.minOccurrences > 1) groupWithEndDate.withOccursAtLeast(panel.minOccurrences) else groupWithEndDate

        groupWithOccurs
      })
    })
    conceptGroupsOption
  }

  private def createTwoEventTimeline(i2b2QueryDefinition: I2b2QueryDefinition, timelineConceptGroupList: Seq[Option[ConceptGroup]]) = {
    val timeline = Timeline(timelineConceptGroupList.head.get)
    val twoEventTimeline = i2b2QueryDefinition.constraints.map(evConstr => {
      val JOIN_COLUMN = Map(
        "STARTDATE" -> EventBoundary.START,
        "ENDDATE" -> EventBoundary.END,
      )
      val EVENT_ANCHOR = Map(
        "ANY" -> EventAnchor.ANY,
        "FIRST" -> EventAnchor.FIRST,
        "LAST" -> EventAnchor.LAST
      )
      val RELATIONSHIP = Map(
        "LESS" -> Relationship.Before,
        "LESSEQUAL" -> Relationship.BeforeOrSimultaneous,
        "EQUAL" -> Relationship.Simultaneous
      )

      val TIMESPAN_OPERATOR = Map(
        "GREATEREQUAL" -> TimeConstraintOperator.GREATEREQUAL,
        "GREATER" -> TimeConstraintOperator.GREATER,
        "EQUAL" -> TimeConstraintOperator.EQUAL,
        "LESSEQUAL" -> TimeConstraintOperator.LESSEQUAL,
        "LESS" -> TimeConstraintOperator.LESS
      )

      val TIMESPAN_UNIT = Map(
        "DAY" -> TimeConstraintUnit.Day,
        "MONTH" -> TimeConstraintUnit.Month,
        "YEAR" -> TimeConstraintUnit.Year
      )
      val timeSpan1 = evConstr.primarySpan.map(timespan => {
        TimeConstraint(TIMESPAN_OPERATOR(timespan.operator), timespan.value.toInt, TIMESPAN_UNIT(timespan.unit))
      })

      val timeSpan2 = evConstr.secondarySpan.map(timespan => {
        TimeConstraint(TIMESPAN_OPERATOR(timespan.operator), timespan.value.toInt, TIMESPAN_UNIT(timespan.unit))
      })

     timeline.appendWithEvent(
      timelineConceptGroupList.last.get,
      EventConstraint(JOIN_COLUMN(evConstr.first.joinColumn), EVENT_ANCHOR(evConstr.first.aggregateOperator)),
      EventConstraint(JOIN_COLUMN(evConstr.second.joinColumn), EVENT_ANCHOR(evConstr.second.aggregateOperator)),
      RELATIONSHIP(evConstr.operator),
      timeSpan1,
      timeSpan2
     )
    }).head
    twoEventTimeline
  }
}

case class TranslatedMessage(toMomQueueName: MomQueueName, envelop: Envelope)

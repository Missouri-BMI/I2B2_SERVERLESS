package net.shrine.protocol.version.v2

import com.typesafe.config.Config
import io.circe.{Decoder, Encoder, Json}
import net.shrine.problem.{JsonProblemDigest, RawProblem}
import net.shrine.protocol.version.{DateStamp, JsonText, NodeId, NodeName, QueryId, ResultId, ValueClass}

import scala.util.Try
import scala.collection.immutable.Seq

/**
  * A query result, expected or completed
  *
  * @since 1.26
  * @author dwalend
  */
sealed abstract class Result(
                       id:ResultId,
                       versionInfo: VersionInfo
                    ) extends Versioned[ResultId] {

  def asJsonText:JsonText = {
    //todo this can maybe be fully generic for all the common currency data, in Versioned
    //todo but the generics didn't work out of the box. Ask on the circe gitter channel SHRINE-2848
    import io.circe.generic.extras.auto.exportEncoder
    import io.circe.syntax.EncoderOps
    import io.circe.generic.extras.Configuration

    implicit val genDevConfig: Configuration = Versioned.genDevConfig

    import io.circe.generic.extras.encoding.UnwrappedEncoder
    implicit def valueClassEncoder[A <: ValueClass[_]](implicit encodeA: UnwrappedEncoder[A]): Encoder[A] = encodeA

    new JsonText(this.asJson.noSpaces)
  }

  def queryId:QueryId
  def adapterNodeId:NodeId
  def adapterNodeName:NodeName //todo remove with SHRINE2020-1370 in v3
  def status:ResultStatus
  def statusMessage:Option[String]
  def crcQueryInstanceId:Option[Long] //id of this query in the CRC
  def resultMetadata: ResultMetadata

  def toErrorBeforeCrc(problem: RawProblem,  resultMetadata: ResultMetadata, adapterTime: DateStamp): ErrorResult =
    toErrorFromJsonProblemDigest(
      problem = JsonProblemDigest(problem),
      status = ResultStatus.ErrorInShrine,
      crcQueryInstanceId = None,
      adapterTime = adapterTime,
      resultMetadata = resultMetadata
    )

  def toCrcError(
                  problem: RawProblem,
                  crcQueryInstanceId: Option[Long],
                  statusMessage: Option[String] = None,
                  adapterTime: DateStamp = DateStamp.now,
                  resultMetadata: ResultMetadata,
                ): ErrorResult = {
    toErrorFromJsonProblemDigest(
      problem = JsonProblemDigest(problem),
      status = ResultStatus.ErrorFromCrc,
      statusMessage = statusMessage,
      crcQueryInstanceId = crcQueryInstanceId,
      resultMetadata: ResultMetadata,
      adapterTime = adapterTime
    )
  }

  def toErrorFromJsonProblemDigest(
               problem: JsonProblemDigest,
               status: ResultStatus,
               statusMessage: Option[String] = None,
               crcQueryInstanceId: Option[Long],
               resultMetadata: ResultMetadata,
               adapterTime: DateStamp
             ): ErrorResult = ErrorResult(
    id = id,
    versionInfo = versionInfo.next(adapterTime),
    queryId = queryId,
    adapterNodeId = adapterNodeId,
    adapterNodeName = adapterNodeName,
    status = status,
    statusMessage = statusMessage,
    crcQueryInstanceId = crcQueryInstanceId,
    problemDigest = problem,
    resultMetadata = resultMetadata
  )

  def toError(
               problem: RawProblem,
               status: ResultStatus,
               statusMessage: Option[String] = None,
               crcQueryInstanceId: Option[Long] = None,
               resultMetadata: ResultMetadata,
               adapterTime: DateStamp = DateStamp.now
             ): ErrorResult = ErrorResult(
    id = id,
    versionInfo = versionInfo.next(adapterTime),
    queryId = queryId,
    adapterNodeId = adapterNodeId,
    adapterNodeName = adapterNodeName,
    status = status,
    statusMessage = statusMessage,
    crcQueryInstanceId = crcQueryInstanceId,
    problemDigest = JsonProblemDigest(problem),
    resultMetadata = resultMetadata
  )


  //todo reorder these arguments
  def toCrcResult(count: Int,
                  crcQueryInstanceId: Long,
                  statusMessage: Option[String] = None,
                  breakdowns: Option[Breakdowns] = None,
                  resultMetadata: ResultMetadata,
                  adapterTime: DateStamp = DateStamp.now
                 ): CountResult = CountResult(
    id = id,
    versionInfo = versionInfo.next(adapterTime),
    queryId = queryId,
    adapterNodeId = adapterNodeId,
    adapterNodeName = adapterNodeName,
    statusMessage = statusMessage,
    crcQueryInstanceId = Option(crcQueryInstanceId),
    count = count,
    resultMetadata = resultMetadata,
    breakdowns = breakdowns
  )

  /**
   * For testing
   */
  def withVersionInfoAndQueryId(versionInfo: VersionInfo, queryId: QueryId): Result

  /**
   * For testing
   */
  def withChangeDate(changeDate: DateStamp): Result

  /**
   * Only for testing
   */
  def withUntestableFieldsFrom(testResult: Result): Result
}

object Result extends VersionedCompanion {

  val envelopeType: String = classOf[Result].getSimpleName

  def create(query: Query,adapterNode:Node, resultMetadata: ResultMetadata):ResultProgress = create(query.id, adapterNode, resultMetadata)

  def create(queryId: QueryId,adapterNode:Node, resultMetadata: ResultMetadata):ResultProgress = ResultProgress(
    queryId = queryId,
    adapterNodeId = adapterNode.id,
    adapterNodeName = adapterNode.name,
    resultMetadata = resultMetadata
  )

  //todo this can maybe be fully generic for all the common currency data, maybe it can live in Versioned
  def tryRead(jsonText:JsonText):Try[Result] = Try {
    import io.circe.parser.decode
    import io.circe.generic.extras.auto.exportDecoder
    import io.circe.generic.extras.Configuration

    implicit val genDevConfig: Configuration = Versioned.genDevConfig

    import io.circe.generic.extras.decoding.UnwrappedDecoder
    implicit def valueClassDecoder[A <: ValueClass[_]](implicit decodeA: UnwrappedDecoder[A]): Decoder[A] = decodeA

    decode[Result](jsonText.underlying) match {
      case Right(result) => result
      case Left(x) => throw x //throw errors for now. Eventually do something clever, maybe with circe optics
    }
  }
}

case class ResultProgress(
                           id: ResultId = ResultId.create(),
                           versionInfo: VersionInfo = VersionInfo.create,
                           queryId: QueryId,
                           adapterNodeId: NodeId,
                           adapterNodeName: NodeName,
                           status: ResultStatus = ResultStatus.IdAssigned,
                           statusMessage: Option[String] = None,
                           crcQueryInstanceId: Option[Long] = None,
                           resultMetadata: ResultMetadata
                         ) extends Result(id, versionInfo) {

  def withStatus(status: ResultStatus, statusMessage: Option[String] = None, crcQueryInstanceId: Option[Long] = None, adapterTime: DateStamp = DateStamp.now, resultMetadata: ResultMetadata): ResultProgress = copy(versionInfo = versionInfo.next(adapterTime), status = status, statusMessage = statusMessage, crcQueryInstanceId = crcQueryInstanceId, resultMetadata = resultMetadata)

  override def withVersionInfoAndQueryId(versionInfo: VersionInfo, queryId: QueryId): Result = copy(versionInfo = versionInfo, queryId = queryId)

  /**
   * For testing
   */
  override def withChangeDate(changeDate: DateStamp): ResultProgress = {
    copy(versionInfo = versionInfo.copy(changeDate = changeDate))
  }

  /**
   * Only for testing
   */
  override def withUntestableFieldsFrom(testResult: Result): ResultProgress = {
    copy(id = testResult.id, versionInfo = testResult.versionInfo)
  }
}

case class CountResult(
                       id: ResultId,
                       versionInfo: VersionInfo,
                       queryId: QueryId,
                       adapterNodeId: NodeId,
                       adapterNodeName: NodeName,
                       status: ResultStatus = ResultStatus.ResultFromCRC,
                       statusMessage: Option[String] = None,
                       crcQueryInstanceId: Option[Long],
                       count: Int,
                       resultMetadata: ResultMetadata,
                       breakdowns: Option[Breakdowns] = None
                      ) extends Result(id, versionInfo) {
  override def withVersionInfoAndQueryId(versionInfo: VersionInfo, queryId: QueryId): Result = copy(versionInfo = versionInfo, queryId = queryId)

  /**
   * For testing
   */
  override def withChangeDate(changeDate: DateStamp): CountResult = {
    copy(versionInfo = versionInfo.copy(changeDate = changeDate))
  }

  /**
   * Only for testing
   */
  override def withUntestableFieldsFrom(testResult: Result): CountResult = {
    copy(id = testResult.id, versionInfo = testResult.versionInfo)
  }

}

/**
  * Need a little extra structure to support optional breakdown counts in circe
  */
case class Breakdowns(
                      counts:Seq[(String,Seq[(String,Int)])]
                     )

case class ObfuscatingParameters(
                                  binSize:Int,
                                  stdDev:Double,
                                  noiseClamp:Int,
                                  lowLimit:Int
                                )

case class ResultMetadata(obfuscatingParameters: Option[ObfuscatingParameters], custom: Option[Map[String, Json]] = None)

object ObfuscatingParameters{
  def fromConfig(config:Config): ObfuscatingParameters = ObfuscatingParameters(config.getInt("binSize"),config.getDouble("sigma"),config.getInt("clamp"),config.getInt("lowLimit"))
}

case class ErrorResult(
                        id: ResultId,
                        versionInfo: VersionInfo,
                        queryId: QueryId,
                        adapterNodeId: NodeId,
                        adapterNodeName: NodeName,
                        status: ResultStatus,
                        statusMessage: Option[String],
                        crcQueryInstanceId: Option[Long],
                        problemDigest: JsonProblemDigest,
                        resultMetadata: ResultMetadata
                      ) extends Result(id, versionInfo) {
  override def withVersionInfoAndQueryId(versionInfo: VersionInfo, queryId: QueryId): Result = copy(versionInfo = versionInfo, queryId = queryId, resultMetadata = resultMetadata)

  /**
   * For testing
   */
  override def withChangeDate(changeDate: DateStamp): ErrorResult = {
    copy(versionInfo = versionInfo.copy(changeDate = changeDate))
  }

  /**
   * Only for testing
   */
  override def withUntestableFieldsFrom(testResult: Result): ErrorResult = {
    copy(id = testResult.id, versionInfo = testResult.versionInfo)
  }
}


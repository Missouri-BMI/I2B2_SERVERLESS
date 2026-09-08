package net.shrine.protocol.version.v2

import io.circe.{Decoder, Encoder}
import net.shrine.problem.{JsonProblemDigest, Problem, RawProblem, XmlProblemDigest}
import net.shrine.protocol.version.{DateStamp, EnvelopeContents, EnvelopeContentsCompanion, JsonText, NodeKey, ProtocolVersion, QueryId, ValueClass}

import scala.util.Try

/**
  * A command to update something about a pending Result
  *
  * @since 1.26
  * @author dwalend
  */
//todo remove all of UpdateCrcQueuedResult in SHRINE2020-1365
sealed abstract class UpdateCrcQueuedResult(
                                              val protocolVersion: ProtocolVersion = ProtocolVersion.current
                                            ) extends EnvelopeContents {

  def asJsonText:JsonText = {
    //todo this can maybe be fully generic for all the common currency data, in Versioned
    //todo but the generics didn't work out of the box. Ask on the circe gitter channel SHRINE-2848
    import io.circe.generic.extras.Configuration
    import io.circe.generic.extras.auto.exportEncoder
    import io.circe.syntax.EncoderOps

    implicit val genDevConfig: Configuration = Versioned.genDevConfig
    import io.circe.generic.extras.encoding.UnwrappedEncoder
    implicit def valueClassEncoder[A <: ValueClass[_]](implicit encodeA: UnwrappedEncoder[A]): Encoder[A] = encodeA

    new JsonText(this.asJson.noSpaces)
  }

  //should not need queryId and adapterNodeKey in the future
  def queryId:QueryId
  def adapterNodeKey:NodeKey

  def status:ResultStatus
  def adapterTime:DateStamp
  def withAdapterTime(dateStamp: DateStamp):UpdateCrcQueuedResult

  def updatedResult(result:ResultProgress):Result
}

object UpdateCrcQueuedResult extends EnvelopeContentsCompanion {

  val envelopeType: String = classOf[UpdateCrcQueuedResult].getSimpleName

  //todo if this can maybe be fully generic for all the common currency data, maybe it can live in Versioned SHRINE-2848
  def tryRead(jsonText:JsonText):Try[UpdateCrcQueuedResult] = Try {
    import io.circe.generic.extras.Configuration
    import io.circe.generic.extras.auto.exportDecoder
    import io.circe.parser.decode

    implicit val genDevConfig: Configuration = Versioned.genDevConfig
    import io.circe.generic.extras.decoding.UnwrappedDecoder
    implicit def valueClassDecoder[A <: ValueClass[_]](implicit decodeA: UnwrappedDecoder[A]): Decoder[A] = decodeA

    decode[UpdateCrcQueuedResult](jsonText.underlying) match {
      case Right(envelopeContents) => envelopeContents
      case Left(x) =>
        x.fillInStackTrace()
        throw x //throw errors for now. Eventually do something clever, maybe with circe optics
    }
  }
}

case class UpdateCrcQueuedResultWithError(
                                           queryId: QueryId,
                                           adapterNodeKey: NodeKey,
                                           problem: JsonProblemDigest,
                                           status: ResultStatus,
                                           statusMessage: Option[String] = None,
                                           crcQueryInstanceId: Option[Long] = None,
                                           resultMetadata: ResultMetadata,
                                           adapterTime: DateStamp = DateStamp.now,
                                         ) extends UpdateCrcQueuedResult {

  override def updatedResult(result: ResultProgress): ErrorResult = {
    result.toErrorFromJsonProblemDigest(
        problem = problem,
        status = status,
        statusMessage = statusMessage,
        crcQueryInstanceId = crcQueryInstanceId,
        resultMetadata = resultMetadata,
        adapterTime = adapterTime,
      )
  }

  override def withAdapterTime(dateStamp: DateStamp): UpdateCrcQueuedResult = copy(adapterTime = dateStamp, resultMetadata = resultMetadata)
}

object UpdateCrcQueuedResultWithError{
  def create(
              queryId: QueryId,
              adapterNodeKey: NodeKey,
              problem: Problem,
              status: ResultStatus,
              statusMessage: Option[String] = None,
              crcQueryInstanceId: Option[Long] = None,
              resultMetadata: ResultMetadata,
              adapterTime: DateStamp = DateStamp.now
            ): UpdateCrcQueuedResultWithError = {

    val jsonProblem = problem match {
      case rp:RawProblem => JsonProblemDigest(rp)
      case jp:JsonProblemDigest => jp
      case xp:XmlProblemDigest => JsonProblemDigest(xp)
    }

    new UpdateCrcQueuedResultWithError(
      queryId = queryId,
      adapterNodeKey = adapterNodeKey,
      problem = jsonProblem,
      status = status,
      statusMessage = statusMessage,
      crcQueryInstanceId = crcQueryInstanceId,
      resultMetadata = resultMetadata,
      adapterTime = adapterTime)
  }
}

case class UpdateCrcQueuedResultWithCount(queryId: QueryId, adapterNodeKey: NodeKey, count: Int, crcQueryInstanceId: Long, breakdowns: Option[Breakdowns] = None, resultMetadata: ResultMetadata, status: ResultStatus = ResultStatus.ResultFromCRC, statusMessage: Option[String] = None, adapterTime: DateStamp = DateStamp.now) extends UpdateCrcQueuedResult {
  override def updatedResult(result: ResultProgress): CountResult = {
    result.toCrcResult(
      count,
      crcQueryInstanceId,
      statusMessage,
      breakdowns,
      resultMetadata,
      adapterTime
    )
  }

  override def withAdapterTime(dateStamp: DateStamp): UpdateCrcQueuedResult = copy(adapterTime = dateStamp)
}

case class UpdateCrcQueuedResultWithProgress(queryId: QueryId, adapterNodeKey: NodeKey, status: ResultStatus, statusMessage: Option[String] = None, crcQueryInstanceId: Option[Long] = None, resultMetadata: ResultMetadata, adapterTime: DateStamp = DateStamp.now) extends UpdateCrcQueuedResult {
  override def updatedResult(result: ResultProgress): ResultProgress = {
    result.withStatus(status, statusMessage, crcQueryInstanceId, adapterTime, resultMetadata)
  }

  override def withAdapterTime(dateStamp: DateStamp): UpdateCrcQueuedResult = copy(adapterTime = dateStamp)
}
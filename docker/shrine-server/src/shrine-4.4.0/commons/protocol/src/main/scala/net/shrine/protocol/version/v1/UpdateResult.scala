package net.shrine.protocol.version.v1

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
sealed abstract class UpdateResult(
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
  def adapterTime:Option[DateStamp] //todo make this not optional in v2 . See SHRINE2020-710

  def updatedResult(result:ResultProgress):Result
}

object UpdateResult extends EnvelopeContentsCompanion {

  val envelopeType: String = classOf[UpdateResult].getSimpleName

  //todo if this can maybe be fully generic for all the common currency data, maybe it can live in Versioned SHRINE-2848
  def tryRead(jsonText:JsonText):Try[UpdateResult] = Try {
    import io.circe.generic.extras.Configuration
    import io.circe.generic.extras.auto.exportDecoder
    import io.circe.parser.decode

    implicit val genDevConfig: Configuration = Versioned.genDevConfig
    import io.circe.generic.extras.decoding.UnwrappedDecoder
    implicit def valueClassDecoder[A <: ValueClass[_]](implicit decodeA: UnwrappedDecoder[A]): Decoder[A] = decodeA

    decode[UpdateResult](jsonText.underlying) match {
      case Right(envelopeContents) => envelopeContents
      case Left(x) =>
        x.fillInStackTrace()
        throw x //throw errors for now. Eventually do something clever, maybe with circe optics
    }
  }
}

case class UpdateResultWithError (
                                   queryId:QueryId,
                                   adapterNodeKey:NodeKey,
                                   problem:JsonProblemDigest,
                                   status: ResultStatus,
                                   statusMessage:Option[String] = None,
                                   crcQueryInstanceId:Option[Long] = None,
                                   adapterTime:Option[DateStamp]
                                 ) extends UpdateResult {

  override def updatedResult(result: ResultProgress): ErrorResult = {
    result.toError(
      problem,
      status,
      statusMessage,
      adapterTime
    )
  }
}

object UpdateResultWithError{
  def create(
              queryId: QueryId,
              adapterNodeKey: NodeKey,
              problem: Problem,
              status: ResultStatus,
              statusMessage: Option[String] = None,
              crcQueryInstanceId: Option[Long] = None,
              adapterTime:Option[DateStamp] = Option(DateStamp.now)
            ): UpdateResultWithError = {

    val jsonProblem = problem match {
      case rp:RawProblem => JsonProblemDigest(rp)
      case jp:JsonProblemDigest => jp
      case xp:XmlProblemDigest => JsonProblemDigest(xp)
    }

    new UpdateResultWithError(queryId, adapterNodeKey, jsonProblem, status, statusMessage, crcQueryInstanceId,adapterTime)
  }
}

case class UpdateResultWithCrcResult(
                                      queryId: QueryId,
                                      adapterNodeKey:NodeKey,
                                      count:Int,
                                      crcQueryInstanceId:Long,
                                      resultType: ResultOutputType,
                                      breakdowns:Option[Breakdowns] = None,
                                      status:ResultStatus = ResultStatuses.ResultFromCRC,
                                      statusMessage:Option[String] = None,
                                      adapterTime:Option[DateStamp]
                                    ) extends UpdateResult {
  override def updatedResult(result: ResultProgress): CrcResult = {
    result.toCrcResult(
      count,
      crcQueryInstanceId,
      resultType,
      statusMessage,
      breakdowns,
      adapterTime
    )
  }
}

case class UpdateResultWithProgress(
                                     queryId: QueryId,
                                     adapterNodeKey:NodeKey,
                                     status:ResultStatus,
                                     statusMessage:Option[String] = None,
                                     adapterTime:Option[DateStamp] = Option(DateStamp.now)
                                   ) extends UpdateResult {
  override def updatedResult(result: ResultProgress): ResultProgress = {
    result.withStatus(status,statusMessage,adapterTime)
  }
}
package net.shrine.protocol.version.v1

import io.circe.{Decoder, Encoder}
import net.shrine.problem.{JsonProblemDigest, Problem, RawProblem}
import net.shrine.protocol.version.{DateStamp, EnvelopeContents, EnvelopeContentsCompanion, JsonText, ProtocolVersion, QueryId, ValueClass}

import scala.util.Try

sealed abstract class UpdateQueryAtQep(
                                        val protocolVersion: ProtocolVersion = ProtocolVersion.current
                                      ) extends EnvelopeContents {

  def queryId:QueryId
  def queryStatus:QueryStatus
  def changeDate:DateStamp

  def updatedQuery(query:Query):Query

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
}

object UpdateQueryAtQep extends EnvelopeContentsCompanion {

  val envelopeType: String = classOf[UpdateQueryAtQep].getSimpleName

  //todo if this can maybe be fully generic for all the common currency data, maybe it can live in Versioned SHRINE-2848
  def tryRead(jsonText:JsonText):Try[UpdateQueryAtQep] = Try {
    import io.circe.parser.decode
    import io.circe.generic.extras.auto.exportDecoder
    import io.circe.generic.extras.Configuration

    implicit val genDevConfig: Configuration = Versioned.genDevConfig
    import io.circe.generic.extras.decoding.UnwrappedDecoder
    implicit def valueClassDecoder[A <: ValueClass[_]](implicit decodeA: UnwrappedDecoder[A]): Decoder[A] = decodeA

    decode[UpdateQueryAtQep](jsonText.underlying) match {
      case Right(runQuery) => runQuery
      case Left(x) =>
        x.fillInStackTrace()
        throw x //throw errors for now. Eventually do something clever, maybe with circe optics
    }
  }
}

case class UpdateQueryAtQepWithStatus(
                                       queryId: QueryId,
                                       queryStatus: QueryStatus,
                                       changeDate:DateStamp
                                     ) extends UpdateQueryAtQep() {
  override def updatedQuery(query: Query): Query = query.withStatus(queryStatus)
}

object UpdateQueryAtQepWithStatus {
  def apply(query: Query): UpdateQueryAtQepWithStatus = new UpdateQueryAtQepWithStatus(
    queryId = query.id,
    queryStatus = query.status,
    changeDate = query.versionInfo.changeDate
  )
}

case class UpdateQueryAtQepWithError(
                                      queryId: QueryId,
                                      problemDigest:JsonProblemDigest,
                                      changeDate:DateStamp,
                                      queryStatus:QueryStatus = QueryStatuses.HubError
                                    ) extends UpdateQueryAtQep() {
  override def updatedQuery(query: Query): Query = query.toError(problemDigest,queryStatus)

}

object UpdateQueryAtQepWithError {
  def apply(
             query:Query,
             problem: RawProblem,
           ): UpdateQueryAtQepWithError = UpdateQueryAtQepWithError(query.id, JsonProblemDigest(problem))

  def apply(
             queryId: QueryId,
             problem: JsonProblemDigest,
           ): UpdateQueryAtQepWithError = new UpdateQueryAtQepWithError(queryId, problem, DateStamp.now, QueryStatuses.QepError)
}

case class UpdateQueryReadyForAdapters(
                                        queryId: QueryId,
                                        changeDate:DateStamp,
                                        resultProgresses:Seq[ResultProgress]
                                      ) extends UpdateQueryAtQep() {
  val queryStatus: QueryStatuses.ReadyForAdapters.type = QueryStatuses.ReadyForAdapters

  override def updatedQuery(query: Query): Query = query.withStatus(queryStatus)

}

object UpdateQueryReadyForAdapters {
  def apply(
             query: Query,
             resultProgresses: Seq[ResultProgress]
           ): UpdateQueryReadyForAdapters = new UpdateQueryReadyForAdapters(
    query.id,
    query.versionInfo.changeDate,
    resultProgresses
  )
}
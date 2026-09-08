package net.shrine.protocol.version.v2

import io.circe.{Decoder, Encoder}
import net.shrine.problem.{JsonProblemDigest, RawProblem}
import net.shrine.protocol.version.ItemVersion.next
import net.shrine.protocol.version.{DateStamp, EnvelopeContents, EnvelopeContentsCompanion, ItemVersion, JsonText, ProtocolVersion, QueryId, ValueClass}

import scala.util.Try

sealed abstract class UpdateQueryAtQep(
                                  val protocolVersion: ProtocolVersion = ProtocolVersion.current
                                ) extends EnvelopeContents {

  def queryId:QueryId

  def expectedItemVersion: ItemVersion

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
                                      expectedItemVersion: ItemVersion,
                                      changeDate:DateStamp
                                     ) extends UpdateQueryAtQep() {
  override def updatedQuery(query: Query): Query = query.withStatus(queryStatus)
}

object UpdateQueryAtQepWithStatus {
  def apply(query: Query): UpdateQueryAtQepWithStatus = new UpdateQueryAtQepWithStatus(
    queryId = query.id,
    queryStatus = query.status,
    expectedItemVersion = next(query.versionInfo.itemVersion),
    changeDate = query.versionInfo.changeDate
  )
}

case class UpdateQueryAtQepWithError(
                                      queryId: QueryId,
                                      problemDigest:JsonProblemDigest,
                                      expectedItemVersion: ItemVersion,
                                      changeDate:DateStamp,
                                      queryStatus:QueryStatus = QueryStatus.HubError
                                     ) extends UpdateQueryAtQep() {
  override def updatedQuery(query: Query): Query = query.toError(problemDigest,queryStatus,changeDate)

}

object UpdateQueryAtQepWithError {
  def apply(
             query:Query,
             problem: RawProblem,
           ): UpdateQueryAtQepWithError = new UpdateQueryAtQepWithError(
    query.id,
    JsonProblemDigest(problem),
    next(query.versionInfo.itemVersion),
    DateStamp.now,
    QueryStatus.HubError
  )
}

case class UpdateQueryReadyForAdapters(
                                        queryId: QueryId,
                                        expectedItemVersion: ItemVersion,
                                        changeDate:DateStamp,
                                        resultProgresses:Seq[ResultProgress]
                                      ) extends UpdateQueryAtQep() {
  val queryStatus: QueryStatus.ReadyForAdapters.type = QueryStatus.ReadyForAdapters

  override def updatedQuery(query: Query): Query = query.withStatus(queryStatus)

}

object UpdateQueryReadyForAdapters {
  def apply(
             query: Query,
             resultProgresses: Seq[ResultProgress]
           ): UpdateQueryReadyForAdapters = new UpdateQueryReadyForAdapters(
                                                                              query.id,
                                                                              expectedItemVersion = query.versionInfo.itemVersion,
                                                                              query.versionInfo.changeDate,
                                                                              resultProgresses
                                                                            )
}
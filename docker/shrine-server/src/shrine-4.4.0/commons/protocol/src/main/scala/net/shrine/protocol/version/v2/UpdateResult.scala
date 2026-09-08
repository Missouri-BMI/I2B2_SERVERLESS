package net.shrine.protocol.version.v2

import io.circe.{Decoder, Encoder}
import net.shrine.protocol.version.{EnvelopeContents, EnvelopeContentsCompanion, JsonText, ProtocolVersion, ValueClass}

import scala.util.Try

 /**
  * A command to update something about a pending Result
  *
  * @since 1.26 - revived in 4.2
  * @author dwalend
  */
sealed abstract class UpdateResult(
                                    val result:Result,
                                    val protocolVersion: ProtocolVersion = ProtocolVersion.current,
                                  ) extends EnvelopeContents {

  def asJsonText:JsonText = {
    //todo this can maybe be fully generic for all the common currency data, in Versioned
    //todo but the generics didn't work out of the box. Ask on the circe gitter channel SHRINE-2848
    import io.circe.generic.extras.Configuration
    import io.circe.generic.extras.auto.exportEncoder
    import io.circe.syntax.EncoderOps

    //noinspection ScalaUnusedSymbol
    implicit val genDevConfig: Configuration = Versioned.genDevConfig
    import io.circe.generic.extras.encoding.UnwrappedEncoder
    //noinspection ScalaUnusedSymbol
    implicit def valueClassEncoder[A <: ValueClass[_]](implicit encodeA: UnwrappedEncoder[A]): Encoder[A] = encodeA

    new JsonText(this.asJson.noSpaces)
  }

  def withRaceConditionResult(found:Result):UpdateResult

   /**
    * Only for testing
    */
   def withUntestableFieldsFrom(testUpdate:UpdateResult):UpdateResult
}

object UpdateResult extends EnvelopeContentsCompanion {

  val envelopeType: String = classOf[UpdateResult].getSimpleName

  def createUpdateResult(result:Result):UpdateResult = {
    result match {
      case r:ErrorResult => UpdateResultWithError(r)
      case r:CountResult => UpdateResultWithCount(r)
      case r:ResultProgress => UpdateResultWithProgress(r)
    }
  }

  //todo if this can maybe be fully generic for all the common currency data, maybe it can live in Versioned SHRINE-2848
  def tryRead(jsonText:JsonText):Try[UpdateResult] = Try {
    import io.circe.generic.extras.Configuration
    import io.circe.generic.extras.auto.exportDecoder
    import io.circe.parser.decode

    //noinspection ScalaUnusedSymbol
    implicit val genDevConfig: Configuration = Versioned.genDevConfig
    import io.circe.generic.extras.decoding.UnwrappedDecoder
    //noinspection ScalaUnusedSymbol
    implicit def valueClassDecoder[A <: ValueClass[_]](implicit decodeA: UnwrappedDecoder[A]): Decoder[A] = decodeA

    decode[UpdateResult](jsonText.underlying) match {
      case Right(envelopeContents) => envelopeContents
      case Left(x) =>
        x.fillInStackTrace()
        throw x //throw errors for now. Eventually do something clever, maybe with circe optics
    }
  }
}

case class UpdateResultWithError(
                                  override val result:ErrorResult
                                 ) extends UpdateResult(result) {

  override def withRaceConditionResult(found: Result): UpdateResultWithError = {
    val r = found.toErrorFromJsonProblemDigest(
      result.problemDigest,
      result.status,
      result.statusMessage,
      result.crcQueryInstanceId,
      resultMetadata = result.resultMetadata,
      result.versionInfo.changeDate,
    )
    copy(result = r)
  }

  /**
   * Only for testing
   */
  override def withUntestableFieldsFrom(testUpdate: UpdateResult): UpdateResult = {
    copy(result = result.withUntestableFieldsFrom(testUpdate.result))
  }
}

case class UpdateResultWithCount(
                                  override val result: CountResult
                                ) extends UpdateResult(result) {
  override def withRaceConditionResult(found: Result): UpdateResultWithCount = {
    val r = found.toCrcResult(result.count,
      result.crcQueryInstanceId.getOrElse(throw new IllegalStateException(s"CRC result without CRC ID $result")),
      result.statusMessage,
      result.breakdowns,
      result.resultMetadata,
      result.versionInfo.changeDate
    )
    copy(result = r)
  }

  /**
   * Only for testing
   */
  override def withUntestableFieldsFrom(testUpdate: UpdateResult): UpdateResult = {
    copy(result = result.withUntestableFieldsFrom(testUpdate.result))
  }
}

case class UpdateResultWithProgress(
                                     override val result: ResultProgress
                                   ) extends UpdateResult(result) {
  override def withRaceConditionResult(found: Result): UpdateResultWithProgress = {
    val r = found match {
      case progress: ResultProgress => progress.withStatus(result.status, result.statusMessage, result.crcQueryInstanceId, result.versionInfo.changeDate, result.resultMetadata)
      case _ => throw new IllegalStateException(s"Must be a ResultProgress, not a ${found.getClass.getSimpleName} $found")
    }
    copy(result = r)
  }

  /**
   * Only for testing
   */
  override def withUntestableFieldsFrom(testUpdate: UpdateResult): UpdateResult = {
    copy(result = result.withUntestableFieldsFrom(testUpdate.result))
  }
}
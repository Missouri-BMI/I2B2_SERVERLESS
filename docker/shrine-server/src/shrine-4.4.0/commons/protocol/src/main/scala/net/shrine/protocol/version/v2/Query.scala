package net.shrine.protocol.version.v2

import io.circe.{Decoder, Encoder}
import net.shrine.problem.JsonProblemDigest
import net.shrine.protocol.version.v2.querydefinition.QueryDefinition
import net.shrine.protocol.version.{DateStamp, Id, JsonText, NodeId, QueryId, ResearcherId, ValueClass}

import scala.util.Try


/**
  * A query.
  *
  * @since 1.26
  * @author dwalend
  */
sealed abstract class Query(
                       id:QueryId,
                       versionInfo: VersionInfo
                    ) extends Versioned[QueryId] {

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

  def status:QueryStatus
  def queryDefinition:QueryDefinition

  def breakdownNames:Seq[String]
  def queryName:String
  def queryNotes:Option[String]
  def nodeOfOriginId:NodeId
  def researcherId:ResearcherId

  /** support for the legacy flag mechanism to be replaced maybe as early as 1.27 */
  def queryFaved: Boolean // set to false by default.

  def withNameAndNotes(queryName:String, queryNotes:Option[String]):Query
  def withName(queryName:String):Query
  def withFavingAndNotes(faved: Boolean, queryNotes: String):Query
  def withFaving(faved: Boolean):Query
  def withStatus(queryStatus: QueryStatus):Query

  def withChangeDate(changeDate:DateStamp):Query

  def toError(problem:JsonProblemDigest, errorStatus:QueryStatus = QueryStatus.HubError, changeDate:DateStamp = DateStamp.now): QueryError = QueryError(
    id = id,
    versionInfo = versionInfo.next(changeDate),
    status = errorStatus,
    queryDefinition = queryDefinition,
    breakdownNames = breakdownNames,
    queryName = queryName,
    queryNotes = queryNotes,
    queryFaved = queryFaved,
    nodeOfOriginId = nodeOfOriginId,
    researcherId = researcherId,
    problemDigest = problem
  )

}

object Query extends VersionedCompanion {

  val envelopeType: String = classOf[Query].getSimpleName

  def create(
              id:Long = Id.nextId(), //todo - someday use QueryId.create(), once the QEP is either gone or speaks versioned data
              queryDefinition: QueryDefinition,
              breakdownNames: Seq[String],
              queryName:String,
              queryNotes:Option[String] = None,
              queryFaved:Boolean = false,
              nodeOfOriginId:NodeId,
              researcherId:ResearcherId,
              versionInfo:VersionInfo = VersionInfo.create
            ): QueryProgress = {
    QueryProgress(
      id = new QueryId(id),
      versionInfo = versionInfo,
      status = QueryStatus.IdAssigned,
      queryDefinition = queryDefinition,
      breakdownNames = breakdownNames,
      queryName = queryName,
      queryNotes = queryNotes,
      queryFaved = queryFaved,
      nodeOfOriginId = nodeOfOriginId,
      researcherId = researcherId,
    )
  }


  //todo this can maybe be fully generic for all the common currency data, maybe it can live in Versioned
  def tryRead(jsonText:JsonText):Try[Query] = Try {
    import io.circe.parser.decode
    import io.circe.generic.extras.auto.exportDecoder
    import io.circe.generic.extras.Configuration

    implicit val genDevConfig: Configuration = Versioned.genDevConfig
    import io.circe.generic.extras.decoding.UnwrappedDecoder
    implicit def valueClassDecoder[A <: ValueClass[_]](implicit decodeA: UnwrappedDecoder[A]): Decoder[A] = decodeA

    decode[Query](jsonText.underlying) match {
      case Right(result) => result
      case Left(x) => throw x //throw errors for now. Eventually do something clever, maybe with circe optics
    }
  }
}

case class QueryProgress(
                          id:QueryId,
                          versionInfo: VersionInfo,
                          status:QueryStatus,
                          queryDefinition: QueryDefinition,
                          breakdownNames:Seq[String],
                          queryName:String,
                          queryNotes:Option[String],
                          queryFaved:Boolean,
                          nodeOfOriginId:NodeId,
                          researcherId:ResearcherId
                        ) extends Query(id,versionInfo) {

  def withStatus(queryStatus: QueryStatus): QueryProgress = copy(
    versionInfo = versionInfo.next(),
    status = queryStatus
  )

  def withNameAndNotes(queryName:String, queryNotes:Option[String]):QueryProgress = copy(
    versionInfo = versionInfo.next(),
    queryName = queryName,
    queryNotes = queryNotes
  )

  def withName(queryName:String):QueryProgress = copy(
    versionInfo = versionInfo.next(),
    queryName = queryName,
  )

  def withFavingAndNotes(queryFaved: Boolean, queryNotes:String):QueryProgress = {
    copy(
      versionInfo = versionInfo.next(),
      queryFaved = queryFaved,
      queryNotes = Some(queryNotes)
    )
  }

  def withFaving(queryFaved: Boolean):QueryProgress = copy(
    versionInfo = versionInfo.next(),
    queryFaved = queryFaved,
  )

  override def withChangeDate(changeDate: DateStamp): QueryProgress = copy(
    versionInfo = versionInfo.copy(changeDate = changeDate)
  )
}

case class QueryError(
                       id:QueryId,
                       versionInfo: VersionInfo,
                       status: QueryStatus,
                       queryDefinition: QueryDefinition,
                       breakdownNames: Seq[String],
                       queryName:String,
                       queryNotes:Option[String],
                       queryFaved: Boolean,
                       nodeOfOriginId:NodeId,
                       researcherId:ResearcherId,
                       problemDigest:JsonProblemDigest
                        ) extends Query(id,versionInfo) {
  if((status != QueryStatus.QepError)&&(status != QueryStatus.HubError)) throw new IllegalStateException(s"$status must be ${QueryStatus.QepError} or ${QueryStatus.HubError} ")

  def withNameAndNotes(queryName:String, queryNotes:Option[String]):QueryError = copy(
    versionInfo = versionInfo.next(),
    queryName = queryName,
    queryNotes = queryNotes
  )

  def withName(queryName:String):QueryError = copy(
    versionInfo = versionInfo.next(),
    queryName = queryName,
  )

  def withFavingAndNotes(queryFaved: Boolean, queryNotes: String):QueryError = copy(
    versionInfo = versionInfo.next(),
    queryFaved = queryFaved,
    queryNotes = Option(queryNotes)
  )

  def withFaving(queryFaved: Boolean):QueryError = copy(
    versionInfo = versionInfo.next(),
    queryFaved = queryFaved
  )

  def withStatus(queryStatus: QueryStatus):Query = copy(
    versionInfo = versionInfo.next(),
    status = queryStatus
  )

  override def withChangeDate(changeDate: DateStamp): QueryError = copy(
    versionInfo = versionInfo.copy(changeDate = changeDate)
  )
}
package net.shrine.protocol.version.v1

import java.util.Base64

import io.circe.{Decoder, Encoder}
import net.shrine.crypto.{BouncyKeyStoreCollection, SignerVerifier}
import net.shrine.problem.JsonProblemDigest
import net.shrine.protocol.version.v1.{ QueryStatus, QueryStatuses, TopicId}
import net.shrine.protocol.version.{Id, JsonText, NodeId, QueryId, ResearcherId,ValueClass}

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
  //todo do we even want this??  def statusMessage:Option[String]
  def queryDefinitionXml:String //i2b2 xml
  def queryDefinitionSignature:Base64String
  def outputTypesXml:String //i2b2 xml
  def queryName:String
  def nodeOfOriginId:NodeId
  def researcherId:ResearcherId
  def topicId:TopicId
  /** The i2b2 xml project_id and group_id fields */
  def projectName: String

  /** support for the legacy flag mechanism to be replaced maybe as early as 1.27 */
  def flagged: Boolean //set to false by default. todo always false after replaced by flagged results
  def flaggedMessage: Option[String] //todo always None after replaced by flagged results

  def withName(queryName:String):Query
  def withFlagging(flagged: Boolean,flaggedMessage: Option[String]):Query
  def withStatus(queryStatus: QueryStatus):Query

  def toError(problem:JsonProblemDigest,errorStatus:QueryStatus = QueryStatuses.HubError): QueryError = QueryError(
    id = id,
    versionInfo = versionInfo.next,
    status = errorStatus,
    queryDefinitionXml = queryDefinitionXml,
    queryDefinitionSignature = queryDefinitionSignature,
    outputTypesXml = outputTypesXml,
    queryName = queryName,
    nodeOfOriginId = nodeOfOriginId,
    researcherId = researcherId,
    topicId = topicId,
    projectName = projectName,
    flagged = flagged,
    flaggedMessage = flaggedMessage,
    problemDigest = problem
  )

  private def stringToSign: String = Query.stringToSign(
    queryDefinitionXml = queryDefinitionXml,
    outputTypesXml = outputTypesXml,
    queryName = queryName,
    nodeOfOriginId = nodeOfOriginId,
    researcherId = researcherId,
    topicId = topicId,
    projectName = projectName
  )

  def verifySignature(certCollection: BouncyKeyStoreCollection): Either[Throwable, Unit] = {
    SignerVerifier(certCollection).verifySignature(queryDefinitionSignature.toByteArray,stringToSign.getBytes)
  }
}

object Query extends VersionedCompanion {

  val envelopeType: String = classOf[Query].getSimpleName

  private def stringToSign(
                            queryDefinitionXml: String,
                            outputTypesXml: String,
                            queryName: String,
                            nodeOfOriginId: NodeId,
                            researcherId: ResearcherId,
                            topicId: TopicId,
                            projectName: String
                          ): String =
    queryDefinitionXml +
      outputTypesXml +
      queryName +
      nodeOfOriginId +
      researcherId +
      topicId +
      projectName

  def create(
              id:Long = Id.nextId(), //todo - someday use QueryId.create(), once the QEP is either gone or speaks versioned data
              queryDefinitionXml:String,
              outputTypesXml:String,
              queryName:String,
              nodeOfOriginId:NodeId,
              researcherId:ResearcherId,
              topicId:TopicId,
              projectName: String,
              certCollection: BouncyKeyStoreCollection = BouncyKeyStoreCollection.fromConfig,
              versionInfo:VersionInfo = VersionInfo.create
            ): QueryProgress = {
    val signThis = stringToSign(
      queryDefinitionXml = queryDefinitionXml,
      outputTypesXml = outputTypesXml,
      queryName = queryName,
      nodeOfOriginId = nodeOfOriginId,
      researcherId = researcherId,
      topicId = topicId,
      projectName = projectName
    )
    val queryDefinitionSignature:Base64String = Base64String(SignerVerifier(certCollection).signBytes(signThis.getBytes))

    QueryProgress(
      id = new QueryId(id),
      versionInfo = versionInfo,
      status = QueryStatuses.IdAssigned,
      queryDefinitionXml = queryDefinitionXml,
      queryDefinitionSignature = queryDefinitionSignature,
      outputTypesXml = outputTypesXml,
      queryName = queryName,
      nodeOfOriginId = nodeOfOriginId,
      researcherId = researcherId,
      topicId = topicId,
      projectName = projectName,
      flagged = false,
      flaggedMessage = None
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
                          queryDefinitionXml:String,
                          queryDefinitionSignature:Base64String,
                          outputTypesXml:String,
                          queryName:String,
                          nodeOfOriginId:NodeId,
                          researcherId:ResearcherId,
                          topicId:TopicId,
                          projectName: String,
                          flagged: Boolean,
                          flaggedMessage: Option[String]
                        ) extends Query(id,versionInfo) {

  def withStatus(queryStatus: QueryStatus): QueryProgress = copy(
    versionInfo = versionInfo.next,
    status = queryStatus
  )

  def withName(queryName:String):QueryProgress = copy(
    versionInfo = versionInfo.next,
    queryName =queryName
  )

  def withFlagging(flagged: Boolean,flaggedMessage: Option[String]):QueryProgress = copy(
    versionInfo = versionInfo.next,
    flagged = flagged,
    flaggedMessage = flaggedMessage
  )
}

case class QueryError(
                       id:QueryId,
                       versionInfo: VersionInfo,
                       status: QueryStatus,
                       queryDefinitionXml:String,
                       queryDefinitionSignature:Base64String,
                       outputTypesXml:String,
                       queryName:String,
                       nodeOfOriginId:NodeId,
                       researcherId:ResearcherId,
                       topicId:TopicId,
                       projectName: String,
                       flagged: Boolean,
                       flaggedMessage: Option[String],
                       problemDigest:JsonProblemDigest
                     ) extends Query(id,versionInfo) {
  if((status != QueryStatuses.QepError)&&(status != QueryStatuses.HubError)) throw new IllegalStateException(s"$status must be ${QueryStatuses.QepError} or ${QueryStatuses.HubError} ")

  def withName(queryName:String):QueryError = copy(
    versionInfo = versionInfo.next,
    queryName = queryName
  )

  def withFlagging(flagged: Boolean,flaggedMessage: Option[String]):QueryError = copy(
    versionInfo = versionInfo.next,
    flagged = flagged,
    flaggedMessage = flaggedMessage
  )

  def withStatus(queryStatus: QueryStatus):Query = copy(
    versionInfo = versionInfo.next,
    status = queryStatus
  )

}

case class Base64String(string:String) {
  def toByteArray:Array[Byte] = Base64.getDecoder.decode(string)
}

object Base64String {
  def apply(bytes:Array[Byte]): Base64String = {
    Base64String(Base64.getEncoder.encodeToString(bytes))
  }
}
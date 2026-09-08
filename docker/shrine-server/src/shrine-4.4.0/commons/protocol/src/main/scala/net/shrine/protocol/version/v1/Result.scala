package net.shrine.protocol.version.v1

import io.circe.{Decoder, Encoder}
import net.shrine.problem.{JsonProblemDigest, RawProblem}
import net.shrine.protocol.version.v1.ResultOutputType
import net.shrine.protocol.version.{DateStamp, ItemVersion, JsonText, NodeId, NodeName, ProtocolVersion, QueryId, ResultId, ValueClass}
import net.shrine.protocol.version.v1.{ResultStatus, ResultStatuses}

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
                              //todo user
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
  def adapterNodeName:NodeName
  def status:ResultStatus
  def statusMessage:Option[String]
  def crcQueryInstanceId:Option[Long] //id of this query in the CRC //todo consider adding another type for results that the CRC should know about
}

object Result extends VersionedCompanion {

  val envelopeType: String = classOf[Result].getSimpleName

  def create(queryId: QueryId,adapterNode:Node):ResultProgress = ResultProgress(
    queryId = queryId,
    adapterNodeId = adapterNode.id,
    adapterNodeName = adapterNode.name
  )

  def create(query: Query,adapterNode:Node):ResultProgress = ResultProgress(
    queryId = query.id,
    adapterNodeId = adapterNode.id,
    adapterNodeName = adapterNode.name
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
                           id:ResultId = ResultId.create(),
                           versionInfo: VersionInfo = VersionInfo(
                             protocolVersion = new ProtocolVersion(1),
                             itemVersion = ItemVersion.one,
                             createDate = DateStamp.now,
                             changeDate = DateStamp.now
                           ),
                           queryId: QueryId,
                           adapterNodeId:NodeId,
                           adapterNodeName:NodeName, //todo should probably be NodeKey
                           status:ResultStatus = ResultStatuses.IdAssigned,
                           statusMessage:Option[String] = None,
                           crcQueryInstanceId:Option[Long] = None
                         ) extends Result(id,versionInfo) {

  def toErrorBeforeCrc(problem: RawProblem,adapterTime:Option[DateStamp]): ErrorResult = toError(
    problem = JsonProblemDigest(problem),
    status = ResultStatuses.ErrorInShrine,
    adapterTime = adapterTime
  )

  def toCrcError(
                  problem: RawProblem,
                  statusMessage:Option[String] = None,
                  adapterTime:Option[DateStamp]
                ): ErrorResult =
    toError(
      problem = JsonProblemDigest(problem),
      status = ResultStatuses.ErrorFromCrc,
      statusMessage = statusMessage,
      adapterTime = adapterTime
    )

  def toError(
               problem:JsonProblemDigest,
               status:ResultStatus,
               statusMessage:Option[String] = None,
               adapterTime:Option[DateStamp]
             ): ErrorResult = ErrorResult(
    id = id,
    versionInfo = versionInfo.next.withChangeDate(adapterTime),
    queryId = queryId,
    adapterNodeId = adapterNodeId,
    adapterNodeName = adapterNodeName,
    status = status,
    statusMessage = statusMessage,
    problemDigest = problem
  )

  def withStatus(status: ResultStatus,statusMessage:Option[String] = None,adapterTime:Option[DateStamp]): ResultProgress = copy(
    versionInfo = versionInfo.next.withChangeDate(adapterTime),
    status = status,
    statusMessage = statusMessage
  )

  //todo reorder these arguments
  def toCrcResult(
                   count:Int,
                   crcQueryInstanceId:Long,
                   resultType: ResultOutputType,
                   statusMessage:Option[String] = None,
                   breakdowns:Option[Breakdowns] = None,
                   adapterTime:Option[DateStamp]
                 ):CrcResult = CrcResult(
    id = id,
    versionInfo = versionInfo.next.withChangeDate(adapterTime),
    queryId = queryId,
    adapterNodeName = adapterNodeName,
    adapterNodeId = adapterNodeId,
    crcQueryInstanceId = Some(crcQueryInstanceId),
    count = count,
    resultType = resultType,
    breakdowns = breakdowns
  )
}

case class CrcResult(
                      id:ResultId,
                      versionInfo: VersionInfo,
                      queryId: QueryId,
                      adapterNodeId:NodeId,
                      adapterNodeName:NodeName,
                      status:ResultStatus = ResultStatuses.ResultFromCRC, //Default value for this??
                      statusMessage:Option[String] = None,
                      crcQueryInstanceId:Option[Long],
                      count:Int,
                      resultType: ResultOutputType,
                      breakdowns: Option[Breakdowns] = None
                    ) extends Result(
  id,
  versionInfo
)

/**
 * Need a little extra structure to support optional breakdown counts in circe
 */
case class Breakdowns(
                       counts:Seq[(String,Seq[(String,Int)])]
                     )

case class ErrorResult(
                        id:ResultId,
                        versionInfo: VersionInfo,
                        queryId: QueryId,
                        adapterNodeId:NodeId,
                        adapterNodeName:NodeName,
                        status:ResultStatus, //Default value for this??
                        statusMessage:Option[String],
                        problemDigest:JsonProblemDigest
                      ) extends Result(
  id,
  versionInfo
) {
  override def crcQueryInstanceId: Option[Long] = None
}

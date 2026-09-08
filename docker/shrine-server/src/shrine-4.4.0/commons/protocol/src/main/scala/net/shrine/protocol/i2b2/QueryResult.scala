package net.shrine.protocol.i2b2

import ch.qos.logback.classic.Level

import javax.xml.datatype.XMLGregorianCalendar
import net.shrine.log.Loggable
import net.shrine.problem.{AbstractProblem, JsonProblemDigest, Problem, ProblemNotYetEncoded, ProblemSources, RawProblem, XmlProblemDigest}
import net.shrine.protocol.i2b2.QueryResult.StatusType
import net.shrine.protocol.i2b2.serialization.{I2b2Marshaller, XmlMarshaller}
import net.shrine.protocol.version.{DateStamp, NodeKey, QueryId}
import net.shrine.protocol.version.v2.{ObfuscatingParameters, ResultMetadata, ResultProgress, ResultStatus, UpdateCrcQueuedResult, UpdateCrcQueuedResultWithCount, UpdateCrcQueuedResultWithError, UpdateCrcQueuedResultWithProgress, UpdateResult, UpdateResultWithCount, UpdateResultWithError, UpdateResultWithProgress}

import scala.xml.NodeSeq
import net.shrine.util.{SEnum, Tries}
import net.shrine.xml.{XmlDateHelper, XmlUtil}

import scala.util.Try
import scala.collection.immutable.Seq

/**
 * @author Bill Simons
 * @since 4/15/11
 * @see http://cbmi.med.harvard.edu
 * @see http://chip.org
 *       <p/>
 *       NOTICE: This software comes with NO guarantees whatsoever and is
 *       licensed as Lgpl Open Source
 * @see http://www.gnu.org/licenses/lgpl.html
 *
 * NB: this is a case class to get a structural equality contract in hashCode and equals, mostly for testing
 */

//todo the QEP uses these as possible status types to support arbitrarily old databases. In 4.0 we could cut that dependency, or could separate out the statuses and move the rest of this class to the adatpaer
//todo SHRINE2020-1369 - move this to the adapter's i2b2 protocol
final case class QueryResult (
                               resultId: Long,
                               instanceId: Long,
                               resultType: Option[ResultOutputType],
                               setSize: Long,
                               startDate: Option[XMLGregorianCalendar],
                               endDate: Option[XMLGregorianCalendar],
                               description: Option[String], // this is used for the adapter name. If this thing stays around todo rename, not an option
                               statusType: StatusType,
                               statusMessage: Option[String],
                               problem: Option[Problem] = None,
                               breakdowns: Map[ResultOutputType,I2b2Result] = Map.empty
) extends XmlMarshaller with I2b2Marshaller with Loggable {

  //only used in tests
  def this(
            resultId: Long,
            instanceId: Long,
            resultType: ResultOutputType,
            setSize: Long,
            startDate: XMLGregorianCalendar,
            endDate: XMLGregorianCalendar,
            statusType: QueryResult.StatusType) = {
    this(
      resultId,
      instanceId,
      Option(resultType),
      setSize,
      Option(startDate),
      Option(endDate),
      None, //description
      statusType,
      None) //statusMessage
  }

  def this(
            resultId: Long,
            instanceId: Long,
            resultType: ResultOutputType,
            setSize: Long,
            startDate: XMLGregorianCalendar,
            endDate: XMLGregorianCalendar,
            description: String,
            statusType: QueryResult.StatusType) = {
    this(
      resultId,
      instanceId,
      Option(resultType),
      setSize,
      Option(startDate),
      Option(endDate),
      Option(description),
      statusType,
      None) //statusMessage
  }

  def resultTypeIs(testedResultType: ResultOutputType): Boolean = resultType match {
    case Some(rt) => rt == testedResultType
    case _ => false
  }

  import QueryResult._

  //NB: Fragile, non-type-safe ==
  def isError: Boolean = statusType == StatusType.Error

  def elapsed: Option[Long] = {
    def inMillis(xmlGc: XMLGregorianCalendar) = xmlGc.toGregorianCalendar.getTimeInMillis

    for {
      start <- startDate
      end <- endDate
    } yield inMillis(end) - inMillis(start)
  }

  //Sorting isn't strictly necessary, but makes deterministic unit testing easier.
  //The number of breakdowns will be at most 4, so performance should not be an issue.
  private def sortedBreakdowns: Seq[I2b2Result] = {
    breakdowns.values.toSeq.sortBy(_.resultType.name)
  }

  override def toI2b2: NodeSeq = {
    import net.shrine.xml.OptionEnrichments._

    XmlUtil.stripWhitespace {
      <query_result_instance>
        <result_instance_id>{ resultId }</result_instance_id>
        <query_instance_id>{ instanceId }</query_instance_id>
        { description.toXml(<description/>) }
        {
          resultType.fold( ResultOutputType.ERROR.toI2b2NameOnly("") ){ rt =>
            if(rt.isBreakdown) rt.toI2b2NameOnly()
            else if (rt.isError) rt.toI2b2NameOnly()  //The result type can be an error
            else if (statusType.isError) rt.toI2b2NameOnly() //Or the status type can be an error
            else rt.toI2b2
          }
        }
        <set_size>{ setSize }</set_size>
        { startDate.toXml(<start_date/>) }
        { endDate.toXml(<end_date/>) }
        <query_status_type>
          <name>{ statusType }</name>
          { statusType.toI2b2(this) }
        </query_status_type>
        {
          //NB: Deliberately use Shrine XML format instead of the i2b2 one.  Adding breakdowns to i2b2-format XML here is deviating from the i2b2 XSD schema in any case,
          //so if we're going to do that, let's produce saner XML.
          sortedBreakdowns.map(_.toXml.head).map(XmlUtil.renameRootTag("breakdown_data"))
        }
      </query_result_instance>
    }
  }

  override def toXml: NodeSeq = XmlUtil.stripWhitespace {
    import net.shrine.xml.OptionEnrichments._

    <queryResult>
      <resultId>{ resultId }</resultId>
      <instanceId>{ instanceId }</instanceId>
      { resultType.toXml(_.toXml) }
      <setSize>{ setSize }</setSize>
      { startDate.toXml(<startDate/>) }
      { endDate.toXml(<endDate/>) }
      { description.toXml(<description/>) }
      <status>{ statusType }</status>
      { statusMessage.toXml(<statusMessage/>) }
      {
        //Sorting isn't strictly necessary, but makes deterministic unit testing easier.
        //The number of breakdowns will be at most 4, so performance should not be an issue.
        sortedBreakdowns.map(_.toXml)
      }
      { problem.map(_.toXml).getOrElse("") }
    </queryResult>
  }

  def withId(id: Long): QueryResult = copy(resultId = id)

  def withInstanceId(id: Long): QueryResult = copy(instanceId = id)

  def modifySetSize(f: Long => Long): QueryResult = withSetSize(f(setSize))

  def withSetSize(size: Long): QueryResult = copy(setSize = size)

  def withDescription(desc: String): QueryResult = copy(description = Option(desc))

  def withResultType(resType: ResultOutputType): QueryResult = copy(resultType = Option(resType))

  def withBreakdown(breakdownData: I2b2Result): QueryResult = copy(breakdowns = breakdowns + (breakdownData.resultType -> breakdownData))

  def withBreakdowns(newBreakdowns: Map[ResultOutputType, I2b2Result]): QueryResult = copy(breakdowns = newBreakdowns)

  def withStatus(
                  statusType: StatusType,
                  statusMessage: Option[String]): QueryResult = copy(
                                                                      statusType = statusType,
                                                                      statusMessage = statusMessage
                                                                    )

  def createUpdateResult(resultProgress: ResultProgress,
                         crcQueryInstanceId: Long,
                         obfuscatingParameters: Option[ObfuscatingParameters]
                        ): UpdateResult = statusType match {
    case finished: StatusType if finished == StatusType.Finished => //This is the only successful done state
      UpdateResultWithCount(
        resultProgress.toCrcResult(
          count = setSize.toInt,
          crcQueryInstanceId = crcQueryInstanceId,
          statusMessage = statusMessage,
          breakdowns = I2b2Result.toV26Breakdowns(breakdowns),
          resultMetadata = ResultMetadata(obfuscatingParameters)
        )
      )
    case error: StatusType if error.isDone => //All other done states are errors
      problem.fold{ //no problem
        UpdateResultWithError(
          resultProgress.toCrcError(
            problem = ProblemNotYetEncoded(s"QueryResult with ${error.name} status and ${statusMessage} but no problem."),
            statusMessage = statusMessage,
            crcQueryInstanceId = Option(crcQueryInstanceId),
            resultMetadata = ResultMetadata(obfuscatingParameters)
          )
        )
      } {
        case rp: RawProblem =>
          UpdateResultWithError(
            resultProgress.toCrcError(
              problem = rp,
              statusMessage = statusMessage,
              crcQueryInstanceId = Option(crcQueryInstanceId),
              resultMetadata = ResultMetadata(obfuscatingParameters)
            )
          )
        case xmlProblemDigest: XmlProblemDigest => //todo this may not ever happen . XmlProblemDigest should only exist in the QEP's database
          warn(s"Encountered an XmlProblemDigest in QueryResult")
          UpdateResultWithError(
            resultProgress.toErrorFromJsonProblemDigest(
              problem = JsonProblemDigest(xmlProblemDigest),
              status = ResultStatus.ErrorFromCrc,
              statusMessage = statusMessage,
              crcQueryInstanceId = Option(crcQueryInstanceId),
              resultMetadata = ResultMetadata(obfuscatingParameters),
              adapterTime = DateStamp.now
            )
          )
        case p => throw new IllegalStateException(s"Encountered a ${p.getClass.getSimpleName} in QueryResult")
      }
    case _ => //Everything else is some flavor of "QUEUED - Ask the CRC Again Later."
      UpdateResultWithProgress(
        resultProgress.withStatus(status = ResultStatus.QueuedByCRC, statusMessage = statusMessage, crcQueryInstanceId = Option(crcQueryInstanceId), resultMetadata = resultProgress.resultMetadata)
      )
  }

  //todo SHRINE2020-1365 when you have the result id
  def createUpdateCrcQueuedResult(queryId: QueryId, crcQueryInstanceId: Long, resultMetadata: ResultMetadata):UpdateCrcQueuedResult = statusType match {
    case finished:StatusType if finished == StatusType.Finished => //Only successful done state

      UpdateCrcQueuedResultWithCount(queryId = queryId, adapterNodeKey = NodeKey.localNodeKey, count = setSize.toInt, crcQueryInstanceId = crcQueryInstanceId, breakdowns = I2b2Result.toV26Breakdowns(breakdowns), resultMetadata = resultMetadata, statusMessage = statusMessage, adapterTime = DateStamp.now)
    case error:StatusType if error.isDone => //All other done states are errors
      UpdateCrcQueuedResultWithError.create(
        queryId = queryId,
        adapterNodeKey = NodeKey.localNodeKey,
        problem = problem.getOrElse(ProblemNotYetEncoded(s"QueryResult with ${error.name} status but no problem.")),
        status = ResultStatus.ErrorFromCrc,
        statusMessage = statusMessage,
        crcQueryInstanceId = Option(crcQueryInstanceId),
        resultMetadata = resultMetadata,
        adapterTime = DateStamp.now
      )
    case _ => //Everything else is some flavor of "QUEUED - Ask the CRC Again Later."
      UpdateCrcQueuedResultWithProgress(queryId = queryId, adapterNodeKey = NodeKey.localNodeKey, status = ResultStatus.QueuedByCRC, statusMessage = statusMessage, crcQueryInstanceId = Option(crcQueryInstanceId), resultMetadata, adapterTime = DateStamp.now)
  }

  def consolidateWithError(queryResult: QueryResult): QueryResult ={
    copy(
      description = queryResult.description,
      statusMessage = queryResult.statusMessage,
      problem = queryResult.problem
    )
  }
}

object QueryResult {

  val defaultI2b2Id: Some[Int] = Some(-1)

  final case class StatusType(
                               name: String,
                               isDone: Boolean,
                               i2b2Id: Option[Int] = defaultI2b2Id, //todo probably should not be an Option, just a default
                               isError: Boolean = false,
                               private val doToI2b2: QueryResult => NodeSeq = StatusType.defaultToI2b2,
                               isCrcCallCompleted:Boolean = true //todo OK to remove. Always true
    ) extends StatusType.Value {

    def crcPromisedToFinishAfterReply: Boolean = isCrcCallCompleted && !isDone

    def toI2b2(queryResult: QueryResult): NodeSeq = doToI2b2(queryResult)
  }

  object StatusType extends SEnum[StatusType] {
    private val defaultToI2b2: QueryResult => NodeSeq = { queryResult =>
      val i2b2Id: Int = queryResult.statusType.i2b2Id.getOrElse{
        throw new IllegalStateException(s"queryResult.statusType ${queryResult.statusType} has no i2b2Id")
      }
      <status_type_id>{ i2b2Id }</status_type_id><description>{ queryResult.statusType.name }</description>
    }

    val noMessage:NodeSeq = null
    val Error: StatusType = StatusType("ERROR", isDone = true,  None, isError = true, { queryResult =>
      (queryResult.statusMessage, queryResult.problem) match {
        case (Some(msg),Some(pd)) => <description>{ if(msg != "ERROR") msg else pd.summary }</description> ++ pd.toXml
        case (Some(msg),None) => <description>{ msg }</description>
        case (None,Some(pd)) => <description>{ pd.summary }</description> ++ pd.toXml
        case (None, None) => noMessage
      }
    })

    //at least all of the states from https://github.com/i2b2/i2b2-core-server/blob/release-1709c-backup/edu.harvard.i2b2.crc/src/server/edu/harvard/i2b2/crc/ejb/QueryManagerBeanUtil.java
    val Finished: StatusType = StatusType("FINISHED", isDone = true, Some(3))

    //All of these are forms of "QUEUEUD - Ask the CRC again later"
    val Processing: StatusType = StatusType("PROCESSING", isDone = false, Some(2))
    val Running: StatusType = StatusType("RUNNING", isDone = false, Some(2))
    val Queued: StatusType = StatusType("QUEUED", isDone = false, Some(2))
    //TODO: What <status_type_id>s should these have?  Does anyone care?
    val Held: StatusType = StatusType("HELD", isDone = false)
    val SmallQueue: StatusType = StatusType("SMALL_QUEUE", isDone = false)
    val TimedOut: StatusType = StatusType("TIMEDOUT", isDone = false)
    val MediumQueue: StatusType = StatusType("MEDIUM_QUEUE", isDone = false)
    val LargeQueue: StatusType = StatusType("LARGE_QUEUE", isDone = false)
    //three new states from i2b2 1.07.8.b
    val MediumQueueRunning: StatusType = StatusType("MEDIUM_QUEUE_RUNNING", isDone = false)
    val LargeQueueRunning: StatusType = StatusType("LARGE_QUEUE_RUNNING", isDone = false)

    val HubWillSubmit: StatusType = StatusType("HUB_WILL_SUBMIT",isDone = true)

    //other final error states from a conversation with the i2b2 team October 17, 2018
    val NoMoreQueue: StatusType = StatusType("NO_MORE_QUEUE", isDone = true, isError = true, doToI2b2 = { queryResult =>
      queryResult.statusMessage.fold(noMessage){msg => <description>{ msg }</description>}
    })

    //from https://github.com/i2b2/i2b2-core-server/blob/38ba20a5b2cffa4bcfc397f19fa1a424c294264a/edu.harvard.i2b2.crc/src/server/edu/harvard/i2b2/crc/dao/setfinder/QueryStatusTypeId.java
    val Incomplete: StatusType = StatusType("INCOMPLETE", isDone = true, Some(5), isError = true, { queryResult =>
      queryResult.statusMessage.fold(noMessage){msg => <description>{ msg }</description>}
    })

    //from https://github.com/i2b2/i2b2-core-server/search?q=NEVER_FINISHED&unscoped_q=NEVER_FINISHED
    val NeverFinished: StatusType = StatusType("NEVER_FINISHED", isDone = true, isError = true, doToI2b2 = { queryResult =>
      queryResult.statusMessage.fold(noMessage){msg => <description>{ msg }</description>}
    })
    //from https://github.com/i2b2/i2b2-core-server/blob/38ba20a5b2cffa4bcfc397f19fa1a424c294264a/edu.harvard.i2b2.crc/src/server/edu/harvard/i2b2/crc/ejb/QueryInfoBean.java
    val Cancelled: StatusType = StatusType("CANCELLED", isDone = true, Some(9), isError = true, { queryResult =>
      queryResult.statusMessage.fold(noMessage){msg => <description>{ msg }</description>}
    })

  }

  def extractLong(nodeSeq: NodeSeq)(elemName: String): Long = (nodeSeq \ elemName).text.toLong

  private def parseDate(lexicalRep: String): Option[XMLGregorianCalendar] = XmlDateHelper.parseXmlTime(lexicalRep).toOption

  def elemAt(path: String*)(xml: NodeSeq): NodeSeq = path.foldLeft(xml)(_ \ _)

  def asText(path: String*)(xml: NodeSeq): String = elemAt(path: _*)(xml).text.trim

  def asResultOutputTypeOption(elemNames: String*)(breakdownTypes: Set[ResultOutputType], xml: NodeSeq): Option[ResultOutputType] = {
    import ResultOutputType.valueOf

    val typeName = asText(elemNames: _*)(xml)

    valueOf(typeName) orElse valueOf(breakdownTypes)(typeName)
  }

  def extractResultOutputType(xml: NodeSeq)(parse: NodeSeq => Try[ResultOutputType]): Option[ResultOutputType] = {
    val attempt = parse(xml)

    attempt.toOption
  }

  def extractProblemDigest(xml: NodeSeq):Option[XmlProblemDigest] = {

    val subXml = xml \ "problem"
    if(subXml.nonEmpty) Some(XmlProblemDigest.fromXml(xml))
    else None
  }

  def fromXml(breakdownTypes: Set[ResultOutputType])(xml: NodeSeq): QueryResult = {
    def extract(elemName: String): Option[String] = {
      Option((xml \ elemName).text.trim).filter(_.nonEmpty)
    }

    def extractDate(elemName: String): Option[XMLGregorianCalendar] = extract(elemName).flatMap(parseDate)

    val asLong = extractLong(xml) _

    import net.shrine.xml.NodeSeqEnrichments.Strictness._
    import Tries.sequence

    def extractBreakdowns(elemName: String): Map[ResultOutputType, I2b2Result] = {
      //noinspection ScalaUnnecessaryParentheses
      val mapAttempt = for {
        subXml <- xml.withChild(elemName)
        envelopes <- sequence(subXml.map(I2b2Result.fromXml(breakdownTypes)))
        mappings = envelopes.map(envelope => (envelope.resultType -> envelope))
      } yield Map.empty ++ mappings

      mapAttempt.getOrElse(Map.empty)
    }

    QueryResult(
      resultId = asLong("resultId"),
      instanceId = asLong("instanceId"),
      resultType = extractResultOutputType(xml \ "resultType")(ResultOutputType.fromXml),
      setSize = asLong("setSize"),
      startDate = extractDate("startDate"),
      endDate = extractDate("endDate"),
      description = extract("description"),
      statusType = StatusType.valueOf(asText("status")(xml)).get, //TODO: Avoid fragile .get call
      statusMessage = extract("statusMessage"),
      problem = extractProblemDigest(xml),
      breakdowns = extractBreakdowns("resultEnvelope")
    )
  }

  def fromI2b2(breakdownTypes: Set[ResultOutputType])(xml: NodeSeq): QueryResult = {
    def asLong = extractLong(xml) _

    def asTextOption(path: String*): Option[String] = elemAt(path: _*)(xml).headOption.map(_.text.trim)

    def asXmlGcOption(path: String): Option[XMLGregorianCalendar] = asTextOption(path).filter(_.nonEmpty).flatMap(parseDate)

    val statusText: String = asText("query_status_type", "name")(xml)
    val statusType: StatusType = StatusType.valueOf(statusText).getOrElse{
      throw new IllegalStateException(s"Unknown status name $statusText is not one of ${StatusType.values.mkString(", ")}")
    }
    val statusMessage: Option[String] = asTextOption("query_status_type", "description").map{description =>
      if(description == "ERROR") s"The I2B2 CRC responded with status type $statusType and a message of '$description'"
      else description
    }
    //todo shrine shouldn't do this anymore ... See if it can be removed in SHRINE2020-1368
    val encodedProblemDigest = extractProblemDigest(xml \ "query_status_type")
    val problemDigest = if (encodedProblemDigest.isDefined) encodedProblemDigest
                        else if (statusType.isError)
                          Some(XmlProblemDigest.create(ErrorStatusFromCrc(statusMessage,xml.toString())))
                        else None

    case class Filling(
                        resultType:Option[ResultOutputType],
                        setSize:Long,
                        startDate:Option[XMLGregorianCalendar],
                        endDate:Option[XMLGregorianCalendar]
                      )

    val filling = if(!statusType.isError) {
      val resultType: Option[ResultOutputType] = extractResultOutputType(xml \ "query_result_type")(ResultOutputType.fromI2b2)
      val setSize = asLong("set_size")
      val startDate = asXmlGcOption("start_date")
      val endDate = asXmlGcOption("end_date")
      Filling(resultType,setSize,startDate,endDate)
    }
    else {
      val resultType = None
      val setSize = 0L
      val startDate = None
      val endDate = None
      Filling(resultType,setSize,startDate,endDate)
    }

    QueryResult(
      resultId = asLong("result_instance_id"),
      instanceId = asLong("query_instance_id"),
      resultType = filling.resultType,
      setSize = filling.setSize,
      startDate = filling.startDate,
      endDate = filling.endDate,
      description = asTextOption("description"),
      statusType = statusType,
      statusMessage = statusMessage,
      problem = problemDigest
    )
  }

  def errorResult(description: Option[String], statusMessage: String, problem:Problem):QueryResult = {
    QueryResult(
      resultId = 0L,
      instanceId = 0L,
      resultType = None,
      setSize = 0L,
      startDate = None,
      endDate = None,
      description = description,
      statusType = StatusType.Error,
      statusMessage = Option(statusMessage),
      problem = Option(problem))
  }

  def errorResult(description: Option[String], statusMessage: String,problem:RawProblem):QueryResult = {

    QueryResult(
      resultId = 0L,
      instanceId = 0L,
      resultType = None,
      setSize = 0L,
      startDate = None,
      endDate = None,
      description = description,
      statusType = StatusType.Error,
      statusMessage = Option(statusMessage),
      problem = Option(problem))
  }

  /**
   * For reconstituting errorResults from a database
   */
  def errorResult(description:Option[String], statusMessage:String, codec:String,stampText:String, summary:String, digestDescription:String,detailsXml:NodeSeq): QueryResult = {
    // This would require parsing the stamp text to change, and without a standard locale that's nigh impossible.
    // If this is replaced with real problems, then this can be addressed then. For now, passing on zero is the best bet.
    val problemDigest = XmlProblemDigest(codec,stampText,summary,digestDescription,detailsXml,0)

    QueryResult(
      resultId = 0L,
      instanceId = 0L,
      resultType = None,
      setSize = 0L,
      startDate = None,
      endDate = None,
      description = description,
      statusType = StatusType.Error,
      statusMessage = Option(statusMessage),
      problem = Option(problemDigest))
  }
}

case class ErrorStatusFromCrc(messageFromCrC:Option[String], xmlResponseFromCrc: String) extends AbstractProblem(ProblemSources.Adapter) {
  override def logLevel: Level = Level.WARN
  override val summary: String = "The I2B2 CRC reported an internal error."
  override val description:String = s"The I2B2 CRC responded with status type ERROR"
  override val detailsText: Option[String] = Some(s"${messageFromCrC.fold(" No message from CRC")(message => s"Message from CRC is '$message'")}. CRC's Response is $xmlResponseFromCrc")
}


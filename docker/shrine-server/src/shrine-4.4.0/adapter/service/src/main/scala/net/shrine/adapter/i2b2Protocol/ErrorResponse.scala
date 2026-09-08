package net.shrine.adapter.i2b2Protocol

import net.shrine.log.Loggable
import net.shrine.problem.{JsonProblemDigest, Problem, RawProblem, XmlProblemDigest}
import net.shrine.protocol.i2b2.serialization.{I2b2Unmarshaller, XmlUnmarshaller}
import net.shrine.protocol.i2b2.ErrorStatusFromCrc
import net.shrine.protocol.version.DateStamp
import net.shrine.protocol.version.v2.{ResultProgress, ResultStatus, UpdateResultWithError}
import net.shrine.xml.{NodeSeqEnrichments, XmlUtil}

import scala.util.Try
import scala.util.control.NonFatal
import scala.xml.{Node, NodeBuffer, NodeSeq}

/**
 * @author Bill Simons
 * @since 4/25/11
 * @see http://cbmi.med.harvard.edu
 * @see http://chip.org
 *       <p/>
 *       NOTICE: This software comes with NO guarantees whatsoever and is
 *       licensed as Lgpl Open Source
 * @see http://www.gnu.org/licenses/lgpl.html
 *
 * NB: Now a case class for structural equality
 */
final case class ErrorResponse(errorMessage: String, problem:Problem) extends ShrineResponse {

  override protected def status: NodeSeq = {
    val buffer = new NodeBuffer
    buffer += <status type="ERROR">{ errorMessage }</status>
    buffer += problem.toXml
  }

  override protected def i2b2MessageBody: NodeSeq = null

  import ErrorResponse.rootTagName

  override def toXml: Node = { XmlUtil.stripWhitespace {
      val xml = XmlUtil.renameRootTag(rootTagName) {
        <errorResponse>
          <message>{ errorMessage }</message>
          {problem.toXml}
        </errorResponse>
      }
      xml
    }
  }

  def toUpdateResult(resultProgress:ResultProgress): UpdateResultWithError = {
    problem match {
      case rp: RawProblem =>
        UpdateResultWithError(
          resultProgress.toError(
            problem = rp,
            status = ResultStatus.ErrorFromCrc,
            statusMessage = Option(errorMessage),
            crcQueryInstanceId = resultProgress.crcQueryInstanceId,
            resultMetadata = resultProgress.resultMetadata
          )
        )
      case xmlProblemDigest: XmlProblemDigest =>
        UpdateResultWithError(
          resultProgress.toErrorFromJsonProblemDigest(
            problem = JsonProblemDigest(xmlProblemDigest),
            status = ResultStatus.ErrorFromCrc,
            statusMessage = Option(errorMessage),
            crcQueryInstanceId = resultProgress.crcQueryInstanceId,
            adapterTime = DateStamp.now,
            resultMetadata = resultProgress.resultMetadata
          )
        )
      case p => throw new IllegalStateException(s"Encountered a ${p.getClass.getSimpleName} in QueryResult")
    }
  }
}

object ErrorResponse extends XmlUnmarshaller[ErrorResponse] with I2b2Unmarshaller[ErrorResponse] with Loggable {
  val rootTagName = "errorResponse"

  def apply(problem:Problem) = new ErrorResponse(problem.summary,problem)

  def apply(errorMessage: String,problem:RawProblem) = new ErrorResponse(errorMessage,problem)

  override def fromXml(xml: NodeSeq): ErrorResponse = {

    val messageXml = xml \ "message"

    //NB: Fail fast
    require(messageXml.nonEmpty)

    val problemDigest = XmlProblemDigest.fromXml(xml)

    ErrorResponse(XmlUtil.trim(messageXml),problemDigest)
  }

  override def fromI2b2(xml: NodeSeq): ErrorResponse = {
    import NodeSeqEnrichments.Strictness._

    //todo what determines parseFormatA vs parseFormatB when written? It looks like our ErrorResponses use A.

    def parseFormatA: Try[ErrorResponse] = {
      for {
        statusXml <- xml withChild "response_header" withChild "result_status" withChild "status"
        resultStatusXml <- xml withChild "response_header" withChild "result_status"
        typeText <- statusXml attribute "type"    if typeText == "ERROR" //NB: Fail fast{
                                                    statusMessage = XmlUtil.trim(statusXml)
                                                    problemDigest = XmlProblemDigest.fromXml(resultStatusXml)
      } yield {
        ErrorResponse(statusMessage,problemDigest)
      }
    }

    def parseFormatB: Try[ErrorResponse] = {
      for {
        conditionXml <- xml withChild "message_body" withChild "response" withChild "status" withChild "condition"
        typeText <- conditionXml attribute "type" if typeText == "ERROR"
                                                    statusMessage = XmlUtil.trim(conditionXml)
                                                    problemDigest = XmlProblemDigest.create(ErrorStatusFromCrc(Option(conditionXml.toString()),xml.toString))//here's another place where an ERROR can have no ProblemDigest

      } yield {
        ErrorResponse(statusMessage,problemDigest)
      }
    }

    parseFormatA.recoverWith { case NonFatal(e) =>
      warn(s"Encountered a problem while parsing an error from I2B2 with 'format A', trying 'format B' ${xml.toString()}",e)
      parseFormatB
    }.get
  }

  /**
   *
   * <ns5:response>
   * <message_body>
   * <ns4:response xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:type="ns4:crc_xml_result_responseType">
   * <status>
   * <condition type="ERROR">Query result instance id 3126 not found</condition>
   * </status>
   * </ns4:response>
   * </message_body>
   * </ns5:response>
   */
}
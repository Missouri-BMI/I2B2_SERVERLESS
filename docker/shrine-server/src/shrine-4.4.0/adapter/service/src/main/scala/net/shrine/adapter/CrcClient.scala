package net.shrine.adapter

import ch.qos.logback.classic.Level
import net.shrine.adapter.i2b2Protocol.{ErrorResponse, HiveCredentials, ShrineResponse}
import net.shrine.http4s.client.legacy.Poster
import net.shrine.log.Loggable
import org.xml.sax.SAXParseException

import scala.xml.{Elem, NodeSeq}
import net.shrine.protocol.i2b2.ShrineRequest
import net.shrine.problem.{AbstractProblem, ProblemSources}
import net.shrine.xml.XmlUtil

import scala.util.control.NonFatal

/**
 * @author Bill Simons
 * @since 4/11/11
 * @see http://cbmi.med.harvard.edu
 * @see http://chip.org
 *       <p/>
 *       NOTICE: This software comes with NO guarantees whatsoever and is
 *       licensed as Lgpl Open Source
 * @see http://www.gnu.org/licenses/lgpl.html
 */
//todo use IO SHRINE2020-1277
final case class CrcClient[T <: ShrineRequest, V <: ShrineResponse](
                                                                    poster: Poster,
                                                                    hiveCredentials: HiveCredentials
                                                                  ) extends Loggable {
//todo is there a way to differentiate between the bad and the ugly ?
  private[adapter] def parseShrineErrorResponseWithFallback(xmlResponseFromCrc: String, shrineNetworkQueryId:Long, parseShrineResponse: (NodeSeq,Long) => Either[ErrorResponse,V]):Either[ErrorResponse,V] = {
    val crcXml: Either[ErrorResponse, Elem] = try {
       Right(XmlUtil.loadString(xmlResponseFromCrc))
    } catch {
      case saxx: SAXParseException => Left(ErrorResponse(CannotParseXmlFromCrc(saxx, xmlResponseFromCrc)))
      case NonFatal(e) =>
        error(s"Error parsing response from CRC: ", e)
        Left(ErrorResponse (ExceptionWhileLoadingCrcResponse(e, xmlResponseFromCrc))) //ugly
    }
    val response: Either[ErrorResponse, V] = crcXml.flatMap(xml => try{
      parseShrineResponse(xml,shrineNetworkQueryId)  //good, or parseShrineResponse's bad or ugly
    }catch{
      case saxx: SAXParseException => Left(ErrorResponse(CannotParseXmlFromCrc(saxx, xmlResponseFromCrc)))
      case NonFatal(e) =>
        error(s"Response from the CRC did not contain the expected XML. Attempting to interpret it as an ErrorResponse: ", e)
        Left(ErrorResponse.fromI2b2(xml))  //bad
    })
    response
  }

  def callCrc(request: T): String = {
    info(s"Sending request to the CRC at '${poster.url}': ${request.toI2b2String}")

    val crcResponse = logDuration(s"Calling the CRC at '${poster.url}'")(debug(_)) {
      //Wrap exceptions in a more descriptive form, to enable sending better error messages back to the legacy web client
      try { poster.post(request.toI2b2String) }
      catch {
        case NonFatal(e) => throw CrcInvocationException(poster.url, request, e)
      }
    }
    if (crcResponse.statusCode == 200) {
      crcResponse.body
    } else {
      throw BadStatusFromCrcException(crcResponse.statusCode,crcResponse.body)
    }
  }

  //todo in SHRINE2020-1277
  //todo transform is at the call site until everything can just use CrcClient, then make parseShrineResponse a class member
  //todo rename callCrc to request and claim the name callCrc for this one
  def request(parseShrineResponse: (NodeSeq,Long) =>  Either[ErrorResponse,V])(request:T,shrineNetworkQueryId:Long):Either[ErrorResponse,V] = {
    val i2b2Response = callCrc(request)

    parseShrineErrorResponseWithFallback(i2b2Response, shrineNetworkQueryId, parseShrineResponse)
  }
}

case class CannotParseXmlFromCrc(saxx:SAXParseException,xmlResponseFromCrc: String) extends AbstractProblem(ProblemSources.Adapter) {
  override def logLevel: Level = Level.WARN
  override val throwable: Option[SAXParseException] = Some(saxx)
  override val summary: String = "Could not parse response from CRC."
  override val description:String = s"Error while parsing the response from the CRC."
  override def detailsText: Option[String] = Some(s"Response is $xmlResponseFromCrc")
}

case class ExceptionWhileLoadingCrcResponse(t:Throwable,xmlResponseFromCrc: String) extends AbstractProblem(ProblemSources.Adapter) {
  override def logLevel: Level = Level.WARN
  override val throwable: Option[Throwable] = Some(t)
  override val summary: String = "Unanticipated exception with response from CRC."
  override val description:String = s"Error while parsing the response from the CRC."
  override def detailsText: Option[String] = Some(s"Response is $xmlResponseFromCrc")
}

case class BadStatusFromCrcException(status:Int,responseString:String) extends Exception(s"Got status code $status from the CRC, expected 200 OK. $responseString")
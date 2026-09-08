package net.shrine.adapter.i2b2Protocol

import net.shrine.adapter.{DoneButHasNoResultsForQueuedQuery, NoResultsForQueuedQuery, i2b2Protocol}
import net.shrine.protocol.i2b2.QueryResult.StatusType
import net.shrine.protocol.i2b2.serialization.XmlUnmarshaller
import net.shrine.protocol.i2b2.{ErrorStatusFromCrc, QueryResult}
import net.shrine.protocol.version.v2.{ObfuscatingParameters, ResultMetadata, ResultStatus, UpdateCrcQueuedResult, UpdateCrcQueuedResultWithError}
import net.shrine.protocol.version.{NodeKey, QueryId}
import net.shrine.xml.OptionEnrichments.OptionHasToXml
import net.shrine.xml.{XmlDateHelper, XmlUtil}

import javax.xml.datatype.XMLGregorianCalendar
import scala.annotation.unused
import scala.util.control.NonFatal
import scala.xml.{Node, NodeSeq}

/**
 * @author Bill Simons
 * @since 4/13/11
 * @see http://cbmi.med.harvard.edu
 * @see http://chip.org
 *       <p/>
 *       NOTICE: This software comes with NO guarantees whatsoever and is
 *       licensed as Lgpl Open Source
 * @see http://www.gnu.org/licenses/lgpl.html
 *
 * NB: this is a case class to get a structural equality contract in hashCode and equals, mostly for testing
 */
final case class ReadQueryInstancesResponse(
                                             queryMasterId: Long,
                                             userId: String,
                                             groupId: String,
                                             queryInstances: Seq[QueryInstance]
                                           ) extends ShrineResponse {

  override protected def i2b2MessageBody: Node = XmlUtil.stripWhitespace {
    <ns5:response xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:type="ns5:instance_responseType">
      <status>
        <condition type="DONE">DONE</condition>
      </status>
      {
        queryInstances.map { queryInstance =>
          XmlUtil.stripWhitespace {
            <query_instance>
              <query_instance_id>{ queryInstance.queryInstanceId }</query_instance_id>
              <query_master_id>{ queryMasterId }</query_master_id>
              <user_id>{ userId }</user_id>
              <group_id>{ groupId }</group_id>
              <batch_mode>{ queryInstance.queryStatus.name }</batch_mode>
              <start_date>{ queryInstance.startDate }</start_date>
              {queryInstance.endDate.toXml(<end_date/>)}
              <query_status_type>
                <status_type_id>{queryInstance.queryStatus.i2b2Id.getOrElse(QueryResult.defaultI2b2Id.get)}</status_type_id>
                <name>{queryInstance.queryStatus.name}</name>
                <description>{queryInstance.queryStatus.name}</description>
              </query_status_type>
            </query_instance>
          }
        }
      }
    </ns5:response>
  }

  override def toXml: Node = XmlUtil.stripWhitespace {
    <readQueryInstancesResponse>
      <masterId>{ queryMasterId }</masterId>
      <userId>{ userId }</userId>
      <groupId>{ groupId }</groupId>
      {
        queryInstances.map { queryInstance =>
          XmlUtil.stripWhitespace {
            <queryInstance>
              <instanceId>{ queryInstance.queryInstanceId }</instanceId>
              <startDate>{ queryInstance.startDate }</startDate>
              {queryInstance.endDate.toXml(<endDate/>)}
              <queryStatusType>{ queryInstance.queryStatus.name }</queryStatusType>
            </queryInstance>
          }
        }
      }
    </readQueryInstancesResponse>
  }

  def withInstances(newInstances: Seq[QueryInstance]): ReadQueryInstancesResponse = this.copy(queryInstances = newInstances)

  def createUpdateResult(queryId:QueryId,obfuscatingParameters: ObfuscatingParameters):Option[UpdateCrcQueuedResult] = {
    val maybeError: Option[QueryInstance] = queryInstances.collectFirst{
      case qi:QueryInstance if qi.queryStatus.isError => qi
    }

    maybeError.map{ errorQueryInstance =>
      UpdateCrcQueuedResultWithError.create(
        queryId = queryId,
        adapterNodeKey = NodeKey.localNodeKey,
        problem = ErrorStatusFromCrc(Some(errorQueryInstance.queryStatus.name),this.toI2b2String),
        status = ResultStatus.ErrorFromCrc,
        statusMessage = Some(errorQueryInstance.queryStatus.name),
        crcQueryInstanceId = None,
        resultMetadata = ResultMetadata(Option(obfuscatingParameters))
      )
    }
  }
}

object ReadQueryInstancesResponse extends XmlUnmarshaller[ReadQueryInstancesResponse] {
  def fromI2b2(nodeSeq: NodeSeq, @unused queryId:Long): Either[ErrorResponse,ReadQueryInstancesResponse] = {
    val queryInstances = (nodeSeq \ "message_body" \ "response" \ "query_instance").map { x =>
      val queryInstanceId = (x \ "query_instance_id").text
      val queryMasterId = (x \ "query_master_id").text
      val userId = (x \ "user_id").text
      val groupId = (x \ "group_id").text
      val statusTypeText = (x \ "batch_mode").text
      val queryStatus: StatusType = QueryResult.StatusType.valueOf(statusTypeText).getOrElse(throw UnknownQueryStatusTypeException(statusTypeText))
      val startDate = XmlDateHelper.parseXmlTime((x \ "start_date").text).get //NB: Preserve old exception-throwing behavior for now
      val endDate = extractDate(x,"end_date") //NB: Preserve old exception-throwing behavior for now
      i2b2Protocol.QueryInstance(queryInstanceId, queryMasterId, userId, groupId, startDate, endDate, queryStatus)
    }

    queryInstances.headOption.map( firstInstance =>
      Right(ReadQueryInstancesResponse(firstInstance.queryMasterId.toLong, firstInstance.userId, firstInstance.groupId, queryInstances))
    ).getOrElse{
      try {
        val condition: NodeSeq = nodeSeq \ "message_body" \ "response" \ "status" \ "condition"
        if (condition.text == "DONE") Left(ErrorResponse(DoneButHasNoResultsForQueuedQuery(XmlUtil.prettyPrint(nodeSeq.head).trim)))
        else Left(ErrorResponse(NoResultsForQueuedQuery(XmlUtil.prettyPrint(nodeSeq.head).trim, None)))
      }
      catch {
        case NonFatal(x) => Left(ErrorResponse(NoResultsForQueuedQuery(XmlUtil.prettyPrint(nodeSeq.head).trim, Option(x))))
      }
    }
  }

  override def fromXml(nodeSeq: NodeSeq): ReadQueryInstancesResponse = {
    val masterId = (nodeSeq \ "masterId").text.toLong
    val userId = (nodeSeq \ "userId").text
    val groupId = (nodeSeq \ "groupId").text

    val queryInstances = (nodeSeq \ "queryInstance").map { x =>
      val queryInstanceId = (x \ "instanceId").text
      val startDate = XmlDateHelper.parseXmlTime((x \ "startDate").text).get //NB: Preserve old exception-throwing behavior for now
      val endDate = extractDate(x,"endDate") //NB: Preserve old exception-throwing behavior for now
      val statusType = QueryResult.StatusType.valueOf(asText("queryStatusType")(x)).get //TODO: Avoid fragile .get call

      i2b2Protocol.QueryInstance(queryInstanceId, masterId.toString, userId, groupId, startDate, endDate, statusType)
    }

    ReadQueryInstancesResponse(masterId, userId, groupId, queryInstances)
  }

  def extractDate(xml: NodeSeq,elemName: String): Option[XMLGregorianCalendar] = extract(xml,elemName).map(XmlDateHelper.parseXmlTime).map(_.get)
  def extract(xml: NodeSeq,elemName: String): Option[String] = { Option((xml \ elemName).text.trim).filter(_.nonEmpty) }

  def elemAt(path: String*)(xml: NodeSeq): NodeSeq = path.foldLeft(xml)(_ \ _)
  def asText(path: String*)(xml: NodeSeq): String = elemAt(path: _*)(xml).text.trim

}

case class UnknownQueryStatusTypeException(statusTypeText:String)
  extends Exception(s"'$statusTypeText' is not one of ${StatusType.values.mkString(", ")}")

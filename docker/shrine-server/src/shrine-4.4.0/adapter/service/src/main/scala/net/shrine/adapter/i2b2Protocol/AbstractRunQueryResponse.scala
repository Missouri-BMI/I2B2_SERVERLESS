package net.shrine.adapter.i2b2Protocol

import net.shrine.adapter.i2b2Protocol
import net.shrine.protocol.i2b2.query.I2b2QueryDefinition
import net.shrine.protocol.i2b2.{I2b2XmlUnmarshaller, QueryResult, ResultOutputType, ShrineXmlUnmarshaller}
import net.shrine.xml.{XmlDateHelper, XmlUtil}

import javax.xml.datatype.XMLGregorianCalendar
import scala.util.Try
import scala.xml.{Atom, Elem, NodeSeq}

/**
 * @author clint
 * @since Nov 29, 2012
 */
abstract class AbstractRunQueryResponse(
                                         rootTagName: String,
                                         val queryId: Long, //this is the network query id !
                                         val createDate: XMLGregorianCalendar,
                                         val userId: String,
                                         val groupId: String,
                                         val requestXml: I2b2QueryDefinition,
                                         val queryInstanceId: Long,
                                         val statusTypeName:String = "DONE"
                                       ) extends ShrineResponse with HasQueryResults {

  final def queryName: String = requestXml.name

  override protected final def i2b2MessageBody: NodeSeq = XmlUtil.stripWhitespace {
    <ns5:response xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:type="ns5:master_instance_result_responseType">
      <status>
        <condition type={ statusTypeName }>{ statusTypeName }</condition>
      </status>
      <query_master>
        <query_master_id>{ queryId }</query_master_id>
        <name>{ queryName }</name>
        <user_id>{ userId }</user_id>
        <group_id>{ groupId }</group_id>
        <create_date>{ createDate }</create_date>
        <request_xml>{ requestXml.toI2b2 }</request_xml>
      </query_master>
      <query_instance>
        <query_instance_id>{ queryInstanceId }</query_instance_id>
        <query_master_id>{ queryId }</query_master_id>
        <user_id>{ userId }</user_id>
        <group_id>{ groupId }</group_id>
        <query_status_type>
          <status_type_id>6</status_type_id>
          <name>COMPLETED</name>
          <description>COMPLETED</description>
        </query_status_type>
      </query_instance>
      {
        results.map(_.withInstanceId(queryInstanceId).toI2b2)
      }
    </ns5:response>
  }

  override final def toXml: NodeSeq = XmlUtil.stripWhitespace {
    XmlUtil.renameRootTag(rootTagName) {
      <placeHolder>
        <queryId>{ queryId }</queryId>
        <instanceId>{ queryInstanceId }</instanceId>
        <userId>{ userId }</userId>
        <groupId>{ groupId }</groupId>
        <requestXml>{ requestXml.toXml }</requestXml>
        <createDate>{ createDate }</createDate>
        <queryResults>
          {
            results.map(_.withInstanceId(queryInstanceId).toXml)
          }
        </queryResults>
      </placeHolder>
    }
  }
}

object AbstractRunQueryResponse {
  //
  //NB: Creatable trait and companion object implement the typeclass pattern:
  //http://www.casualmiracles.com/2012/05/03/a-small-example-of-the-typeclass-pattern-in-scala/
  //A typeclass is used here in place of an abstract method with multiple concrete implementations,
  //or another similar strategy. -Clint

  //But why? Why would you swing that big a hammer for code this simple? - Dave todo unwind this and just use an intermediate case class
  private trait Creatable[T] {
    def apply(queryId: Long, createDate: XMLGregorianCalendar, userId: String, groupId: String, requestXml: I2b2QueryDefinition, queryInstanceId: Long, results: Seq[QueryResult]): T
  }

  private object Creatable {
    implicit val runQueryResponseIsCreatable: Creatable[RunQueryResponse] = new Creatable[RunQueryResponse] {
      override def apply(queryId: Long, createDate: XMLGregorianCalendar, userId: String, groupId: String, requestXml: I2b2QueryDefinition, queryInstanceId: Long, results: Seq[QueryResult]): RunQueryResponse = {
        i2b2Protocol.RunQueryResponse(queryId, createDate, userId, groupId, requestXml, queryInstanceId, results.head)
      }
    }

    implicit val rawCrcRunQueryResponseIsCreatable: Creatable[RawCrcRunQueryResponse] = new Creatable[RawCrcRunQueryResponse] {
      override def apply(queryId: Long, createDate: XMLGregorianCalendar, userId: String, groupId: String, requestXml: I2b2QueryDefinition, queryInstanceId: Long, results: Seq[QueryResult]): RawCrcRunQueryResponse = {
        val queryResultMap = RawCrcRunQueryResponse.toQueryResultMap(results)

        RawCrcRunQueryResponse(queryId, createDate, userId, groupId, requestXml, queryInstanceId, queryResultMap)
      }
    }
  }

  abstract class Companion[R <: AbstractRunQueryResponse: Creatable] extends ShrineXmlUnmarshaller[R] with I2b2XmlUnmarshaller[R] {
    private val createResponse = implicitly[Creatable[R]]

//todo the whole "try" business didn't really work out. Much of the code just calls get(),
// todo and cats IO does a fine job handling the ugly side of interpreting the xml
    override def fromI2b2(breakdownTypes: Set[ResultOutputType])(nodeSeq: NodeSeq): Try[R] = {
      def firstChild(nodeSeq: NodeSeq) = nodeSeq.head.asInstanceOf[Elem].child.head

      val responseXml = nodeSeq \ "message_body" \ "response"

      val queryMasterXml = responseXml \ "query_master"

      val results = (responseXml \ "query_result_instance").map(QueryResult.fromI2b2(breakdownTypes))
      val queryId = (queryMasterXml \ "query_master_id").text.toLong
      val userId = (queryMasterXml \ "user_id").text
      val groupId = (queryMasterXml \ "group_id").text
      val createDateText = (queryMasterXml \ "create_date").text

      def asString(a: Atom[_]): String = a.data.asInstanceOf[String]

      def holdsString(a: Atom[_]): Boolean = a.data.isInstanceOf[String]

      //NB: We need to handle the query def situtation two different ways:
      // 1) Where the query def is an escaped String, as is returned by the CRC
      // 2) Where the query def is plain XML, as is provided by Shrine
      val queryDefAttempt = firstChild(queryMasterXml \ "request_xml") match {
        case a: Atom[_] if holdsString(a) => I2b2QueryDefinition.fromI2b2(asString(a))
        case xml: NodeSeq => I2b2QueryDefinition.fromI2b2(xml)
      }

      for {
        queryDef <- queryDefAttempt
        queryInstanceId <- Try((responseXml \ "query_instance" \ "query_instance_id").text.toLong)
        createDate <- XmlDateHelper.parseXmlTime(createDateText)
      } yield {
        createResponse(queryId, createDate, userId, groupId, queryDef, queryInstanceId, results)
      }
    }

    override def fromXml(breakdownTypes: Set[ResultOutputType])(nodeSeq: NodeSeq): Try[R] = {
      for {
        queryResults <- Try((nodeSeq \ "queryResults" \ "_").map(QueryResult.fromXml(breakdownTypes)))
        queryId <- Try((nodeSeq \ "queryId").text.toLong)
        createDate <- XmlDateHelper.parseXmlTime((nodeSeq \ "createDate").text)
        queryDef <- I2b2QueryDefinition.fromXml(nodeSeq \ "requestXml" \ "queryDefinition")
        instanceId <- Try((nodeSeq \ "instanceId").text.toLong)
      } yield {
        createResponse(
          queryId,
          createDate,
          (nodeSeq \ "userId").text,
          (nodeSeq \ "groupId").text,
          queryDef,
          instanceId,
          queryResults)
      }
    }
  }
}
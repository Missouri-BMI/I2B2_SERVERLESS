package net.shrine.adapter.i2b2Protocol

import net.shrine.crypto.SecureRandomSource
import net.shrine.protocol.i2b2.query.I2b2QueryDefinition
import net.shrine.protocol.i2b2.serialization.I2b2UnmarshallingHelpers
import net.shrine.protocol.i2b2.{AuthenticationInfo, I2b2XmlUnmarshaller, RequestType, ResultOutputType, ShrineRequest, ShrineXmlUnmarshaller}
import net.shrine.util.Tries
import net.shrine.xml.XmlUtil

import scala.concurrent.duration.Duration
import scala.util.Try
import scala.xml.{Elem, NodeSeq}

/**
 * @author Bill Simons
 * @since 3/9/11
 * @see http://cbmi.med.harvard.edu
 * @see http://chip.org
 *       <p/>
 *       NOTICE: This software comes with NO guarantees whatsoever and is
 *       licensed as Lgpl Open Source
 * @see http://www.gnu.org/licenses/lgpl.html
 *
 * NB: this is a case class to get a structural equality contract in hashCode and equals, mostly for testing
 */
final case class RunQueryRequest(
                                  override val projectId: String,
                                  override val waitTime: Duration,
                                  override val authn: AuthenticationInfo,
                                  networkQueryId: Long,
                                  outputTypes: Set[ResultOutputType],
                                  queryDefinition: I2b2QueryDefinition,
                                  nodeId: Option[XmlNodeName] = None
  ) extends ShrineRequest(projectId, waitTime, authn) with CrcRequest {

  override val crcRequestType: Option[CrcRequestType] = Option(CrcRequestTypes.QueryDefinitionRequestType)

  override val requestType: RequestType = RequestType(crcRequestType.get.name)

  //NB: Sort ResultOutputTypes, for deterministic testing
  private def sortedOutputTypes: Seq[ResultOutputType] = outputTypes.toSeq.sortBy(_.name)

  override def toXml: NodeSeq = XmlUtil.stripWhitespace {
    <runQuery>
      { headerFragment }
      <queryId>{ networkQueryId }</queryId>
      <outputTypes>
        { sortedOutputTypes.map(_.toXml) }
      </outputTypes>
      { queryDefinition.toXml }
      { nodeId.fold(NodeSeq.Empty)(_.toXml) }
    </runQuery>
  }

  protected override def i2b2MessageBody: NodeSeq = XmlUtil.stripWhitespace {
    <message_body>
      { i2b2PsmHeaderWithDomain }
      <ns4:request xsi:type="ns4:query_definition_requestType" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
        { queryDefinition.toI2b2 }
        { i2b2OutputList }
      </ns4:request>
    </message_body>
  }

  private def i2b2OutputList: NodeSeq = XmlUtil.stripWhitespace{
    <result_output_list>
      {
      for {
        (outputType, i) <- sortedOutputTypes.zipWithIndex
        priorityIndex = outputType.id.getOrElse(i + 1)
      } yield {
          <result_output priority_index={ priorityIndex.toString } name={ outputType.toString.toLowerCase }/>
      }
      }
    </result_output_list>
  }

  def withProject(proj: String): RunQueryRequest = this.copy(projectId = proj)

  def withAuthn(ai: AuthenticationInfo): RunQueryRequest = this.copy(authn = ai)

  private def withQueryDefinition(qDef: I2b2QueryDefinition): RunQueryRequest = this.copy(queryDefinition = qDef)

  def mapQueryDefinition(f: I2b2QueryDefinition => I2b2QueryDefinition): RunQueryRequest = this.withQueryDefinition(f(queryDefinition))
}

object RunQueryRequest extends I2b2XmlUnmarshaller[RunQueryRequest] with ShrineXmlUnmarshaller[RunQueryRequest] with ShrineRequestUnmarshaller with I2b2UnmarshallingHelpers {

  def apply(projectId: String,
  waitTime: Duration,
  authn: AuthenticationInfo,
  outputTypes: Set[ResultOutputType],
  queryDefinition: I2b2QueryDefinition
             ):RunQueryRequest = RunQueryRequest(
                                                  projectId,
                                                  waitTime,
                                                  authn,
                                                  SecureRandomSource.nextId(),
                                                  outputTypes,
                                                  queryDefinition
                                                )

  val neededI2b2Namespace = "http://www.i2b2.org/xsd/cell/crc/psm/1.1/"

  override def fromI2b2(breakdownTypes: Set[ResultOutputType])(xml: NodeSeq): Try[RunQueryRequest] = {
    val queryDefNode = xml \ "message_body" \ "request" \ "query_definition"

    val queryDefXml = queryDefNode.head match {
      //NB: elem.scope.getPrefix(neededI2b2Namespace) will return null if elem isn't part of a larger XML chunk that has
      //the http://www.i2b2.org/xsd/cell/crc/psm/1.1/ declared
      case elem: Elem => elem.copy(elem.scope.getPrefix(neededI2b2Namespace))
      case _ => throw new Exception("When unmarshalling a RunQueryRequest, encountered unexpected XML: '" + queryDefNode + "', <query_definition> might be missing.")
    }

    val attempt = for {
      projectId <- i2b2ProjectId(xml)
      waitTime <- i2b2WaitTime(xml)
      authn <- i2b2AuthenticationInfo(xml)
      outputTypes = determineI2b2OutputTypes(breakdownTypes)(xml \ "message_body" \ "request" \ "result_output_list")
      queryDef <- I2b2QueryDefinition.fromI2b2(queryDefXml)
    } yield {
      RunQueryRequest(
        projectId,
        waitTime,
        authn,
        outputTypes,
        queryDef
      )
    }
    
    attempt.map(addPatientCountXmlIfNecessary)
  }

  private def determineI2b2OutputTypes(breakdownTypes: Set[ResultOutputType])(nodeSeq: NodeSeq): Set[ResultOutputType] = {
    val sequence = (nodeSeq \ "result_output").flatMap { breakdownXml =>
      val breakdownName = XmlUtil.trim(breakdownXml \ "@name")

      ResultOutputType.valueOf(breakdownTypes)(breakdownName)
    }

    sequence.toSet
  }

  private def determineShrineOutputTypes(nodeSeq: NodeSeq): Set[ResultOutputType] = {
    val attempts: Seq[Try[ResultOutputType]] = (nodeSeq \ "resultType").map(ResultOutputType.fromXml)

    Tries.sequence(attempts).map(_.toSet).get
  }

  def addPatientCountXmlIfNecessary(req: RunQueryRequest): RunQueryRequest = {
    import ResultOutputType.PATIENT_COUNT_XML
    
    if (req.outputTypes.contains(PATIENT_COUNT_XML)) { req }
    else { req.copy(outputTypes = req.outputTypes + PATIENT_COUNT_XML) }
  }

  override def fromXml(breakdownTypes: Set[ResultOutputType])(xml: NodeSeq): Try[RunQueryRequest] = {
    import net.shrine.xml.NodeSeqEnrichments.Strictness._

    val attempt = for {
      projectId <- shrineProjectId(xml)
      waitTime <- shrineWaitTime(xml)
      authn <- shrineAuthenticationInfo(xml)
      queryId <- xml.withChild("queryId").map(XmlUtil.toLong)
      outputTypes <- xml.withChild("outputTypes").map(determineShrineOutputTypes)
      queryDef <- xml.withChild(I2b2QueryDefinition.rootTagName).flatMap(I2b2QueryDefinition.fromXml)
      nodeId <- XmlNodeName.fromXmlOption(xml \ "nodeId")
    } yield {
      RunQueryRequest(
        projectId,
        waitTime,
        authn,
        queryId,
        outputTypes,
        queryDef,
        nodeId
      )
    }

    attempt.map(addPatientCountXmlIfNecessary)
  }
}
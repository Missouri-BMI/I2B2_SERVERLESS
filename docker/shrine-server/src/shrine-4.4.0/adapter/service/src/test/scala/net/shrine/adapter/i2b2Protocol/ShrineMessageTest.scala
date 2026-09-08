package net.shrine.adapter.i2b2Protocol

import net.shrine.problem.{TestProblem, XmlProblemDigest}
import net.shrine.protocol.i2b2.query.{I2b2QueryDefinition, Term}
import net.shrine.protocol.i2b2.{AuthenticationInfo, Credential, DefaultBreakdownResultOutputTypes, I2b2Result, QueryResult, ResultOutputType, ShrineMessage, XmlUnmarshallers}
import net.shrine.util.ShouldMatchersForJUnit
import net.shrine.xml.XmlDateHelper
import org.junit.Test

import scala.concurrent.duration.{Duration, DurationLong}

/**
 * @author clint
 * @since Feb 24, 2014
 */
final class ShrineMessageTest extends ShouldMatchersForJUnit {
  @Test
  def testRoundTrips():Unit = {
    val projectId = "salkdjksaljdkla"

    val waitTime: Duration = 98374L.milliseconds
    val userId = "foo-user"
    val groupId = "foo-group"
    val authn = AuthenticationInfo("blarg-domain", userId, Credential("sajkhdkjsadh", isToken = true))
    val queryId = 485794359L
    val queryName = "saljkd;salda"
    val outputTypes = ResultOutputType.nonBreakdownTypes.toSet
    val queryDefinition = I2b2QueryDefinition(queryName, Term("oiweruoiewkldfhsofi", "oiweruoiewkldfhsofiName"))
    val localResultId = "aoiduaojsdpaojcmsal"
    val start = Some(XmlDateHelper.now)
    val end = Some(XmlDateHelper.now)
    val singleNodeResult1 = QueryResult.errorResult(Some("blarg"), "glarg",XmlProblemDigest.create(TestProblem()))
    val singleNodeResult2 = QueryResult(
      42L,
      99L,
      Option(ResultOutputType.PATIENT_COUNT_XML),
      123L,
      start,
      end,
      Some("description"),
      QueryResult.StatusType.Finished,
      Some("status"))

    val queryInstance1 = QueryInstance("asd", "42", userId, groupId, start.get, end.get, QueryResult.StatusType.Finished)
    val queryInstance2 = QueryInstance("asdasd", "99", userId, groupId, start.get, end.get, QueryResult.StatusType.Finished)
    val envelope = I2b2Result(DefaultBreakdownResultOutputTypes.PATIENT_AGE_COUNT_XML, Map("x" -> 1, "y" -> 2))

    //Non-i2b2able requests
    doMarshallingRoundTrip(ReadQueryResultRequest(projectId, waitTime, authn, queryId))

    //I2b2able requests
    doMarshallingRoundTrip(ReadInstanceResultsRequest(projectId, waitTime, authn, queryId))
    doMarshallingRoundTrip(ReadQueryInstancesRequest(projectId, waitTime, authn, queryId))
    doMarshallingRoundTrip(RunQueryRequest(projectId, waitTime, authn, queryId, outputTypes, queryDefinition))
    doMarshallingRoundTrip(ReadResultRequest(projectId, waitTime, authn, localResultId))

    //I2b2able responses
    doMarshallingRoundTrip(ReadQueryInstancesResponse(42L, userId, groupId, Seq(queryInstance1, queryInstance2)))
    doMarshallingRoundTrip(RunQueryResponse(queryId, start.get, userId, groupId, queryDefinition, 12345L, singleNodeResult1))
    doMarshallingRoundTrip(ReadResultResponse(42L, singleNodeResult2, envelope))
  }

  private def doMarshallingRoundTrip[T <: ShrineMessage](message: T):Unit = {
    val xml = message.toXml

    object ShrineMessageDecoder extends XmlUnmarshallers.Chained(
      _ => xml => BroadcastMessage.fromXml(xml), //todo last use of BroadcastMessage in code - clean this out! SHRINE-2384
      BaseShrineRequest.fromXml,
      BaseShrineResponse.fromXml
    )

    val unmarshalled = ShrineMessageDecoder.fromXml(DefaultBreakdownResultOutputTypes.toSet)(xml).get

    message match {
      //NB: Special handling of ReadInstanceResultsResponse because its member QueryRequests are munged
      //before serialization
      case readInstanceResultsResponse: ReadInstanceResultsResponse =>
        val unmarshalledResp = unmarshalled.asInstanceOf[ReadInstanceResultsResponse]

        val expected = readInstanceResultsResponse.copy(results = Seq(readInstanceResultsResponse.results.head.copy(instanceId = readInstanceResultsResponse.shrineNetworkQueryId)))

        unmarshalledResp should equal(expected)

      //NB: Special handling of ReadQueryInstancesResponse because its member QueryInstances are not exactly preserved 
      //on serialization round trips
      case _: ReadQueryInstancesResponse =>
        val unmarshalledResp = unmarshalled.asInstanceOf[ReadQueryInstancesResponse]

        val _ = unmarshalledResp.withInstances(unmarshalledResp.queryInstances.map(_.copy(queryMasterId = unmarshalledResp.queryMasterId.toString)))
      //NB: Special handling of RunQueryResponse because its member QueryRequest is munged 
      //during serialization
      case runQueryResponse: RunQueryResponse =>
        val unmarshalledResp = unmarshalled.asInstanceOf[RunQueryResponse]

        val expected = runQueryResponse.withResult(runQueryResponse.singleNodeResult.copy(instanceId = runQueryResponse.queryInstanceId))

        unmarshalledResp should equal(expected)
      case _ => unmarshalled should equal(message)
    }
  }
}
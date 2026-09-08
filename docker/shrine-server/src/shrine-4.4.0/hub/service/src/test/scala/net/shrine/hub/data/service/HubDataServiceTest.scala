package net.shrine.hub.data.service

import cats.data.OptionT
import cats.effect.IO
import net.shrine.hub.data.store.HubDb
import net.shrine.protocol.version.{JsonText, MomQueueName, NodeKey, NodeName, UserDomainName}
import net.shrine.protocol.version.v2.{Network, Node}
import net.shrine.hub.data.client.HubServiceRequests
import org.http4s.dsl.io.{Accepted, Conflict, NotFound, Ok}
import org.http4s.headers.Authorization
import org.http4s.{BasicCredentials, Headers, Method, Request, Response, Uri}
import org.junit.{After, Before, Test}
import org.scalatestplus.junit.AssertionsForJUnit

import scala.util.Try
import org.http4s.syntax.literals._

class HubDataServiceTest extends AssertionsForJUnit {
  import cats.effect.unsafe.implicits.global
  private val momBaseUri = uri""

  val network: Network = Network(
    networkName = "Test network",
    hubQueueName = MomQueueName("testHub"),
    adminEmail = "yourname@example.com",
    momId = "testNetwork",
    awsSqsConfig = None,
    kafkaConfig = None
  )

  @Test
  def testPing():Unit = {
    val response: Response[IO] = requestResponse(Request(method = Method.GET, uri = uri"/ping"))
    assertResult(Ok)(response.status)
    val entityText = response.as[String].unsafeRunSync()
    assertResult("pong")(entityText)
  }

  @Test
  def testNoNodeForId():Unit = {
    val response: Response[IO] = requestResponse(Request(method = Method.GET, uri = uri"/node/1234"))
    assertResult(NotFound)(response.status)
  }

  @Test
  def testNoNodeForKey():Unit = {
    val response: Response[IO] = requestResponse(Request(method = Method.GET, uri = uri"/node/fakeKey"))
    assertResult(NotFound)(response.status)
  }

  @Test
  def testGetNetwork():Unit = {
    HubDb.db.upsertNetworkIO(network).unsafeRunSync()

    val response: Response[IO] = requestResponse(HubServiceRequests.getNetworkRequest(momBaseUri))
    assertResult(Ok)(response.status)
    val entityText = response.as[String].unsafeRunSync()
    val maybeNetwork: Try[Network] = Network.tryRead(new JsonText(entityText))

    assertResult(network)(maybeNetwork.get)
  }

  @Test
  def testGetNodeForKey():Unit = {
    val expectedNode = Node(
      new NodeName("Test Node"),
      new NodeKey("testNode"),
      new UserDomainName("testnode.domain"),
      adminEmail= "email@foo",
      momId = "testNode",
    )

    HubDb.db.upsertNodeIO(expectedNode).unsafeRunSync()

    val response: Response[IO] = requestResponse(HubServiceRequests.getNodeRequest(momBaseUri,expectedNode.key))
    assertResult(Ok)(response.status)
    val entityText = response.as[String].unsafeRunSync()
    val maybeNode: Try[Node] = Node.tryRead(new JsonText(entityText))

    assertResult(maybeNode.get)(expectedNode)
  }

  @Test
  def testGetNodeForId():Unit = {
    val expectedNode = Node(
      new NodeName("Test Node"),
      new NodeKey("testNode"),
      new UserDomainName("testnode.domain"),
      momId = "testNode",
      adminEmail = "email@foo",
    )

    HubDb.db.upsertNodeIO(expectedNode).unsafeRunSync()

    val response: Response[IO] = requestResponse(HubServiceRequests.getNodeRequest(momBaseUri,expectedNode.id))
    assertResult(Ok)(response.status)
    val entityText = response.as[String].unsafeRunSync()
    val maybeNode: Try[Node] = Node.tryRead(new JsonText(entityText))

    assertResult(maybeNode.get)(expectedNode)
  }

  //todo refactor to a common test package
  private def requestResponse(request: Request[IO]): Response[IO] = {
    val responseOptionIo: OptionT[IO, Response[IO]] = HubDataService.service.run(request)

    val iorio: IO[Response[IO]] = responseOptionIo.map { r: Response[IO] => r }.fold {
      //todo is there some kind of exception or the like available?
      fail(s"No response from service for $request")
    } { r: Response[IO] => r
    }
    iorio.unsafeRunSync()
  }

  @Before
  def beforeEach(): Unit = {
    HubDb.db.createTables()
  }

  @After
  def afterEach(): Unit = {
    HubDb.db.dropTables()
  }

}

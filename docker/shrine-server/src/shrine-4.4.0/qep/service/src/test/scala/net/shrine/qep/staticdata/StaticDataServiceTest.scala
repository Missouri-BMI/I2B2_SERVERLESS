package net.shrine.qep.staticdata

import cats.data.OptionT
import cats.effect.IO
import cats.effect.unsafe.implicits.global
import cats.implicits.{catsSyntaxEither => _}
import com.typesafe.config.ConfigFactory
import io.circe.Json
import net.shrine.config.ConfigSource
import org.http4s.circe.jsonOf
import org.http4s.dsl.io.Ok
import org.http4s.headers.{Accept, Authorization}
import org.http4s.{BasicCredentials, EntityDecoder, Headers, MediaType, Method, Request, Response, Uri}
import org.junit.Test
import org.junit.jupiter.api.Assertions.{assertEquals, fail}

import scala.io.Source
import org.http4s.syntax.literals._

class StaticDataServiceTest  {

  implicit val jsonDecoder: EntityDecoder[IO, Json] = jsonOf[IO, Json]

  def readFile(filename: String): String = {
    val source = Source.fromFile(getClass.getResource(filename).getFile)
    try source.mkString finally source.close()
  }

  def extractResponse(request: Request[IO]): Response[IO] = {
    val responseOptionIo: OptionT[IO, Response[IO]] = StaticDataService().service.run(request)

    responseOptionIo.map { r: Response[IO] => r }.fold {
      fail("No response from service")
    } { r: Response[IO] => r
    }.unsafeRunSync()
  }

  @Test
  def replyToPingWithPong(): Unit =  {
    val pingRequest: Request[IO] = Request(method = Method.GET, uri = uri"/ping")

    val response: Response[IO]= extractResponse(pingRequest)

    assertEquals(Ok,response.status)
    val entityText = response.as[String].unsafeRunSync()
    assertEquals("pong",entityText)
  }

  @Test
  def testTimeoutConfig(): Unit  = {

    val request: Request[IO] = Request(method = Method.GET,
      uri = Uri.fromString(s"/config/timeout").getOrElse(throw new IllegalStateException()),
      headers = Headers(Accept(MediaType.application.json)))

    val response: Response[IO] = extractResponse(request)

    assertEquals(Ok,response.status)
    val entityText = response.as[String].unsafeRunSync()
    assertEquals("\"45 seconds\"",entityText)
  }

  @Test
  def testWebclientConfigWhenMissingShrineUrl(): Unit =  {
    import io.circe.Json
    import io.circe.parser.parse

    val request: Request[IO] = Request(method = Method.GET,
      uri = Uri.fromString(s"/webClientConfig").getOrElse(throw new IllegalStateException()),
      headers = Headers(Accept(MediaType.application.json)))

    //todo this test is far too ridged, possibly unneeded - it's doing little more than testing typesafe config
    val expectedResponse = """{
                              |    "domain": "foo",
                              |    "name": "SHRINE",
                              |    "bannerText" : "SHRINE Network",
                              |    "shrineUrl": null,
                              |    "siteAdminEmail" : "baz@foo",
                              |    "termsOfUseText" : "By accepting, \"I acknowledge\",\n                       I agree to adhere to the terms of use\n                       specified and aware that my usage may be audited.\"",
                              |    "unauthorizedMessage" : "You currently do not have access to SHRINE. Please contact your institution's SHRINE administrator for more information.",
                              |    "usernameLabel" : "User Name",
                              |    "passwordLabel" : "User Password",
                              |    "defaultNumberOfOntologyChildren" : 10000,
                              |    "queryFavingInstructions" : "Here you can manage whether a query is a favorite or not. You can also edit the associated message.",
                              |    "favingIconInstructions" : "Make a query a favorite",
                              |    "favingPlaceholderText" : null,
                              |     "nextStepsUrl" : "https://enact-network.org/site-access-contacts/",
                              |     "helpLinks": {
                              |        "Application Help": "https://open.catalyst.harvard.edu/wiki/display/SHRINE/SHRINE+4.4+Webclient+Help",
                              |        "Network Help": "https://enact-network.org/site-access-contacts/"
                              |      },
                              |      "ssoLinks" : {
                              |        "MockSAML" : "https://saml.example.com/entityid",
                              |        "HMS" : "http://sso.med.harvard.edu/adfs/services/trust",
                              |        "SSOCircle" : "https://idp.ssocircle.com"
                              |      },
                              |      "ssoLogoutUrl" : "some-logout-url"
                              |}""".stripMargin
    val expectedJson: Json = parse(expectedResponse).getOrElse(throw new IllegalStateException())
    val response: Response[IO] = extractResponse(request)

    assertEquals(Ok,response.status)
    val entityText = response.as[String].unsafeRunSync()
    val resultJson: Json = parse(entityText).getOrElse(throw new IllegalStateException())

    assertEquals(expectedJson,resultJson)
  }

  @Test
  def testShrineConfig(): Unit  = {

    val username = "admin"
    val password = "pwd"
    val request: Request[IO] = Request(method = Method.GET,
      uri = Uri.fromString(s"/auth/config").getOrElse(throw new IllegalStateException()),
      headers = Headers(Accept(MediaType.application.json), Authorization(BasicCredentials(username, password))))

    val response: Response[IO] = extractResponse(request)

    assertEquals(Ok,response.status)
    val entityText = response.as[String].unsafeRunSync()

    val actualConfig = ConfigFactory.parseString(entityText)
    val expectedConfig = ConfigSource.config.getConfig("shrine")

    assertEquals(expectedConfig.getString("static.timeout"),actualConfig.getString("static.timeout"))
    assert(!entityText.contains("password"))
  }

  @Test
  def testGetLogo(): Unit  = {

    val request: Request[IO] = Request(method = Method.GET,
      uri = Uri.fromString(s"/webclient/logo").getOrElse(fail("No logo from string")),
      headers = Headers(Accept(MediaType.image.png)))

    val response: Response[IO] = extractResponse(request)

    assertEquals(Ok,response.status)
    val entityText = response.as[Array[Byte]].unsafeRunSync()

    assertEquals(32767,entityText.length)
  }

  @Test
  def testGetVersion(): Unit  = {
    import io.circe.syntax.EncoderOps
    import io.circe.generic.auto.exportEncoder

    val request: Request[IO] = Request(method = Method.GET,
      uri = Uri.fromString(s"/version").getOrElse(fail("No version from string")),
      headers = Headers(Accept(MediaType.application.json)))

    val expectedResponse = AppVersion().asJson.toString()

    val response: Response[IO] = extractResponse(request)

    assertEquals(Ok,response.status)
    val entityText = response.as[String].unsafeRunSync()

    assertEquals(expectedResponse,entityText)
    assert(entityText.length > 30) //Make sure it is not empty
  }


  @Test
  def testGetDataDistributionTypes(): Unit = {
    val expectedDataDistributionJson: String = readFile("/dataDistributionTypes.json")

    val reloadRequest: Request[IO] = Request(method = Method.GET,
      uri = Uri.unsafeFromString(s"/dataDistributionTypes")
    )

    val response: Response[IO] = extractResponse(reloadRequest)

    assertEquals(Ok, response.status)

    val resultJson: Json = response.as[Json].unsafeRunSync()
    val result: String = resultJson.toString()

    assertEquals(expectedDataDistributionJson, result)
  }

}

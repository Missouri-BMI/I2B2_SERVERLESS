package net.shrine.authentication.http4s

import java.util.Base64
import cats.data.OptionT
import cats.effect.IO
import cats.implicits.{catsSyntaxEither => _}
import net.shrine.authentication.pm.User
import net.shrine.util.ShouldMatchersForJUnit
import org.http4s.dsl.impl.{->, /, Auth}
import org.http4s.dsl.io.{GET, Ok, Root, Unauthorized, http4sOkSyntax}
import org.http4s.headers.Authorization
import org.http4s.{AuthScheme, AuthedRoutes, BasicCredentials, Credentials, Headers, HttpRoutes, Method, Request, Response}
import org.junit.Test
import org.http4s.syntax.literals._
/**
  * Test web api of BasicOrBearerAuthService
  */
class AuthMiddlewareSelectorTest extends ShouldMatchersForJUnit with Auth{
  import cats.effect.unsafe.implicits.global
  @Test
  def testBasicOrBearerAuthentication(): Unit = {

    val bearerToken: String =
      """{"username": "adminSession", "sessionId": "1234567"}
      """.stripMargin
    val encodedBearerToken: String = Base64.getEncoder.encodeToString(bearerToken.getBytes)

    val bearerRequest: Request[IO] = Request(method = Method.GET,
      uri = uri"/private",
      headers = Headers(Authorization(Credentials.Token(AuthScheme.Bearer, encodedBearerToken))))

    val bearerResponse: Response[IO]= extractResponse(bearerRequest)

    assertResult(Ok)(bearerResponse.status)
    val entityText = bearerResponse.as[String].unsafeRunSync()
    assertResult("This page is protected using authentication; logged in as adminSession")(entityText)

    val username = "admin"
    val password = "pwd"
    val basicRequest: Request[IO] = Request(method = Method.GET,
      uri = uri"/private",
      headers = Headers(Authorization(BasicCredentials(username, password))))

    val basicResponse: Response[IO]= extractResponse(basicRequest)
    assertResult(Ok)(basicResponse.status)
    assertResult("This page is protected using authentication; logged in as adminSession")(entityText)
  }

  @Test
  def testUnauthorizedBasicAuthentication(): Unit = {
    val username = "admin"
    val password = "badpassword"
    val basicRequest: Request[IO] = Request(method = Method.GET,
      uri = uri"/private",
      headers = Headers(Authorization(BasicCredentials(username, password))))

    val basicResponse: Response[IO]= extractResponse(basicRequest)
    val entityText = basicResponse.as[String].unsafeRunSync()

    assertResult(Unauthorized)(basicResponse.status)
    assertResult(BasicAuthentication.unauthorizedMsg)(entityText)
  }

  @Test
  def testUnauthorizedBearerAuthentication(): Unit = {
    val bearerToken: String =
      """{"username": "adminSession", "sessionId": "100"}
      """.stripMargin
    val encodedBearerToken: String = Base64.getEncoder.encodeToString(bearerToken.getBytes)

    val bearerRequest: Request[IO] = Request(method = Method.GET,
      uri = uri"/private",
      headers = Headers(Authorization(Credentials.Token(AuthScheme.Bearer, encodedBearerToken))))

    val bearerResponse: Response[IO]= extractResponse(bearerRequest)
    val entityText = bearerResponse.as[String].unsafeRunSync()

    assertResult(Unauthorized)(bearerResponse.status)
    assertResult(BearerAuthentication.unauthorizedMsg)(entityText)
  }

  //Auth is needed for 'as' to work in 'Root / "private" as user'
  private val authService  =  AuthedRoutes.of[User, IO] {
    case GET -> Root / "private" as user =>
      Ok(s"This page is protected using authentication; logged in as ${user.username}")
  }
  val wrappedAuthService: HttpRoutes[IO] = AuthMiddlewareSelector(authService)

  def extractResponse(request: Request[IO]): Response[IO] = {
    val responseOptionIo: OptionT[IO, Response[IO]] = wrappedAuthService.run(request)

    responseOptionIo.map { r: Response[IO] => r }.fold {
      fail("No response from service")
    } { r: Response[IO] => r
    }.unsafeRunSync()
  }
}

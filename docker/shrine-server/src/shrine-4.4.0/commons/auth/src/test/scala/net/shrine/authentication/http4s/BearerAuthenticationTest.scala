package net.shrine.authentication.http4s

import java.util.Base64
import cats.data.{Kleisli, OptionT}
import cats.effect.IO
import cats.implicits.{catsSyntaxEither => _}
import net.shrine.authentication.pm.User
import net.shrine.log.LogCensor
import org.http4s.dsl.impl.{->, /, Auth}
import org.http4s.dsl.io.{GET, Ok, Root, Unauthorized, http4sOkSyntax}
import org.http4s.headers.Authorization
import org.http4s.{AuthScheme, AuthedRoutes, Credentials, Headers, Method, Request, Response}
import org.junit.runner.RunWith
import org.scalatest.FlatSpec
import org.scalatestplus.junit.JUnitRunner
import org.http4s.syntax.literals._
/**
  * Test web api of BearerAuthentication
  */
@RunWith(classOf[JUnitRunner])
class BearerAuthenticationTest extends FlatSpec with Auth  {
  import cats.effect.unsafe.implicits.global
  //Auth is needed for 'as' to work in 'Root / "private" as user'
  private val authService  =  AuthedRoutes.of[User, IO] {
    case GET -> Root / "private" as user =>
      Ok(s"This page is protected using Bearer authentication; logged in as ${user.username}")
  }
  val wrappedAuthService: Kleisli[({
    type Lambda[Beta$1$] = OptionT[IO, Beta$1$]
  })#Lambda, Request[IO], Response[IO]] = BearerAuthentication.bearerAuthMiddleware(authService)

  def extractResponse(request: Request[IO]): Response[IO] = {
    val responseOptionIo: OptionT[IO, Response[IO]] = wrappedAuthService.run(request)

    responseOptionIo.map { r: Response[IO] => r }.fold {
      fail("No response from service")
    } { r: Response[IO] => r
    }.unsafeRunSync()
  }

  "An authorized user" should "be allowed access to the service" in {
    val bearerToken: String =
      """{"username": "adminSession", "sessionId": "1234567"}
      """.stripMargin

    val encodedBearerToken: String = Base64.getEncoder.encodeToString(bearerToken.getBytes)
    val request: Request[IO] = Request(method = Method.GET,
      uri = uri"/private",
      headers = Headers(Authorization(Credentials.Token(AuthScheme.Bearer, encodedBearerToken)))
    )

    val response: Response[IO]= extractResponse(request)

    assertResult(Ok)(response.status)
    val entityText = response.as[String].unsafeRunSync()
    assertResult("This page is protected using Bearer authentication; logged in as adminSession")(entityText)
  }

  "A user with no username or session Id" should "not be allowed access to the service" in {
    val request: Request[IO] = Request(method = Method.GET, uri = uri"/private")

    val response: Response[IO]= extractResponse(request)

    assertResult(Unauthorized)(response.status)
    val entityText = response.as[String].unsafeRunSync()
    assertResult(BearerAuthentication.unauthorizedMsg)(entityText)
  }

  "A user with a username and no session Id" should "not be allowed access to the service" in {
    val bearerToken: String =
      """{"username": "adminSession", "sessionId": ""}
      """.stripMargin
    val encodedBearerToken: String = Base64.getEncoder.encodeToString(bearerToken.getBytes)

    val pingRequest: Request[IO] = Request(method = Method.GET,
      uri = uri"/private",
      headers = Headers(Authorization(Credentials.Token(AuthScheme.Bearer, encodedBearerToken))))

    val response: Response[IO]= extractResponse(pingRequest)

    assertResult(Unauthorized)(response.status)
    val entityText = response.as[String].unsafeRunSync()
    assertResult(BearerAuthentication.unauthorizedMsg)(entityText)
  }

  "A user with a username and bad session Id" should "not be allowed access to the service" in {
    val bearerToken: String =
      """{"username": "adminSession", "sessionId": "S1234567"}
      """.stripMargin
    val encodedBearerToken: String = Base64.getEncoder.encodeToString(bearerToken.getBytes)

    val pingRequest: Request[IO] = Request(method = Method.GET,
      uri = uri"/private",
      headers = Headers(Authorization(Credentials.Token(AuthScheme.Bearer, encodedBearerToken))))

    val response: Response[IO]= extractResponse(pingRequest)

    assertResult(Unauthorized)(response.status)
    val entityText = response.as[String].unsafeRunSync()
    assertResult(BearerAuthentication.unauthorizedMsg)(entityText)
  }

  "The session Id" should "should be censored in the log" in {

    val userTokenString = UserToken("username","sessionId").toString
    val expectedString = "UserToken(username,REDACTED)"

    val result = LogCensor.censor(userTokenString)
    assertResult(expectedString)(result)
  }
}

package net.shrine.authentication.http4s

import cats.data.OptionT
import cats.effect.IO
import cats.implicits.{catsSyntaxEither => _}
import net.shrine.authentication.pm.User
import org.http4s.dsl.impl.{->, /, Auth}
import org.http4s.dsl.io.{GET, Ok, Root, Unauthorized, http4sOkSyntax}
import org.http4s.headers.Authorization
import org.http4s.{AuthedRoutes, BasicCredentials, Header, Headers, HttpRoutes, Method, Request, Response}
import org.junit.runner.RunWith
import org.scalatest.FlatSpec
import org.scalatestplus.junit.JUnitRunner
import org.http4s.syntax.literals._
import org.typelevel.ci.CIString
/**
  * Test web api of BasicAuthentication
  */
@RunWith(classOf[JUnitRunner])
class BasicAuthenticationTest extends FlatSpec with Auth  {

  import cats.effect.unsafe.implicits.global
  //Auth is needed for 'as' to work in 'Root / "private" as user'
  private val authService  =  AuthedRoutes.of[User, IO] {
    case GET -> Root / "private" as user =>
      Ok(s"This page is protected using HTTP authentication; logged in as ${user.username}")
  }
  val wrappedAuthService: HttpRoutes[IO] = BasicAuthMiddleware(authService)

  def extractResponse(request: Request[IO]): Response[IO] = {
    val responseOptionIo: OptionT[IO, Response[IO]] = wrappedAuthService.run(request)

    responseOptionIo.map { r: Response[IO] => r }.fold {
      fail("No response from service")
    } { r: Response[IO] => r
    }.unsafeRunSync()
  }

  "An authorized user" should "be allowed access to the service" in {
    val username = "admin"
    val password = "pwd"
    val pingRequest: Request[IO] = Request(method = Method.GET,
      uri = uri"/private",
      headers = Headers(Authorization(BasicCredentials(username, password)))
    )

    val response: Response[IO]= extractResponse(pingRequest)

    assertResult(Ok)(response.status)
    val entityText = response.as[String].unsafeRunSync()
    assertResult("This page is protected using HTTP authentication; logged in as admin")(entityText)
  }

  "A user with no username or password" should "not be allowed access to the service" in {
    val pingRequest: Request[IO] = Request(method = Method.GET, uri = uri"/private")

    val response: Response[IO]= extractResponse(pingRequest)

    assertResult(Unauthorized)(response.status)
    val entityText = response.as[String].unsafeRunSync()
    assertResult(BasicAuthentication.unauthorizedMsg)(entityText)
  }

  "A user with a username and no password" should "not be allowed access to the service" in {
    val username = "admin"
    val password = ""
    val pingRequest: Request[IO] = Request(method = Method.GET,
      uri = uri"/private",
      headers = Headers(Authorization(BasicCredentials(username, password))))

    val response: Response[IO]= extractResponse(pingRequest)

    assertResult(Unauthorized)(response.status)
    val entityText = response.as[String].unsafeRunSync()
    assertResult(BasicAuthentication.unauthorizedMsg)(entityText)
  }


  "A user with invalid credentials" should "should not get auth challenge if request is an XMLHttpRequest" in {

    import org.http4s.headers.`WWW-Authenticate`

    val username = "admin"
    val password = ""

    val xmlHttpRequest: Request[IO] = Request(method = Method.GET,
      uri = uri"/private",
      headers = Headers(Authorization(BasicCredentials(username, password)), Header.Raw(CIString("X-Requested-With"), "XMLHttpRequest")))

    val xmlHttpResponse: Response[IO]= extractResponse(xmlHttpRequest)

    assertResult(Unauthorized)(xmlHttpResponse.status)
    assert(!xmlHttpResponse.headers.headers.exists(_.name == `WWW-Authenticate`.headerInstance.name))

    val xmlHttpEntityText = xmlHttpResponse.as[String].unsafeRunSync()
    assertResult(BasicAuthentication.unauthorizedMsg)(xmlHttpEntityText)
  }

  "A user with invalid credentials" should "should get auth challenge if request is not an XMLHttpRequest" in {
    import org.http4s.headers.`WWW-Authenticate`

    val username = "admin"
    val password = ""
    val request: Request[IO] = Request(method = Method.GET,
      uri = uri"/private",
      headers = Headers(Authorization(BasicCredentials(username, password))))

    val response: Response[IO]= extractResponse(request)

    assertResult(Unauthorized)(response.status)
    assert(response.headers.headers.exists(h =>h.name == `WWW-Authenticate`.headerInstance.name))

    val entityText = response.as[String].unsafeRunSync()
    assertResult(BasicAuthentication.unauthorizedMsg)(entityText)
  }
}

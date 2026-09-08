package net.shrine.http4s.servlet

import cats.conversions.all.{autoWidenBifunctor, autoWidenFunctor}
import cats.data.{NonEmptyList, OptionT}
import cats.effect.IO
import cats.implicits.toFoldableOps
import org.http4s.{Header, HttpRoutes, Method, ParseResult, Request, Response, Uri}
import org.http4s.dsl.impl.->
import org.http4s.dsl.io.{GET, NotFound, Ok, Root, http4sNotFoundSyntax, http4sOkSyntax}
import org.http4s.headers.`Strict-Transport-Security`
import org.junit.jupiter.api.Assertions.{assertEquals, fail}
import org.junit.Test

import scala.concurrent.duration.DurationInt

class ShrineServletMounterTest{

  private[servlet] def extractResponse(request: Request[IO], service: HttpRoutes[IO]): Response[IO] = {
    import cats.effect.unsafe.implicits.global

    val responseOptionIo: OptionT[IO, Response[IO]] = service.run(request)
    responseOptionIo.map { r: Response[IO] => r }.fold {
      fail("No response from service")
    } { r: Response[IO] => r
    }.unsafeRunSync()
  }

  val pingService: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case GET -> Root => Ok("pong")
    case x => NotFound(s"The service does not respond to $x")
  }

  @Test
  def testHSTSResponseHeader(): Unit = {
    val shrineService = ShrineServletMounter.wrapResponseHeaders(pingService)
    val pingRequest: Request[IO] = Request(method = Method.GET, uri = Uri.unsafeFromString(s"/ping"))
    val response: Response[IO] = extractResponse(pingRequest, shrineService)

    val expectedStsHeader: `Strict-Transport-Security` = `Strict-Transport-Security`.unsafeFromDuration(
      maxAge = 365.days,
      includeSubDomains = true,
      preload = false
    )

    val responseHstsHeader: Option[NonEmptyList[Header.Raw]] = response.headers.get(`Strict-Transport-Security`.headerInstance.name)
    responseHstsHeader.fold(fail("Missing HSTS header")){(stsHeaders: NonEmptyList[Header.Raw]) =>
      stsHeaders match {
        case NonEmptyList(stsHeader,Nil) =>
          val maybeParsed: ParseResult[`Strict-Transport-Security`] = `Strict-Transport-Security`.parse(stsHeader.value)
          maybeParsed.fold(notParsed => fail(s"$notParsed"),
                            parsed => assertEquals(expectedStsHeader,parsed))
        case _ => fail(s"Multiple Strict-Transport-Security headers in $stsHeaders")
      }
    }
  }
}

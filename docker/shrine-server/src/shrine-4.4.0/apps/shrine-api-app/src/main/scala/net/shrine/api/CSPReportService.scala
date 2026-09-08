package net.shrine.api

import cats.effect.IO
import net.shrine.log.Log
import org.http4s.HttpRoutes
import org.http4s.dsl.impl.{->, /}
import org.http4s.dsl.io.{NoContent, NotFound, Ok, POST, Root, http4sNoContentSyntax, http4sNotFoundSyntax, http4sOkSyntax}


case class CSPReportService(){

  val service: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case POST -> Root / "ping" => Ok("pong")
    case req@POST -> Root  =>
      val reportStr: IO[String] = req.as[String]
      reportStr.flatMap(reportStr => {
        Log.error(s"Context Security Policy Violation: $reportStr")
        NoContent()
      })
    case x => NotFound(s"The CSPReport service does not respond to $x")
  }
}

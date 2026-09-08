package net.shrine.http4s.servlet

import cats.data.Kleisli
import cats.effect.IO
import net.shrine.log.Log
import org.http4s.{HttpRoutes, Request, Response}

object Http4sRequestResponseLogger {

  def apply(service: HttpRoutes[IO]): HttpRoutes[IO] = Kleisli { request: Request[IO] =>
    logRequest(request)

    service(request).map {response: Response[IO] =>
      logRequestResponse(request,response)
      response
    }
  }

  def logRequest(request:Request[IO]): Request[IO] = {
    Log.info(s"Request received: $request")
    request
  }

  def logRequestResponse(request:Request[IO],response: Response[IO]): Response[IO] = {
    Log.info(
      s"""Request: $request
         |Response: $response
       """.stripMargin)
    response
  }
}

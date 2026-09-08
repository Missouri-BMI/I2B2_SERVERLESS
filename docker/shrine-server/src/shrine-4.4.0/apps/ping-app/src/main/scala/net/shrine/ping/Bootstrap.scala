package net.shrine.ping

import cats.effect.IO
import cats.effect.std.Dispatcher
import cats.effect.unsafe.implicits.global
import org.http4s._
import org.http4s.dsl.impl.{->, /}
import org.http4s.dsl.io.{GET, Ok, Root}
import org.http4s.servlet.syntax._

import javax.servlet.{ServletContextEvent, ServletContextListener, ServletRegistration}
import javax.servlet.annotation.WebListener

@WebListener
class Bootstrap extends ServletContextListener {
  private val routes: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case GET -> Root / "ping" => IO.pure(Response(Status.Ok).withEntity("pong with root"))

    case req if req.method == Method.GET =>
      IO.pure(Response(Status.Ok).withEntity("pong for all gets"))
  }

  private val moreRoutes: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req if req.method == Method.GET =>
      IO.pure(Response(Status.Ok).withEntity("pong for moreRoutes"))
  }

  @volatile private var shutdown: IO[Unit] = IO.unit

  override def contextInitialized(sce: ServletContextEvent): Unit = {
    Dispatcher
      .parallel[IO]
      .allocated
      .flatMap { case (dispatcher, shutdown) =>
        IO(this.shutdown = shutdown) *>
          mountService(sce,dispatcher,"ping", "/mapped/*",routes) *>
          mountService(sce,dispatcher,"more", "/more/*",moreRoutes)
      }
      .unsafeRunSync()
    ()
  }

  private def mountService(sce: ServletContextEvent,dispatcher: Dispatcher[IO],name:String,mapping:String,routes:HttpRoutes[IO]): IO[ServletRegistration.Dynamic] = {
    IO{
      val ping = sce.getServletContext.mountRoutes(
        name = name,
        mapping = mapping,
        service = routes,
        dispatcher = dispatcher
      )
      println(s"$name mapping is ${ping.getMappings}")

      ping
    }
  }

  override def contextDestroyed(sce: ServletContextEvent): Unit = {
    println(s"Start contextDestroyed $sce")
    shutdown.unsafeRunSync()
    println(s"End contextDestroyed $sce")
  }
}
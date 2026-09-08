package net.shrine

import cats.effect.IO
import cats.effect.std.Dispatcher

import javax.servlet.annotation.WebListener
import javax.servlet.{ServletContextEvent, ServletContextListener}
import net.shrine.http4s.servlet.ShrineServletMounter
import cats.effect.unsafe.implicits.global


/**
  * This class is needed to load the http4s services in a servlet container
  * Created by marc-danie on 7/24/18.
  */

@WebListener
class Bootstrap extends ServletContextListener {
  @volatile private var shutdown: IO[Unit] = IO.unit

  override def contextInitialized(sce: ServletContextEvent): Unit = {
    Dispatcher.parallel[IO]//(await = true)
      .allocated
      .flatMap { case (dispatcher, shutdown) =>
        IO(this.shutdown = shutdown) *>
          ShrineServletMounter.mountService(dispatcher,sce.getServletContext, "SHRINE Mock Service", MockCRCApp.service)
        }
      .unsafeRunSync()
  }
}

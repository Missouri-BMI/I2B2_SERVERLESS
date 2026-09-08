package net.shrine.http4s.servlet

import javax.servlet.{ServletContext, ServletRegistration}
import net.shrine.config.{ConfigExtensions, ConfigSource}
import org.http4s.headers.`Strict-Transport-Security`
import org.http4s.server.middleware.HSTS
import org.http4s.{HttpApp, HttpRoutes}
import cats.effect.IO
import cats.effect.std.Dispatcher
import net.shrine.log.Loggable
import org.http4s.server.DefaultServiceErrorHandler
import org.http4s.servlet.{AsyncHttp4sServlet, BlockingServletIo}

import scala.concurrent.duration.{Duration, DurationInt}

/**
 * This code began as a copy of http4s' ServletContextSyntax. It adds request/response logging, a default
 * timeout from shrine, and a security header.
 */
object ShrineServletMounter extends Loggable {

  private[servlet] def wrapResponseHeaders(service: HttpRoutes[IO]) = {
   val serviceWithLogging = Http4sRequestResponseLogger(service)

   val hstsHeader = `Strict-Transport-Security`.unsafeFromDuration(
     maxAge = 365.days, //if preload is true, then 2 years is the recommended time according to https://hstspreload.org
     includeSubDomains = true,
     preload = false //if set to true, need to submit site to https://hstspreload.org for preload to work
   )
   HSTS(serviceWithLogging, hstsHeader)
  }

  /** Wraps an [[HttpRoutes]] and mounts it as an [[AsyncHttp4sServlet]]
   *
   * Assumes non-blocking servlet IO is available, and thus requires at least Servlet 3.1.
   */
  def mountService(
                    dispatcher: Dispatcher[IO],
                    context: ServletContext,
                    name: String,
                    service: HttpRoutes[IO],
                    mapping: String = "/*"): IO[ServletRegistration.Dynamic] = {
    info(s"Mounting $name")
    val wrappedService = wrapResponseHeaders(service)
    mountHttpApp(dispatcher,context,name, wrappedService.orNotFound, mapping)
  }

  private def mountHttpApp(
                    dispatcher: Dispatcher[IO],
                    context: ServletContext,
                    name: String,
                    service: HttpApp[IO],
                    mapping: String = "/*"): IO[ServletRegistration.Dynamic] = {

    //TODO: find out why AutoSlash middleware does not work - SHRINE-2581
    IO{
      //must use blocking servlet IO - and AsyncHttp4sServlet's deprecated constructor - until https://github.com/http4s/http4s/issues/2354 is fixed
      val servlet = new AsyncHttp4sServlet(
        httpApp = service,
        asyncTimeout = ConfigSource.config.get("shrine.api.asyncTimeout", Duration(_)),
        servletIo = BlockingServletIo[IO](4096),
        serviceErrorHandler = DefaultServiceErrorHandler[IO],
        dispatcher = dispatcher,
      )
      val registration = context.addServlet(name, servlet)
      registration.setLoadOnStartup(1)
      registration.setAsyncSupported(true)
      registration.addMapping(mapping)

      registration
    } flatMap { r: ServletRegistration.Dynamic =>
      info(s"mounted $name ${r.getName} ${r.getMappings}")
      IO(r)
    }
  }
}
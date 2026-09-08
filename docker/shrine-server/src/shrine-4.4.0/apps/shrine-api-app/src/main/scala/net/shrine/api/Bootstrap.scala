package net.shrine.api

import cats.effect.IO
import cats.effect.std.Dispatcher
import net.shrine.adapter.QueuedQueriesPoller
import net.shrine.adapter.mappings.AdapterMappings
import net.shrine.api.ontology.OntologyService
import net.shrine.authentication.AuthenticationType
import net.shrine.authz.AuthzHttp4sService
import net.shrine.config.ConfigSource
import net.shrine.http4s.catsio.{ExecutionContexts, RepeatedIOTask, RetryIO}
import net.shrine.http4s.servlet.ShrineServletMounter
import net.shrine.hub.data.service.HubDataService
import net.shrine.hub.mom.OkToRetry
import net.shrine.hub.{HubLifecycle, HubReceiver, OverdueResultsPoller}
import net.shrine.log.Loggable
import net.shrine.messagequeuemiddleware.{LocalMessageQueueStopper, MessageQueueWebApi}
import net.shrine.qep.QepService
import net.shrine.qep.staticdata.StaticDataService
import net.shrine.receiver.{NodeSystemSpecSender, Receiver}

import javax.servlet.annotation.WebListener
import javax.servlet.{ServletContextEvent, ServletContextListener}
import scala.concurrent.duration.DurationInt
import scala.language.postfixOps
import cats.effect.unsafe.implicits.global

/**
  * This class is needed to load the http4s services in a servlet container
  * Created by marc-danie on 7/24/18.
  */
@WebListener
class Bootstrap extends ServletContextListener with Loggable {

  private def afterInitialized(dispatcher: Dispatcher[IO]): IO[Unit] = IO{
    // This is async and retried.
    // These calls use the hub's web service; the hub's tomcat has to finish starting before this code can succeed.
    // On the hub these calls almost always fail the first attempt. This is not a problem.
    // todo find some way to block until tomcat is ready to serve
    dispatcher.unsafeRunAndForget {
      if(ConfigSource.config.getBoolean("shrine.hub.create")) {
        info("Start creating queues from database")
        //todo only really needed for the Legacy queues. Maybe switch off for other cases
        RetryIO
          .keepTryingBounded(HubLifecycle.queuesFromDatabaseIO(), 5 seconds, 12, {case OkToRetry(_) => true},"Start queues")
          .redeemWith((t: Throwable) => IO(error("Something went wrong while starting the hub's queues", t)), //todo see SHRINE-3663 . Failure should stop shrine
            _ => IO(info("Hub's queues started successfully")))
      } else IO.unit *>
        NodeSystemSpecSender.startIO()
    }
  }

  @volatile private var shutdown: IO[Unit] = IO.unit

  override def contextInitialized(servletContextEvent: ServletContextEvent): Unit = {
    info(s"contextInitialized started for ${servletContextEvent.getServletContext.getContextPath}")

    val shrineSystemIO: IO[Unit] = Dispatcher.parallel[IO].allocated.flatMap { case (dispatcher, shutdown) =>
      IO(this.shutdown = shutdown) *>
      RepeatedIOTask.startInParallelAndWaitForAll(
        if(ConfigSource.config.getBoolean("shrine.hub.create")) initHub(dispatcher,servletContextEvent) else IO.unit, // receiver starts after hub is set up
        if(ConfigSource.config.getBoolean("shrine.adapter.create")) initAdapter() else IO.unit,
      ) *>
      mountServices(dispatcher,servletContextEvent) *>
      Receiver.startIO(dispatcher) *>
      afterInitialized(dispatcher).attempt.flatMap {
        case Left(t)=> IO(abortStartup(t))
        case Right(_) => IO(info("Shrine startup completed."))
      }
    }

    shrineSystemIO.unsafeRunSync()
  }

  private def abortStartup(cause: Throwable): Unit = {
    error("Shrine startup aborted", cause)
    throw new RuntimeException("Shrine startup aborted", cause) // This stops the servlet
  }

  private def initHub(dispatcher: Dispatcher[IO],sce: ServletContextEvent): IO[Unit] = {
    HubLifecycle.initHubFromConfigIfEmptyIO() *>
    RepeatedIOTask.startInParallelAndWaitForAll(
      IO(info(s"This node is a shrine hub.")),
      mountHubServices(dispatcher, sce),
      HubReceiver.startIO(dispatcher),
      OverdueResultsPoller.startIO(),
    )
  }

  private def initAdapter(): IO[Unit] = for {
    _ <- AdapterMappings.compareAndReloadMappings(ConfigSource.config.getString("shrine.adapter.adapterMappingsFileName"))
    _ <- QueuedQueriesPoller.startIO()
  } yield ()

  private def mountServices(dispatcher: Dispatcher[IO],servletContextEvent: ServletContextEvent):IO[Unit] = {
    //TODO: find out why AutoSlash middleware does not work - SHRINE-2581

    ShrineServletMounter.mountService(
      dispatcher = dispatcher,
      context = servletContextEvent.getServletContext,
      service = StaticDataService().service,
      name = "SHRINE Static Data Service",
      mapping = "/staticData/*"
    ) *> ifIO(ConfigSource.config.getString("shrine.qep.authenticationType") == AuthenticationType.Sso.name) {
      // SHRINE2020-1331
      // In Bootstrap.scala, should the code for SSO be in its own Servlet?
      ShrineServletMounter.mountService(
              dispatcher = dispatcher,
              context = servletContextEvent.getServletContext,
              service = AuthzHttp4sService.service,
              name = "Auth Endpoints Service",
              mapping = "/authorizer/*"
            )  *> IO.unit
    } *> ifIO(ConfigSource.config.getBoolean("shrine.qep.create")) {
      ShrineServletMounter.mountService(
        dispatcher = dispatcher,
        context = servletContextEvent.getServletContext,
        name = "SHRINE Ontology Service",
        service = OntologyService().service,
        mapping = "/ontology/*"
      ) *> ShrineServletMounter.mountService(
        dispatcher = dispatcher,
        context = servletContextEvent.getServletContext,
        service = QepService().router,
        name = "SHRINE QEP Service",
        mapping = "/qep/*"
      ) *> IO.unit
    } *> ShrineServletMounter.mountService(
      dispatcher = dispatcher,
      context = servletContextEvent.getServletContext,
      name = "SHRINE CSP Violation Report Service",
      service = CSPReportService().service,
      mapping = "/csp/*"
    ) *> IO.unit
  }

  private def mountHubServices(dispatcher: Dispatcher[IO],sce: ServletContextEvent):IO[Unit] = {
    //todo do not bother if using AWS SQS
    ShrineServletMounter.mountService(
      dispatcher = dispatcher,
      context = sce.getServletContext,
      service = MessageQueueWebApi().service,
      name = "SHRINE MOM",
      mapping = "/mom/*"
    ) *> ShrineServletMounter.mountService(
      dispatcher = dispatcher,
      context = sce.getServletContext,
      service = HubDataService.service,
      name = "SHRINE Hub Data Service",
      mapping = "/hub/*"
    ) *> IO(info(s"hub services mounted for ${sce.getServletContext.getContextPath}"))
  }

  //todo tomcat does not call this method on shutdown. When that's fixed consider using the dispatcher (and shutdown) everywhere. See SHRINE2020-1632
  override def contextDestroyed(sce: ServletContextEvent): Unit = {
    info(s"contextDestroyed() started $sce")
    shutdown.unsafeRunSync()
    RepeatedIOTask.startInParallelAndWaitForAll(
      NodeSystemSpecSender.stopIO(),
      if(ConfigSource.config.getBoolean("shrine.adapter.create")) destroyAdapter else IO.unit,
      if(ConfigSource.config.getBoolean("shrine.hub.create")) destroyHub else IO.unit,
      Receiver.stopIO(),
    ).flatMap(_ => ExecutionContexts.shutdownIO())
      .unsafeRunSync()

    global.shutdown()
    info(s"contextDestroyed() finished")
  }

  private def destroyAdapter: IO[Unit] = QueuedQueriesPoller.stopIO()

  private def destroyHub: IO[Unit] = {
    OverdueResultsPoller.stopIO() *> HubReceiver.stopIO() *> IO(LocalMessageQueueStopper.stop())
  }

  private def ifIO(condition: => Boolean)(io: IO[Unit]): IO[Unit] = {
    if(condition) io
    else IO.unit
  }
}

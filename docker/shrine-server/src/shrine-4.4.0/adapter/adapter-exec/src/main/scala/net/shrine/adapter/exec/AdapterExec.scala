package net.shrine.adapter.exec

import cats.effect.{Deferred, IO}
import cats.effect.std.Dispatcher
import net.shrine.adapter.QueuedQueriesPoller
import net.shrine.adapter.mappings.AdapterMappings
import net.shrine.config.ConfigSource
import net.shrine.http4s.catsio.ExecutionContexts
import net.shrine.log.Loggable
import net.shrine.receiver.{NodeSystemSpecSender, Receiver}
import net.shrine.util.Versions
import scopt.OParser

import scala.language.postfixOps

object AdapterExec extends Loggable {
  import cats.effect.unsafe.implicits.global

  def main(args: Array[String]): Unit = try {
    Runtime.getRuntime.addShutdownHook(new Thread("shutdown hook") with Loggable{
      override def run():Unit = {
        info("start shutdown hook")

        (keepAlive.complete() *>
          shutdown *>
          NodeSystemSpecSender.stopIO() *>
          ExecutionContexts.shutdownIO()).unsafeRunSync()

        info("end shutdown hook")
      }
    })//addShutdownHook

    doCommand(args)
  } catch {
    case x: Throwable =>
      x.printStackTrace()
      System.exit(2)
  } finally {
    System.exit(0)
  }

  private case class CommandLine()

  private val parser: OParser[Unit, CommandLine] = {
    val builder = OParser.builder[CommandLine]
    OParser.sequence(
      builder.programName("adapterExec"),
      builder.head("Run a SHRINE adapter"),
      builder.head(Versions.version),
      builder.help('h', "help"),
      builder.version('v', "version"),
    )
  }

//separate method for testing without calling System.exit()
  private def doCommand(args: Array[String]): Unit = {
    //todo maybe someday there's something to glean from the command line args
    //noinspection ScalaUnusedSymbol
    OParser.parse(parser, args, CommandLine()) match {
      case Some(commandLine: CommandLine) =>
        runAdapter()
      case None => // The args are wrong. An error message will have been displayed
    }
  }

  @volatile private var shutdown: IO[Unit] = IO.unit
  @volatile private lazy val keepAlive:Deferred[IO,Unit] = Deferred[IO,Unit].unsafeRunSync()

  private def runAdapter(): Unit = {

    val shrineSystemIO = Dispatcher.parallel[IO].allocated.flatMap { case (dispatcher, shutdown) =>
      IO(this.shutdown = shutdown) *>
        AdapterMappings.compareAndReloadMappings(ConfigSource.config.getString("shrine.adapter.adapterMappingsFileName")) *>
        Receiver.startIO(dispatcher) *>
        QueuedQueriesPoller.startIO() *>
        NodeSystemSpecSender.startIO().start *>
        IO(info("Adapter exec started")) *>
        keepAlive.get *> //wait until the shutdown hook completes the
        IO(info(s"keep-alive completed"))
    }

    shrineSystemIO.unsafeRunSync()
    info(s"runAdapter() end")
  }
}
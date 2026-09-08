package net.shrine.qep.metrics

import net.shrine.config.ConfigSource
import net.shrine.crypto.SealerRevealer
import net.shrine.http4s.catsio.ExecutionContexts
import net.shrine.protocol.version.{DateStamp, UserName}
import net.shrine.util.Versions
import scopt.OParser

import java.io.File
import java.time.Instant
import java.util.{Calendar, Date}
import javax.crypto.SealedObject
import scala.language.postfixOps

object ResearcherMetrics {
  private val QUERIES_COMMAND = "queries"
  private val RESULTS_COMMAND = "results"
  private val RESEARCHERS_COMMAND = "researchers"

  private val DEFAULT_OUT_FILE = new File("")
  private case class CommandLine(
                                  command:String = QUERIES_COMMAND,
                                  out: File = DEFAULT_OUT_FILE,
                                  researcher:Option[UserName] = None,
                                  startDate: Calendar = Calendar.getInstance(),
                                  endDate: Calendar = Calendar.getInstance(),
                                )

  private val parser: OParser[Unit, CommandLine] = {
    val builder = OParser.builder[CommandLine]
    OParser.sequence(
      builder.programName("researcherMetrics"),
      builder.head("Gather metrics of SHRINE from the local QEP database"),
      builder.head(Versions.version),
      builder.help('h', "help"),
      builder.version('v', "version"),
      builder.cmd(QUERIES_COMMAND)
        .action((_, c) => c.copy(command = QUERIES_COMMAND))
        .text("Create a csv file of queries"),
      builder.cmd(RESULTS_COMMAND)
        .action((_, c) => c.copy(command = RESULTS_COMMAND))
        .text("Create a csv file of results"),
      builder.cmd(RESEARCHERS_COMMAND)
        .action((_, c) => c.copy(command = RESEARCHERS_COMMAND))
        .text("Create a csv file of researchers"),
      builder.opt[File]('o', "out")
        .optional()
        .valueName("<file>")
        .action((out, c) => c.copy(out = out))
        .text("csv files are written to this file. Defaults to command.datestamp.csv"),
      builder.opt[String]('u', "username")
        .optional()
        .valueName("<researcher user name>")
        .action((username, c) => c.copy(researcher = Option(new UserName(username))))
        .text("filter for just this researcher"),
      builder.opt[Calendar]('s',"startDate")
        .withFallback(() => {
          val cal = Calendar.getInstance()
          cal.setTime(Date.from(Instant.EPOCH))
          cal
        })
        .valueName("<date>")
        .action((startDate,c) => c.copy(startDate = startDate))
        .text("first date (included) like 2000-12-01. Defaults to the start of the epoch - 1970")
      ,
      builder.opt[Calendar]('e', "endDate")
        .withFallback(() => {
          val cal = Calendar.getInstance()
          cal.setTime(Date.from(Instant.now))
          cal
        })
        .valueName("<date>")
        .action((endDate, c) => c.copy(endDate = endDate))
        .text("last date (excluded) like 2000-12-01. Defaults to now")
      ,
    )
  }

//separate method - unit tests shouldn't call exit
  def doCommand(args: Array[String]): Unit = {
    OParser.parse(parser, args, CommandLine()) match {
      case Some(commandLine: CommandLine) =>
        val outFile: File = if(commandLine.out == DEFAULT_OUT_FILE) new File(s"${commandLine.command}.${DateStamp.now.underlying}.csv")
                            else commandLine.out

        val startDate = new DateStamp(commandLine.startDate.getTimeInMillis)
        val endDate = new DateStamp(commandLine.endDate.getTimeInMillis)

        val passwordConfigPath = "shrine.qep.database.dataSourceConfig.credentials.password"

        val databasePassword: SealedObject = SealerRevealer.seal(
          if (ConfigSource.config.hasPath(passwordConfigPath)) ConfigSource.config.getString(passwordConfigPath)
          else {
            print("database password>")
            val stdIn = System.console()
            new String(stdIn.readPassword())
          }
        )

        ConfigSource.configForBlock(passwordConfigPath, SealerRevealer.reveal(databasePassword), "password") {
          commandLine.command match {
            case command if command == QUERIES_COMMAND => QueryCsv.writeQueryCsv(outFile, startDate, endDate, commandLine.researcher)
            case command if command == RESULTS_COMMAND => ResultCsv.writeResultsCsv(outFile, startDate, endDate, commandLine.researcher)
            case command if command == RESEARCHERS_COMMAND => ResearcherCsv.writeResearcherCsv(outFile, startDate, endDate, commandLine.researcher)
          }
        }
      case None => // The args are wrong. An error message will have been displayed
    }
  }

  def main(args: Array[String]): Unit = try {
    doCommand(args)
  } catch {
    case x: Throwable =>
      x.printStackTrace()
      System.exit(2)
  } finally {
    import cats.effect.unsafe.implicits.global
    ExecutionContexts.shutdownIO().unsafeRunSync()
    System.exit(0)
  }
}
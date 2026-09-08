package net.shrine.hub.setup.downstream

import cats.effect.IO
import net.shrine.http4s.catsio.ExecutionContexts
import net.shrine.hub.mom.ShrineMomClient
import net.shrine.log.Loggable
import net.shrine.messagequeueclient.AwsSqsMessageQueueClient
import net.shrine.protocol.version.NodeKey

import scala.language.postfixOps

/**
 * A command line tool to help set up some of the more tedious parts of downstream nodes.
 */
object ShrineDownstreamSetup extends Loggable {
  import cats.effect.unsafe.implicits.global

  def main(args: Array[String]): Unit = {
    val status = try {
      executeCommand(args)
      0
    } catch {
      case wax: WrongArgumentsException =>
        println(wax.getMessage)
        1
      case x: Throwable =>
        x.printStackTrace()
        2
    } finally {
      ExecutionContexts.shutdownIO().unsafeRunSync()
    }
    System.exit(status)
  }

  def executeCommand(args: Array[String]): Unit = { //separated to allow testing without exit codes
    args match {
      case Array() => throw WrongArgumentsException(s"Requires at least one command argument of ${commands.map(_.name).mkString(", ")}")
      case _ => namesToCommands.get(args(0)).map(_.doIt(args)).map(_.unsafeRunSync())
        .orElse(throw WrongArgumentsException(s"${args(0)} is not a valid command. Use one of ${commands.map(_.name).mkString(", ")}"))
    }
  }

  def printUsage(): Unit = {
    println(
      """./shrineDownstream help - to see a list of commands
      """.stripMargin)
  }

  lazy val commands: Seq[Command] = Seq(
    Help,
    SetMomUserPolicy
  )

  lazy val namesToCommands: Map[String, Command] = commands.map(c => c.name -> c).toMap

  sealed trait Command {
    def name: String

    def help: String

    def doIt(args: Array[String]): IO[Unit]
  }

  object Help extends Command {
    override def name: String = "help"

    private val basicHelp = s"Call ./shrineDownstream <command> with one of these commands: ${commands.map(_.name).mkString(", ")}"

    override val help: String =
      s"""$basicHelp
         |Get help with a specific command with ./shrineDownstream help <command>
         |""".stripMargin

    override def doIt(args: Array[String]): IO[Unit] = IO {
      args match {
        case Array(_) => println(help)
        case Array(_, command) =>
          val helpString = namesToCommands.get(command).map(_.help).getOrElse(s"$command unknown. $basicHelp")
          println(helpString)
        case _ => println(help)
      }
    }
  }

  object SetMomUserPolicy extends Command {
    override def name: String = "setMomUserPolicy"

    private val basicHelp = s"Set the user policy for a downstream node to use the SHRINE network's MOM system"

    val hubQueueArnKey = "hubQueueArn"
    val nodeQueueArnKey = "nodeQueueArn"

    override val help: String =
      s"""./shrineDownstream $name iamUserName $hubQueueArnKey=<hubQueueArn> $nodeQueueArnKey=<nodeQueueArn>
         |
         |$basicHelp
         |
         |The hub admin will share these Amazon SQS ARNs with you after creating the queues.
         |""".stripMargin

    override def doIt(args: Array[String]): IO[Unit] = {
      args match {
        case Array(_) => IO(println(help))
        case Array(_, userName, _, _) =>
          val keysToValues: Map[String, String] = args.tail.tail.map { arg =>
            val strings = arg.split("=", 2)
            if (strings.length < 2) throw WrongArgumentsException(s"args to $name must be in key=value form which $arg does not follow.")
            strings(0) -> strings(1)
          }.toMap
          setMomUserPolicy(userName, keysToValues(hubQueueArnKey), keysToValues(nodeQueueArnKey))
        case _ => IO(println(help))
      }
    }

    def setMomUserPolicy(userName: String, hubQueueArn: String, nodeQueueArn: String): IO[Unit] = {
      ShrineMomClient.serviceIO.flatMap {
        case awsSqsMessageQueueClient: AwsSqsMessageQueueClient =>
          val nodeKey = NodeKey.localNodeKey
          for {
            _ <- awsSqsMessageQueueClient.putIamReceiveUserPolicy(userName, s"hub-to-${nodeKey.underlying}", nodeQueueArn)
            _ <- awsSqsMessageQueueClient.putIamSendUserPolicy(userName, s"hub-from-${nodeKey.underlying}", hubQueueArn)
          } yield ()
        case _ => IO.unit
      }
    }
  }
}

case class WrongArgumentsException(message:String) extends Exception(message)
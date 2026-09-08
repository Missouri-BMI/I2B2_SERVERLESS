package net.shrine.hub.setup

import cats.effect.IO
import com.typesafe.config.{Config, ConfigFactory}
import fs2.Stream
import net.shrine.http4s.catsio.ExecutionContexts
import net.shrine.hub.data.store.{HubDatabaseNetworkNotFoundException, HubDb}
import net.shrine.hub.mom.ShrineMomClient
import net.shrine.log.Loggable
import net.shrine.messagequeueclient.AwsSqsMessageQueueClient
import net.shrine.messagequeueservice.{MessageQueueService, MomQueue}
import net.shrine.protocol.version.NodeKey
import net.shrine.protocol.version.v2.{Network, Node}

import java.io.{File, FileNotFoundException}
import java.nio.file.Path
import scala.language.postfixOps

object ShrineNetworkLifecycle extends Loggable {
  import cats.effect.unsafe.implicits.global

  def main(args: Array[String]): Unit = {
    val status = try {
      executeCommand(args)
      0
    } catch {
      case wax: WrongArgumentsException =>
        println(wax.getMessage)
        printUsage()
        1
      case x:Throwable =>
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
      """./shrineLifecycle help - to see a list of commands
      """.stripMargin)
  }

  lazy val commands: Seq[Command] = Seq(
    CreateNetwork,
    ModifyNetwork,
    ShowNetwork,
    ListNodes,
    CreateNode,
    ModifyNode,
    ShowNode,
    DownstreamNodeMomArgs,
    RetireNode,
    RestoreNode,
    DeleteNetworkQueues,
    RecreateNetworkQueues,
    ListNetworkQueues,
    SwitchMomSystem,
    Help
  )

  lazy val namesToCommands: Map[String, Command] = commands.map(c => c.name -> c).toMap

  sealed trait Command{
    def name:String

    def help:String

    def doIt(args:Array[String]):IO[Unit]
  }

  object Help extends Command {
    override def name: String = "help"

    private val basicHelp = s"Call ./shrineLifecycle <command> with one of these commands: ${commands.map(_.name).mkString(", ")}"

    override val help: String =
      s"""$basicHelp
         |Get help with a specific command with ./shrineLifecycle help <command>
         |""".stripMargin

    override def doIt(args: Array[String]): IO[Unit] = IO{
      args match {
        case Array(_) => println(help)
        case Array(_,command) =>
          val helpString = namesToCommands.get(command).map(_.help).getOrElse(s"$command unknown. $basicHelp")
          println(helpString)
        case _ => println(help)
      }
    }
  }

  object CreateNetwork extends Command {
    override def name: String = "createNetwork"

    override val help:String =
      s"""./shrineLifecycle $name [path to network.conf file]
        |
        |The network.conf file is an HCONN-formatted file that specifies the initial network and nodes.
        |Here is a complete example that supports using AWS SQS on a two-node network:
        |
        |shrine {
        |  network {
        |    network {
        |      name = "SHRINE Dev test network"
        |      hubQueueName = "hub"
        |      adminEmail = "yourEmail@your.ctsa.edu"
        |      momId = "arn:aws:iam::CtsaAWSAccountNumber:user/hubUserName"
        |      aws.sqs = {
        |        queueOwnerAWSAccountId = "CtsaAWSAccountNumber"
        |        networkPrefix = "shrine-dev"
        |        region = "us-east-1"
        |      }
        |    }
        |    nodes = [
        |      {
        |        name = "Test CTSA"
        |        key = "shrine-dev-hub"
        |        userDomainName = "shrine-dev-hub"
        |        queueName = "shrine-dev-hub"
        |        sendQueries = "false"
        |        adminEmail = "yourEmail@your.ctsa.edu"
        |        momId = "arn:aws:iam::CtsaAWSAccountNumber:user/hubUserName"
        |      },
        |      {
        |        name = "Famous Hospital 1"
        |        key = "shrine-dev-node01"
        |        userDomainName = "shrine-dev-node01"
        |        adminEmail = "admin@hospital.famousUniversity1.edu"
        |        momId = "arn:aws:iam::Node1AWSAccountNumber:user/node01UserName"
        |      },
        |      {
        |        name = "Famous Hospital 2"
        |        key = "shrine-dev-node02"
        |        userDomainName = "shrine-dev-node02"
        |        adminEmail = "admin@hospital.famousUniversity2.edu"
        |        momId = "arn:aws:iam::Node2AWSAccountNumber:user/node02UserName"
        |      }
        |    ]
        |  }
        |}
        |
        |The file format is HCONN, so it supports comments and all features supported by Typesafe Config.
        |
        |Put the most thought into selecting node keys. These are immutable for the life of the network; they are meant
        |to be human-readable keys for the network admins to handle. queueNames are difficult but possible to change.
        |The rest of the values can be changed by modifyNetwork and modifyNode.
        |
        |Including the aws.sqs section in network tells shrine to use AWS SQS for its MOM system. This tool will only
        |make changes to queues with names that begin with the networkPrefix.
        |
        |For AWS SQS momIds are AWS user ARNs. AWS account numbers are the multi-digit AWS account numbers.
        |
        |For Kafka momIds are Kafka usernames.
        |
        |""".stripMargin

    override def doIt(args: Array[String]):IO[Unit] = {
      args match {
        case Array(_,configFileName) => importNetwork(Path.of(configFileName)).flatMap(createAllNeededQueues)
        case _ => throw WrongArgumentsException(s"createNetwork requires only a configFileName, not ${args.mkString(" ")}")
      }
    }

    def importNetwork(configFilePath:Path): IO[Network] = {
      info(s"About to import network")
      HubDb.db.selectTheNetworkIO.attempt.flatMap {
        case Left(_: HubDatabaseNetworkNotFoundException) =>
          info(s"No Network record found. Building one from $configFilePath")

          val networkConfigFile: File = configFilePath.toFile
          if(!networkConfigFile.exists()) throw new FileNotFoundException(s"$networkConfigFile does not exist")

          val importConfig:Config = ConfigFactory.parseFileAnySyntax(networkConfigFile).resolve()

          //get the initial network from shrine.conf 's hub section
          val networkConfig: Config = importConfig.getConfig("shrine.network")

          //turn the config into network and node entries
          val network: Network = Network.networkFromConfig(networkConfig)
          HubDb.db.upsertNetworkIO(network).flatMap { _ =>
            HubDb.db.selectLatestNodesIO.map(_.map(_.get.key)).map(_.toSet)
          }.flatMap { existingNodeKeys =>
            val nodes: Seq[Node] = Node.nodesFromConfig(networkConfig)
            val nodesToUpsert: Seq[Node] = nodes.filterNot(node => existingNodeKeys.contains(node.key))
            val nodeUpserts: Seq[IO[Unit]] = nodesToUpsert.map(node => HubDb.db.upsertNodeIO(node).flatMap(_ => IO.unit))
            info(s"Upserting nodes ${nodes.map(_.name.underlying).mkString(",")}")
            nodeUpserts.foldLeft(IO.unit) { (a, b) => a.flatMap(_ => b) }
          }.flatMap(_ => IO(network))
        case Left(x) => throw x
        case Right(network) =>
          throw WrongArgumentsException(s"${network.networkName} network already exists. Will use the network and nodes from existing records")
      }
    }
  }

  object ModifyNetwork extends Command {
    override def name: String = "modifyNetwork"

    override val help:String =
      s"""./shrineLifecycle $name [key=value [key=value[...]]
         |
         |Change the value of elements in the network. Possible keys include name, adminEmail, and momId
         |""".stripMargin

    override def doIt(args: Array[String]): IO[Unit] = {

      val keysToValues: Map[String, String] = args.tail.map { arg =>
        val strings = arg.split("=", 2)
        if (strings.length < 2) throw WrongArgumentsException(s"args to modifyNetwork must be in key=value form which $arg does not follow.")
        strings(0) -> strings(1)
      }.toMap

      import scala.jdk.CollectionConverters._
      val networkConfig: Config = ConfigFactory.parseMap(keysToValues.asJava)

      for {
        foundNetwork <- HubDb.db.selectTheNetworkIO
        modifiedNetwork <- IO(foundNetwork.versionWithoutNewMomSystemConfig(networkConfig))
        //Updating the bits of the queue for changes in the hub gets pretty involved. I'll kick that into the future.
        //I expect our hub admins can get all this right the first time
        _ <- HubDb.db.upsertNetworkIO(modifiedNetwork)
      } yield {
        println(s"Network modified ${modifiedNetwork.asJsonText.underlying}")
      }
    }
  }

  object ShowNetwork extends Command {
    override def name: String = "showNetwork"

    override val help:String =
      s"""./shrineLifecycle $name
         |
         |Show the current state of the network - as json
         |""".stripMargin

    override def doIt(args: Array[String]): IO[Unit] = {
      HubDb.db.selectTheNetworkIO.map(network => println(network.asJsonText.underlying))
    }
  }

  object ListNodes extends Command {
    override def name: String = "listNodes"

    override val help:String =
      s"""./shrineLifecycle $name
         |
         |List the node keys of nodes currently in the network
         |""".stripMargin

    override def doIt(args: Array[String]): IO[Unit] = {
      println("nodeKey\tsendQueries\tuserDomain")
      HubDb.db.selectLatestNodesIO.map(nodeTries => nodeTries.map{ nodeTry =>
        val node = nodeTry.get
        println(s"${node.key.underlying}\t${node.sendQueries}\t${node.userDomainName}")
      })
    }
  }

  object CreateNode extends Command {
    override def name: String = "createNode"

    override val help:String =
      s"""./shrineLifecycle $name [key=value [key=value[...]]
         |
         |Create a node . Values for key, name, userDomainName, adminEmail, queueName, and momId are required.
         |sendQueries is optional and defaults to true.
         |""".stripMargin

    override def doIt(args: Array[String]): IO[Unit] = {
      val keysToValues: Map[String, String] = args.tail.map { arg =>
        val strings = arg.split("=", 2)
        if (strings.length < 2) throw WrongArgumentsException(s"args to createNode must be in key=value form which $arg does not follow.")
        strings(0) -> strings(1)
      }.toMap

      import scala.jdk.CollectionConverters._
      val nodeConfig: Config = ConfigFactory.empty().withValue("node",ConfigFactory.parseMap(keysToValues.asJava).root())

      val node = Node.nodeFromConfig(nodeConfig)

      for {
        network <- HubDb.db.selectTheNetworkIO //if the network is not set up this will fail before making a mess
        oldNode <- HubDb.db.selectNodeByKeyIO(node.key)
        _ <- IO(if(oldNode.isDefined) throw WrongArgumentsException(s"Node ${node.key} already exists. Use modifyNode to change it."))
        maybeNode <- HubDb.db.upsertNodeIO(node)
        _ <- maybeNode.map(n => setUpNode(n, network)).getOrElse(IO.unit)
      } yield ()
    }
  }

  object DownstreamNodeMomArgs extends Command {
    override def name: String = "downstreamNodeMomArgs"

    override val help:String =
      s"""./shrineLifecycle $name <nodeKey>
         |
         |Show command line argument help for downstream nodes.
         |
         |For AWS SQS this shows the hub and node SQS queue ARNs, needed to configure the downstream node AWS IAM user policy.
         |""".stripMargin

    override def doIt(args: Array[String]): IO[Unit] = {
      args match {
        case Array(_, nodeKey) => showArns(nodeKey)
        case _ => throw WrongArgumentsException(s"$name requires only a node key, not ${args.mkString(" ")}")
      }
    }

    private def showArns(nodeKey: String):IO[Unit] = {
      ShrineMomClient.serviceIO.flatMap {
        case awsClient: AwsSqsMessageQueueClient =>
          for {
            network <- HubDb.db.selectTheNetworkIO
            node <- HubDb.db.selectNodeByKeyIO(new NodeKey(nodeKey))
            hubQueueArn <- awsClient.getQueueArn(network.hubQueueName)
            nodeQueueArn <- awsClient.getQueueArn(node.get.momQueueName)
          } yield {
            println(s"""hubQueueArn="$hubQueueArn" nodeQueueArn="$nodeQueueArn"""")
          }
        case _ => IO(println("No help needed for the downstream node admin"))
      }
    }
  }

  object ModifyNode extends Command {
    override def name: String = "modifyNode"

    override val help:String =
      s"""./shrineLifecycle $name nodeKey [key=value [key=value[...]]
         |
         |Modify the node identified by nodeKey . Use this command to change values for userDomainName, adminEmail, momId and sendQueries
         |""".stripMargin

    override def doIt(args: Array[String]): IO[Unit] = {
      val nodeKey = new NodeKey(args(1))

      val keysToValues: Map[String, String] = args.tail.tail.map { arg =>
        val strings = arg.split("=", 2)
        if (strings.length < 2) throw WrongArgumentsException(s"args to modifyNode must be in key=value form which $arg does not follow.")
        strings(0) -> strings(1)
      }.toMap

      import scala.jdk.CollectionConverters._
      val nodeConfig: Config = ConfigFactory.parseMap(keysToValues.asJava)

      for {
        foundNode <- HubDb.db.selectNodeByKeyIO(nodeKey).map(_.getOrElse(throw WrongArgumentsException(s"No node found for $nodeKey")))
        modifiedNode <- IO(foundNode.versionWithConfig(nodeConfig))
        service <- ShrineMomClient.serviceIO
        _ <-  if(nodeConfig.hasPath("momQueueName") && (foundNode.momQueueName != modifiedNode.momQueueName)) service.deleteQueueIO(foundNode.momQueueName)
              else IO.unit
        network <- HubDb.db.selectTheNetworkIO
        _ <-  if(nodeConfig.hasPath("momId") || nodeConfig.hasPath("momQueueName")) setUpNode(modifiedNode,network)
              else IO.unit
        _ <- HubDb.db.upsertNodeIO(modifiedNode)
      } yield {
        println(s"Modified node is ${modifiedNode.asJsonText.underlying}")
      }
    }
  }

  object ShowNode extends Command {
    override def name: String = "showNode"

    override val help:String =
      s"""./shrineLifecycle $name nodeKey
         |
         |Show the current state of the node identified by nodeKey as json
         |""".stripMargin

    override def doIt(args: Array[String]): IO[Unit] = {
      val nodeKey = new NodeKey(args(1))
      HubDb.db.selectNodeByKeyIO(nodeKey)
        .map(_.getOrElse(throw WrongArgumentsException(s"No node found for $nodeKey")))
        .map(node => println(node.asJsonText.underlying))
    }
  }

  /**
   * Note that the node will remain in the database but its queue will be deleted and the hub will no longer send it queries
   */
  object RetireNode extends Command {
    override def name: String = "retireNode"

    override val help:String =
      s"""./shrineLifecycle $name nodeKey
         |
         |Retire the the node identified by nodeKey.
         |
         |This command deletes the node's queue, removes its access to the hub's queue, and sets its "sendQueries" value to false.
         |""".stripMargin

    override def doIt(args: Array[String]): IO[Unit] = {
      val nodeKey = new NodeKey(args(1))

      for {
        foundNode <- HubDb.db.selectNodeByKeyIO(nodeKey).map(_.getOrElse(throw WrongArgumentsException(s"No node found for $nodeKey")))
        network <- HubDb.db.selectTheNetworkIO
        service <- ShrineMomClient.serviceIO
        modifiedNode <- HubDb.db.upsertNodeIO(foundNode.versionWithSendQueries(false))
        _ <- service.deleteQueueIfExistsIO(foundNode.momQueueName)
        _ <- service.removePermissionFromQueueIO(network.hubQueueName,foundNode.momId)
      } yield {
        println(s"${modifiedNode.get.key} will no longer receive queries or result progress. ${modifiedNode.get.asJsonText.underlying}")
      }
    }
  }

  object RestoreNode extends Command {
    override def name: String = "restoreNode"

    override val help:String =
      s"""./shrineLifecycle $name nodeKey
         |
         |Restore a retired node .
         |This node recreates the node's inbound queue, restores its access to the hub queue, and sets sendQueries to true.
         |
         |
         |""".stripMargin

    override def doIt(args: Array[String]): IO[Unit] = {
      val nodeKey = new NodeKey(args(1))

      for {
        network <- HubDb.db.selectTheNetworkIO
        foundNode <- HubDb.db.selectNodeByKeyIO(nodeKey).map(_.getOrElse(throw WrongArgumentsException(s"No node found for $nodeKey")))
        _ <- setUpNode(foundNode,network)
        modifiedNode <- HubDb.db.upsertNodeIO(foundNode.versionWithSendQueries(true))
      } yield {
        println(s"${modifiedNode.get.key} will again receive queries and result progress. ${modifiedNode.get.asJsonText.underlying}")
      }
    }
  }

  object DeleteNetworkQueues extends Command {
    override def name: String = "deleteNetworkQueues"

    override val help:String =
      s"""./shrineLifecycle $name
         |
         |Deletes all queues with this network's networkPrefix.
         |""".stripMargin

    override def doIt(args: Array[String]): IO[Unit] = {
      args match {
        case Array(_) => deleteQueues()
        case _ => throw WrongArgumentsException(s"deleteNetwork requires no extra arguments. Can not ues ${args.tail.mkString(" ")}")
      }
    }

    def deleteQueues():IO[Unit] = {
      ShrineMomClient.serviceIO.flatMap{ service =>
        service.queuesIO.flatMap{ queues: Seq[MomQueue] =>
          val ios: Seq[IO[Unit]] = queues.map(q => service.deleteQueueIO(q.name))
          val streams: Seq[Stream[IO, Unit]] = ios.map(Stream.eval)
          streams.foldLeft[Stream[IO, Unit]](Stream.empty) { (soFar, next) =>
            soFar.append(next)
          }.compile.drain
        }
      }
    }
  }

  object RecreateNetworkQueues extends Command {
    override def name: String = "recreateNetworkQueues"

    override val help:String =
      s"""./shrineLifecycle $name
         |
         |Recreate all queues used by this network, to reverse a previous deleteNetworkQueues.
         |""".stripMargin

    override def doIt(args: Array[String]): IO[Unit] = {
      args match {
        case Array(_) => recreateNetworkQueues()
        case _ => throw WrongArgumentsException(s"recreateNetworkQueues requires no extra arguments. Can not ues ${args.tail.mkString(" ")}")
      }
    }

    def recreateNetworkQueues():IO[Unit] = {
      for {
        network <- HubDb.db.selectTheNetworkIO
        _ <- createAllNeededQueues(network)
      } yield ()
    }
  }

  object ListNetworkQueues extends Command {
    override def name: String = "listLiveNetworkQueues"

    override val help: String =
      s"""./shrineLifecycle $name
         |
         |List all the live queues associated with this network.
         |""".stripMargin

    override def doIt(args: Array[String]): IO[Unit] = {
      args match {
        case Array(_, configFileName) => listLiveQueues(Path.of(configFileName))
        case _ => throw WrongArgumentsException(s"listLiveNetworkQueues requires a configFileName, not ${args.mkString(" ")}")
      }
    }
    def listLiveQueues(configFilePath:Path): IO[Unit] = {

      val momServiceIO:IO[MessageQueueService] = HubDb.db.selectTheNetworkIO.attempt.flatMap {
        case Left(_: HubDatabaseNetworkNotFoundException) =>
          info(s"No Network record found. Will ask the queue service what queues it has")

          val networkConfigFile: File = configFilePath.toFile
          if (!networkConfigFile.exists()) throw new FileNotFoundException(s"$networkConfigFile does not exist")

          val importConfig: Config = ConfigFactory.parseFileAnySyntax(networkConfigFile).resolve()
          //get the initial network from shrine.conf 's hub section
          val networkConfig: Config = importConfig.getConfig("shrine.network")
          //turn the config into network and node entries
          val network: Network = Network.networkFromConfig(networkConfig)

          //use the to get a mom service without writing it to the database
          ShrineMomClient.serviceIOFromNetwork(network)
        case Left(x) => throw x
        case Right(_) =>
          //use this network to get the mom service
          ShrineMomClient.serviceIO
      }

      info(s"About to list all the live queues.")
      for {
        momService <- momServiceIO
        _ <- IO(info(s"mom service $momService"))
        queues <- momService.queuesIO
      } yield {
        println(s"Live queues:\n${queues.map(_.name.underlying).mkString("\n")}")
      }
    }
  }

  object SwitchMomSystem extends Command {
    override def name: String = "switchMomSystem"

    override val help: String =
      s"""./shrineLifecycle $name [path to an abbreviated network.conf file]
         |
         |Deletes the existing network queues, changes the network configuration to use a new MOM system, last creates the required queues and access.
         |
         |The network.conf file is an HCONN-formatted file that specifies bits for the network and nodes in the new MOM system.
         |Here is a complete example that supports changing to AWS SQS:
         |
         |shrine {
         |  network {
         |    network {
         |      momId = "arn:aws:iam::CtsaAWSAccountNumber:user/hubUserName"
         |      aws.sqs = {
         |        queueOwnerAWSAccountId = "CtsaAWSAccountNumber"
         |        networkPrefix = "shrine-dev"
         |        region = "us-east-1"
         |      }
         |    }
         |    nodes = [
         |      {
         |        key = "shrine-dev-hub"
         |        momId = "arn:aws:iam::CtsaAWSAccountNumber:user/hubUserName"
         |      },
         |      {
         |        key = "shrine-dev-node01"
         |        momId = "arn:aws:iam::Node1AWSAccountNumber:user/node01UserName"
         |      },
         |      {
         |        key = "shrine-dev-node02"
         |        momId = "arn:aws:iam::Node2AWSAccountNumber:user/node02UserName"
         |      }
         |    ]
         |  }
         |}
         |
         |The file format is HCONN, so it supports comments and all features supported by Typesafe Config.
         |
         |Including the aws.sqs section in network tells shrine to use AWS SQS for its MOM system. This tool will only
         |make changes to queues with names that begin with the networkPrefix.
         |
         |For AWS SQS momIds are AWS user ARNs. AWS account numbers are the multi-digit AWS account numbers.
         |
         |For Kafka momIds are Kafka usernames.
         |
         |""".stripMargin

    override def doIt(args: Array[String]): IO[Unit] = {
      args match {
        case Array(_, configFileName) => updateNetwork(Path.of(configFileName)).flatMap(createAllNeededQueues)
        case _ => throw WrongArgumentsException(s"switchMomSystem requires only a configFileName, not ${args.mkString(" ")}")
      }
    }

    def updateNetwork(configFilePath: Path): IO[Network] = {
      info(s"About to switch the network's MOM system")
      HubDb.db.selectTheNetworkIO.attempt.flatMap {
        case Right(foundNetwork) =>
          //read the config file
          val networkConfigFile: File = configFilePath.toFile
          if (!networkConfigFile.exists()) throw new FileNotFoundException(s"$networkConfigFile does not exist")

          val newConfig: Config = ConfigFactory.parseFileAnySyntax(networkConfigFile).resolve()

          for{
            _ <- DeleteNetworkQueues.deleteQueues()
            _ <- updateNodes(newConfig)
            network <- updateNetwork(foundNetwork,newConfig)
            _ <- RecreateNetworkQueues.recreateNetworkQueues()
          } yield network
        case Left(_: HubDatabaseNetworkNotFoundException) =>
          throw WrongArgumentsException(s"No network found in the hub's database. Use createNetwork instead.")
        case Left(x) => throw x
      }
    }

    def updateNodes(config:Config):IO[Seq[Node]] = {

      import scala.jdk.CollectionConverters.ListHasAsScala
      val nodeConfigs: Seq[Config] = config.getConfigList("shrine.network.nodes").asScala.toSeq
      val nodesToUpsertIO: IO[Seq[Node]] = for {
        foundNodes <- HubDb.db.selectLatestNodesIO
        nodeMap <- IO{foundNodes.map{node => (node.get.key.underlying, node.get)}.toMap}
      } yield {
        nodeConfigs.map { nodeConfig =>
          nodeMap(nodeConfig.getString("key")).versionWithConfig(nodeConfig)
        }
      }
      nodesToUpsertIO.flatMap{nodesToUpsert =>
        val nodeUpserts: Seq[IO[Unit]] = nodesToUpsert.map(node => HubDb.db.upsertNodeIO(node).flatMap(_ => IO.unit))
        nodeUpserts.foldLeft(IO.unit) { (a, b) => a.flatMap(_ => b) }.map(_ => nodesToUpsert)
      }
    }

    def updateNetwork(foundNetwork:Network,config:Config):IO[Network] = {
      val networkConfig = config.getConfig("shrine.network.network")
      val updatedNetwork = foundNetwork.versionWithNewMomSystem(networkConfig)
      HubDb.db.upsertNetworkIO(updatedNetwork).map(_.get)
    }
  }

  def setUpNode(node: Node, network: Network): IO[Unit] = {

    for {
      shrineMomClient <- ShrineMomClient.serviceIO
      _ <- shrineMomClient.createQueueIfAbsentIO(node.momQueueName)
      _ <- shrineMomClient.addReceiverPermissionToQueueIO(node.momQueueName, node.momId)
      _ <- shrineMomClient.addSenderPermissionToQueueIO(node.momQueueName, network.momId)
      _ <- shrineMomClient.addSenderPermissionToQueueIO(network.hubQueueName, node.momId)
      _ <- IO(info(s"Created ${node.momQueueName}"))
    } yield ()
  }

  def createAllNeededQueues(network:Network):IO[Unit] = {
    val createSqsQueues = network.awsSqsConfig.isDefined || network.kafkaConfig.isDefined
    if(createSqsQueues) {
      info(s"About to create all the queues for ${network.networkName}")

      val networkAndNodesIO: IO[(Network, Seq[Node])] = for {
        momService <- ShrineMomClient.serviceIOFromNetwork(network)
        _ <- IO(info(s"mom service to create ${network.hubQueueName}"))
        _ <- momService.createQueueIfAbsentIO(network.hubQueueName)
        _ <- IO(info(s"add receiver permission to ${network.hubQueueName}"))
        _ <- momService.addReceiverPermissionToQueueIO(network.hubQueueName, network.momId)
        _ <- IO(info(s"Created ${network.hubQueueName}"))
        nodes <- HubDb.db.selectLatestNodesIO.map(_.toSeq).map(_.map(_.get))
      } yield (network, nodes)

      networkAndNodesIO.flatMap { networkAndNodes =>
        val ios: Seq[IO[Unit]] = networkAndNodes._2.map(node => setUpNode(node, networkAndNodes._1))
        val streams: Seq[Stream[IO, Unit]] = ios.map[Stream[IO, Unit]](Stream.eval)
        streams.foldLeft[Stream[IO, Unit]](Stream.empty) { (soFar, next) =>
          soFar.append(next)
        }.compile.drain
      }
    } else IO.unit
  }
}

//noinspection ScalaFileName
case class WrongArgumentsException(message:String) extends Exception(message)

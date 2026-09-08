/**
  * A simple scala script that test MessageQueueWedClient
  *
  * Load the file in scala REPL to test MessageQueueWebClient
  *
  * Created by yifan on 8/14/17.
  */
// To Run the test, simply start the scala REPL and load the file
// Enter the following commands using the command line

// #1. Start scala REPL with 2.11.x version
// mvn -Dscala.repl.maxprintstring=64000 scala:console

// #2. Load file in REPL:
// :load <path-to-file>

import cats.effect.IO
import net.shrine.messagequeueservice.{Message, MessageQueueService, MomQueue}

import scala.collection.immutable.Seq
import scala.concurrent.duration.Duration
import fs2.Stream
import net.shrine.config.ConfigSource
import net.shrine.protocol.version.MomQueueName
import net.shrine.protocol.version.v2.AwsSqsConfig

val configMap: Map[String, String] = Map(
  //"shrine.shrineHubBaseUrl" -> "https://shrine-dev1.catalyst:6443"
  "shrine.aws.sqs.queueOwnerAWSAccountId" -> "714168927121",
  "shrine.aws.sqs.networkPrefix" -> "daveTest",
  "shrine.aws.accessKeyId" -> "BringYourOwnKey",
  "shrine.aws.secretAccessKey" -> "BringYourOwnSecret"
)


def withMessageQueueClient[Out](block:MessageQueueService  =>Out):Out = {
  ConfigSource.atomicConfig.configForBlock(configMap, "MessageQueueWebClientTest") {
    import net.shrine.messagequeueclient.AwsSqsMessageQueueClient
    val messageQueueService:MessageQueueService = AwsSqsMessageQueueClient(AwsSqsConfig.fromConfig(ConfigSource.atomicConfig.config.getConfig("shrine.aws.sqs")))

    block(messageQueueService)
  }
}

withMessageQueueClient{ messageQueueService =>
  //Test getting a first queue
  val firstQueue = messageQueueService.createQueueIfAbsentIO(MomQueueName("firstQueue")).unsafeRunSync()
  messageQueueService.addSenderPermissionToQueueIO(MomQueueName("firstQueue"),"arn:aws:iam::714168927121:user/shrine-sqs-node01").unsafeRunSync()
  messageQueueService.addReceiverPermissionToQueueIO(MomQueueName("firstQueue"),"arn:aws:iam::714168927121:user/shrine-sqs-hub").unsafeRunSync()

  val allQueues: Seq[MomQueue] = messageQueueService.queuesIO.unsafeRunSync()

  val firstQueueGotten = messageQueueService.getQueueIO(MomQueueName("firstQueue")).unsafeRunSync()

  assert(firstQueue == firstQueueGotten,s"firstQueue should be the same as firstQueueGotten, but is not")

  //Test sending and receiving a message

  firstQueue.sendIO("firstMessage",0).unsafeRunSync()
  val firstDuration: Duration = Duration.create(19, "seconds")
  val receivedMsg: Option[Message] = firstQueue.receiveIO(firstDuration).unsafeRunSync()
  assert(receivedMsg.isDefined)
  val msg: Message = receivedMsg.get
  msg.completeIO().unsafeRunSync()

  assert(msg.contents == "firstMessage")

  val receivedMsg2: Option[Message] = firstQueue.receiveIO(firstDuration).unsafeRunSync()
  assert(receivedMsg2.isEmpty)

  //Test receiving a stream
  firstQueue.sendIO("one",1).unsafeRunSync()
  firstQueue.sendIO("two",2).unsafeRunSync()
  firstQueue.sendIO("three",3).unsafeRunSync()

  val stream: fs2.Stream[IO, Message] = firstQueue.receiveStream()

  val dispatchStream: Stream[IO, String] = stream.flatMap{ m =>
    println(s"${m.contents}")
    Stream.eval(m.completeIO().map(_ => m.contents))
  }

  val messages = dispatchStream.take(3).compile.toList.unsafeRunTimed(firstDuration).get
  assert(messages.size == 3)

  firstQueue.sendIO("four",4).unsafeRunSync()
  firstQueue.sendIO("five",5).unsafeRunSync()
  firstQueue.sendIO("six",6).unsafeRunSync()

  val moreMessages = dispatchStream.take(3).compile.toList.unsafeRunTimed(firstDuration).get
  assert(moreMessages.size == 3)

  messageQueueService.deleteQueueIO(firstQueue.name).unsafeRunSync()
  val noQueues: Seq[MomQueue] = messageQueueService.queuesIO.unsafeRunSync()
}


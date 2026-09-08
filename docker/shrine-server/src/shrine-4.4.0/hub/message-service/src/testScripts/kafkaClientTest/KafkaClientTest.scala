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

val configMap: Map[String, String] = Map(
  "shrine.shrineHubBaseUrl" -> "https://shrine-dev1.catalyst:6443"
)

val messageQueueWebClient:MessageQueueService = ConfigSource.atomicConfig.configForBlock(configMap, "MessageQueueWebClientTest") {
  import net.shrine.messagequeueclient.KafkaMessageQueueClient
  KafkaMessageQueueClient
}

//Test getting a first queue

val firstQueue = messageQueueWebClient.createQueueIfAbsentIO(new MomQueueName("firstQueue")).unsafeRunSync()

val allQueues: Seq[MomQueue] = messageQueueWebClient.queuesIO.unsafeRunSync()

val firstQueueGotten = messageQueueWebClient.getQueueIO(new MomQueueName("firstQueue")).unsafeRunSync()

assert(firstQueue == firstQueueGotten,s"firstQueue should be the same as firstQueueGotten, but is not")

//Test sending and receiving a message

firstQueue.sendIO("firstMessage", 1L).unsafeRunSync()
val firstDuration: Duration = Duration.create(19, "seconds")
val receivedMsg: Option[Message] = firstQueue.receiveIO(firstDuration).unsafeRunSync()
assert(receivedMsg.isDefined)
val msg: Message = receivedMsg.get
msg.completeIO().unsafeRunSync()

assert(msg.contents == "firstMessage")

val receivedMsg2: Option[Message] = firstQueue.receiveIO(firstDuration).unsafeRunSync()
assert(receivedMsg2.isEmpty)

//Test receiving a stream
firstQueue.sendIO("one", 1L).unsafeRunSync()
firstQueue.sendIO("two", 1L).unsafeRunSync()
firstQueue.sendIO("three", 1L).unsafeRunSync()

val stream: fs2.Stream[IO, Message] = firstQueue.receiveStream()

val dispatchStream: Stream[IO, String] = stream.flatMap{ m =>
  println(s"${m.contents}")
  Stream.eval(m.completeIO().map(_ => m.contents))
}

val messages = dispatchStream.take(3).compile.toList.unsafeRunTimed(firstDuration).get
assert(messages.size == 3)

firstQueue.sendIO("four", 1L).unsafeRunSync()
firstQueue.sendIO("five", 1L).unsafeRunSync()
firstQueue.sendIO("six", 1L).unsafeRunSync()

val moreMessages = dispatchStream.take(3).compile.toList.unsafeRunTimed(firstDuration).get
assert(moreMessages.size == 3)

messageQueueWebClient.deleteQueueIO(firstQueue.name).unsafeRunSync()
val noQueues: Seq[MomQueue] = messageQueueWebClient.queuesIO.unsafeRunSync()

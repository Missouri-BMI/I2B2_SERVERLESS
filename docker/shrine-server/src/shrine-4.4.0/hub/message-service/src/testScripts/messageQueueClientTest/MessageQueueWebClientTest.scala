/**
  * A simple scala script that test MessageQueueWedClient
  *
  * Load the file in scala REPL to test MessageQueueWebClient
  *
  * Created by yifan on 8/14/17.
  */
// To Run the test, simply start the scala REPL and load the file
// Enter the following commands using the command line

// #1. Start scala REPL with 2.11.8 version
// mvn scala:console

// #2. Load file in REPL:
// :load <path-to-file>

import cats.effect.IO
import net.shrine.messagequeueclient.MessageQueueWebClient
import net.shrine.messagequeueservice.{Message, Queue}

import scala.collection.immutable.Seq
import scala.concurrent.duration.Duration
import fs2.Stream
import net.shrine.config.ConfigSource

val configMap: Map[String, String] = Map(
  "shrine.shrineHubBaseUrl" -> "https://shrine-dev1.catalyst:6443"
)

val messageQueueWebClient = ConfigSource.atomicConfig.configForBlock(configMap, "MessageQueueWebClientTest") {
  new MessageQueueWebClient
}

//---------------

val firstQueue = messageQueueWebClient.createQueueIfAbsentIO("firstQueue").unsafeRunSync()

val allQueues: Seq[Queue] = messageQueueWebClient.queuesIO.unsafeRunSync()

val firstQueueGotten = messageQueueWebClient.getQueueIO("firstQueue").unsafeRunSync()

messageQueueWebClient.sendIO("firstMessage", firstQueue).unsafeRunSync()
val firstDuration: Duration = Duration.create(1, "seconds")
val receivedMsg: Option[Message] = messageQueueWebClient.receiveIO(firstQueue, firstDuration).unsafeRunSync()
assert(receivedMsg.isDefined)
val msg: Message = receivedMsg.get
msg.completeIO().unsafeRunSync()

val receivedMsg2: Option[Message] = messageQueueWebClient.receiveIO(firstQueue, firstDuration).unsafeRunSync()
assert(receivedMsg2.isEmpty)
val receivedMsg3: Option[Message] = messageQueueWebClient.receiveIO(firstQueue, firstDuration).unsafeRunSync()
assert(receivedMsg3.isEmpty)

messageQueueWebClient.sendIO("one", firstQueue).unsafeRunSync()
messageQueueWebClient.sendIO("two", firstQueue).unsafeRunSync()
messageQueueWebClient.sendIO("three", firstQueue).unsafeRunSync()

val stream: fs2.Stream[IO, Message] = messageQueueWebClient.receiveStream(firstQueue)

val dispatchStream = stream.flatMap{m =>
  println(s"${m.contents}")
  Stream.eval(m.completeIO())
}

val messages = dispatchStream.take(3).compile.toList.unsafeRunTimed(firstDuration).get
assert(messages.size == 3)

messageQueueWebClient.sendIO("four", firstQueue).unsafeRunSync()
messageQueueWebClient.sendIO("five", firstQueue).unsafeRunSync()
messageQueueWebClient.sendIO("six", firstQueue).unsafeRunSync()

val moreMessages = dispatchStream.take(3).compile.toList.unsafeRunTimed(firstDuration).get
assert(moreMessages.size == 3)

messageQueueWebClient.deleteQueueIO(firstQueue.name).unsafeRunSync()
val noQueues: Seq[Queue] = messageQueueWebClient.queuesIO.unsafeRunSync()

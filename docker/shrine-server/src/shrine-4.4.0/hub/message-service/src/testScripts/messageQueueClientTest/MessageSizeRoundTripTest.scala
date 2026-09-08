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

import cats.data.OptionT
import cats.effect.IO
import net.shrine.messagequeueclient.MessageQueueWebClient
import net.shrine.messagequeueservice.{Message, MessageAsJson, Queue, ShrineMessageRequests}

import scala.collection.immutable.Seq
import scala.concurrent.duration.Duration
import fs2.{Pure, Stream}
import net.shrine.config.ConfigSource
import org.http4s.{Request, Response, ServerSentEvent}
import org.json4s.native.Serialization.read



val configMap: Map[String, String] = Map(
  "shrine.shrineHubBaseUrl" -> "https://shrine-dev1.catalyst:6443"
)

val messageQueueWebClient = ConfigSource.atomicConfig.configForBlock(configMap, "MessageQueueWebClientTest") {
  new MessageQueueWebClient
}

//---------------

ConfigSource.atomicConfig.configForBlock(configMap, "MessageQueueWebApiTest") {


  val twoSeconds = Duration.create(2,"seconds")
  val queue = messageQueueWebClient.createQueueIfAbsentIO("messageSizeTest").unsafeRunSync()

  val stream: Stream[IO, Message] = messageQueueWebClient.receiveStream(queue)

  val iters = 14
  val expectedMessages = (1 to iters).map{ i =>
    val number = scala.math.pow(2,i).toInt
    s"$i $number ${"*".*(number)}"
  }

  expectedMessages.foreach{ expected =>
    messageQueueWebClient.sendIO(expected,queue).unsafeRunSync()
  }

  val expectedMessageStream: Stream[Pure, String] = Stream.emits(expectedMessages)

  val zippedStream = expectedMessageStream.zip(stream)

  zippedStream.map{expectedAndMessage: (String, Message) =>
    val expected: String = expectedAndMessage._1
    val message = expectedAndMessage._2

    println(s"${expected.length} ${message.contents.length}")

//    assert(expected.length == message.contents.length)

//    assert(expected == message.contents)

    message.completeIO()
  }.take(iters).compile.drain.unsafeRunTimed(twoSeconds)

  messageQueueWebClient.deleteQueueIO(queue.name).unsafeRunSync()
}

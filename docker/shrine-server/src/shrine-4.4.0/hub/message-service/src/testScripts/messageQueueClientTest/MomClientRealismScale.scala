/**
  * A simple scala script that scale up the test for running queries using shrine-dev1
  *
  * Load the file in scala REPL to run the tests
  *
  * Created by yifan on 10/17/17.
  */
// To Run the test, simply start the scala REPL and load the file
// Enter the following commands using the command line

// #1. Start scala REPL with 2.11.8 version
// mvn scala:console

// #2. Load file in REPL:
// :load <path-to-file>
import java.util.concurrent.Executors

import net.shrine.messagequeueclient.MessageQueueWebClient
import net.shrine.messagequeueservice.{Message, Queue}

import scala.collection.immutable.Seq
import scala.concurrent.duration.Duration
import java.io.{BufferedOutputStream, FileOutputStream, PrintStream}

import cats.effect.IO
import fs2.Stream
import net.shrine.config.ConfigSource

val timestamp: Long = System.currentTimeMillis / 1000
//val printStream: PrintStream = new PrintStream(new BufferedOutputStream(new FileOutputStream(s"ScaleUpTestOnDev1_$timestamp.txt")))
//System.setOut(printStream)
//System.setErr(printStream)
val configMap: Map[String, String] = Map( "shrine.shrineHubBaseUrl" -> "https://shrine-dev1.catalyst:6443")

ConfigSource.atomicConfig.configForBlock(configMap, "MessageQueueClientDev1") {

  val messageQueueWebClient = new MessageQueueWebClient

  val numberOfQEPs: Int = 64 // Can do 64 (with 16 messages)
  val numberOfMessages: Int = 16 // Can do 16 (with 64 QEPs)
  System.out.println(s"Running tests on ${messageQueueWebClient.momBaseUri}")

  //make the queues
  val queues: Seq[Queue] = (1 to numberOfQEPs).map { i =>
    val queueName: String = s"QEPQueue$i"
    //    val delete = messageQueueWebClient.deleteQueueIO(queueName).unsafeRunSync()
    //    System.out.println(s"Deleted queue: $queueName, $delete")

    val queue: Queue = messageQueueWebClient.createQueueIfAbsentIO(queueName).unsafeRunSync()
    System.out.println(s"Created queue $queueName on QEP $i")
    queue
  }

  val firstDuration: Duration = Duration.create(60, "seconds")
  val executor = Executors.newFixedThreadPool(numberOfQEPs)

  //listen for messages from each queue
  queues.map(queue => {
    new Runnable {
      override def run(): Unit = {
        Thread.currentThread().setName(s"QEPQueue${queue.name}")
          val receiveStream: Stream[IO, Message] = messageQueueWebClient.receiveStream(queue)
          receiveStream.flatMap{message: Message =>
            System.out.println(s"Received messages from the HUB, $message, thread: ${Thread.currentThread().getName}, id: ${Thread.currentThread().getId}")
            Stream.eval(message.completeIO())
        }.compile.drain.unsafeRunTimed(firstDuration)
      }
    }
  }).par.foreach(worker => executor.execute(worker))

  // send messages each queue
  queues.map{queue =>
    /*
        //get the hub's Queue and send the result to the hub
    val networkIO: IO[Network] = HubClient.getNetworkIO
    val queueIO: IO[Queue] = networkIO.flatMap{network =>
      Log.debug(s"network is $network")
      MessageQueueService.service.getQueueIO(network.hubQueueName.underlying)}
    val sendIO = queueIO.flatMap { queue: Queue =>
      Log.debug(s"sendResultMessage queue is ${queue.name}")
      MessageQueueService.service.sendIO(envelope.asJsonText.underlying, queue)
    }

     */
    for (i <- 1 to numberOfMessages) {
      val queueToUse = messageQueueWebClient.getQueueIO(queue.name)
      val sendIo = queueToUse.flatMap{queue =>
        messageQueueWebClient.sendIO(s"Message $i sent to ${queue.name}",queue)
      }.flatMap{_ => IO(System.out.println(s"Sent message $i to ${queue.name}"))}
      sendIo.unsafeRunTimed(firstDuration)
    }
  }

  val allQueues: Seq[Queue] = messageQueueWebClient.queuesIO.unsafeRunSync().filter(queue => {
    (queue.name != "shrinedev1") && (queue.name != "shrinedev2")
  })
  System.out.println(s"All Existing Queues: $allQueues")

}
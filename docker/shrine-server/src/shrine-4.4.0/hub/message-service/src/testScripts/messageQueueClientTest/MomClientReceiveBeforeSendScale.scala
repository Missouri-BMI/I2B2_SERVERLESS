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

  val firstDuration: Duration = Duration.create(15, "seconds")
  val executor = Executors.newFixedThreadPool(numberOfQEPs)

  //listen for messages from each queue
  queues.map(queue => {
    new Runnable {
      override def run(): Unit = {
        while (true) {
          val receivedOpt: Option[Message] = messageQueueWebClient.receiveIO(queue, firstDuration).unsafeRunSync()
          Thread.currentThread().setName(s"QEPQueue${queue.name}")
          System.out.println(s"Receiving messages from the HUB, $receivedOpt, thread: ${Thread.currentThread().getName}, id: ${Thread.currentThread().getId}")
          receivedOpt.foreach(msg => {
            msg.completeIO().unsafeRunSync()
            System.out.println(s"Completed Message $msg")
          })
        }
      }
    }
  }).par.foreach(worker => executor.execute(worker))

  // send messages each queue
  queues.map{queue =>
//    val queueName: String = s"QEPQueue$i"
//    val delete = messageQueueWebClient.deleteQueueIO(queueName).unsafeRunSync()
//    System.out.println(s"Deleted queue: $queueName, $delete")
//    val queue: Queue = messageQueueWebClient.createQueueIfAbsentIO(queueName).unsafeRunSync()
//    System.out.println(s"Created queue $queueName on QEP $i")
    for (i <- 1 to numberOfMessages) {
      messageQueueWebClient.sendIO(s"Message$i sent to ${queue.name}", queue).unsafeRunSync()
      System.out.println(s"Sent messages to dev1, attempt: $i")
    }
  }

  val allQueues: Seq[Queue] = messageQueueWebClient.queuesIO.unsafeRunSync().filter(queue => {
    (queue.name != "shrinedev1") && (queue.name != "shrinedev2")
  })
  System.out.println(s"All Existing Queues: $allQueues")

}
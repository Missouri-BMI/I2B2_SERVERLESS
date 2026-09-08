import cats.effect.IO
import net.shrine.config.ConfigSource
import net.shrine.messagequeueclient.AwsSqsMessageQueueClient
import net.shrine.protocol.version.v2.AwsSqsConfig


val configMap: Map[String, String] = Map(
  "shrine.aws.sqs.queueOwnerAWSAccountId" -> "714168927121",
  "shrine.aws.sqs.networkPrefix" -> "daveTest",
//  "shrine.aws.accessKeyId" -> "BringYourOwnKey",
//  "shrine.aws.secretAccessKey" -> "BringYourOwnSecret",
)

def withAwsMessageQueueClient[Out](block:AwsSqsMessageQueueClient  =>Out):Out = {
  ConfigSource.atomicConfig.configForBlock(configMap, "AwsMessageQueueWebClientTest") {
    import net.shrine.messagequeueclient.AwsSqsMessageQueueClient
    val messageQueueService:AwsSqsMessageQueueClient = AwsSqsMessageQueueClient(AwsSqsConfig.fromConfig(ConfigSource.atomicConfig.config.getConfig("shrine.aws.sqs")))

    block(messageQueueService)
  }
}

val nodeUserName = "shrine-dev-node01"

withAwsMessageQueueClient{aws =>
  for{
    _ <- aws.putIamSendUserPolicy(nodeUserName,"ShrineDevHub","*")
    _ <- aws.putIamReceiveUserPolicy(nodeUserName,"ShrineDevNode01","*")
  } yield IO.unit
}.unsafeRunSync()

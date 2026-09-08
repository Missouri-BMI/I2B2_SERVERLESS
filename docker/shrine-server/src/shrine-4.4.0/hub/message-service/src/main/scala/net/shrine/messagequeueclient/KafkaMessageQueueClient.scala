package net.shrine.messagequeueclient

import cats.effect.{Async, IO, Resource}
import fs2.kafka.{AdminClientSettings, AutoOffsetReset, CommittableConsumerRecord, CommittableOffset, ConsumerSettings, Deserializer, KafkaAdminClient, KafkaByteProducer, KafkaConsumer, ProducerSettings, Serializer}
import net.shrine.log.Loggable
import net.shrine.messagequeueservice.{DeliveryAttemptId, Message, MessageQueueService, MomQueue}
import net.shrine.protocol.version.MomQueueName
import net.shrine.http4s.catsio.LazyIO
import fs2.Stream
import net.shrine.config.ConfigSource
import net.shrine.crypto.SealerRevealer
import net.shrine.protocol.version.v2.KafkaConfig
import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.clients.producer.{ProducerRecord, RecordMetadata}
import org.apache.kafka.common.acl.{AccessControlEntry, AccessControlEntryFilter, AclBinding, AclBindingFilter, AclOperation, AclPermissionType}
import org.apache.kafka.common.resource.{PatternType, ResourcePattern, ResourcePatternFilter, ResourceType}
import org.apache.kafka.common.serialization.ByteArraySerializer

import java.util.Properties
import scala.concurrent.duration.Duration

case class KafkaMessageQueueClient(kafkaConfig:KafkaConfig) extends MessageQueueService with Loggable {
  info(s"Using KafkaMessageQueueClient")

  private def authenticationProps:Map[String,String] = {
    val kafkaUserName = ConfigSource.config.getString("shrine.kafka.sasl.jaas.username")
    val kafkaPassword = SealerRevealer.seal(ConfigSource.config.getString("shrine.kafka.sasl.jaas.password"))
    val saslJassConfig =
      s"""org.apache.kafka.common.security.scram.ScramLoginModule required username="$kafkaUserName" password="${SealerRevealer.reveal(kafkaPassword)}";"""

    Map(
      "bootstrap.servers" -> kafkaConfig.bootstrapServers,
      "security.protocol" -> kafkaConfig.securityProtocol,
      "sasl.mechanism" -> kafkaConfig.securityMechanism,
      "sasl.jaas.config" -> saslJassConfig,
      "ssl.truststore.location" -> ConfigSource.config.getString("shrine.kafka.ssl.truststore.location"),
      "ssl.truststore.password" -> ConfigSource.config.getString("shrine.kafka.ssl.truststore.password")
    )
  }

  private val producerSettings: ProducerSettings[IO, Array[Byte], Array[Byte]] =
    ProducerSettings[IO, Array[Byte], Array[Byte]](
      keySerializer = Serializer[IO, Array[Byte]],
      valueSerializer = Serializer[IO, Array[Byte]]
    ).withProperties(authenticationProps)
      .withMaxInFlightRequestsPerConnection(maxInFlightRequestsPerConnection = 1)
      .withRetries(retries = ConfigSource.config.getInt("shrine.kafka.retries"))

  private val consumerSettings: ConsumerSettings[IO, String, String] = ConsumerSettings[IO, String, String](
    keyDeserializer = Deserializer[IO, String],
    valueDeserializer = Deserializer[IO, String]
  ).withAutoOffsetReset(AutoOffsetReset.Earliest)
    .withProperties(authenticationProps)


  def createQueueIfAbsentIO(queueName: MomQueueName): IO[MomQueue] = {
    kafkaAdminClientResource[IO]().use[IO[MomQueue]] { client: KafkaAdminClient[IO] =>
      client.createTopic(new NewTopic(queueName.underlying, 1, 1.toShort))
        .map(_ => IO(KafkaTopic(new MomQueueName(queueName.underlying))))
    }.flatten
  }

  override def addReceiverPermissionToQueueIO(queueName: MomQueueName, receiverId: String): IO[Unit] = {
    val groupPattern = new ResourcePattern(ResourceType.GROUP, queueName.underlying, PatternType.LITERAL)
    val groupReadEntry = new AccessControlEntry(
      s"User:$receiverId",
      "*",
      AclOperation.READ,
      AclPermissionType.ALLOW
    )
    val groupAccess = new AclBinding(groupPattern, groupReadEntry)

    val topicPattern = new ResourcePattern(ResourceType.TOPIC, queueName.underlying, PatternType.LITERAL)
    val readEntry = new AccessControlEntry(
      s"User:$receiverId",
      "*",
      AclOperation.READ,
      AclPermissionType.ALLOW
    )
    val readAccess = new AclBinding(topicPattern,readEntry)
    val describeEntry = new AccessControlEntry(
      s"User:$receiverId",
      "*",
      AclOperation.DESCRIBE,
      AclPermissionType.ALLOW
    )
    val describeAccess = new AclBinding(topicPattern, describeEntry)

    kafkaAdminClientResource[IO]().use[Unit]{ client: KafkaAdminClient[IO] =>
      client.createAcls(List(readAccess,describeAccess,groupAccess))
    }
  }

  override def addSenderPermissionToQueueIO(queueName: MomQueueName, senderId: String): IO[Unit] = {
    val topicPattern = new ResourcePattern(ResourceType.TOPIC, queueName.underlying, PatternType.LITERAL)

    val writeEntry = new AccessControlEntry(
      s"User:$senderId",
      "*",
      AclOperation.WRITE,
      AclPermissionType.ALLOW
    )
    val writeAccess = new AclBinding(topicPattern, writeEntry)

    kafkaAdminClientResource[IO]().use[Unit] { client: KafkaAdminClient[IO] =>
      client.createAcls(List(writeAccess))
    }
  }

  override def removePermissionFromQueueIO(queueName: MomQueueName, receiverId: String): IO[Unit] = {
    val accessControlEntryFilter = new AccessControlEntryFilter(
      s"User:$receiverId",
      "*",
      AclOperation.ANY,
      AclPermissionType.ALLOW
    )

    val topicPatternFilter = new ResourcePatternFilter(ResourceType.TOPIC,queueName.underlying,PatternType.LITERAL)
    val topicAclFilter = new AclBindingFilter(topicPatternFilter,accessControlEntryFilter)

    val groupPatternFilter = new ResourcePatternFilter(ResourceType.GROUP,queueName.underlying,PatternType.LITERAL)
    val groupAclFilter = new AclBindingFilter(groupPatternFilter,accessControlEntryFilter)

    info(s"About to remove permissions $topicAclFilter $groupAclFilter")

    kafkaAdminClientResource[IO]().use[Unit] { client: KafkaAdminClient[IO] =>
      client.deleteAcls(Seq(topicAclFilter,groupAclFilter))
    }
  }

  def getQueueIO(queueName: MomQueueName): IO[MomQueue] = {
    IO(KafkaTopic(queueName))
  }

  def deleteQueueIO(queueName: MomQueueName): IO[Unit] = {

    info(s"About to delete topic $queueName ")
    kafkaAdminClientResource[IO]()
    .use[Unit] { client: KafkaAdminClient[IO] =>
      for {
        _ <- client.deleteTopic(queueName.underlying)
      } yield info(s"deleted topic ${queueName.underlying}")
    }
  }

  private def kafkaAdminClientResource[F[_]: Async](): Resource[IO, KafkaAdminClient[IO]] = {
    KafkaAdminClient.resource[IO](AdminClientSettings.apply("").withProperties(authenticationProps))
  }

  def queuesIO: IO[Seq[MomQueue]] = {

    kafkaAdminClientResource[IO]().use[Seq[MomQueue]] { client: KafkaAdminClient[IO] =>
      val topicNames: IO[Set[String]] = for {
        topic <- client.listTopics.names
      } yield topic

      topicNames.map(_.filter(_.startsWith(kafkaConfig.networkPrefix))) //just the topics for this shrine network
        .map[Seq[KafkaTopic]](m => m.map(p => KafkaTopic(new MomQueueName(p))).toSeq)
    }
  }

  //todo consider using a Kafka FS2 producer instead of a Java KafkaProducer with SHRINE2020-1304
  private lazy val producerIO: IO[KafkaByteProducer] = LazyIO("Kafka producer"){
    IO {
      val properties: Properties = {
        //isolating all the early-1990s style java around properties in these brackets.
        import scala.jdk.CollectionConverters._
        val properties = new Properties()
        properties.putAll(producerSettings.properties.asJava)
        properties
      }
      new org.apache.kafka.clients.producer.KafkaProducer[Array[Byte], Array[Byte]](properties, new ByteArraySerializer(), new ByteArraySerializer())
    }
  }

  private case class KafkaTopic(name: MomQueueName) extends MomQueue {

    override def sendIO(contents: String, subject: Long): IO[Unit] = {
      val record: ProducerRecord[Array[Byte], Array[Byte]] = new ProducerRecord(name.underlying, "".getBytes, contents.getBytes())
      producerIO.flatMap{producer: KafkaByteProducer =>
        IO.blocking{producer.send(record).get()}//IO.fromFuture(IO(producer.send(record) ))//todo not a CompatibleFuture - maybe do better with SHRINE2020-1304
      }.map{r: RecordMetadata =>
        info(s"send record metadata is $r")
      }

    }

    override def receiveIO(timeout:Duration): IO[Option[Message]] = {
      def processRecord(commitableRecord: CommittableConsumerRecord[IO,String, String]): IO[KafkaMessage] = {
        IO(KafkaMessage(commitableRecord.record.value, commitableRecord.offset))
      }

      val settings = consumerSettings.withGroupId(name.underlying)

      val stream: Stream[IO, KafkaMessage] = KafkaConsumer.stream(settings)
        .subscribeTo(name.underlying)
        .records.interruptScope.take(1)
        .mapAsync(1) { committable =>
          processRecord(committable)
        }

      stream.handleErrorWith( err => {
        error(s"ERROR receiving message from kafka: $err", err)
        Stream.empty
      }).compile.toList.map(p => p.headOption)
    }

    override def receiveStream():Stream[IO,Message] = {
      def processRecord(commitableRecord: CommittableConsumerRecord[IO,String, String]): IO[KafkaMessage] = {
        IO(KafkaMessage(commitableRecord.record.value, commitableRecord.offset))
      }

      val settings = consumerSettings.withGroupId(name.underlying)

      val stream =
        KafkaConsumer.stream(settings)
          .subscribeTo(name.underlying)
          .records.interruptScope
          .mapAsync(10) { committable =>
            processRecord(committable)
          }

      stream
    }
  }

  private case class KafkaMessage(
                            contents:String,
                            //unique position id of a message in a kafka partition
                            committableOffset: CommittableOffset[IO]
                        ) extends Message {
    /**
      * Call after the receiver has completed work on this message to prevent it being redelivered
      */
    override def completeIO(): IO[Unit] = {
      committableOffset.commit
    }

    override lazy val deliveryAttemptId: DeliveryAttemptId = new DeliveryAttemptId(committableOffset.offsetAndMetadata.offset())

    override lazy val millisecondsToComplete: Long = 30000L //could be read from a property, but 30 seconds is the default
  }
}



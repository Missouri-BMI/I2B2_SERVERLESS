package net.shrine.protocol.version.v2

import com.typesafe.config.Config
import io.circe.{Decoder, Encoder}
import net.shrine.protocol.version.{JsonText, MomQueueName, NetworkId, ValueClass}
import net.shrine.config.ConfigExtensions

import scala.util.Try


/**
  * A shrine network. This class holds some changable information about the network.
  *
  * @since 1.26
  * @author dwalend
  */
case class Network(
                    id: NetworkId = NetworkId.oneNetwork,
                    versionInfo: VersionInfo = VersionInfo.create,
                    networkName: String,
                    hubQueueName: MomQueueName,
                    adminEmail: String,
                    momId:String,
                    awsSqsConfig: Option[AwsSqsConfig], //this or kafka, but not both
                    kafkaConfig: Option[KafkaConfig]
                  ) extends Versioned[NetworkId] {

  override def asJsonText:JsonText = {
    //todo this can maybe be fully generic for all the common currency data, in Versioned
    //todo but the generics didn't work out of the box. Ask on the circe gitter channel SHRINE-2848
    import io.circe.generic.extras.Configuration
    import io.circe.generic.extras.auto.exportEncoder
    import io.circe.syntax.EncoderOps

    implicit val genDevConfig: Configuration = Versioned.genDevConfig
    import io.circe.generic.extras.encoding.UnwrappedEncoder
    implicit def valueClassEncoder[A <: ValueClass[_]](implicit encodeA: UnwrappedEncoder[A]): Encoder[A] = encodeA

    new JsonText(this.asJson.noSpaces)
  }

//Only call from the shrine lifecycle tool. Everything else should get it from the hub's data service
  def versionWithoutNewMomSystemConfig(networkConfig:Config):Network = {
    this.copy(
      versionInfo = versionInfo.next(),
      networkName = networkConfig.getOption("networkName",_.getString).orElse(networkConfig.getOption("name",_.getString)).getOrElse(networkName),
      adminEmail = networkConfig.getOption("adminEmail",_.getString).getOrElse(adminEmail),
      hubQueueName = networkConfig.getOption("hubQueueName",_.getString).map(new MomQueueName(_)).getOrElse(hubQueueName),
      momId = networkConfig.getOption("momId",_.getString).getOrElse(momId)
    )
  }

  def versionWithNewMomSystem(networkConfig:Config):Network = {
    this.copy(
      versionInfo = versionInfo.next(),
      networkName = networkConfig.getOption("networkName", _.getString).orElse(networkConfig.getOption("name", _.getString)).getOrElse(networkName),
      adminEmail = networkConfig.getOption("adminEmail", _.getString).getOrElse(adminEmail),
      hubQueueName = networkConfig.getOption("hubQueueName", _.getString).map(new MomQueueName(_)).getOrElse(hubQueueName),
      momId = networkConfig.getOption("momId", _.getString).getOrElse(momId),
      awsSqsConfig = networkConfig.getOptionConfigured("aws.sqs", AwsSqsConfig.fromConfig),
      kafkaConfig = networkConfig.getOptionConfigured("kafka", KafkaConfig.fromConfig)
    )
  }
}


object Network extends VersionedCompanion {

  val envelopeType: String = classOf[Network].getSimpleName

  def create(
              networkName: String,
              hubQueueName: String,
              adminEmail: String,
              momId:String,
              awsSqsConfig: Option[AwsSqsConfig],
              kafkaConfig: Option[KafkaConfig]
            ): Network = Network(
    networkName = networkName,
    hubQueueName = MomQueueName(hubQueueName),
    adminEmail = adminEmail,
    momId = momId,
    awsSqsConfig = awsSqsConfig,
    kafkaConfig = kafkaConfig
  )

  //todo if this can maybe be fully generic for all the common currency data, maybe it can live in Versioned SHRINE-2848
  def tryRead(jsonText: JsonText): Try[Network] = Try {
    import io.circe.parser.decode
    import io.circe.generic.extras.auto.exportDecoder
    import io.circe.generic.extras.Configuration

    implicit val genDevConfig: Configuration = Versioned.genDevConfig
    import io.circe.generic.extras.decoding.UnwrappedDecoder
    implicit def valueClassDecoder[A <: ValueClass[_]](implicit decodeA: UnwrappedDecoder[A]): Decoder[A] = decodeA

    decode[Network](jsonText.underlying) match {
      case Right(node) => node
      case Left(x) => throw x //throw errors for now. Eventually do something clever, maybe with circe optics
    }
  }

//Only call from the shrine lifecycle tool. Everything else should get it from the hub's data service
  def networkFromConfig(config: Config): Network = {
    val networkConfig = config.getConfig("network")
    val awsSqsConfig = networkConfig.getOptionConfigured("aws.sqs",AwsSqsConfig.fromConfig)
    val kafkaConfig = networkConfig.getOptionConfigured("kafka",KafkaConfig.fromConfig)

    (awsSqsConfig,kafkaConfig) match {
      case (Some(_),Some(_)) => throw new IllegalStateException(s"Only one of aws.sqs or kafka can be specified, not both.")
      case _ => //this is fine
    }

    Network.create(
      networkName = networkConfig.getOption("networkName",_.getString).getOrElse(networkConfig.getString("name")),
      hubQueueName = networkConfig.getString("hubQueueName"),
      adminEmail = networkConfig.getString("adminEmail"),
      momId = networkConfig.getString("momId"),
      awsSqsConfig = awsSqsConfig,
      kafkaConfig = kafkaConfig
    )
  }
}

case class AwsSqsConfig(regionName: String, queueOwnerAwsAccountId:String, networkPrefix: String) {
  def asJsonText:JsonText = {
    //todo this can maybe be fully generic for all the common currency data, in Versioned
    //todo but the generics didn't work out of the box. Ask on the circe gitter channel SHRINE-2848
    import io.circe.generic.extras.auto.exportEncoder
    import io.circe.syntax.EncoderOps
    import io.circe.generic.extras.Configuration

    implicit val genDevConfig: Configuration = Versioned.genDevConfig

    new JsonText(this.asJson.noSpaces)
  }
}

object AwsSqsConfig{
  //Only call from the shrine lifecycle tool. Everything else should get it from the hub's data service
  def fromConfig(config:Config): AwsSqsConfig = {
    val queueOwnerAwsAccountId: String = config.getString("queueOwnerAWSAccountId") //todo or else get it from the AWS ID Key
    val networkPrefix: String = config.getString("networkPrefix")
    val regionName:String = config.getOption("region",config => config.getString).getOrElse("us-east-1")
    AwsSqsConfig(regionName, queueOwnerAwsAccountId, networkPrefix)
  }

  //todo this can maybe be fully generic for all the common currency data, maybe it can live in Versioned
  def read(jsonText:JsonText):AwsSqsConfig = {
    import io.circe.parser.decode
    import io.circe.generic.extras.auto.exportDecoder
    import io.circe.generic.extras.Configuration

    implicit val genDevConfig: Configuration = Versioned.genDevConfig

    decode[AwsSqsConfig](jsonText.underlying) match {
      case Right(result) => result
      case Left(x) => throw x //throw errors for now. Eventually do something clever, maybe with circe optics
    }
  }
}

case class KafkaConfig(
                        networkPrefix: String,
                        bootstrapServers:String,
                        securityProtocol:String,
                        securityMechanism:String
                      ) {
  def asJsonText:JsonText = {
    //todo this can maybe be fully generic for all the common currency data, in Versioned
    //todo but the generics didn't work out of the box. Ask on the circe gitter channel SHRINE-2848
    import io.circe.generic.extras.auto.exportEncoder
    import io.circe.syntax.EncoderOps
    import io.circe.generic.extras.Configuration

    implicit val genDevConfig: Configuration = Versioned.genDevConfig

    new JsonText(this.asJson.noSpaces)
  }
}

object KafkaConfig{
  //Only call from the shrine lifecycle tool. Everything else should get it from the hub's data service
  def fromConfig(config:Config): KafkaConfig = {
    KafkaConfig(
      networkPrefix = config.getString("networkPrefix"),
      bootstrapServers = config.getString("bootstrapServers"),
      securityProtocol = config.getString("securityProtocol"),
      securityMechanism = config.getString("securityMechanism")
    )
  }

  //todo this can maybe be fully generic for all the common currency data, maybe it can live in Versioned
  def read(jsonText:JsonText):KafkaConfig = {
    import io.circe.parser.decode
    import io.circe.generic.extras.auto.exportDecoder
    import io.circe.generic.extras.Configuration

    implicit val genDevConfig: Configuration = Versioned.genDevConfig

    decode[KafkaConfig](jsonText.underlying) match {
      case Right(result) => result
      case Left(x) => throw x //throw errors for now. Eventually do something clever, maybe with circe optics
    }
  }
}

package net.shrine.protocol.version.v1

//fromConnfig add email address

import com.typesafe.config.Config
import io.circe.{Decoder, Encoder}
import net.shrine.protocol.version.{DateStamp, ItemVersion, JsonText, MomQueueName, NodeId, NodeKey, NodeName, ProtocolVersion, UserDomainName, ValueClass}
import net.shrine.config.ConfigExtensions

import scala.util.Try
import scala.collection.immutable.Seq

/**
 * A node in the shrine network
 *
 * @since 1.26
 * @author dwalend
 */
case class Node(
                 id:NodeId = NodeId.create(),
                 versionInfo: VersionInfo = VersionInfo(
                   protocolVersion = new ProtocolVersion(1),
                   itemVersion = ItemVersion.one,
                   createDate = DateStamp.now,
                   changeDate = DateStamp.now
                 ),
                 name:NodeName,
                 key:NodeKey,
                 userDomainName:UserDomainName,
                 momQueueName:MomQueueName,
                 adminEmail: String,
                 sendQueries:Boolean = true,
                 understandsProtocol:ProtocolVersion = ProtocolVersion.current
               ) extends Versioned[NodeId] {

  def versionWithNodeName(name:String):Node = {
    this.copy(
      versionInfo = this.versionInfo.next,
      name = new NodeName(name)
    )
  }

  def versionWithSendQueries(sendQueries: Boolean):Node = {
    this.copy(
      versionInfo = this.versionInfo.next,
      sendQueries = sendQueries
    )
  }

  def asJsonText:JsonText = {
    //todo this can maybe be fully generic for all the common currency data, in Versioned
    //todo but the generics didn't work out of the box. Ask on the circe gitter channel SHRINE-2848
    //todo see https://tpolecat.github.io/2015/04/29/f-bounds.html for a hint
    import io.circe.generic.extras.Configuration
    import io.circe.generic.extras.auto.exportEncoder
    import io.circe.syntax.EncoderOps

    implicit val genDevConfig: Configuration = Versioned.genDevConfig
    import io.circe.generic.extras.encoding.UnwrappedEncoder
    implicit def valueClassEncoder[A <: ValueClass[_]](implicit encodeA: UnwrappedEncoder[A]): Encoder[A] = encodeA

    new JsonText(this.asJson.noSpaces)
  }
}

object Node extends VersionedCompanion {

  val envelopeType: String = classOf[Node].getSimpleName

  def apply(
             name: NodeName,
             key: NodeKey,
             userDomainName: UserDomainName,
             adminEmail: String
           ): Node = new Node(
    name = name,
    key = key,
    userDomainName = userDomainName,
    momQueueName = new MomQueueName(key.underlying),
    adminEmail = adminEmail
  )

  def create(
              name:String,
              key:String,
              userDomainName: String,
              adminEmail: String,
              momQueueName: Option[String] = None, //to use the key
              sendQueries:Boolean = true
            ):Node = Node(
    name = new NodeName(name),
    key = new NodeKey(key),
    userDomainName = new UserDomainName(userDomainName),
    adminEmail = adminEmail,
    momQueueName = new MomQueueName(momQueueName.getOrElse(key)),
    sendQueries = sendQueries
  )

  //todo if this can maybe be fully generic for all the common currency data, maybe it can live in Versioned SHRINE-2848
  def tryRead(jsonText:JsonText):Try[Node] = Try {
    import io.circe.generic.extras.Configuration
    import io.circe.generic.extras.auto.exportDecoder
    import io.circe.parser.decode

    implicit val genDevConfig: Configuration = Versioned.genDevConfig
    import io.circe.generic.extras.decoding.UnwrappedDecoder
    implicit def valueClassDecoder[A <: ValueClass[_]](implicit decodeA: UnwrappedDecoder[A]): Decoder[A] = decodeA

    decode[Node](jsonText.underlying) match {
      case Right(node) => node
      case Left(x) => throw x //throw errors for now. Eventually do something clever, maybe with circe optics
    }
  }

  private def fromConfig(config:Config):Node = create(
    name = config.getString("name"),
    key = config.getString("key"),
    userDomainName = config.getString("userDomainName"),
    adminEmail = config.getString("adminEmail"),
    momQueueName = config.getOption("queueName",_.getString),
    sendQueries = config.getOption("sendQueries",_.getBoolean).getOrElse(true)
  )

  def nodeFromConfig(config:Config):Node = fromConfig(config.getConfig("node"))

  def nodesFromConfig(config:Config):Seq[Node] = {
    import scala.jdk.CollectionConverters.ListHasAsScala
    config.getConfigList("nodes").asScala.toSeq.map(Node.fromConfig)
  }

  def nodesToJson(nodes:Iterable[Node]): JsonText = {
    import io.circe.generic.extras.Configuration
    import io.circe.generic.extras.auto.exportEncoder
    import io.circe.syntax.EncoderOps

    implicit val genDevConfig: Configuration = Versioned.genDevConfig

    new JsonText(nodes.toSeq.sortBy(_.name.underlying.toLowerCase).asJson.spaces2)
  }

  def jsonToNodes(jsonText: JsonText): Seq[Node] = {
    import io.circe.generic.extras.Configuration
    import io.circe.generic.extras.auto.exportDecoder
    import io.circe.parser.decode

    implicit val genDevConfig: Configuration = Versioned.genDevConfig

    decode[Seq[Node]](jsonText.underlying) match {
      case Right(nodes) => nodes
      case Left(x) => throw x //throw errors for now. Eventually do something clever, maybe with circe optics
    }

  }
}
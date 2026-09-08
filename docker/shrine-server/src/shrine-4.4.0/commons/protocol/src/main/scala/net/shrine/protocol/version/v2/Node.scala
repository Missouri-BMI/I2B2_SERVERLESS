package net.shrine.protocol.version.v2

import com.typesafe.config.Config
import io.circe.{Decoder, Encoder}
import net.shrine.protocol.version.{DateStamp, ItemVersion, JsonText, MomQueueName, NodeId, NodeKey, NodeName, ProtocolVersion, ShrineVersion, UserDomainName, ValueClass}
import net.shrine.config.ConfigExtensions
import net.shrine.protocol.version.ItemVersion.next

import scala.util.Try

/**
  * A node in the shrine network
  *
  * @since 1.26
  * @author dwalend
  */
case class Node(
                 id:NodeId = NodeId.create(),
                 versionInfo: VersionInfo = VersionInfo.create,
                 name:NodeName,
                 key:NodeKey,
                 userDomainName:UserDomainName,
                 momQueueName:MomQueueName,
                 adminEmail: String,
                 sendQueries:Boolean = true,
                 understandsProtocol:ProtocolVersion = ProtocolVersion.current,
                 momId:String,
                ) extends Versioned[NodeId] {

  def versionWithNodeName(name:String):Node = {
    this.copy(
      versionInfo = this.versionInfo.next(),
      name = new NodeName(name)
    )
  }

  def versionWithSendQueries(sendQueries: Boolean):Node = {
    this.copy(
      versionInfo = this.versionInfo.next(),
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

  def versionWithConfig(config:Config):Node = {
    this.copy(
      versionInfo = VersionInfo(
        protocolVersion = new ProtocolVersion(config.getOption("protocolVersion",_.getInt).getOrElse(versionInfo.protocolVersion.underlying)),
        shrineVersion = new ShrineVersion(config.getOption("shrineVersion",_.getString).getOrElse(ShrineVersion.current.underlying)),
        itemVersion = ItemVersion.next(versionInfo.itemVersion),
        createDate = DateStamp.now,
        changeDate = DateStamp.now
      ),
      name = config.getOption("name",_.getString).map(new NodeName(_)).getOrElse(name),
      userDomainName = config.getOption("userDomainName",_.getString).map(new UserDomainName(_)).getOrElse(userDomainName),
      adminEmail = config.getOption("adminEmail",_.getString).getOrElse(adminEmail),
      momQueueName = config.getOption("momQueueName",_.getString).orElse(config.getOption("queueName",_.getString)).map(new MomQueueName(_)).getOrElse(momQueueName),
      sendQueries = config.getOption("sendQueries",_.getBoolean).getOrElse(sendQueries),
      momId = config.getOption("momId",_.getString).getOrElse(momId),
      understandsProtocol = new ProtocolVersion(config.getOption("understandsProtocol",_.getInt).getOrElse(understandsProtocol.underlying)),
    )
  }
}

object Node extends VersionedCompanion {

  val envelopeType: String = classOf[Node].getSimpleName

  def apply(
             name: NodeName,
             key: NodeKey,
             userDomainName: UserDomainName,
             adminEmail: String,
             momId:String,
           ): Node = new Node(
    name = name,
    key = key,
    userDomainName = userDomainName,
    momQueueName = MomQueueName(key.underlying),
    adminEmail = adminEmail,
    momId = momId,
  )

  def create(
              name:String,
              key:String,
              userDomainName: String,
              adminEmail: String,
              momQueueName: Option[String] = None, //to use the key
              sendQueries:Boolean = true,
              momId:String,
              versionInfo: VersionInfo = VersionInfo.create,
              understandsProtocol:ProtocolVersion = ProtocolVersion.current,
            ):Node = Node(
    name = new NodeName(name),
    key = new NodeKey(key),
    userDomainName = new UserDomainName(userDomainName),
    adminEmail = adminEmail,
    momQueueName = MomQueueName(momQueueName.getOrElse(key)),
    sendQueries = sendQueries,
    momId = momId,
    versionInfo = versionInfo,
    understandsProtocol = understandsProtocol
  )

  //todo if this can maybe be fully generic for all the common currency data, maybe it can live in Versioned SHRINE-2848
  def tryRead(jsonText:JsonText):Try[Node] = Try {
    import io.circe.generic.extras.Configuration
    import io.circe.generic.extras.auto.exportDecoder
    import io.circe.parser.decode

    implicit val genDevConfig: Configuration = Versioned.genDevConfig
    import io.circe.generic.extras.decoding.UnwrappedDecoder
    implicit def valueClassDecoder[A <: ValueClass[_]](implicit decodeA: UnwrappedDecoder[A]): Decoder[A] = decodeA

    val node: Node = decode[Node](jsonText.underlying) match {
      case Right(node) => node
      case Left(x) => throw x //throw errors for now. Eventually do something clever, maybe with circe optics
    }
    node.copy(momQueueName = MomQueueName(node.momQueueName.underlying)) //convert old stored values to something url-safe todo can be removed in v2
  }

  private def fromConfig(config:Config):Node = create(
    name = config.getString("name"),
    key = config.getString("key"),
    userDomainName = config.getString("userDomainName"),
    adminEmail = config.getString("adminEmail"),
    momQueueName = config.getOption("momQueueName",_.getString).orElse(config.getOption("queueName",_.getString)),
    sendQueries = config.getOption("sendQueries",_.getBoolean).getOrElse(true),
    momId = config.getString("momId"),
    versionInfo = VersionInfo(
      protocolVersion = new ProtocolVersion(config.getOption("protocolVersion",_.getInt).getOrElse(ProtocolVersion.current.underlying)),
      shrineVersion = new ShrineVersion(config.getOption("shrineVersion",_.getString).getOrElse(ShrineVersion.current.underlying)),
      itemVersion = ItemVersion.one,
      createDate = DateStamp.now,
      changeDate = DateStamp.now
    ),
    understandsProtocol = new ProtocolVersion(config.getOption("understandsProtocol",_.getInt).getOrElse(ProtocolVersion.current.underlying)),
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
package net.shrine.protocol.version.v1

import io.circe.{Decoder, Encoder}
import net.shrine.protocol.version.{DateStamp, ItemVersion, JsonText, NodeId, ProtocolVersion, ResearcherId, UserDomainName, UserName, ValueClass}

import scala.util.Try


/**
  * A researcher in the shrine network
  *
  * @since 1.26
  * @author dwalend
  */
case class Researcher(
                 id:ResearcherId = ResearcherId.create(),
                 versionInfo: VersionInfo = VersionInfo(
                   protocolVersion = new ProtocolVersion(1),
                   itemVersion = ItemVersion.one,
                   createDate = DateStamp.now,
                   changeDate = DateStamp.now
                 ),
                 userName:UserName,
                 userDomainName:UserDomainName,
                 nodeId:NodeId
                ) extends Versioned[ResearcherId] {

  def asJsonText:JsonText = {
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

  def equalsIgnoreVersion(other:Researcher):Boolean = {
    //a little tupled foo for future-proofing
    val thisTupled = Researcher.unapply(this).get
    val otherTupled = Researcher.unapply(other).get
    val zipped: Iterator[(Any, Any)] = thisTupled.productIterator.zip(otherTupled.productIterator)
    zipped.forall{e =>
      if (e._1.isInstanceOf[VersionInfo]) true //don't care about version info
      else e._1 == e._2
    }
  }

  def withMemberValuesOf(t:Researcher):Researcher = {
    //a little tupled foo for future-proofing
    val tupled = Researcher.unapply(t).get
    val idAndVersion = tupled.copy(_1 = id,_2 = versionInfo.next)
    (Researcher.apply _).tupled(idAndVersion)
  }
}

object Researcher extends VersionedCompanion {

  val envelopeType: String = classOf[Researcher].getSimpleName

  def create(
            userName: String,
            userDomainName: String,
            nodeId: NodeId
            ): Researcher = Researcher(
    id = ResearcherId.create(),
    userName = new UserName(userName),
    userDomainName = new UserDomainName(userDomainName),
    nodeId = nodeId
  )

  /**
   * This odd method exists because the QEP does not really have a notion of Researchers.
   * The researcher only exists as fields in a query there -  not as a first-class entity. However, a researcher needs
   * a consistent ID when the QEP creates a query and sends it to the hub. This method does that. When the QEP has
   * access to a collection of researchers then this method should be obsolete, or just for testing. See SHRINE2020-1104
   */
  def createWithRepeatableId(
              userName: String,
              userDomainName: String,
              nodeId: NodeId
            ): Researcher = Researcher(
    id = new ResearcherId(userName.hashCode  ^ userDomainName.hashCode ^ nodeId.underlying.hashCode()),
    versionInfo = VersionInfo.createWithRepeatableFakeDateStamp,
    userName = new UserName(userName),
    userDomainName = new UserDomainName(userDomainName),
    nodeId = nodeId
  )

  //todo if this can maybe be fully generic for all the common currency data, maybe it can live in Versioned SHRINE-2848
  def tryRead(jsonText:JsonText):Try[Researcher] = Try {
    import io.circe.parser.decode
    import io.circe.generic.extras.auto.exportDecoder
    import io.circe.generic.extras.Configuration

    implicit val genDevConfig: Configuration = Versioned.genDevConfig
    import io.circe.generic.extras.decoding.UnwrappedDecoder
    implicit def valueClassDecoder[A <: ValueClass[_]](implicit decodeA: UnwrappedDecoder[A]): Decoder[A] = decodeA

    decode[Researcher](jsonText.underlying) match {
      case Right(researcher) => researcher
      case Left(x) => throw x //throw errors for now. Eventually do something clever, maybe with circe optics
    }
  }
}

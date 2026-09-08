package net.shrine.protocol.version.v1

import io.circe.{Decoder, Encoder}
import net.shrine.protocol.version.v1.TopicId
import net.shrine.protocol.version.{DateStamp, ItemVersion, JsonText, ProtocolVersion, ResearcherId, ValueClass}

import scala.util.Try


/**
 * A Topic in the shrine network
 *
 * @since 1.26
 * @author dwalend
 */
case class Topic(
                  id:TopicId = TopicId.create(),
                  versionInfo: VersionInfo = VersionInfo(
                    protocolVersion = new ProtocolVersion(1),
                    itemVersion = ItemVersion.one,
                    createDate = DateStamp.now,
                    changeDate = DateStamp.now
                  ),
                  researcherId:ResearcherId,
                  name:String,
                  description:String
                  //todo TopicState
                ) extends Versioned[TopicId] {

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

  def equalsIgnoreVersion(other:Topic):Boolean = {
    //a little tupled foo for future-proofing
    val thisTupled = Topic.unapply(this).get
    val otherTupled = Topic.unapply(other).get
    val zipped: Iterator[(Any, Any)] = thisTupled.productIterator.zip(otherTupled.productIterator)
    zipped.forall{e =>
      if (e._1.isInstanceOf[VersionInfo]) true //don't care about version info
      else e._1 == e._2
    }
  }

  def withMemberValuesOf(t:Topic):Topic = {
    //a little tupled foo for future-proofing
    val tupled = Topic.unapply(t).get
    val idAndVersion = tupled.copy(_1 = id,_2 = versionInfo.next)
    (Topic.apply _).tupled(idAndVersion)
  }
}

object Topic extends VersionedCompanion {

  //Used only for tests.Do not use for new work
  def createRepeatableForShrine200(
                                    researcherId:ResearcherId,
                                    name:String,
                                    description:String
                                  ): Topic = Topic(
    id = new TopicId(researcherId.underlying.hashCode() ^ name.hashCode ^ description.hashCode),
    versionInfo = VersionInfo.createWithRepeatableFakeDateStamp,
    researcherId = researcherId,
    name = name,
    description = description
  )

  //Use this one for all new work
  def createCompatibleWithShrine200(
                                     researcherId:ResearcherId,
                                     name:String,
                                     description:String,
                                     localId:Int
                                   ): Topic = Topic(
    id = new TopicId(researcherId.underlying.hashCode() ^ localId.toString.hashCode ^ name.hashCode),
    versionInfo = VersionInfo.createWithRepeatableFakeDateStamp,
    researcherId = researcherId,
    name = name,
    description = description
  )

  val envelopeType: String = classOf[Topic].getSimpleName

  //todo if this can maybe be fully generic for all the common currency data, maybe it can live in Versioned SHRINE-2848
  def tryRead(jsonText:JsonText):Try[Topic] = Try {
    import io.circe.generic.extras.Configuration
    import io.circe.generic.extras.auto.exportDecoder
    import io.circe.parser.decode

    implicit val genDevConfig: Configuration = Versioned.genDevConfig
    import io.circe.generic.extras.decoding.UnwrappedDecoder
    implicit def valueClassDecoder[A <: ValueClass[_]](implicit decodeA: UnwrappedDecoder[A]): Decoder[A] = decodeA

    decode[Topic](jsonText.underlying) match {
      case Right(topic) => topic
      case Left(x) => throw x //throw errors for now. Eventually do something clever, maybe with circe optics
    }
  }
}

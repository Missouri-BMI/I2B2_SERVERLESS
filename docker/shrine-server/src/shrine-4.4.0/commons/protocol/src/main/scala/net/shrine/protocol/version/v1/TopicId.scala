package net.shrine.protocol.version.v1
import net.shrine.protocol.version.Id

class TopicId(val underlying:Long) extends AnyVal with Id

object TopicId {
  private[version] def create():TopicId = Id.create(new TopicId(_))
}
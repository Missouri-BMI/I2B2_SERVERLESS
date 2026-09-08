package net.shrine.protocol.version.v1

import net.shrine.protocol.version.{DateStamp, ItemVersion, ProtocolVersion}

/**
 * Version information for entities stored and transmitted as json
 *
 * @since 1.26
 * @author dwalend
 *
 * @param protocolVersion version of the protocol used - v1
 * @param itemVersion version of this item - starts at 1 and counts up
 * @param createDate date this item was created
 * @param changeDate date this version of the item was created
 */
//todo if you think this is right forever, move it up to the version package.
case class VersionInfo(
                        protocolVersion:ProtocolVersion,
                        itemVersion:ItemVersion,
                        createDate:DateStamp,
                        changeDate:DateStamp
                      ) {

  def next: VersionInfo = {
    this.copy(itemVersion = ItemVersion.next(itemVersion), changeDate = DateStamp.now)
  }

  def withChangeDate(maybeChangeDate:Option[DateStamp]):VersionInfo = {
    maybeChangeDate.fold(this)(date => this.copy(changeDate = date))
  }
}

object VersionInfo {
  private[version] def create: VersionInfo = {
    VersionInfo(
      protocolVersion = new ProtocolVersion(1),
      itemVersion = ItemVersion.one,
      createDate = DateStamp.now,
      changeDate = DateStamp.now
    )
  }

  private[version] def createWithRepeatableFakeDateStamp: VersionInfo = {
    VersionInfo(
      protocolVersion = new ProtocolVersion(1),
      itemVersion = ItemVersion.one,
      createDate = DateStamp.repeatableFakeDateStamp,
      changeDate = DateStamp.repeatableFakeDateStamp
    )
  }
}

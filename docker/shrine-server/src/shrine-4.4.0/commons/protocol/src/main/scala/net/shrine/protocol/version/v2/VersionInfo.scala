package net.shrine.protocol.version.v2

import net.shrine.protocol.version.{DateStamp, ItemVersion, ProtocolVersion, ShrineVersion}

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
case class VersionInfo(
                        protocolVersion:ProtocolVersion,
                        shrineVersion:ShrineVersion,
                        itemVersion:ItemVersion,
                        createDate:DateStamp,
                        changeDate:DateStamp
                  ) {

  def next(changeDate:DateStamp = DateStamp.now): VersionInfo = {
    this.copy(itemVersion = ItemVersion.next(itemVersion), changeDate = changeDate)
  }
}

object VersionInfo {
  private[version] def create: VersionInfo = {
    VersionInfo(
      protocolVersion = ProtocolVersion.current,
      shrineVersion = ShrineVersion.current,
      itemVersion = ItemVersion.one,
      createDate = DateStamp.now,
      changeDate = DateStamp.now
    )
  }

  /**
   * This odd method exists because the QEP does not really have a notion of Researchers.
   * The researcher only exists as fields in a query there -  not as a first-class entity. However, a researcher needs
   * a consistent ID when the QEP creates a query and sends it to the hub. This method does that. When the QEP has
   * access to a collection of researchers then this method should be obsolete, or just for testing. See SHRINE2020-1104
   */
  private[version] def createWithRepeatableFakeDateStamp: VersionInfo = {
    VersionInfo(
      protocolVersion = ProtocolVersion.current,
      shrineVersion = ShrineVersion.current,
      itemVersion = ItemVersion.one,
      createDate = DateStamp.repeatableFakeDateStamp,
      changeDate = DateStamp.repeatableFakeDateStamp
    )
  }
}


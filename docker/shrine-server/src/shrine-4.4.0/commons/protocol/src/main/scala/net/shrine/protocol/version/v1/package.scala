package net.shrine.protocol.version

import net.shrine.protocol.version.ProtocolVersion

/**
 * This package contains a general JSON protocol envelope for SHRINE's messages' formats in version 1.26
 *
 * @author david
 * @since 1.26.1.1
 */
/*
The package object remains in commons/protocol to support its version id to help identify messages from older versions
of shrine. We moved the rest of this package to adapter/v1Protocol along with the i2b2 xml only used by the i2b2 CRC.
 */
package object v1 {
  val packageName: String = getClass.getPackage.getName

  val versionId: ProtocolVersion = new ProtocolVersion(packageName.split('.').drop(4).head.tail.toInt)
}

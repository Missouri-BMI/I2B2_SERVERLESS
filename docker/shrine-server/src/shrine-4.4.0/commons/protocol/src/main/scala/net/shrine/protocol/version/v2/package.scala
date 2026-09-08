package net.shrine.protocol.version

/**
  * This package contains a general JSON protocol envelope for SHRINE's messages' formats updated for SHRINE 3.3
  *
  * @author david
  * @since 3.3.0
  */
package object v2 {
  val packageName: String = getClass.getPackage.getName

  val versionId: ProtocolVersion = new ProtocolVersion(packageName.split('.').drop(4).head.tail.toInt)
}

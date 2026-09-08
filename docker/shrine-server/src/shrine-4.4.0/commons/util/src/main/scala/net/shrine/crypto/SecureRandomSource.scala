package net.shrine.crypto

import java.security.SecureRandom
import java.util.UUID

/**
 * A wrapper for Java's SecureRandom to save multiple initializations when entropy is scarce.
 */
object SecureRandomSource {
  private lazy val secureRandom = new SecureRandom()

  def fillBytes(bytes:Array[Byte]):Array[Byte] = synchronized {
    secureRandom.nextBytes(bytes)
    bytes
  }

  /**
   * Note that it is fine to use an insecure random, or even a carefully chosen hashcode, to create IDs for shrine.
   * Using SecureRandom is an attempt to satisfy FSMA scans.
   * @return
   */
  def nextId(): Long = synchronized {
    val uuid = UUID.randomUUID() //this uses SecureRandom internally
    (uuid.getMostSignificantBits ^ uuid.getLeastSignificantBits).abs
  }

  /**
   * Note that it is fine to use an insecure random, or even a carefully chosen hashcode, to create IDs for shrine.
   * Using SecureRandom is an attempt to satisfy FSMA scans.
   * @return
   */
  def nextGaussian(): Double = synchronized {
    Math.abs(secureRandom.nextGaussian())
  }
}

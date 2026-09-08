package net.shrine.crypto

import javax.crypto.spec.SecretKeySpec
import javax.crypto.{Cipher, SealedObject}

/**
 * A utility to meet FSMA requirements for protecting passwords in memory.
 *
 * I have serious doubts about its effectiveness - the key for the crypto algorithm is in the same heap as the
 * encrypted bits, so it's just security-by-obscurity. The strings still flow through the heap on their way in and out.
 */
object SealerRevealer {

  private lazy val key: SecretKeySpec = {
    val randomBytes = SecureRandomSource.fillBytes(new Array[Byte](32))
    val key = new SecretKeySpec(randomBytes, "AES")
    key
  }

  private val transformation = "AES/CBC/PKCS5Padding"
  private lazy val encrypter:Cipher = {
    val c:Cipher = Cipher.getInstance(transformation)
    c.init(Cipher.ENCRYPT_MODE,key)
    c
  }

  def seal(secret:String):SealedObject = {
    new SealedObject(secret,encrypter)
  }

  def reveal(sealedObject: SealedObject):String = {
    sealedObject.getObject(key).asInstanceOf[String]
  }
}

package net.shrine.crypto

/**
  * An adapter object so that the new crypto package can coexist with the
  * existing Signer and Verifier interfaces
  * @param keyStoreCollection The BouncyKeyStoreCollection that is signing
  *                           and verifying broadcast messages
  */
case class SignerVerifier(keyStoreCollection: BouncyKeyStoreCollection)
  extends BouncyKeyStoreCollection
{
  override def signBytes(bytesToSign: Array[Byte]): Array[Byte] = keyStoreCollection.signBytes(bytesToSign)

  override def verifyBytes(cmsEncodedSignature: Array[Byte], originalMessage: Array[Byte]): Boolean =
    keyStoreCollection.verifyBytes(cmsEncodedSignature, originalMessage)

  def verifySignature(cmsEncodedSignature: Array[Byte],originalMessage: Array[Byte]):Either[Throwable,Unit] =
    keyStoreCollection.verifySignature(cmsEncodedSignature, originalMessage)

  override val myEntry: KeyStoreEntry = keyStoreCollection.myEntry

  override def allEntries: Iterable[KeyStoreEntry] = keyStoreCollection.allEntries

  //todo put the modern signing/ encryption/ compression steps here

}

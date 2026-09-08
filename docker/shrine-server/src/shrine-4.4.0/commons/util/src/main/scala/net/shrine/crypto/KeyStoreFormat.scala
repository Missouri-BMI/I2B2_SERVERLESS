package net.shrine.crypto

/**
 * @author clint
 * @since Nov 22, 2013
 */
final case class KeyStoreFormat private(name: String) {
  override def toString: String = name
}

object KeyStoreFormat {
  def Default: KeyStoreFormat = PKCS12

  val PKCS12: KeyStoreFormat = KeyStoreFormat("PKCS12")

  val JKS: KeyStoreFormat = KeyStoreFormat("JKS")

  def valueOf(name: String): Option[KeyStoreFormat] = {
    def normalize(s: String) = s.toLowerCase

    val n = normalize(name)

    if (n == normalize(JKS.name)) { Some(JKS) }
    else if (n == normalize(PKCS12.name)) { Some(PKCS12) }
    else { None }
  }
}
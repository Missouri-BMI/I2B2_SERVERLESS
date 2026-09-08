package net.shrine.crypto

import ch.qos.logback.classic.Level
import javax.naming.ConfigurationException
import net.shrine.problem.{AbstractProblem, ProblemSources}

/**
  * Created by ty on 10/27/16.
  */
case class ImproperlyConfiguredKeyStoreProblem(override val throwable: Option[Throwable], override val description: String)
  extends AbstractProblem(ProblemSources.Commons)
{
  override val summary: String = "There is a problem with how the KeyStore has been configured"

  override def logLevel: Level = Level.ERROR
}

case class ImproperlyConfiguredKeyStoreException(message: String) extends ConfigurationException(message)

object CryptoErrors {
  type Entries = Iterable[KeyStoreEntry]

  def comma(entries: Entries): String = entries.flatMap(_.aliases).mkString(", ")

  final val NoPrivateKeyInStore =
    "Could not find a key in the KeyStore with a PrivateKey. Without one, SHRINE cannot sign messages."
  final val CouldNotFindCa =
    "You must specify at least one ca cert alias corresponding to a PrivateKey entry for the Hub"
  final val CouldNotFindSigningCert =
    "There is no private entry signed by a public entry in the keystore corresponding to the Hub."
  final def CouldNotFindCaAlias(entries: Entries) =
    s"Could not find a KeyStore Entry corresponding to the aliases '${comma(entries)}"
  final def NotSignedByCa(entries: Entries, caEntry: KeyStoreEntry) =
    s"The private entries `${comma(entries)}` were not signed by the ca entry `${caEntry.aliases.first}`"
  final def PrivateEntryIsCaEntry(aliases: Iterable[String]) =
    s"Your private cert must not also be your CA cert. Intersecting aliases: `${aliases.mkString(", ")}`"
  final def ExpiredCertificates(entries: Entries) =
    s"The following certificates have expired: `${comma(entries)}`"

  private[crypto] def noKeyError(myEntry: KeyStoreEntry) = {
    val illegalEntry = new IllegalArgumentException(s"The provided keystore entry $myEntry did not have a private key")
    val problem = ImproperlyConfiguredKeyStoreProblem(Some(illegalEntry),
      s"The KeyStore entry identified as the signing cert for this node did not provide a private key to sign with." +
        s" Please check the KeyStore entry with the alias `${myEntry.aliases.first}`.")
    throw problem.throwable.get
  }

  private[crypto] def invalidSignatureFormat(bytes: Array[Byte]) = {
    val illegalSignature = new IllegalArgumentException("Given a signature with bytes that are not valid CMSSignedData")
    val problem = InvalidSignatureFormatProblem(bytes, Some(illegalSignature))
    throw problem.throwable.get
  }

  private[crypto] def configureError(description: String): ImproperlyConfiguredKeyStoreProblem = {
    ImproperlyConfiguredKeyStoreProblem(Some(ImproperlyConfiguredKeyStoreException(description)), description)
  }
}

case class InvalidSignatureFormatProblem(illegalBytes: Array[Byte], override val throwable: Option[Throwable])
  extends AbstractProblem(ProblemSources.Commons)
{
  override def logLevel: Level = Level.INFO

  override def summary: String = s"An incoming message contained a signature that was not signed using CMSSignedData."

  override def description: String =
    s"The message with signature bytes ${s"[${illegalBytes.mkString(" ")}]"} was not signed using CMSSignedData, which means" +
      s"it's in an invalid format. Please check that every SHRINE node is operating on the same version."
}

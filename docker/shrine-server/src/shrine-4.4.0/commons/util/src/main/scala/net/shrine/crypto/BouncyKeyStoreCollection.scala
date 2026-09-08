package net.shrine.crypto


import java.io.{File, FileInputStream, IOException, InputStream}
import java.security.cert.X509Certificate
import java.security.{KeyStore, PrivateKey, Security}

import com.typesafe.config.Config
import CryptoErrors.{CouldNotFindCa, CouldNotFindSigningCert, ExpiredCertificates, configureError}
import net.shrine.config.ConfigSource
import net.shrine.log.Loggable
import net.shrine.util.NonEmptySeq
import org.bouncycastle.jce.provider.BouncyCastleProvider

import scala.jdk.CollectionConverters.EnumerationHasAsScala

/**
  * Created by ty on 10/25/16.
  *
  * Abstracts away the need to track down
  * all the corresponding pieces of a KeyStore entry by collecting them into a collection
  * of [[KeyStoreEntry]]s.
  * See: [[DownStreamCertCollection]]
  */
trait BouncyKeyStoreCollection extends Loggable {

  val myEntry: KeyStoreEntry

  val provider: BouncyCastleProvider = BouncyKeyStoreCollection.provider

  def signBytes(bytesToSign: Array[Byte]): Array[Byte] = myEntry.sign(bytesToSign).getOrElse(CryptoErrors.noKeyError(myEntry))

  def verifyBytes(cmsEncodedSignature: Array[Byte], originalMessage: Array[Byte]): Boolean

  def verifySignature(cmsEncodedSignature: Array[Byte],originalMessage: Array[Byte]):Either[Throwable,Unit]

  def allEntries: Iterable[KeyStoreEntry]

  def keyStore: KeyStore = BouncyKeyStoreCollection.keyStore.getOrElse(throw new IllegalStateException("Accessing keyStore without loading from keyStore file first!"))

  def descriptor: KeyStoreDescriptor = BouncyKeyStoreCollection.descriptor.getOrElse(throw new IllegalStateException("Accessing keyStoreDescriptor without loading from keyStore file first!"))
}

/**
  * Factory object that reads the correct cert collection from the file.
  */
object BouncyKeyStoreCollection extends Loggable {

  val provider = new BouncyCastleProvider()
  Security.addProvider(provider)
  var descriptor: Option[KeyStoreDescriptor] = None
  var keyStore: Option[KeyStore] = None
  val SHA256 = "SHA256withRSA"

  lazy val fromConfig: BouncyKeyStoreCollection = {
    import net.shrine.config.ConfigExtensions

    val shrineConfig: Config = ConfigSource.config.getConfig("shrine")
    val keyStoreDescriptor = KeyStoreDescriptor(shrineConfig.getConfig("keystore"), shrineConfig.getConfigOrEmpty("hub"), shrineConfig.getConfigOrEmpty("queryEntryPoint"))
    BouncyKeyStoreCollection.fromFileRecoverWithClassPath(keyStoreDescriptor)
  }

  // On failure creates a problem so it gets logged into the database.
  type EitherCertError = Either[ImproperlyConfiguredKeyStoreProblem, BouncyKeyStoreCollection]

  /**
    * Creates a cert collection from a keyStore. Returns an Either to abstract away
    * try catches/problem construction until the end.
    *
    * @return [[EitherCertError]]
    */
  def createCertCollection(keyStore: KeyStore, descriptor: KeyStoreDescriptor):
    EitherCertError =
  {
    BouncyKeyStoreCollection.descriptor = Some(descriptor)
    BouncyKeyStoreCollection.keyStore = Some(keyStore)
    // Read all of the KeyStore entries from the file into a KeyStore Entry
    val values = keyStore.aliases().asScala.map(alias =>
      (alias, keyStore.getCertificate(alias), Option(keyStore.getKey(alias, descriptor.password.toCharArray).asInstanceOf[PrivateKey])))
    val entries = values.map(value => KeyStoreEntry(value._2.asInstanceOf[X509Certificate], NonEmptySeq(value._1, Nil), value._3)).toSet
    //OK to try to use an expired cert, but still log a Problem
    if (entries.exists(_.isExpired())) configureError(ExpiredCertificates(entries.filter(_.isExpired())))

    createCentralCertCollection(entries, descriptor,descriptor.trustModel.isCa)
  }

  def createCentralCertCollection(entries: Set[KeyStoreEntry], descriptor: KeyStoreDescriptor, isHub: Boolean):
    EitherCertError =
  {
    val hubEntryOption:  Option[KeyStoreEntry] = entries.find(e => e.privateKey.isEmpty && e.aliases.intersect(descriptor.caCertAliases).nonEmpty)
    hubEntryOption.fold[EitherCertError](Left(configureError( CouldNotFindCa))){hub =>
      val signingEntry: Option[KeyStoreEntry] = entries.find(e => e.privateKey.isDefined && e.wasSignedBy(hub))
      signingEntry.fold[EitherCertError](Left(configureError(CouldNotFindSigningCert))){ signing =>
        if (isHub) {
          Right(HubCertCollection(signing, hub))
        } else {
          Right(DownStreamCertCollection(signing, hub))
        }
      }
    }
  }

  def fromFileRecoverWithClassPath(descriptor: KeyStoreDescriptor): BouncyKeyStoreCollection = {
    val keyStore =
      if (new File(descriptor.file).exists)
        fromStreamHelper(descriptor, new FileInputStream(_))
      else
        fromStreamHelper(descriptor, getClass.getClassLoader.getResourceAsStream(_))

    BouncyKeyStoreCollection.keyStore = Some(keyStore)
    BouncyKeyStoreCollection.descriptor = Some(descriptor)

    createCertCollection(keyStore, descriptor)
      .fold(problem => throw problem.throwable.get, identity)
  }

  def fromStreamHelper(descriptor: KeyStoreDescriptor, streamFrom: String => InputStream): KeyStore = {
    debug(s"Loading keystore using descriptor: $descriptor")

    val stream = streamFrom(descriptor.file)

    require(stream != null,s"null stream for descriptor $descriptor¬")

    val keystore = KeyStore.getInstance(descriptor.keyStoreFormat.name)

    try {
      keystore.load(stream, descriptor.password.toCharArray)
    } catch {case x:IOException => throw new IOException(s"Unable to load keystore from $descriptor",x)}

    debug(s"Keystore aliases: ${keystore.aliases.asScala.mkString(",")}")

    debug(s"Keystore $descriptor loaded successfully")

    keystore
  }
}
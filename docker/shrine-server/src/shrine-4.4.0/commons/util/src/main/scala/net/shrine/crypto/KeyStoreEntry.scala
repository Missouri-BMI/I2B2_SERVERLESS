package net.shrine.crypto

import java.security.{PrivateKey, PublicKey, Signature}
import java.security.cert.X509Certificate
import java.util.Collection
import java.util.Date

import net.shrine.util.NonEmptySeq
import org.bouncycastle.asn1.x500.style.{BCStyle, IETFUtils}
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.cert.jcajce.{JcaX509CertificateConverter, JcaX509CertificateHolder}
import org.bouncycastle.cms.CMSSignedData
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentVerifierProviderBuilder
import org.bouncycastle.util.{Selector, Store}

import scala.jdk.CollectionConverters.IterableHasAsScala

/**
  * Created by ty on 10/26/16.
  * Represents a single entry in a key store collection. As a key entry may be either a PrivateKey Entry
  * or a TrustedCert Entry, there's no guarantee that there is a privateKey available
  *
  * @param cert: The x509 certificate in the entry
  * @param aliases: The alias of the certificate in the keystore
  * @param privateKey: The private key of the certificate, which is only available if this keystore represents
  *                    a private key entry (i.e., do we own this certificate?)
  */
final case class KeyStoreEntry(cert: X509Certificate,
                               aliases: NonEmptySeq[String],
                               privateKey: Option[PrivateKey]) {
  val publicKey: PublicKey = cert.getPublicKey
  val certificateHolder: X509CertificateHolder = new JcaX509CertificateHolder(cert) // Helpful methods are defined in the cert holder.
  val isSelfSigned: Boolean = certificateHolder.getSubject == certificateHolder.getIssuer // May or may not be a CA
  val provider: BouncyCastleProvider = BouncyKeyStoreCollection.provider
  val commonName: Option[String] =
    KeyStoreEntry.getCommonNameFromCert(certificateHolder)
  val SHA256: String = BouncyKeyStoreCollection.SHA256

//    certificateHolder.getSubject.getRDNs(BCStyle.CN).headOption.flatMap(rdn =>
//                                      Option(rdn.getFirst).map(cn => IETFUtils.valueToString(cn.getValue)))

  def verify(signedBytes: Array[Byte], originalMessage: Array[Byte]): Boolean = {
    val signer = Signature.getInstance(SHA256, provider)
    signer.initVerify(publicKey)
    signer.update(originalMessage)
    signer.verify(signedBytes)
  }

  /**
    * Provided that this is a PrivateKey Entry, sign the incoming bytes.
    * @return Returns None if this is not a PrivateKey Entry
    */
  def sign(bytesToSign: Array[Byte]): Option[Array[Byte]] = {

    privateKey.map(key => {
      val signature = Signature.getInstance(SHA256, provider)
      signature.initSign(key)
      signature.update(bytesToSign)
      signature.sign
    })
  }

  def wasSignedBy(entry: KeyStoreEntry): Boolean = wasSignedBy(entry.publicKey)

  def wasSignedBy(publicKey: PublicKey): Boolean =
    certificateHolder.isSignatureValid(
      new JcaContentVerifierProviderBuilder()
        .setProvider(provider)
        .build(publicKey)
    )

  def signed(cert: X509Certificate): Boolean =
    new JcaX509CertificateHolder(cert).isSignatureValid(
      new JcaContentVerifierProviderBuilder()
        .setProvider(provider)
        .build(publicKey)
    )

  def isExpired(time: Long = System.currentTimeMillis()): Boolean = {
    certificateHolder.getNotAfter.before(new Date(time))
  }
}

object KeyStoreEntry {
  def extractCertHolder(signedData:CMSSignedData): Option[X509CertificateHolder] = {
    val store: Store[X509CertificateHolder] = signedData.getCertificates
    val certCollection: Collection[X509CertificateHolder] = store.getMatches(SelectAll)
    certCollection.asScala.headOption
  }

  def getCommonNameFromCert(certHolder: X509CertificateHolder): Option[String] = {
    for {
      rdn <- certHolder.getSubject.getRDNs(BCStyle.CN).headOption
      cn <- Option(rdn.getFirst)
    } yield IETFUtils.valueToString(cn.getValue)
  }

  def extractCommonName(signedBytes: Array[Byte]): Option[String] = {
    val signedData = new CMSSignedData(signedBytes)
    for {
      certHolder <- extractCertHolder(signedData)
      commonName <- getCommonNameFromCert(certHolder)
    } yield commonName
  }

  def extractX509Cert(
      certificateHolder: X509CertificateHolder): X509Certificate = {
    new JcaX509CertificateConverter()
      .setProvider(BouncyKeyStoreCollection.provider)
      .getCertificate(certificateHolder)
  }

  def certSignedOtherCert(signer: X509CertificateHolder,
                          signee: X509CertificateHolder): Boolean = {
    signee.isSignatureValid(
      new JcaContentVerifierProviderBuilder()
        .setProvider(BouncyKeyStoreCollection.provider)
        .build(signer))
  }
}

object SelectAll extends Selector[X509CertificateHolder] {
  override def clone = throw new CloneNotSupportedException
  override def `match`(obj: X509CertificateHolder): Boolean = true
}
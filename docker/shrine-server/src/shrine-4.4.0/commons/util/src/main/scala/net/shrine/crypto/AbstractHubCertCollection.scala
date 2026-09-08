package net.shrine.crypto

import java.security.Signature
import java.security.cert.X509Certificate

import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.cert.jcajce.JcaCertStore
import org.bouncycastle.cms.{CMSException, CMSProcessableByteArray, CMSSignedData, CMSSignedDataGenerator}

import scala.jdk.CollectionConverters.SeqHasAsJava
import scala.util.Try

/**
 * Created by ty on 11/7/16.
 */
abstract class AbstractHubCertCollection(override val myEntry: KeyStoreEntry,
                                         caEntry: KeyStoreEntry)
  extends BouncyKeyStoreCollection {

  override val allEntries: Iterable[KeyStoreEntry] = myEntry +: caEntry +: Nil

  /**
   *
   * First, extract the X509Certificate from the signature.
   * Second, verify that the certificate was signed by our CA
   * Third, ensure that the attached X509Certificate actually signed the incoming
   * signature and data
   */
  override def verifyBytes(cmsEncodedSignature: Array[Byte],
                           originalMessage: Array[Byte]): Boolean = {
    val sigTry = Try(new CMSSignedData(cmsEncodedSignature))
    if (sigTry.isFailure)
      CryptoErrors.invalidSignatureFormat(cmsEncodedSignature)
    else {
      val sig =
        sigTry.get.getSignedContent.getContent.asInstanceOf[Array[Byte]]

      KeyStoreEntry
        .extractCertHolder(sigTry.get)
        .exists(incomingCert => {
          val signedByCa = KeyStoreEntry
            .certSignedOtherCert(caEntry.certificateHolder, incomingCert)
          val x509Cert = KeyStoreEntry.extractX509Cert(incomingCert)
          val signer =
            Signature.getInstance(BouncyKeyStoreCollection.SHA256, provider)

          signer.initVerify(x509Cert.getPublicKey)
          signer.update(originalMessage)
          val correctSignature = signer.verify(sig)

          signedByCa && correctSignature
        })
    }
  }

  def nonFatalToEither[A](f: => A): Either[Throwable, A] = {
    try {
      Right(f)
    } catch {
      case scala.util.control.NonFatal(t) => Left(t)
    }
  }

  def verifySignature(
                       cmsEncodedSignature: Array[Byte],
                       originalMessage: Array[Byte]
                     ):Either[Throwable,Unit] = {

    for {
      cmsSignedData <- asCmsSignedData(cmsEncodedSignature)
      incomingCertHolder <- nonFatalToEither(KeyStoreEntry.extractCertHolder(cmsSignedData).get)
      _ <- verifySignedByCa(incomingCertHolder)
      incomingX509 <- nonFatalToEither(KeyStoreEntry.extractX509Cert(incomingCertHolder))
      signer <- nonFatalToEither(Signature.getInstance(BouncyKeyStoreCollection.SHA256,provider))
      signedContent <- nonFatalToEither(cmsSignedData.getSignedContent.getContent.asInstanceOf[Array[Byte]])
      _ <- verifySignatureVsPublicKey(signer,signedContent,originalMessage,incomingX509)
    } yield ()
  }

  private def asCmsSignedData(cmsEncodedSignature: Array[Byte]): Either[Throwable, CMSSignedData] = nonFatalToEither {
    try {
      new CMSSignedData(cmsEncodedSignature)
    } catch {
      case cmsx: CMSException => throw InvalidSignatureFormatNotVerifiedException(cmsEncodedSignature, cmsx)
    }
  }

  private def verifySignedByCa(incomingCertHolder: X509CertificateHolder):Either[Throwable,Unit] = nonFatalToEither {
    if(!KeyStoreEntry.certSignedOtherCert(caEntry.certificateHolder,incomingCertHolder))
      throw CaDidNotSignSigningCertException(incomingCertHolder,caEntry.certificateHolder)
  }

  private def verifySignatureVsPublicKey(signer: Signature,signedContent:Array[Byte],originalMessage:Array[Byte],incomingX509:X509Certificate):Either[Throwable,Unit] = nonFatalToEither{
    signer.initVerify(incomingX509.getPublicKey)
    signer.update(originalMessage)
    try{incomingX509.checkValidity()} catch {case x: Throwable => throw CertificateRejectedException(incomingX509,x)}
    if(!signer.verify(signedContent)) throw SignatureRejectedException(incomingX509)
  }

  /**
   * First, sign the incoming bytes using the private key of our PrivateKeyEntry
   * Then, encode the signature into CMSSignedData, and encode our X509Certificate into the signature
   *
   * Note, it's perfectly safe to encode our X509Certificate (it's basically our public key)
   */
  override def signBytes(bytesToSign: Array[Byte]): Array[Byte] = {
    val data = new CMSProcessableByteArray(
      myEntry.sign(bytesToSign).getOrElse(CryptoErrors.noKeyError(myEntry)))
    val gen = new CMSSignedDataGenerator()

    gen.addCertificates(new JcaCertStore(Seq(myEntry.certificateHolder).asJava))
    gen.generate(data, true).getEncoded
  }
}

/**
 * Created by ty on 10/25/16.
 */
case class DownStreamCertCollection(override val myEntry: KeyStoreEntry,
                                    caEntry: KeyStoreEntry)
  extends AbstractHubCertCollection(myEntry, caEntry)

/**
 * Created by ty on 11/4/16.
 */
case class HubCertCollection(
                              override val myEntry: KeyStoreEntry,
                              caEntry: KeyStoreEntry
                            )
  extends AbstractHubCertCollection(myEntry, caEntry)

abstract class SignatureNotVerifiedException(message:String, caus:Option[Throwable] = None) extends
  Exception(message,caus.orNull)

case class InvalidSignatureFormatNotVerifiedException(
                                                       illegalBytes:Array[Byte],
                                                       cmsx:CMSException
                                                     ) extends SignatureNotVerifiedException(s"The message with signature bytes ${s"[${illegalBytes.mkString("")}]"} was not signed using CMSSignedData",Option(cmsx)) {
  fillInStackTrace()
}

case class CaDidNotSignSigningCertException(
                                             incomingCertHolder: X509CertificateHolder,
                                             caCertHolder:X509CertificateHolder
                                           ) extends SignatureNotVerifiedException(s"The signing cert ${incomingCertHolder.getSubject} was not signed by the CA ${caCertHolder.getSubject}.")

case class CertificateRejectedException(incomingX509:X509Certificate,x:Throwable) extends SignatureNotVerifiedException(s"${incomingX509.getSubjectX500Principal}'s certificate rejected",Option(x))

case class SignatureRejectedException(incomingX509:X509Certificate) extends SignatureNotVerifiedException(s"${incomingX509.getSubjectX500Principal}'s signature is not valid")

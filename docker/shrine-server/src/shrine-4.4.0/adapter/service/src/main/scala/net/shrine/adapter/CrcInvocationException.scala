package net.shrine.adapter

import net.shrine.protocol.i2b2.ShrineRequest

/**
 * @author clint
 * @since Mar 31, 2014
 */
final case class CrcInvocationException private (invokedUrl: String, request: ShrineRequest, rootCause: Throwable) extends AdapterException(rootCause) {
  override def getMessage = s"CrcInvocationException(invokedUrl='$invokedUrl', request=${request.toString.take(5000)}, rootCause=$rootCause)"
}

object CrcInvocationException {
  def unapply(e: CrcInvocationException): Option[(String, ShrineRequest, Throwable)] = {
    Some((e.invokedUrl, e.request, e.rootCause))
  }
}

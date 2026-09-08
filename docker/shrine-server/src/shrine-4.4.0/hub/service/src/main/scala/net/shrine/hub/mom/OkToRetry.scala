package net.shrine.hub.mom

import scala.util.control.NonFatal

object OkToRetry {
  //OK to retry on all but fatal
  def apply(t: Throwable): Boolean = t match {
    case NonFatal(_) => true
    /*
    Exceptions discovered during 2.0.0 development, stored for posterity

    case _:CouldNotCompleteMomTaskButOKToRetryException => true //something in the messaging subsystem went wrong, already cataloged.
    case _:CouldNotCompleteApiTaskButOKToRetryException => true
    case ux:UnknownHostException => ux.getMessage.contains("Temporary") //Found at UCLA with the message "Temporary failure in name resolution"
    case cx:ConnectException =>
      cx.getMessage.contains("handshake timed out") || //symptom when the hub is still starting
        cx.getMessage.contains("Connection refused") || //symptom when the hub has not started
        (cx.getCause match {
          case _:ConnectTimeoutException => true//symptom when the firewall hole is closed
          case _ => false
        })
    case _ => false
     */
  }
  def unapply(t: Throwable): Option[Throwable] = Some(t).filter(apply)
}
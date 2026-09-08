package net.shrine.messagequeueservice

import cats.effect.IO

/**
  * A Message Trait that is implemented by SimpleMessage and MessageQueueClientMessage
  * Created by yifan on 9/8/17.
  */

trait Message {

  /**
    * Call after the receiver has completed work on this message to prevent it being redelivered
    */
  def completeIO(): IO[Unit]

  def contents: String

  def deliveryAttemptId:DeliveryAttemptId

  def millisecondsToComplete:Long
}

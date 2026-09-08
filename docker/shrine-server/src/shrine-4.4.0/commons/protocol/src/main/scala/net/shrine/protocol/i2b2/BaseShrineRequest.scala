package net.shrine.protocol.i2b2

import scala.concurrent.duration.Duration


/**
 * @author clint
 * @since Feb 14, 2014
 */
trait BaseShrineRequest extends ShrineMessage {
  def authn: AuthenticationInfo
  def waitTime: Duration
  
  def requestType: RequestType
  //todo maybe add a request-originated-from optional field here?
}


package net.shrine.protocol.i2b2

import scala.concurrent.duration.Duration


/**
 * @author clint
 * @date Aug 16, 2012
 */
final case class RequestHeader(val projectId: String, val waitTime: Duration, val authn: AuthenticationInfo)
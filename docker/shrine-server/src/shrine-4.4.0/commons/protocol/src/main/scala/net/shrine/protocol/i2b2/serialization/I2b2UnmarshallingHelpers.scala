package net.shrine.protocol.i2b2.serialization

import net.shrine.protocol.i2b2.{AuthenticationInfo, RequestHeader}

import scala.xml.NodeSeq
import scala.util.Try

import scala.concurrent.duration.Duration

/**
 * @author clint
 * @since Oct 30, 2014
 */
trait I2b2UnmarshallingHelpers {
  final def i2b2Header(xml: NodeSeq): Try[RequestHeader] = {
    for {
      projectId <- i2b2ProjectId(xml)
      waitTime <- i2b2WaitTime(xml)
      authn <- i2b2AuthenticationInfo(xml)
    } yield {
      RequestHeader(projectId, waitTime, authn)
    }
  }
  
  import net.shrine.xml.NodeSeqEnrichments.Strictness._
  
  final def i2b2ProjectId(xml: NodeSeq): Try[String] = (xml withChild "message_header" withChild "project_id").map(_.text)

  final def i2b2WaitTime(xml: NodeSeq): Try[Duration] = {
    import scala.concurrent.duration.DurationLong
    
    (xml withChild "request_header" withChild "result_waittime_ms").map(_.text.toLong.milliseconds)
  }

  final def i2b2AuthenticationInfo(xml: NodeSeq): Try[AuthenticationInfo] = (xml withChild "message_header" withChild "security").flatMap(AuthenticationInfo.fromI2b2)

}
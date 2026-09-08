package net.shrine.hub.mom

import cats.data.OptionT
import cats.effect.IO
import net.shrine.config.ConfigSource
import net.shrine.hub.data.client.{HubHttpClient, HubServiceRequests}
import net.shrine.hub.data.store.HubDb
import net.shrine.protocol.version.v2.Node
import net.shrine.protocol.version.{JsonText, NodeKey, NodeName, UserDomainName}
import org.http4s.dsl.io.{Accepted, Conflict, NotFound, Ok}
import org.http4s.headers.Authorization
import org.http4s.{BasicCredentials, Headers, Method, ParseFailure, Request, Response, Uri}
import org.junit.{After, Before, Test}
import org.scalatest.Assertion
import org.scalatestplus.junit.AssertionsForJUnit

import scala.collection.immutable.Seq
import scala.util.Try

class HubClientTest extends AssertionsForJUnit {

  @Test
  def testBadConfig():Unit = {
    val badUriString = "bad uri"
    ConfigSource.configForBlock("shrine.shrineHubBaseUrl",badUriString,this.getClass.getSimpleName){
      try {
        val uri = HubHttpClient.hubServiceUri
        fail(s"No exception thrown for the bad URI from config: $uri")
      } catch {
        case thrown:ExceptionInInitializerError =>
          assertResult(classOf[ParseFailure])(thrown.getCause.getClass)
          assert(thrown.getCause.getMessage.contains(badUriString))
        case f:Throwable => fail("Unexpected exception",f)
      }
    }
  }
}

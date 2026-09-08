package net.shrine.utilities.http4sclienttest

import cats.effect.IO
import net.shrine.config.ConfigSource
import net.shrine.http4s.client.Http4sHttpClient
import org.http4s.{Method, ParseResult, Request, Status, Uri}

import scala.concurrent.duration.DurationInt
import scala.language.postfixOps

/**
 * Test tool to see if an https client can navigate proxies and firewalls to reach a shrine hub.
 *
 * We expect that an admin may need to set -Dhttp.proxyHost and -Dhttp.proxyPort to find a way through a proxy.
 *
 * @author dwalend
 * @since 1.25
 */

object Http4sClientTest {
  def main(args: Array[String]): Unit = {
    import cats.effect.unsafe.implicits.global
    try {
      if (args.length != 2) throw WrongNumberOfArguments("Requires two arguments: the http verb (PUT or GET) and the URL to call.")

      val verbString = args(0)
      val verb: ParseResult[Method] = Method.fromString(verbString)

      val urlString = args(1)
      val uri: ParseResult[Uri] = Uri.fromString(urlString)

      (verb,uri) match {
        case (Right(v),Right(u)) =>
          val requestResponseIo: IO[String] = testHttpRequest(v,u)
          requestResponseIo.unsafeRunTimed(30 seconds).fold{println("No response after 30 seconds")}{println(_)}
          System.exit(0)
        case sad => println(s"something went wrong with $sad") //todo do better - and look at error messages
      }
    } catch {
      case _: WrongNumberOfArguments =>
        printUsage()
        System.exit(1)
      case x:Throwable =>
        x.printStackTrace()
        System.exit(2)
    }
  }

  def printUsage(): Unit = {
    println(
      """Usage: ./http4sClientTest VERB URL
        |
        |Try the URL using the http VERB via the http4s client. This is primarily meant to test the http4s client in
        |environments with https client-side proxies, and to detect other potential barriers between a downstream node
        |and a hub in a SHRINE network.
        |
        |Exit codes: 0 success
        |            1 known error
        |            2 unknown error (with an accompanying stack trace).
        |
        |Examples:
        |./http4sClientTest GET https://shrine-dev-hub.catalyst.harvard.edu:6443/shrine-api/hub/ping
        |./http4sClientTest PUT https://shrine-dev-hub.catalyst.harvard.edu:6443/shrine-api/mom/createQueue/HarmlessTestQueue
      """.stripMargin)
  }

  def testHttpRequest(verb:Method,uri:Uri): IO[String] = {
    val request:Request[IO] = Request(
      method = verb,
      uri = uri
    )
    val http4sHttpClient = Http4sHttpClient(ConfigSource.config.getConfig("shrine.hub.client"))

    http4sHttpClient.webFetchAndDecodeIO(request){ (status: Status, bodyString: String) =>
      IO(s"Received $status $bodyString")
    }
  }

  case class WrongNumberOfArguments(message:String) extends Exception(message)
}

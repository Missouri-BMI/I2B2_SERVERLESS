package net.shrine.http4s.client.legacy

import cats.effect.IO
import cats.effect.std.Semaphore
import fs2.Chunk
import net.shrine.http4s.catsio.{ExecutionContexts, RetryIO}
import net.shrine.http4s.client.Http4sHttpClient
import net.shrine.log.Loggable
import net.shrine.config.{ConfigExtensions, ConfigSource}
import net.shrine.xml.{StringEnrichments, XmlUtil}
import org.http4s.EntityEncoder.simple
import org.http4s.headers.`Content-Type`
import org.http4s.{Charset, EntityEncoder, MediaType, Method, Request, Status, Uri}

import scala.concurrent.duration.FiniteDuration

/**
 * An Http4s-based API replacement for the old JerseyHttpClient, only used to communicate with i2b2. Do not use this in new code.
 */
final case class Http4sI2b2Client(endpointConfig: EndpointConfig)
  extends HttpClient with Loggable {

  private val http4sHttpClient: Http4sHttpClient = Http4sHttpClient(
    endpointConfig.clientConfig,
    Option(endpointConfig.timeout),
  )

  //todo prefer using postIO - but must wait until the CRC and PM code gets refactored
  override def post(input: String, url: String): HttpResponse = {
    import cats.effect.unsafe.implicits.global
    postIO(input,url).unsafeRunSync()
  }

  private val semaphore: Semaphore[IO] = {
    import cats.effect.unsafe.implicits.global
    Semaphore[IO](endpointConfig.concurrentLimit).unsafeRunSync()
  }
  private def postIO(body: String, url: String): IO[HttpResponse] = {

    def xmlStringEncoder[F[_]](implicit charset: Charset = Charset.`UTF-8`): EntityEncoder[F, String] = {
      val hdr = `Content-Type`(MediaType.text.xml).withCharset(charset)
      simple(hdr)(s => Chunk.array(s.getBytes(charset.nioCharset)))
    }

    val request: Request[IO] = Request(
      method = Method.POST, //always POST with i2b2
      uri = Uri.unsafeFromString(url)
    ).withEntity(body)(xmlStringEncoder)

    def prettyPrintIfXml(s: String): String = {
      import StringEnrichments._

      s.tryToXml.map(_.head).map(XmlUtil.prettyPrint).getOrElse(s)
    }

    def toHttpResponse(status:Status,contents:String):IO[HttpResponse] = IO (HttpResponse(status.code,contents))

    def safeLogResponse(httpResponse: HttpResponse):IO[Unit] = IO{
      if(httpResponse.statusCode < 400) {
        info(s"Got response from '$url' of '${prettyPrintIfXml(httpResponse.body)}'")
      } else {
        error(s"Got error code ${httpResponse.statusCode} from '$url' of '${httpResponse.body}'")
      }
    }

    val httpResponseIO = for {
      _ <- semaphore.acquire
      count <- semaphore.count
      _ <- IO(info(s"$count Invoking '$url' with ${prettyPrintIfXml(body).length} characters of '${prettyPrintIfXml(body)}'"))
      httpResponse <- http4sHttpClient.webFetchAndDecodeIO(request)(toHttpResponse).timeout(endpointConfig.timeout)
      _ <- safeLogResponse(httpResponse)
      _ <- semaphore.release
    } yield httpResponse

    def asyncHttpsClientVsNetty(throwable:Throwable):Boolean = throwable match {
      case _: IllegalArgumentException => true
      case _: Throwable => false
    }

    RetryIO.keepTryingBounded(task = httpResponseIO, delay = endpointConfig.retryDelay, maxRetries = 1,asyncHttpsClientVsNetty,s"post to $url")
  }
}
package net.shrine.http4s.client

import java.net.http.HttpClient
import java.security.cert.X509Certificate
import cats.effect.IO
import com.typesafe.config.Config

import javax.net.ssl.{SSLContext, TrustManager, X509TrustManager}
import net.shrine.log.Loggable
import org.http4s.client.Client
import org.http4s.jdkhttpclient.JdkHttpClient
import org.http4s.{EntityDecoder, Request, Response, Status}

import scala.concurrent.ExecutionContextExecutorService
import scala.concurrent.duration.FiniteDuration

/**
  * @author david
  * @since 1.25.3.1
  */
case class Http4sHttpClient(
                             trustManagers:TrustManagers,
                             maybeConnectTimeout:Option[FiniteDuration],
                             maybeExecutionContext: Option[ExecutionContextExecutorService],
                           ) extends Loggable {

  private val httpClient: Client[IO] = {
    JdkHttpClient[IO](createBuilder().build())
  }

  def webFetchAndDecodeIO[A](request:Request[IO])(toA: (Status,String) => IO[A]): IO[A] = {
    httpClient.run(request).use{ response: Response[IO] =>
      EntityDecoder.decodeText(response).flatMap(toA(response.status, _))
    }
  }

  private def createBuilder(): HttpClient.Builder = {
    //Shouldn't need to do anything special for proxies

    val builder: HttpClient.Builder = HttpClient.newBuilder()

    import scala.jdk.javaapi.DurationConverters.toJava
    maybeConnectTimeout.foreach(d => builder.connectTimeout(toJava(d)))

    val sslContext: SSLContext = SSLContext.getInstance("TLS")//"Default" will not work with "trust all certs"
    sslContext.init(null, trustManagers.trustManagerArray, null)

    builder.sslContext(sslContext)
    maybeExecutionContext.fold(builder)(builder.executor(_))
  }
}

object Http4sHttpClient extends Loggable {

  /**
   * @param clientConfig a config structure that has entries to configure the http client. (That's just tls{...} so far)
   * @return
   */
  def apply(
             clientConfig:Config,
             maybeConnectTimeout:Option[FiniteDuration] = None,
             maybeExecutionContext: Option[ExecutionContextExecutorService] = None,
           ):Http4sHttpClient = {
    Http4sHttpClient(TrustManagers.trustManagersForConfig(clientConfig),maybeConnectTimeout,maybeExecutionContext)
  }
}

sealed trait TrustManagers {

  def name: String = this.getClass.getSimpleName.dropRight(1)
  def trustManagerArray:Array[TrustManager]
}

object TrustManagers extends Loggable {

  private val configNamesToTrustManagers: Map[String, TrustManagers] = Seq(VerifyServerCerts,TrustAllCerts).map(tms => tms.name -> tms).toMap

  def trustManagersForConfig(clientConfig:Config): TrustManagers = {

    val tlsConfig = clientConfig.getConfig("tls")

    val trustManagers: TrustManagers = configNamesToTrustManagers(tlsConfig.getString("trustManager"))

    if(trustManagers == TrustAllCerts) {
      warn(
        s"""Using ${trustManagers.name} as the trustManager for ${tlsConfig.origin().description()} skips verifying the server's certificate in the TLS algorithm for https.
           |This makes https sessions vulnerable to man-in-the-middle attacks. Configure your systems to use ${VerifyServerCerts.name} instead.""".stripMargin)
    }

    trustManagers
  }

  /**
   * Verify the server's certificate in TLS vs the JVM's collection of top-level trusted certificates.
   *
   * Use this to prevent man-in-the-middle attacks.
   */
  private case object VerifyServerCerts extends TrustManagers {
    /**
     * @return null - Java's TLS API accepts null as a signal to use the JVM's default array of trust managers.
     */
    override def trustManagerArray: Array[TrustManager] = null
  }

  /**
   * Skip verification of the server's certificate in TLS. This permits man-in-the-middle attacks, and enables replay
   * attacks when using i2b2-based authentication.
   */
  private case object TrustAllCerts extends TrustManagers {
    override def trustManagerArray: Array[TrustManager] = Array(new X509TrustManager() {
      def getAcceptedIssuers: Array[X509Certificate] = null

      def checkClientTrusted(certs: Array[java.security.cert.X509Certificate], authType: String): Unit = {}

      def checkServerTrusted(certs: Array[java.security.cert.X509Certificate], authType: String): Unit = {}
    })
  }
}

// To Run the test, first start a simple server to count the number of lines received each time. I used
//
// ncat -l -k -p 8080 | awk '{print length}'
//
// Then load the following into the REPL and observe the results.
// mvn scala:console
// :load <path-to-file>

import cats.effect.{ContextShift, IO, Resource}
import io.netty.handler.ssl.{SslContext, SslContextBuilder}
import io.netty.handler.ssl.util.InsecureTrustManagerFactory
import org.asynchttpclient.{DefaultAsyncHttpClientConfig, Dsl, Realm}
import org.asynchttpclient.proxy.ProxyServer
import org.http4s.client.Client
import org.http4s.client.asynchttpclient.AsyncHttpClient
import org.http4s.{EntityDecoder, Method, Request, Response, Status, Uri}

import scala.concurrent.ExecutionContext.Implicits.global

val serverUri = Uri.unsafeFromString("https://localhost:8080")

val httpClientBuilder: DefaultAsyncHttpClientConfig.Builder = {

  //Build up proxy parameters from standard locations
  val proxyHost = Option(System.getProperty("http.proxyHost"))
  val proxyPort = Option(System.getProperty("http.proxyPort")).map(_.toInt)
  val proxyUser = Option(System.getProperty("http.proxyUser"))
  val proxyPassword = Option(System.getProperty("http.proxyPassword"))

  val proxyParameters = (proxyHost,proxyPort,proxyUser,proxyPassword) match {
    case (None, None, None, None) => None
    case (Some(host), Some(port), None, None) => Some((host, port, None))
    case (Some(host), Some(port), Some(user), Some(password)) => Some((host, port, Some((user, password))))
    case _ => throw new IllegalArgumentException(
      s"""If either of http.proxyHost and http.proxyPort are set then both must be set (host: $proxyHost,port: $proxyPort).
         |If either of http.proxyUser and http.proxyPassword are set then both must be set (user: $proxyUser,password provided : ${proxyPassword.isDefined}).""".stripMargin)
  }

  //todo use a more particular trust manager
  val sslContext: SslContext = SslContextBuilder.forClient().trustManager(InsecureTrustManagerFactory.INSTANCE).build()
  val sslConfigBuilder: DefaultAsyncHttpClientConfig.Builder = Dsl.config().setSslContext(sslContext)

  val proxyConfigBuilder: DefaultAsyncHttpClientConfig.Builder = proxyParameters.fold {
    sslConfigBuilder
  } { parameters =>
    val host = parameters._1
    val port = parameters._2
    val proxyServer: ProxyServer = parameters._3.fold {
      new ProxyServer.Builder(host, port).build()
    } { credentials =>
      val realm = new Realm.Builder(credentials._1, credentials._2)
      new ProxyServer.Builder(host, port).setRealm(realm).build()
    }
    sslConfigBuilder.setProxyServer(proxyServer)
  }
  proxyConfigBuilder
}

val httpClientResource: Resource[IO, Client[IO]] = {
  implicit val concurrentEffect: ContextShift[IO] = IO.contextShift(global)
  AsyncHttpClient.resource[IO](httpClientBuilder.build)
}

def webFetchAndDecodeIO[A](request:Request[IO])(toA: (Status,String) => IO[A]): IO[A] = {
  httpClientResource.use { client: Client[IO] =>
    client.fetch(request) { response: Response[IO] =>
      EntityDecoder.decodeString(response).flatMap(toA(response.status, _))
      //todo figure out how to use EntityDecoder[IO, String].decode() SHRINE-2943
    }
  }
}

def createRequest(contents:String): Request[IO] = Request(
  method = Method.PUT,
  uri = serverUri
).withEntity(contents)




val iters = 14
val contents = (1 to iters).map{ i =>
  val number = scala.math.pow(2,i).toInt -1 //-1 for the end-of-line to make awk play nice
  s"$i $number ${"*".*(number)}\n"
}

val statuses = contents.map{content =>
  webFetchAndDecodeIO(createRequest(content)){(status,string) => IO(status)}.unsafeRunAsyncAndForget()}

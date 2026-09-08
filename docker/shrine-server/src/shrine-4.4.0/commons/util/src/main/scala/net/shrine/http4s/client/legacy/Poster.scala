package net.shrine.http4s.client.legacy

/**
 * @author clint
 * @since Dec 18, 2013
 */

//todo add an apply(Config based on EndpointConfig)
final case class Poster(url: String, httpClient: HttpClient) {
  def post(data: String): HttpResponse = httpClient.post(data, url)
}

object Poster {
  //todo a version based on config
  def apply(endpoint: EndpointConfig):Poster = {

    val httpClient = Http4sI2b2Client(endpoint)

    Poster(endpoint.url.toString, httpClient)
  }
}

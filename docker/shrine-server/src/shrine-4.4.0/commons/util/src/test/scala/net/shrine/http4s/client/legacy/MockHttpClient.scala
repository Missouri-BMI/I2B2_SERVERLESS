package net.shrine.http4s.client.legacy

/**
 * @author clint
 * @date Jan 27, 2014
 */
class MockHttpClient extends HttpClient {
  var lastInput: Option[String] = None
  var lastUrl: Option[String] = None
    
  override def post(input: String, url: String): HttpResponse = {
    lastInput = Some(input)
    lastUrl = Some(url)
      
    HttpResponse.ok(input)
  }
}
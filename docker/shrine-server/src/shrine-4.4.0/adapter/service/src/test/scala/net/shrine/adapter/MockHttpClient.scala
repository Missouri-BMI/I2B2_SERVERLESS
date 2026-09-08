package net.shrine.adapter

import net.shrine.http4s.client.legacy.{HttpClient, HttpResponse}

/**
 * @author clint
 * @date Sep 20, 2012
 */
object MockHttpClient extends HttpClient {
  override def post(input: String, url: String): HttpResponse = HttpResponse.ok("")
    
  def apply(f: => String): HttpClient = new HttpClient {
    override def post(input: String, url: String): HttpResponse = HttpResponse.ok(f)
  }    
}
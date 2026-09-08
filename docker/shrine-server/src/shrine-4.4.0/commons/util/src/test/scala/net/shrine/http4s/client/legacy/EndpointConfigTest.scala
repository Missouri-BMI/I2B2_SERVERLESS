package net.shrine.http4s.client.legacy

import java.net.URL
import com.typesafe.config.ConfigFactory
import org.junit.jupiter.api.Assertions.{assertEquals, assertThrows}
import org.junit.jupiter.api.Test

import scala.concurrent.duration.DurationInt

/**
 * @author clint
 * @since Dec 5, 2013
 */
final class EndpointConfigTest {
  @Test
  def testApply():Unit = {
    def url(s: String) = new URL(s)
    
    assertThrows(classOf[Exception], () =>
      EndpointConfig(ConfigFactory.empty)
    )

    {
      val configText = """
      foo {
        bar = 123
        baz = 456
        fooEndpoint {
          urlPath = "/path/to/getServices"
          timeout = "123 days"
          concurrentLimit = "12"
          retryDelay = "250 milliseconds"
          tls.trustManager = "VerifyServerCerts"
        }
      }"""

      val endpoint = EndpointConfig(ConfigFactory.parseString(configText).getConfig("foo.fooEndpoint"))

      assertEquals( url("http://i2b2.example.com:9090/path/to/getServices"), endpoint.url)
      assertEquals(123.days, endpoint.timeout)
    }
  }
}
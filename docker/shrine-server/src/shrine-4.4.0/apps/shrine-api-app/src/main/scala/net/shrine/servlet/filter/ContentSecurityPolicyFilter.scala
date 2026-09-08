package net.shrine.servlet.filter

import javax.servlet._
import javax.servlet.annotation.WebFilter
import javax.servlet.http.HttpServletResponse
import java.io.IOException

class ContentSecurityPolicyFilter extends Filter {
  override def destroy(): Unit = {
  }

  @throws[IOException]
  @throws[ServletException]
  override def doFilter(request: ServletRequest, response: ServletResponse, chain: FilterChain): Unit = {

    val httpResponse: HttpServletResponse = response match {
      case r: HttpServletResponse => r
      case x => throw UnexpectedServletResponseType(s"Expected HttpServletResponse but got ${x.getClass}")
    }

    httpResponse.setHeader("Content-Security-Policy", """
                                                        |default-src 'self';
                                                        |connect-src 'self';
                                                        |style-src 'self' 'unsafe-inline';
                                                        |script-src 'self' 'unsafe-inline' 'unsafe-eval' ;
                                                        |img-src 'self' data: blob: ;
                                                        |report-uri /shrine-api/csp ;
                                                        |font-src 'self' data:;
                                                        |object-src 'self';
                                                        |media-src 'self';
                                                        |frame-src 'self';
                                                        |child-src 'self';
                                                        |form-action 'self' https://*/shrine-api/shrine-webclient*;
                                                        |""".stripMargin)

    httpResponse.setHeader("X-Frame-Options", "SAMEORIGIN")
    httpResponse.setHeader("Strict-Transport-Security",
      "max-age=31536000; " +
        "includeSubDomains")

    chain.doFilter(request, httpResponse)
    }

}

case class UnexpectedServletResponseType(message: String) extends Exception(message)


package net.shrine.servlet.filter

import net.shrine.authentication.AuthenticationType
import net.shrine.authz.AuthorizationCoordinator
import net.shrine.config.ConfigSource

import java.io.IOException
import javax.servlet.{Filter, FilterChain, ServletException, ServletRequest, ServletResponse}
import javax.servlet.annotation.WebFilter
import javax.servlet.http.{HttpServletRequest, HttpServletResponse}

/**
 * This web filter (same as those in web.xml) has its doFilter method called at every
 * HTTP request (unless an upstream filter stops the chaining of filters.)
 * The purpose of this filter is to enforce access authorization based on the
 * user's user ID as returned from SSO (Shibboleth in the current implementation)
 * any number of configured "attribute providers", and a configured ahtorizer.
 *
 * When the system is not configured to use SSO at all, then this filter does nothing
 * and calls the next filter in the chain.
 *
 * When the system is configured for SSO but no authorization is required, then
 * similarly the class does noting and calls the next filter in the chain.
 *
 * If the system is configured to require authorization, then this class, via the
 * doFilter() method, delegates to do AuthorizationCoordinator class the application of
 * the authorization criteria as per the configuration. It also sends the user to
 * an "unauthorized" page if authorization fails.
 *
 * Some URLs are not filtered because they must be accessible regardless of authorization,
 * and they are identified by the freePassUrl() method. For instance these are URLs to
 * resources used to display the authorization failure page.
 *
 */
// re-write the scala way: SHRINE2020-1312
class AuthorizationFilter extends Filter {
  override def destroy(): Unit = {
  }

  @throws[IOException]
  @throws[ServletException]
  override def doFilter(request: ServletRequest, response: ServletResponse, chain: FilterChain): Unit = {

    val httpResponse: HttpServletResponse = response match {
      case r: HttpServletResponse => r
      case x => throw new IllegalArgumentException(s"Expected HttpServletResponse but got ${x.getClass}")
    }
    val httpRequest: HttpServletRequest = request match {
      case r: HttpServletRequest => r
      case x => throw new IllegalArgumentException(s"Expected HttpServletRequest but got ${x.getClass}")
    }

    val authzRequired = ConfigSource.config.getString(
        "shrine.qep.authenticationType") == AuthenticationType.Sso.name &&
        AuthorizationCoordinator.isAuthorizationRequired()

    // Look for the _single_ case where the request and/or its result is such that the filter chain should be stopped
    // (and the user sent to the un-authorized page)
    val uid = Option(httpRequest.getHeader(AuthorizationCoordinator.REMOTE_USER_STRING)).map(_.toUpperCase()).getOrElse("")
    if (authzRequired && uid.nonEmpty) {
      val incomingUri = httpRequest.getRequestURI()
      val incomingQueryString = httpRequest.getQueryString
      val userInfoKey = AuthorizationCoordinator.userInfoKey
      val logoutUri = "/shrine-api/authorizer/logout"
      val session = httpRequest.getSession();
      val userInfo = session.getAttribute(userInfoKey)
      val unauthUri: String = ConfigSource.config.getString("shrine.config.authorizer.unauthorizedUrl")
      val newUserInfo = AuthorizationCoordinator.userAuthorizationInfo(uid, httpRequest)

      // part of : "SHRINE2020-1312 Re-write AuthorizationFilter.scala the Scala way"
      // Simplify this logic -> rewrite using Options / try-success-failure
      // (including re-shaping the if/else statements)
      if (
        // could be done with an exception instead of a null check
        (incomingUri != logoutUri)
          && (userInfo == null && !freePassUrl(incomingUri, incomingQueryString))
          && (newUserInfo == null)) {
        httpResponse.sendRedirect(unauthUri)
        // do not call chain.doFilter(request, httpResponse)
      }
      else {
        if (incomingUri == logoutUri) {
          // Invalidate the user's session
          session.setAttribute(userInfoKey, null)
          session.invalidate()
        }
        else if (userInfo == null && !freePassUrl(incomingUri, incomingQueryString)) {
          // yup, we need to authorize
          // Right now, AuthorizationCoordinator.userAuthorizationInfo(uid, httpRequest)
          // returns null for authentication failure, and a Map with user attributes
          // for success.

          // In the future, userAuthorizationInfo() could be so that it never returns
          // null -- instead, the boolean value representing authorization success or
          // failure would be added to the data structure returned by userAuthorizationInfo().
          if (newUserInfo != null) {
            httpRequest.getSession().setAttribute(userInfoKey, newUserInfo)
          }
          else {
            // do nothing, this case is handled by the very topmost "if" statement
          }
        }
        chain.doFilter(request, httpResponse)
      }
    }
    else {
      chain.doFilter(request, httpResponse)
    }

  }


  def freePassUrl(uri: String, queryString: String): Boolean = {

    val webclientNoSlash = "/shrine-api/shrine-webclient"
    val staticData = "/shrine-api/staticData"

    // for equality check, see https://www.oreilly.com/library/view/scala-cookbook/9781449340292/ch01s02.html
    (
      ( Seq (webclientNoSlash, s"${webclientNoSlash}/").contains(uri) &&
        queryString == "isAuth=false")
      ||
      Seq(    s"${webclientNoSlash}/shrine.bundle.js",
              s"${staticData}/webclient/logo",
              s"${staticData}/webClientConfig")
        .contains(uri)
    )
  }
}


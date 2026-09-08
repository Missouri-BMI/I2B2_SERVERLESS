package net.shrine.authz

import cats.effect.IO
import net.shrine.authz.providerService.attributes.AttrProviderTrait
import net.shrine.authz.providerService.authorize.AuthorizerTrait
import net.shrine.config.ConfigSource
import net.shrine.log.Loggable
import org.http4s.{Header, Request}

import javax.servlet.http.HttpServletRequest
import scala.collection.mutable
import scala.jdk.CollectionConverters._


/**
 * This class follows the authorization steps in the shrine.conf file: instantiates the
 * needed attribute providers and authorizer classes, saves the resulting collected attributes
 * in an HttpSession attribute named "userInfo", and returns a Boolean corresponding to the
 * determination of authorization made by the authorizer class.
 */
// SHRINE2020-1315 : Re-write AuthorizationCoordinator using immutable maps
// SHRINE2020-1329 : revisit the whole architecture of attribute providers and authorizers
// SHRINE2020-1321 : Fix imports in AuthorizationCoordinator.scala
object AuthorizationCoordinator extends  Loggable {

  // The following is the name of the header containing the SSO-logged-in username, as provided by Shibboleth
  val REMOTE_USER_STRING:String = "REMOTE_USER"
  val userInfoKey = "userInfo"

  def isAuthorizationRequired(): Boolean = {
    ConfigSource.config.getBoolean("shrine.config.authorizer.requireAuthorization")
  }

  def userAuthorizationInfo(uid: String, httpRequest: HttpServletRequest): mutable.Map[String, mutable.Map[String, Seq[String]]] = {

    val headers = getHeadersFromHttpRequest(httpRequest)

    // Headers are passed -- in case they are needed by one of the attribute providers --
    val userInfo = getAuthorizationInfo(uid, headers)
    val result = isUserAuthorizedGivenInfo(userInfo)
    if (result) {
      userInfo
      // Future proofing: add an entry to userInfo to indicates whether
      // the user is authorized. Currently we rely on whether the userInfo
      // attribute exists or not in the HttpSession in order to tell whether the user
      // is authorized. In the future we might want userInfo to be in the HttpSession
      // even if the user is not authorized. In that case we would rely on
      // that new entry in userInfo for authz status
    }
    else {
      warn(s"Authorization failed for username '$uid'")
      // Where does that null pointer exception burst out of the code no
      // longer an option b/c of the way we recoded the class
      // could be done with an exception instead of a null check
      // Rework AuthorizationCoordinator.userAuthorizationInfo()
      // Ticket for reworking everything: SHRINE2020-1332
      null
    }
  }

  /**
   * In conjunction with AttrProviderTrait, will generate and return a "userInfo" map of this shape:
   *
   *   {
   *     attribute type 1 -> {
   *       attribute 1 -> [value 1, value 2, ...],
   *       attribute 2 -> [value 1, value 2, ...],
   *       ...
   *     },
   *     attribute type 2 ->
   *       attribute 1 -> [value 1, value 2, ...],
   *       attribute 2 -> [value 1, value 2, ...],
   *       ...
   *     },
   *    ...
   *   }
   *
   * @param uid
   * @param headers
   * @return
   */
  def getAuthorizationInfo(uid: String, headers: List[(String, String)]): mutable.Map[String, mutable.Map[String, Seq[String]]] = {
    // Initialize payload
    var globalUserInfo: mutable.Map[String, mutable.Map[String, Seq[String]]] = mutable.Map()

    // Extract the attribute provider names
    val attrProviderConfigs = ConfigSource.config.getConfigList("shrine.config.authorizer.attributeProviders")

    for (attrProviderConfig <- attrProviderConfigs.asScala) {

      // for each attribute provider name and class-name in the config, extract
      // the attributes via that class, and store the results in the global user
      // attributes map, under the provider name
      val providerClass = Class.forName(attrProviderConfig.getString("class"))
      val providerInstance = providerClass.getConstructor().newInstance().asInstanceOf[AttrProviderTrait]

      globalUserInfo += providerInstance.populateAttributes(uid, headers, attrProviderConfig)

    }
    globalUserInfo
  }
  def getHeadersFromHttpRequest(httpRequest: HttpServletRequest) = {
    httpRequest.getHeaderNames.asScala.map(name => {
      // SHRINE2020-1319 Fix upper/lowercase issue in AuthorizationCoordinator
      (name.toUpperCase(), httpRequest.getHeader(name).toString().toUpperCase())
    }).toList
  }

  //todo maybe never used - could be deleted
  def getHeadersFromRequestIo(request: Request[IO]): Seq[(String, String)] = {
    request.headers.headers
      .map((h: Header.Raw) => {
        (h.name.toString.toUpperCase(), h.value)
      })
  }

  /**
   * Takes as input the user info map created by a set of attribute providers (and passed around by
   * this class's userAuthorizationInfo() method ), i.e.:
   *
   * {
   *   attribute type 1 -> {
   *     attribute 1 -> [value 1, value 2, ...],
   *     attribute 2 -> [value 1, value 2, ...],
   *     ...
   *   },
   *   attribute type 2 -> {
   *     attribute 1 -> [value 1, value 2, ...],
   *     attribute 2 -> [value 1, value 2, ...],
   *     ...
   *   },
   *   ...
   * }
   *
   * */
  def isUserAuthorizedGivenInfo(userInfo: mutable.Map[String, mutable.Map[String, Seq[String]]]) : Boolean = {
    val authorizerConf = ConfigSource.config.getConfig("shrine.config.authorizer.authorizer")

    val authorizerClass = Class.forName(authorizerConf.getString("name"))
    val authorizerInstance = authorizerClass.getConstructor().newInstance().asInstanceOf[AuthorizerTrait]

    authorizerInstance.isAuthorized(userInfo)
  }
}

package net.shrine.authz.providerService.authorize

import scala.collection.mutable

// authorize user simply based on being white-listed (while not at the same time being black-listed)
// SHRINE2020-1318 : Refactor HmsAuthorizer and BWAuthorizer
// SHRINE2020-1329 : revisit the whole architecture of attribute providers and authorizers
class BWAuthorizer extends AuthorizerTrait with BWAuthorizerTrait {
  def isAuthorized(collectedAttributeMaps: mutable.Map[String, mutable.Map[String, Seq[String]]]): Boolean = {

    val isBlack = isListedAs(collectedAttributeMaps, "isBlack")
    val isWhite = isListedAs(collectedAttributeMaps, "isWhite")

    // you must be white-listed (and not also black-listed)
    !isBlack && isWhite
  }
}

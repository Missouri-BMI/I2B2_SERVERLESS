package net.shrine.authz.providerService.authorize

import net.shrine.config.ConfigSource

import scala.collection.mutable

// use HMS criteria applied to collected userInfo in order to determine whether user is authorized
// SHRINE2020-1318 : Refactor HmsAuthorizer and BWAuthorizer
class HmsAuthorizer extends AuthorizerTrait with BWAuthorizerTrait {

  def isAuthorized(collectedAttributeMaps: mutable.Map[String, mutable.Map[String, Seq[String]]]): Boolean = {

    val isBlack = isListedAs(collectedAttributeMaps, "isBlack")
    val isWhite = isListedAs(collectedAttributeMaps, "isWhite")

    val facultyType = collectedAttributeMaps("profiles_faculty_type_and_id")("faculty_type")
    val hasGoodRank = facultyType.nonEmpty && facultyType(0).matches("[0-4]")

    ( ! isBlack && (isWhite || hasGoodRank))

  }

}

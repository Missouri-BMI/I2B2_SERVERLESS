package net.shrine.authz.providerService.authorize

import scala.collection.mutable

trait BWAuthorizerTrait {

  def isListedAs(collectedAttributeMaps: mutable.Map[String, mutable.Map[String, Seq[String]]], color: String): Boolean = {
    collectedAttributeMaps("wb-list")(color).exists(_.contains("true"))
  }

}

package net.shrine.authz.providerService.authorize

import scala.collection.mutable

trait AuthorizerTrait {

  // in scala, the 'goto' sequence is Seq vs List. See, eg,
  //   https://stackoverflow.com/questions/10866639/difference-between-a-seq-and-a-list-in-scala
  @throws[Exception]
  def isAuthorized(attributes: mutable.Map[String, mutable.Map[String, Seq[String]]]): Boolean

}

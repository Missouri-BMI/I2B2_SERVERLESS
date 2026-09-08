package net.shrine.authz.providerService.examples

import net.shrine.authz.providerService.authorize.AuthorizerTrait
import net.shrine.config.ConfigSource

import scala.collection.mutable
import scala.jdk.CollectionConverters.CollectionHasAsScala

/**
 * Makes a string out of the collected attributes passed to isAuthorized();
 * applies each of the regexes to that string. If the string matches all regexes
 * then isAuthorized returns true. Note that regexes that start with a "!"
 * have a special meaning here: the string should NOT match the regex
 * (without the !).
  */
class RegexAuthorizer extends AuthorizerTrait {

  def isAuthorized(collectedAttributeMaps: mutable.Map[String, mutable.Map[String, Seq[String]]]): Boolean = {

    val config = ConfigSource.config.getConfig("shrine.config.authorizer.authorizer")
    val regexTerms: Seq[String] = config.getStringList("regexTerms")
      .asScala
      .toSeq

    val parsyUserInfo = collectedInfoMapsToString(collectedAttributeMaps)

    // does each term match? except of course, the ones beginning '!' should NOT match
    regexTerms.forall(t => {
      val wantMatch = ( ! t.matches("^[!].*"))

      val innerT = t.replaceAll("^!", "")
      val wrappedT = s"(?i).*($innerT).*" // (?i) for case-insensitive

      val tRegex = wrappedT.r
      val doesInnerTermMatch = tRegex.matches(parsyUserInfo)

      doesInnerTermMatch == wantMatch
    })
  }

  def innerInfoMapToString(inner: mutable.Map[String, Seq[String]], prefix:String) : String = {
    inner.toSeq.sortBy(_._1).map(kvPair => {
      kvPair._2.toSeq.map(oneVal => s"<<$prefix.${kvPair._1}.${oneVal}>>").mkString("")
    }).mkString("")
  }
  def collectedInfoMapsToString(outer: mutable.Map[String, mutable.Map[String, Seq[String]]]) : String = {
    outer.toSeq.sortBy(_._1).map(kvPair => {
      s"${innerInfoMapToString(kvPair._2, kvPair._1)}"
    }).mkString("")
  }

}

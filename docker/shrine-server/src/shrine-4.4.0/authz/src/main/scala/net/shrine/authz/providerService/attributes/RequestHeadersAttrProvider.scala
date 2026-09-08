package net.shrine.authz.providerService.attributes

import com.typesafe.config.Config

import scala.collection.mutable

/**
* This class implements AttrProviderTrait so its output can be used
* by an authorizer. The attribute values will be the values of the HTTP
* headers named in the configuration.
*/
// SHRINE2020-1327 Rewrite EndPointAttrProvider, etc. using val's and IO's
class RequestHeadersAttrProvider extends AttrProviderTrait {

  import scala.jdk.CollectionConverters._

  def populateAttributes(userId: String,
                         headers: List[(String, String)],
                         config:Config
             ):(String, mutable.Map[String, Seq[String]]) = {


    val attributeNames: mutable.Buffer[String] = config.getStringList("headerNames") // from config
      .asScala
      .map(h => h.toUpperCase()) // canonical uppercase
    val userInfo: mutable.Map[String, Seq[String]] = mutable.Map[String, Seq[String]]()

    headers.toList
      .filter(h => attributeNames.contains(h._1))
      .foreach(h => {userInfo(h._1) = Seq(h._2)})

    (config.getString("name"), userInfo)
  }

}

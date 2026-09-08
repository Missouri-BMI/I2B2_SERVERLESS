package net.shrine.authz.providerService.attributes

import com.typesafe.config.Config

import scala.collection.mutable

/**
 * An AttrProviderTrait provides one abstract method populateAttributes()
 * which must be implemented to provide a map of attribute values based on the
 * system configuration. These attribute values are meant
 * to be used by an implementer of AuthorizerTrait to determine if a user is authorized
 * into the system (though the use could be anything, not necessarily authorization.
 * For instance it could be used for the simple purpose of providing the system
 * with some useful information about the user.)
 */

// SHRINE2020-1316: use immutable maps in AttrProviderTrait.scala
trait AttrProviderTrait {

  /**
   * An implementer of AttrProviderTrait must implement a method named
   * populateAttributes() which, based on the userId, and optionally on the headers,
   * will return the following data structure for a given configuration
   * of the provider:
   *
   * ( attribute type -> {
   *                        attribute 1 -> [value 1, value 2, ...],
   *                        attribute 2 -> [value 1, value 2, ...],
   *                        ...
   *                     } )
   *
   * The HTTP headers are passed along with the userId because they are needed
   * by some implementations of populateAttributes(). It's cleaner than passing
   * the HTTP request to this method
   *
   * @param userId
   * @param headers
   * @param config
   * @return
   */
  def populateAttributes(userId: String,
                         headers: List[(String, String)],
                         config:Config
                        ): (String, mutable.Map[String, Seq[String]])

}

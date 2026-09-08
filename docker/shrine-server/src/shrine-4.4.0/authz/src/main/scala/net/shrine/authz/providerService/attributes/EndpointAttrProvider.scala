package net.shrine.authz.providerService.attributes

import cats.effect.IO
import com.typesafe.config.Config
import net.shrine.config.ConfigSource
import net.shrine.http4s.client.Http4sHttpClient
import net.shrine.log.Log
import org.http4s.{Method, Request, Status, Uri}

import scala.collection.mutable

/**
 * This class implements AttrProviderTrait so its output can be used
 * by an authorizer. As per the configuration, this class makes a
 * REST call to a 3rd-party REST end-point, and use Regexes from the configuration
 * to extract attribute values (one or more) from the returned payload
 */
// SHRINE2020-1327 Rewrite EndPointAttrProvider, etc. using val's and IO's
class EndpointAttrProvider extends AttrProviderTrait {

    private lazy val http4sHttpClient: Http4sHttpClient = Http4sHttpClient.apply(ConfigSource.config.getConfig(
      "shrine.config.authorizer.endpointAttrProvider"
    ))

    lazy val configList: java.util.List[_ <: Config] = ConfigSource.config.getConfigList("shrine.config.authorizer.endpointAttrProviderConfigs")

    def populateAttributes(userId: String,
                           headers: List[(String, String)],
                           config: Config
                      ): (String, mutable.Map[String, Seq[String]]) = {

      val userInfo = mutable.Map[String, Seq[String]]()

      val urlTemplate = config.getString("url")
      val uidPlaceholder = config.getString("userIdPlaceHolder")
      val uriString: String = urlTemplate.replace(uidPlaceholder, userId)
      val endpointStringOutput = hitUrl(uriString)

      import scala.jdk.CollectionConverters._

      val regexConfigs = config.getConfigList("attributeRegexes").asScala

      for (regexConf <- regexConfigs) {
        val regexName = regexConf.getString("name");
        val regexValue = regexConf.getString("regex");
        val regex = regexValue.r
        val values = regex.findAllMatchIn(endpointStringOutput).map(m=>m.subgroups(0)).toSeq
        userInfo += (regexName -> values)
      }

      (config.getString("name"), userInfo)

    }

    def hitUrl(uriString: String): String = {
        val uri = Uri.unsafeFromString(uriString)

        val endpointRequest: Request[IO] = Request(method = Method.GET).withUri(uri)

        val fetchIO = http4sHttpClient.webFetchAndDecodeIO(endpointRequest) { (status: Status, bodyString: String) =>
            if (status.isSuccess) {
                IO(bodyString)
            }
            else {
                val complaint = s"Request: $endpointRequest failed with status: $status"
                Log.error(complaint)
                throw new RuntimeException(s"Unable to get $uriString in EndpointAttrProvider, HTTP status is $status")
            }
        }
      // despite its discouraging name, unsafeRunSync() seems the way
      //    to 'ultimately' / synchronously harvest the IO
      import cats.effect.unsafe.implicits.global
      val stringResult = fetchIO.unsafeRunSync()
      stringResult
    }


}

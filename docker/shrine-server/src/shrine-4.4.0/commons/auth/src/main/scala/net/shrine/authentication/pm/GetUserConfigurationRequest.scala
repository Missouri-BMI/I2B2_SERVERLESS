package net.shrine.authentication.pm

import net.shrine.protocol.i2b2.AuthenticationInfo
import net.shrine.protocol.i2b2.serialization.I2b2Marshaller

import javax.xml.datatype.XMLGregorianCalendar
import net.shrine.xml.{XmlDateHelper, XmlUtil}

import scala.xml.Node

/**
 * @author Bill Simons
 * @since 3/2/12
 * @see http://cbmi.med.harvard.edu
 * @see http://chip.org
 *       <p/>
 *       NOTICE: This software comes with NO guarantees whatsoever and is
 *       licensed as Lgpl Open Source
 * @see http://www.gnu.org/licenses/lgpl.html
 */
//NB: timestamp field allows for deterministic testing; optional for minimal verbosity and to require less refactoring
final case class GetUserConfigurationRequest(authn: AuthenticationInfo, timestamp: Option[XMLGregorianCalendar] = None) extends I2b2Marshaller {

  override def toI2b2: Node = XmlUtil.stripWhitespace {
    <ns2:request xmlns:ns2="http://www.i2b2.org/xsd/hive/msg/1.1/" xmlns:ns5="http://www.i2b2.org/xsd/cell/pm/1.1/">
      <message_header>
        <i2b2_version_compatible>1.1</i2b2_version_compatible>
        <hl7_version_compatible>2.4</hl7_version_compatible>
        <sending_application>
          <application_name>SHRINE</application_name>
          <application_version>1.3-compatible</application_version>
        </sending_application>
        <sending_facility>
          <facility_name>SHRINE</facility_name>
        </sending_facility>
        <datetime_of_message>{ timestamp.getOrElse(XmlDateHelper.now) }</datetime_of_message>
        { authn.toI2b2 }
        <project_id></project_id>
      </message_header>
      <request_header>
        <result_waittime_ms>0</result_waittime_ms>
      </request_header>
      <message_body>
        <ns5:get_user_configuration>
          <project>undefined</project>
        </ns5:get_user_configuration>
      </message_body>
    </ns2:request>
  }
}
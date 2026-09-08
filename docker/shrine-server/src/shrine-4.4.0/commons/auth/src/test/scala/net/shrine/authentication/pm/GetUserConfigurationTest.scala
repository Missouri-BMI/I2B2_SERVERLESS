package net.shrine.authentication.pm

import net.shrine.util.ShouldMatchersForJUnit
import org.junit.Test

import javax.xml.datatype.XMLGregorianCalendar
import net.shrine.protocol.i2b2.AuthenticationInfo
import net.shrine.protocol.i2b2.Credential
import net.shrine.xml.{XmlDateHelper, XmlUtil}

/**
 * @author clint
 * @since Feb 25, 2014
 */
final class GetUserConfigurationTest extends ShouldMatchersForJUnit {
  @Test
  def testToI2b2():Unit = {
    doTestToI2b2(AuthenticationInfo("d", "u", Credential("p", isToken = false)), XmlDateHelper.now)
    
    doTestToI2b2(AuthenticationInfo("d", "u", Credential("lsakjdlkasjdl", isToken = true)), XmlDateHelper.now)
  }

  def doTestToI2b2(authn: AuthenticationInfo, timestamp: XMLGregorianCalendar): Unit = {
    val expectedXml = XmlUtil.stripWhitespace {
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
          <datetime_of_message>{ timestamp }</datetime_of_message>
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
    
    GetUserConfigurationRequest(authn, Some(timestamp)).toI2b2 should equal(expectedXml)
  }
}
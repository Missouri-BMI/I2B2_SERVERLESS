package net.shrine.authentication.pm

import org.junit.Test
import net.shrine.protocol.i2b2.Credential
import net.shrine.util.ShouldMatchersForJUnit
import net.shrine.xml.XmlUtil

/**
 * @author Bill Simons
 * @since 3/6/12
 * @see http://cbmi.med.harvard.edu
 * @see http://chip.org
 *       <p/>
 *       NOTICE: This software comes with NO guarantees whatsoever and is
 *       licensed as Lgpl Open Source
 * @see http://www.gnu.org/licenses/lgpl.html
 */
final class UserTest extends ShouldMatchersForJUnit {

  private val projectId1 = "foo"

  private val projectId2 = "bar"

  private val params = Map("x" -> "1", "y" -> "2")

  private val roles1 = Set("a", "b", "c")

  private val roles2 = Set("MANAGER", "x", "y")

  private lazy val projects = Seq((projectId1, roles1), (projectId2, roles2))

  private val fullName = "Full name"

  private val userName = "user name"

  private val domain = "demo"

  private lazy val response = XmlUtil.stripWhitespace {
    <ns4:response xmlns:ns2="http://www.i2b2.org/xsd/hive/pdo/1.1/" xmlns:ns3="http://www.i2b2.org/xsd/cell/crc/pdo/1.1/" xmlns:ns4="http://www.i2b2.org/xsd/hive/msg/1.1/" xmlns:ns5="http://www.i2b2.org/xsd/cell/crc/psm/1.1/" xmlns:ns6="http://www.i2b2.org/xsd/cell/pm/1.1/" xmlns:ns7="http://sheriff.shrine.net/" xmlns:ns8="http://www.i2b2.org/xsd/cell/crc/psm/querydefinition/1.1/" xmlns:ns9="http://www.i2b2.org/xsd/cell/crc/psm/analysisdefinition/1.1/" xmlns:ns10="http://www.i2b2.org/xsd/cell/ont/1.1/" xmlns:ns11="http://www.i2b2.org/xsd/hive/msg/result/1.1/">
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
        <datetime_of_message>2011-04-08T16:21:12.251-04:00</datetime_of_message>
        <security/>
        <project_id xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:nil="true"/>
      </message_header>
      <response_header>
        <result_status>
          <status type="DONE">DONE</status>
        </result_status>
      </response_header>
      <message_body>
        <ns6:configure>
          <environment>DEVELOPMENT</environment>
          <helpURL>http://www.i2b2.org</helpURL>
          <user>
            <full_name>{ fullName }</full_name>
            <user_name>{ userName }</user_name>
            <password token_ms_timeout="1800000" is_token="true">SessionKey:key</password>
            <domain>{ domain }</domain>
            {
              params.map {
                case (name, value) =>
                  <param name={ name }>{ value }</param>
              }
            }
            {
              projects.map {
                case (projectId, roles) =>
                  <project id={ projectId }>
                    <name>Demo Group { projectId } </name>
                    <wiki>http://www.i2b2.org</wiki>
                    {
                      roles.map(r => <role>{ r }</role>)
                    }
                  </project>
              }
            }
          </user>
          <cell_datas>
            <cell_data id="CRC">
              <name>Data Repository</name>
              <url>http://localhost/crc</url>
              <method>REST</method>
            </cell_data>
            <cell_data id="ONT">
              <name>Ontology Cell</name>
              <url>http://localhost/ont</url>
              <method>REST</method>
              <param name="OntSynonyms">false</param>
              <param name="OntMax">200</param>
              <param name="OntHidden">false</param>
            </cell_data>
          </cell_datas>
        </ns6:configure>
      </message_body>
    </ns4:response>
  }

  @Test
  def testToAuthInfo(): Unit = {
    val username = "some-user"
    val domain = "some-domain"
    val credential = Credential("kalsdjald", isToken = true)

    val user = User("some-full-name", username, domain, credential, Map.empty, Map.empty)

    val authn = user.toAuthInfo

    authn should not be null
    authn.credential should equal(credential)
    authn.domain should equal(domain)
    authn.username should equal(username)
  }

  @Test
  def testFromI2b2():Unit = {
    val user = User.fromI2b2(response).get

    user.fullName should equal(fullName)
    user.username should equal(userName)
    user.domain should equal(domain)
    user.credential.value should equal("SessionKey:key")
    user.credential.isToken should be(true)
    user.params should equal(params)
    user.rolesByProject should equal(Map(projectId1 -> roles1, projectId2 -> roles2))
  }

  @Test
  def testFailsOnBadInput():Unit = {
   User.fromI2b2(<foo/>).isFailure should be(true)

    val i2b2ErrorXml = XmlUtil.stripWhitespace {
      <ns4:response xmlns:ns2="http://www.i2b2.org/xsd/hive/pdo/1.1/" xmlns:ns3="http://www.i2b2.org/xsd/cell/crc/pdo/1.1/" xmlns:ns4="http://www.i2b2.org/xsd/hive/msg/1.1/" xmlns:ns5="http://www.i2b2.org/xsd/cell/crc/psm/1.1/" xmlns:ns6="http://www.i2b2.org/xsd/cell/pm/1.1/" xmlns:ns7="http://sheriff.shrine.net/" xmlns:ns8="http://www.i2b2.org/xsd/cell/crc/psm/querydefinition/1.1/" xmlns:ns9="http://www.i2b2.org/xsd/cell/crc/psm/analysisdefinition/1.1/" xmlns:ns10="http://www.i2b2.org/xsd/cell/ont/1.1/" xmlns:ns11="http://www.i2b2.org/xsd/hive/msg/result/1.1/">
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
          <datetime_of_message>2011-04-08T16:21:12.251-04:00</datetime_of_message>
          <security/>
          <project_id xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:nil="true"/>
        </message_header>
        <response_header>
          <result_status>
            <status type="ERROR">foo</status>
          </result_status>
        </response_header>
        <message_body>
        </message_body>
      </ns4:response>
    }

    User.fromI2b2(i2b2ErrorXml).isFailure should be(true)
  }
}
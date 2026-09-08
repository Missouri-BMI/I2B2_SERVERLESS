package net.shrine.log

import org.http4s.BasicCredentials
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * @author david
 * @since 7/24/15
 */
class LogCensorTest {

  @Test
  def testCensorI2b2():Unit = {
    val i2b2PasswordString = """<password is_token="false" token_ms_timeout="1800000">kapow</password>"""
    val expectedI2b2PasswordString = """<password is_token="false" token_ms_timeout="1800000">REDACTED</password>"""

    val result = LogCensor.censor(i2b2PasswordString)
    assertEquals(expectedI2b2PasswordString,result)
  }

  @Test
  def testCensorI2b2CrcResult():Unit = {
    val i2b2CountString = """<set_size>239</set_size>"""
    val expectedI2b2CountString = """<set_size>REDACTED</set_size>"""

    val result = LogCensor.censor(i2b2CountString)
    assertEquals(expectedI2b2CountString,result)
  }

  @Test
  def testCensorBasicAuthWithMore():Unit = {
    val basicAuthLine = "HttpRequest(GET,https://shrine-qa1.hms.harvard.edu:6443/qep/approvedTopics/user/shrine,List(Host: shrine-qa1.hms.harvard.edu:6443, Authorization: Basic cWVwOnRydXN0bWU=, User-Agent: spray-can/1.3.3),Empty,HTTP/1.1)"
    val expectedBasicAuthLine = "HttpRequest(GET,https://shrine-qa1.hms.harvard.edu:6443/qep/approvedTopics/user/shrine,List(Host: shrine-qa1.hms.harvard.edu:6443, Authorization: Basic REDACTED, User-Agent: spray-can/1.3.3),Empty,HTTP/1.1)"

    val result = LogCensor.censor(basicAuthLine)
    assertEquals(expectedBasicAuthLine,result)
  }

  //Request: HttpRequest(POST,http://example.com/steward/rejectTopic/topic/1,List(Authorization: Basic ZGF2ZTprYWJsYW0=),Empty,HTTP/1.1)
  @Test
  def testCensorBasicAuthLast():Unit = {
    val basicAuthLine = "Request: HttpRequest(POST,http://example.com/steward/rejectTopic/topic/1,List(Authorization: Basic ZGF2ZTprYWJsYW0=),Empty,HTTP/1.1)"
    val expectedBasicAuthLine = "Request: HttpRequest(POST,http://example.com/steward/rejectTopic/topic/1,List(Authorization: Basic REDACTED),Empty,HTTP/1.1)"

    val result = LogCensor.censor(basicAuthLine)
    assertEquals(expectedBasicAuthLine,result)
  }

  //"password" : "flarf",
  @Test
  def testCensorTypesafeConfigPassword():Unit = {
    val typesafeConfigLine = "\"password\" : \"flarf\","
    val expectedTypesafeConfigLine = "\"password\" : \"REDACTED\","

    val result = LogCensor.censor(typesafeConfigLine)
    assertEquals(expectedTypesafeConfigLine,result)
  }

  //"qepPassword" : "flarf",
  @Test
  def testCensorTypesafeConfigQepPassword():Unit = {
    val typesafeConfigLine = "\"qepPassword\" : \"flarf\","
    val expectedTypesafeConfigLine = "\"qepPassword\" : \"REDACTED\","

    val result = LogCensor.censor(typesafeConfigLine)
    assertEquals(expectedTypesafeConfigLine,result)
  }

  @Test
  def testCensorBasicCredentials():Unit = {
    val basicCredentialsString = BasicCredentials("username","password").toString

    val expectedString = "BasicCredentials(username,REDACTED,UTF-8)"

    val result = LogCensor.censor(basicCredentialsString)
    assertEquals(expectedString,result)
  }
}


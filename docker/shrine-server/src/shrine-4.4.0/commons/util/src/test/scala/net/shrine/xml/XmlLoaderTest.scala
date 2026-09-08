package net.shrine.xml

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertThrows

import java.io.File
import scala.xml.{Elem, SAXParseException}

class XmlLoaderTest {

  @Test
  def testXmlInjectionDoesNotWork():Unit = {

    val pathToSecrets = new File("./target/test-classes/hidden.txt").getAbsolutePath

    val xmlInjectionAttack =
      s"""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><!DOCTYPE foo [<!ENTITY xxeruekz SYSTEM "file://$pathToSecrets"> ]>
      |<i2b2:request xmlns:i2b2="http://www.i2b2.org/xsd/hive/msg/1.1/" xmlns:pm="http://www.i2b2.org/xsd/cell/pm/1.1/">
      |    <message_header>
      |        <proxy>
      |            <redirect_url>http://172.28.128.3:9090/i2b2/services/PMService/getServices&xxeruekz;</redirect_url>
      |        </proxy>
      |    </message_header>
      |</i2b2:request>
      |""".stripMargin

    assertThrows(classOf[SAXParseException],() => {
      val xml: Elem = XmlUtil.loadString(xmlInjectionAttack)
      assert(!xml.toString.contains("Too Many Secrets"), "xml message contains 'Too Many Secrets' and is vulnerable to XML injection attacks.")
    })
  }

}

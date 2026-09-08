package net.shrine.protocol.i2b2

import org.junit.Test
import net.shrine.util.ShouldMatchersForJUnit
import net.shrine.xml.XmlUtil

/**
 * @author clint
 * @date Aug 15, 2012
 */
final class I2b2ResultEnvelopeTest extends ShouldMatchersForJUnit {
  private val tuples = Seq("x" -> 1L, "y" -> 2L, "z" -> 3L)

  import DefaultBreakdownResultOutputTypes._

  @Test
  def testMapValues: Unit = {
    val resultType = PATIENT_RACE_COUNT_XML
    
    val env = new I2b2Result(resultType, tuples: _*)
    
    val mapped = env.mapValues(_ + 1)
    
    mapped.resultType should equal(resultType)
    mapped.data should equal(Map("x" -> 2L, "y" -> 3L, "z" -> 4L))
  }
  
  @Test
  def testPlusPlus: Unit = {
    val resultType = PATIENT_RACE_COUNT_XML

    val env = new I2b2Result(resultType)

    val envWithData = env ++ tuples

    (env eq envWithData) should not be (true)

    env.toMap should equal(Map.empty)

    envWithData.toMap should equal(Map(tuples: _*))

    val anotherEnvWithData = env ++ Map(tuples: _*)

    env.toMap should equal(Map.empty)

    anotherEnvWithData.toMap should equal(Map(tuples: _*))
  }

  @Test
  def testConstructorTupleVarargs: Unit = {
    val resultType = PATIENT_GENDER_COUNT_XML

    val env = new I2b2Result(resultType, tuples: _*)

    env.resultType should equal(resultType)

    env.toMap should equal(Map(tuples: _*))
  }

 /* @Test SHRINE-3578
  def testFromI2b2: Unit = {
    val xml = I2b2Workarounds.unescape("""&lt;?xml version="1.0" encoding="UTF-8" standalone="yes"?>
&lt;ns10:i2b2_result_envelope xmlns:ns2="http://www.i2b2.org/xsd/hive/pdo/1.1/" xmlns:ns4="http://www.i2b2.org/xsd/cell/crc/psm/1.1/" xmlns:ns3="http://www.i2b2.org/xsd/cell/crc/pdo/1.1/" xmlns:ns9="http://www.i2b2.org/xsd/cell/ont/1.1/" xmlns:ns5="http://www.i2b2.org/xsd/hive/msg/1.1/" xmlns:ns6="http://www.i2b2.org/xsd/cell/crc/psm/querydefinition/1.1/" xmlns:ns10="http://www.i2b2.org/xsd/hive/msg/result/1.1/" xmlns:ns7="http://www.i2b2.org/xsd/cell/crc/psm/analysisdefinition/1.1/" xmlns:ns8="http://www.i2b2.org/xsd/cell/pm/1.1/">
    &lt;body>
        &lt;ns10:result name="PATIENT_AGE_COUNT_XML">
            &lt;data type="int" column="  0-9 years old">0&lt;/data>
            &lt;data type="int" column="  10-17 years old">11&lt;/data>
            &lt;data type="int" column="  18-34 years old">26&lt;/data>
            &lt;data type="int" column="  35-44 years old">26&lt;/data>
            &lt;data type="int" column="  45-54 years old">8&lt;/data>
            &lt;data type="int" column="  55-64 years old">6&lt;/data>
            &lt;data type="int" column="  65-74 years old">5&lt;/data>
            &lt;data type="int" column="  75-84 years old">0&lt;/data>
            &lt;data type="int" column="&amp;gt;= 65 years old">5&lt;/data>
            &lt;data type="int" column="&amp;gt;= 85 years old">0&lt;/data>
            &lt;data type="int" column="Not recorded">0&lt;/data>
        &lt;/ns10:result>
    &lt;/body>
&lt;/ns10:i2b2_result_envelope>""")

    val env = I2b2ResultEnvelope.fromI2b2String(DefaultBreakdownResultOutputTypes.toSet)(xml).get

    env.resultType should be(PATIENT_AGE_COUNT_XML)

    val expected = Map("  0-9 years old" -> 0L,
      "  10-17 years old" -> 11L,
      "  18-34 years old" -> 26L,
      "  35-44 years old" -> 26L,
      "  45-54 years old" -> 8L,
      "  55-64 years old" -> 6L,
      "  65-74 years old" -> 5L,
      "  75-84 years old" -> 0L,
      ">= 65 years old" -> 5L,
      ">= 85 years old" -> 0L,
      "Not recorded" -> 0L)

    env.data should equal(expected)

    val badXml1 = I2b2Workarounds.unescape("""&lt;?xml version="1.0" encoding="UTF-8" standalone="yes"?>
&lt;ns10:i2b2_result_envelope xmlns:ns2="http://www.i2b2.org/xsd/hive/pdo/1.1/" xmlns:ns4="http://www.i2b2.org/xsd/cell/crc/psm/1.1/" xmlns:ns3="http://www.i2b2.org/xsd/cell/crc/pdo/1.1/" xmlns:ns9="http://www.i2b2.org/xsd/cell/ont/1.1/" xmlns:ns5="http://www.i2b2.org/xsd/hive/msg/1.1/" xmlns:ns6="http://www.i2b2.org/xsd/cell/crc/psm/querydefinition/1.1/" xmlns:ns10="http://www.i2b2.org/xsd/hive/msg/result/1.1/" xmlns:ns7="http://www.i2b2.org/xsd/cell/crc/psm/analysisdefinition/1.1/" xmlns:ns8="http://www.i2b2.org/xsd/cell/pm/1.1/">
    &lt;body>
        &lt;ns10:result name="PATIENT_AGE_COUNT_XML_JKALSHFKAJSFHKASHFJKSAHFKHJH">
            &lt;data type="int" column="  0-9 years old">0&lt;/data>
            &lt;data type="int" column="  10-17 years old">11&lt;/data>
            &lt;data type="int" column="  18-34 years old">26&lt;/data>
            &lt;data type="int" column="  35-44 years old">26&lt;/data>
            &lt;data type="int" column="  45-54 years old">8&lt;/data>
            &lt;data type="int" column="  55-64 years old">6&lt;/data>
            &lt;data type="int" column="  65-74 years old">5&lt;/data>
            &lt;data type="int" column="  75-84 years old">0&lt;/data>
            &lt;data type="int" column="&amp;gt;= 65 years old">5&lt;/data>
            &lt;data type="int" column="&amp;gt;= 85 years old">0&lt;/data>
            &lt;data type="int" column="Not recorded">0&lt;/data>
        &lt;/ns10:result>
    &lt;/body>
&lt;/ns10:i2b2_result_envelope>""")

    I2b2ResultEnvelope.fromI2b2String(DefaultBreakdownResultOutputTypes.toSet)(badXml1).isFailure should be(true)

    val badXml2 = I2b2Workarounds.unescape("""&lt;?xml version="1.0" encoding="UTF-8" standalone="yes"?>
&lt;ns10:i2b2_result_envelope xmlns:ns2="http://www.i2b2.org/xsd/hive/pdo/1.1/" xmlns:ns4="http://www.i2b2.org/xsd/cell/crc/psm/1.1/" xmlns:ns3="http://www.i2b2.org/xsd/cell/crc/pdo/1.1/" xmlns:ns9="http://www.i2b2.org/xsd/cell/ont/1.1/" xmlns:ns5="http://www.i2b2.org/xsd/hive/msg/1.1/" xmlns:ns6="http://www.i2b2.org/xsd/cell/crc/psm/querydefinition/1.1/" xmlns:ns10="http://www.i2b2.org/xsd/hive/msg/result/1.1/" xmlns:ns7="http://www.i2b2.org/xsd/cell/crc/psm/analysisdefinition/1.1/" xmlns:ns8="http://www.i2b2.org/xsd/cell/pm/1.1/">
    &lt;body>
        &lt;ns10:result name="PATIENT_AGE_COUNT_XML">
            &lt;foo type="int" column="  0-9 years old">0&lt;/foo>
        &lt;/ns10:result>
    &lt;/body>
&lt;/ns10:i2b2_result_envelope>""")

    I2b2ResultEnvelope.fromI2b2String(DefaultBreakdownResultOutputTypes.toSet)(badXml2).get.data.isEmpty should be(true)
  }    */

  @Test
  def testPlusTuple: Unit = {
    val resultType = ResultOutputType.PATIENT_COUNT_XML

    val empty = I2b2Result.empty(resultType)

    val env = empty + ("foo" -> 123L)

    (env eq empty) should be(false)

    env.resultType should be(resultType)

    env.data should equal(Map("foo" -> 123L))
  }

  @Test
  def testEmpty: Unit = {
    for (resultType <- ResultOutputType.values) {
      val emptyResult = I2b2Result.empty(resultType)

      emptyResult should not be (null)
      emptyResult.resultType should be(resultType)
      emptyResult.data.isEmpty should be(true)

      val env2 = I2b2Result.empty(resultType)

      (emptyResult eq env2) should be(false)
    }
  }

  @Test
  def testToMap: Unit = {
    val resultType = ResultOutputType.PATIENT_COUNT_XML

    val env = new I2b2Result(resultType, Map("foo" -> 123L, "bar" -> 99L))

    env.toMap should equal(Map("foo" -> 123L, "bar" -> 99L))
  }

  @Test
  def testToXml: Unit = {
    val resultType = DefaultBreakdownResultOutputTypes.PATIENT_AGE_COUNT_XML

    val env = new I2b2Result(resultType, Map("foo" -> 123L, "bar" -> 99L, "baz" -> 456L, "nuh" -> 88L))

    val xml = env.toXmlString

    xml should equal {
      XmlUtil.stripWhitespace {
        <resultEnvelope>
          <resultType>PATIENT_AGE_COUNT_XML</resultType>
          <column>
            <name>bar</name>
            <value>99</value>
          </column>
          <column>
            <name>baz</name>
            <value>456</value>
          </column>
          <column>
            <name>foo</name>
            <value>123</value>
          </column>
          <column>
            <name>nuh</name>
            <value>88</value>
          </column>
        </resultEnvelope>
      }.toString
    }
  }
}
package net.shrine.adapter.i2b2Protocol

import net.shrine.util.ShouldMatchersForJUnit
import org.junit.Test

/**
 * @author clint
 * @date Aug 15, 2012
 */
final class ReadResultResponseTest extends ShouldMatchersForJUnit {
  
 /* @Test SHRINE-3578
  def testUnescape(): Unit =  {
    val semiEscapedXml = """&lt;?xml version="1.0" encoding="UTF-8" standalone="yes"?>
&lt;ns10:i2b2_result_envelope xmlns:ns2="http://www.i2b2.org/xsd/hive/pdo/1.1/" xmlns:ns4="http://www.i2b2.org/xsd/cell/crc/psm/1.1/" xmlns:ns3="http://www.i2b2.org/xsd/cell/crc/pdo/1.1/" xmlns:ns9="http://www.i2b2.org/xsd/cell/ont/1.1/" xmlns:ns5="http://www.i2b2.org/xsd/hive/msg/1.1/" xmlns:ns6="http://www.i2b2.org/xsd/cell/crc/psm/querydefinition/1.1/" xmlns:ns10="http://www.i2b2.org/xsd/hive/msg/result/1.1/" xmlns:ns7="http://www.i2b2.org/xsd/cell/crc/psm/analysisdefinition/1.1/" xmlns:ns8="http://www.i2b2.org/xsd/cell/pm/1.1/">
    &lt;body>
        &lt;ns10:result name="PATIENT_GENDER_COUNT_XML">
            &lt;data type="int" column="Female">0&lt;/data>
            &lt;data type="int" column="Male">82&lt;/data>
            &lt;data type="int" column="Unknown">0&lt;/data>
        &lt;/ns10:result>
    &lt;/body>
&lt;/ns10:i2b2_result_envelope>"""

    val expected = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<ns10:i2b2_result_envelope xmlns:ns2="http://www.i2b2.org/xsd/hive/pdo/1.1/" xmlns:ns4="http://www.i2b2.org/xsd/cell/crc/psm/1.1/" xmlns:ns3="http://www.i2b2.org/xsd/cell/crc/pdo/1.1/" xmlns:ns9="http://www.i2b2.org/xsd/cell/ont/1.1/" xmlns:ns5="http://www.i2b2.org/xsd/hive/msg/1.1/" xmlns:ns6="http://www.i2b2.org/xsd/cell/crc/psm/querydefinition/1.1/" xmlns:ns10="http://www.i2b2.org/xsd/hive/msg/result/1.1/" xmlns:ns7="http://www.i2b2.org/xsd/cell/crc/psm/analysisdefinition/1.1/" xmlns:ns8="http://www.i2b2.org/xsd/cell/pm/1.1/">
    <body>
        <ns10:result name="PATIENT_GENDER_COUNT_XML">
            <data type="int" column="Female">0</data>
            <data type="int" column="Male">82</data>
            <data type="int" column="Unknown">0</data>
        </ns10:result>
    </body>
</ns10:i2b2_result_envelope>"""
    
    I2b2Workarounds.unescape(semiEscapedXml) should equal(expected)
  }

  @Test SHRINE-3578
  def testUnescapeWithLessThanOrEqual(): Unit =  {
    val semiEscapedXml = """&lt;?xml version="1.0" encoding="UTF-8" standalone="yes"?>
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
&lt;/ns10:i2b2_result_envelope>"""

    val expected = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<ns10:i2b2_result_envelope xmlns:ns2="http://www.i2b2.org/xsd/hive/pdo/1.1/" xmlns:ns4="http://www.i2b2.org/xsd/cell/crc/psm/1.1/" xmlns:ns3="http://www.i2b2.org/xsd/cell/crc/pdo/1.1/" xmlns:ns9="http://www.i2b2.org/xsd/cell/ont/1.1/" xmlns:ns5="http://www.i2b2.org/xsd/hive/msg/1.1/" xmlns:ns6="http://www.i2b2.org/xsd/cell/crc/psm/querydefinition/1.1/" xmlns:ns10="http://www.i2b2.org/xsd/hive/msg/result/1.1/" xmlns:ns7="http://www.i2b2.org/xsd/cell/crc/psm/analysisdefinition/1.1/" xmlns:ns8="http://www.i2b2.org/xsd/cell/pm/1.1/">
    <body>
        <ns10:result name="PATIENT_AGE_COUNT_XML">
            <data type="int" column="  0-9 years old">0</data>
            <data type="int" column="  10-17 years old">11</data>
            <data type="int" column="  18-34 years old">26</data>
            <data type="int" column="  35-44 years old">26</data>
            <data type="int" column="  45-54 years old">8</data>
            <data type="int" column="  55-64 years old">6</data>
            <data type="int" column="  65-74 years old">5</data>
            <data type="int" column="  75-84 years old">0</data>
            <data type="int" column=">= 65 years old">5</data>
            <data type="int" column=">= 85 years old">0</data>
            <data type="int" column="Not recorded">0</data>
        </ns10:result>
    </body>
</ns10:i2b2_result_envelope>"""

    I2b2Workarounds.unescape(semiEscapedXml) should equal(expected)
  }*/

  @Test
  def testUnescapeWithLessThan(): Unit = {
    val semiEscapedXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                           |<ns10:i2b2_result_envelope xmlns:ns6="http://www.i2b2.org/xsd/cell/crc/psm/querydefinition/1.1/" xmlns:ns5="http://www.i2b2.org/xsd/hive/msg/1.1/" xmlns:ns8="http://www.i2b2.org/xsd/cell/pm/1.1/" xmlns:ns7="http://www.i2b2.org/xsd/cell/crc/psm/analysisdefinition/1.1/" xmlns:ns9="http://www.i2b2.org/xsd/cell/ont/1.1/" xmlns:ns10="http://www.i2b2.org/xsd/hive/msg/result/1.1/" xmlns:ns2="http://www.i2b2.org/xsd/hive/pdo/1.1/" xmlns:ns4="http://www.i2b2.org/xsd/cell/crc/psm/1.1/" xmlns:ns3="http://www.i2b2.org/xsd/cell/crc/pdo/1.1/">
                           |    <body>
                           |        <ns10:result name="PATIENT_AGE_COUNT_XML">
                           |            <data column="(adults) &gt;= 18 years old" type="int">51</data>
                           |            <data column="(children) &lt; 18 years old" type="int">0</data>
                           |            <data column="0-9 years old" type="int">0</data>
                           |            <data column="10-17 years old" type="int">0</data>
                           |            <data column="18-34 years old" type="int">16</data>
                           |            <data column="35-44 years old" type="int">16</data>
                           |            <data column="45-54 years old" type="int">6</data>
                           |            <data column="55-64 years old" type="int">3</data>
                           |            <data column="65-74 years old" type="int">3</data>
                           |            <data column="75-84 years old" type="int">2</data>
                           |            <data column="85-89 years old" type="int">2</data>
                           |            <data column="&gt;= 65 years old" type="int">10</data>
                           |            <data column="&gt;= 85 years old" type="int">5</data>
                           |            <data column="&gt;= 90 years old" type="int">3</data>
                           |            <data column="Not recorded" type="int">0</data>
                           |        </ns10:result>
                           |    </body>
                           |</ns10:i2b2_result_envelope>""".stripMargin


    val expected = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                     |<ns10:i2b2_result_envelope xmlns:ns6="http://www.i2b2.org/xsd/cell/crc/psm/querydefinition/1.1/" xmlns:ns5="http://www.i2b2.org/xsd/hive/msg/1.1/" xmlns:ns8="http://www.i2b2.org/xsd/cell/pm/1.1/" xmlns:ns7="http://www.i2b2.org/xsd/cell/crc/psm/analysisdefinition/1.1/" xmlns:ns9="http://www.i2b2.org/xsd/cell/ont/1.1/" xmlns:ns10="http://www.i2b2.org/xsd/hive/msg/result/1.1/" xmlns:ns2="http://www.i2b2.org/xsd/hive/pdo/1.1/" xmlns:ns4="http://www.i2b2.org/xsd/cell/crc/psm/1.1/" xmlns:ns3="http://www.i2b2.org/xsd/cell/crc/pdo/1.1/">
                     |    <body>
                     |        <ns10:result name="PATIENT_AGE_COUNT_XML">
                     |            <data column="(adults) &gt;= 18 years old" type="int">51</data>
                     |            <data column="(children) &lt; 18 years old" type="int">0</data>
                     |            <data column="0-9 years old" type="int">0</data>
                     |            <data column="10-17 years old" type="int">0</data>
                     |            <data column="18-34 years old" type="int">16</data>
                     |            <data column="35-44 years old" type="int">16</data>
                     |            <data column="45-54 years old" type="int">6</data>
                     |            <data column="55-64 years old" type="int">3</data>
                     |            <data column="65-74 years old" type="int">3</data>
                     |            <data column="75-84 years old" type="int">2</data>
                     |            <data column="85-89 years old" type="int">2</data>
                     |            <data column="&gt;= 65 years old" type="int">10</data>
                     |            <data column="&gt;= 85 years old" type="int">5</data>
                     |            <data column="&gt;= 90 years old" type="int">3</data>
                     |            <data column="Not recorded" type="int">0</data>
                     |        </ns10:result>
                     |    </body>
                     |</ns10:i2b2_result_envelope>""".stripMargin

    ReadResultResponse.i2b2Unescape(semiEscapedXml) should equal(expected)
  }
}
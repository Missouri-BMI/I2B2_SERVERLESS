package net.shrine.xml

import org.junit.jupiter.api.Assertions.{assertEquals, assertThrows}
import org.junit.jupiter.api.Test

/**
 * @author Bill Simons
 * @since 2/14/12
 * @see http://cbmi.med.harvard.edu
 * @see http://chip.org
 *       <p/>
 *       NOTICE: This software comes with NO guarantees whatsoever and is
 *       licensed as Lgpl Open Source
 * @see http://www.gnu.org/licenses/lgpl.html
 */
final class XmlUtilTest {
  @Test
  def testCondense(): Unit = {
    import net.shrine.xml.XmlUtil.condense

    val alreadyCondensed = <foo>bar</foo>

    assertEquals(condense(alreadyCondensed),alreadyCondensed)
    assertEquals(condense(<foo> bar</foo>) , alreadyCondensed)
    assertEquals(condense(<foo>bar  </foo>),alreadyCondensed)
    assertEquals(condense(<foo>    bar    </foo>),alreadyCondensed)

    assertEquals(condense(<foo>
               bar
             </foo>),alreadyCondensed)

    {
      val nested = {
        <baz>
          <blarg>
            <foo>
              bar
            </foo>
          </blarg>
        </baz>
      }

      val expected = {
        <baz>
          <blarg>
            <foo>bar</foo>
          </blarg>
        </baz>
      }

      assertEquals(condense(nested),expected)
    }

    {
      val complex = {
        <query_definition>
          <query_name>Acquired hemoly@13:24:42</query_name>
          <query_timing>ANY</query_timing>
          <specificity_scale>0</specificity_scale>
          <use_shrine>1</use_shrine>
          <panel>
            <panel_number>1</panel_number>
            <invert>0</invert>
            <panel_timing>ANY</panel_timing>
            <total_item_occurrences>1</total_item_occurrences>
            <item>
              <hlevel>3</hlevel>
              <item_name>
                \\SHRINE\SHRINE\Diagnoses\Diseases of the blood and blood-forming organs (280-289.99)\Acquired hemolytic anemias (283)\
              </item_name>
              <item_key>
                \\SHRINE\SHRINE\Diagnoses\Diseases of the blood and blood-forming organs (280-289.99)\Acquired hemolytic anemias (283)\
              </item_key>
              <tooltip>
                \\SHRINE\SHRINE\Diagnoses\Diseases of the blood and blood-forming organs (280-289.99)\Acquired hemolytic anemias (283)\
              </tooltip>
              <class>ENC</class>
              <constrain_by_date/>
              <item_icon>LA</item_icon>
              <item_is_synonym>false</item_is_synonym>
            </item>
          </panel>
        </query_definition>
      }

      val expected = {
        <query_definition>
          <query_name>Acquired hemoly@13:24:42</query_name>
          <query_timing>ANY</query_timing>
          <specificity_scale>0</specificity_scale>
          <use_shrine>1</use_shrine>
          <panel>
            <panel_number>1</panel_number>
            <invert>0</invert>
            <panel_timing>ANY</panel_timing>
            <total_item_occurrences>1</total_item_occurrences>
            <item>
              <hlevel>3</hlevel>
              <item_name>\\SHRINE\SHRINE\Diagnoses\Diseases of the blood and blood-forming organs (280-289.99)\Acquired hemolytic anemias (283)\</item_name>
              <item_key>\\SHRINE\SHRINE\Diagnoses\Diseases of the blood and blood-forming organs (280-289.99)\Acquired hemolytic anemias (283)\</item_key>
              <tooltip>\\SHRINE\SHRINE\Diagnoses\Diseases of the blood and blood-forming organs (280-289.99)\Acquired hemolytic anemias (283)\</tooltip>
              <class>ENC</class>
              <constrain_by_date/>
              <item_icon>LA</item_icon>
              <item_is_synonym>false</item_is_synonym>
            </item>
          </panel>
        </query_definition>
      }

      assertEquals(condense(complex),expected)
    }
  }

  @Test
  def testTrim(): Unit = {
    import net.shrine.xml.XmlUtil.trim

    assertEquals(trim(<foo>bar</foo>),"bar")
    assertEquals(trim(<foo> bar</foo>),"bar")
    assertEquals(trim(<foo>bar  </foo>),"bar")
    assertEquals(trim(<foo>   bar    </foo>),"bar")
  }

  @Test
  def testToInt(): Unit = {
    assertThrows(classOf[Exception], () =>
      XmlUtil.toInt(<foo>bar</foo>)
    )

    assertThrows(classOf[Exception], () =>
      XmlUtil.toInt(<foo></foo>)
    )

    assertThrows(classOf[Exception], () =>
      XmlUtil.toInt(<foo>  </foo>)
    )

    assertThrows(classOf[Exception], () =>
      XmlUtil.toInt(<foo>123bar</foo>)
    )

    assertEquals(XmlUtil.toInt(<foo>123 </foo>),123)
    assertEquals(XmlUtil.toInt(<foo> 123</foo>),123)
    assertEquals(XmlUtil.toInt(<foo> 123 </foo>),123)
    assertEquals(XmlUtil.toInt(<foo>123</foo>),123)
    assertEquals(XmlUtil.toInt(<foo>0</foo>), 0)
    assertEquals(XmlUtil.toInt(<foo>-123</foo>),-123)
  }

  @Test
  def testToLong(): Unit = {
    assertThrows(classOf[Exception], () =>
      XmlUtil.toLong(<foo>bar</foo>)
    )

    assertThrows(classOf[Exception], () =>
      XmlUtil.toLong(<foo></foo>)
    )

    assertThrows(classOf[Exception], () =>
      XmlUtil.toLong(<foo>  </foo>)
    )

    assertThrows(classOf[Exception], () =>
      XmlUtil.toLong(<foo>123bar</foo>)
    )

    assertEquals(XmlUtil.toLong(<foo>123 </foo>),123L)
    assertEquals(XmlUtil.toLong(<foo> 123</foo>) ,123L)
    assertEquals(XmlUtil.toLong(<foo> 123 </foo>),123L)
    assertEquals(XmlUtil.toLong(<foo>123</foo>),123L)
    assertEquals(XmlUtil.toLong(<foo>0</foo>) ,0L)
    assertEquals(XmlUtil.toLong(<foo>-123</foo>),-123L)
  }

  @Test
  def testLoadStringIgnoringRemoteResources(): Unit = {
    val xml = <foo><bar>  <baz/>  <nuh><zuh>123</zuh>     </nuh></bar></foo>

    val loaded = XmlUtil.loadStringIgnoringRemoteResources(xml.toString)

    assertEquals(loaded,Some(xml))

    val loadedViaXmlLoadString = XmlUtil.loadString(xml.toString)

    assertEquals(loadedViaXmlLoadString,xml)

    assertEquals(loaded.get,loadedViaXmlLoadString)

    //TODO: Test with nefarious DTD URL
  }

  @Test
  def testStripWhitespace(): Unit = {
    val node = XmlUtil.loadString("<foo>\n\t<bar>  baz     </bar>\n</foo>")

    assertEquals(XmlUtil.stripWhitespace(node).toString() ,"<foo><bar>  baz     </bar></foo>")
  }

  @Test
  def testRenameRootTag(): Unit = {
    val xml = <foo><bar><baz/></bar></foo>

    val expected = <blarg><bar><baz/></bar></blarg>

    assertEquals(XmlUtil.renameRootTag("blarg")(xml).toString,expected.toString)
  }

  @Test
  def testPrettyPrint(): Unit = {
    {
      val xml = <foo><bar><baz/></bar><blerg>123</blerg></foo>

      val expected = {
        """<foo>
  <bar>
    <baz/>
  </bar>
  <blerg>123</blerg>
</foo>"""
      }

      assertEquals(XmlUtil.prettyPrint(xml),expected)
    }

    {
      val complex = {
        <query_definition>
          <query_name>Acquired hemoly@13:24:42</query_name>
          <query_timing>ANY</query_timing>
          <specificity_scale>0</specificity_scale>
          <use_shrine>1</use_shrine>
          <panel>
            <panel_number>1</panel_number>
            <invert>0</invert>
            <panel_timing>ANY</panel_timing>
            <total_item_occurrences>1</total_item_occurrences>
            <item>
              <hlevel>3</hlevel>
              <item_name>
                \\SHRINE\SHRINE\Diagnoses\Diseases of the blood and blood-forming organs (280-289.99)\Acquired hemolytic anemias (283)\
              </item_name>
              <item_key>
                \\SHRINE\SHRINE\Diagnoses\Diseases of the blood and blood-forming organs (280-289.99)\Acquired hemolytic anemias (283)\
              </item_key>
              <tooltip>
                \\SHRINE\SHRINE\Diagnoses\Diseases of the blood and blood-forming organs (280-289.99)\Acquired hemolytic anemias (283)\
              </tooltip>
              <class>ENC</class>
              <constrain_by_date/>
              <item_icon>LA</item_icon>
              <item_is_synonym>false</item_is_synonym>
            </item>
          </panel>
        </query_definition>
      }

      val expected = {
        """<query_definition>
  <query_name>Acquired hemoly@13:24:42</query_name>
  <query_timing>ANY</query_timing>
  <specificity_scale>0</specificity_scale>
  <use_shrine>1</use_shrine>
  <panel>
    <panel_number>1</panel_number>
    <invert>0</invert>
    <panel_timing>ANY</panel_timing>
    <total_item_occurrences>1</total_item_occurrences>
    <item>
      <hlevel>3</hlevel>
      <item_name>\\SHRINE\SHRINE\Diagnoses\Diseases of the blood and blood-forming organs (280-289.99)\Acquired hemolytic anemias (283)\</item_name>
      <item_key>\\SHRINE\SHRINE\Diagnoses\Diseases of the blood and blood-forming organs (280-289.99)\Acquired hemolytic anemias (283)\</item_key>
      <tooltip>\\SHRINE\SHRINE\Diagnoses\Diseases of the blood and blood-forming organs (280-289.99)\Acquired hemolytic anemias (283)\</tooltip>
      <class>ENC</class>
      <constrain_by_date/>
      <item_icon>LA</item_icon>
      <item_is_synonym>false</item_is_synonym>
    </item>
  </panel>
</query_definition>"""
      }

      assertEquals(XmlUtil.prettyPrint(complex),expected)
    }
  }

  @Test
  def testSurroundWith(): Unit = {
    import net.shrine.xml.XmlUtil._

    assertEquals(stripWhitespace(surroundWith(<foo/>)(<bar/><baz/>).head), <foo><bar/><baz/></foo>)

    assertEquals(stripWhitespace(surroundWith("foo")(<bar/><baz/>).head), <foo><bar/><baz/></foo>)
  }
}
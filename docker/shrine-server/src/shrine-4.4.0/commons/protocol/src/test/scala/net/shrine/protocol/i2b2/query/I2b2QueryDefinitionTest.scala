package net.shrine.protocol.i2b2.query

import net.shrine.protocol.i2b2.query
import net.shrine.protocol.version.v2.querydefinition.QueryDefinitionTest
import net.shrine.util.ShouldMatchersForJUnit
import net.shrine.xml.{XmlDateHelper, XmlUtil}
import org.junit.Test

import javax.xml.datatype.XMLGregorianCalendar
import scala.xml.{Node, NodeSeq, Utility}

/**
 *
 * @author Clint Gilbert
 * @since Jan 26, 2012
 *
 * @see http://cbmi.med.harvard.edu
 *
 * This software is licensed under the LGPL
 * @see http://www.gnu.org/licenses/lgpl.html
 *
 */
final class I2b2QueryDefinitionTest extends ShouldMatchersForJUnit {
  private val t1 = Term("""\\SHRINE\SHRINE\Diagnoses\Congenital anomalies\Cardiac and circulatory congenital anomalies\Aortic valve stenosis\Congenital stenosis of aortic valve\""",
  "Congenital stenosis of aortic valve")
  private val t2 = Term("""\\SHRINE\SHRINE\Demographics\Language\Bosnian\""", "Bosnian")
  private val t3 = Term("""\\SHRINE\SHRINE\Demographics\Age\18-34 years old\30 years old\""", "30 years old")
  private val t4 = Term("foo", "fooName")
  private val t5 = Term("blarg", "blargName")
  private val t6 = Term("nuh", "nuhName")
  private val t7 = Term("""\\i2b2_DEMO\i2b2\Demographics\Gender\Male\""", "Male")
  private val t8 = Term("""\\i2b2_DEMO\i2b2\Demographics\Gender\Unknown\""", "Unknown")
  private val t9 = Term("""\\i2b2_DIAG\i2b2\Diagnoses\Circulatory system (390-459)\Hypertensive disease (401-405)\""", "Hypertensive disease (401-405)")
  private val t10 = Term("""\\i2b2_DIAG\i2b2\Diagnoses\Circulatory system (390-459)\Ischemic heart disease (410-414)\""", "Ischemic heart disease (410-414)")

  private val q1 = I2b2QueryDefinition("blarg", t1)

  private val modifiers = Modifiers("n", "ap", "k")
  private val valueConstraint = ValueConstraint("n", Some("ap"), "k", "bar")

  private val startDate = Some(now)
  private val endDate = Some(now)

  private val expr = And(OccuranceLimited(99,
    DateBounded(startDate, endDate, Not(t1))),
    OccuranceLimited(88, DateBounded(startDate, endDate, Not(t2))),
    OccuranceLimited(77, DateBounded(startDate, endDate, Not(t3))))

  private val exprConstrainedTerm = Or(t1, query.Constrained(t2, modifiers, valueConstraint))

  @Test
  def testToXml(): Unit = {
    val expected = Utility.trim {
      <queryDefinition>
        <name>blarg</name>
        <expr>
          <term>
            <value>{ t1.value }</value>
            <name>{ t1.name }</name>
          </term>
        </expr>
      </queryDefinition>
    }

    q1.toXmlString should equal(expected.toString)
  }

  @Test
  def testToXmlAllOptionalFields1Span():Unit = {
    val timing = Some(QueryTiming.SameInstanceNum)
    val i2b2Id = "i2b2-id"
    val sqId0 = "i2b2-sq-0"
    val sqId1 = "i2b2-sq-1"
    val i2b2QueryType = "i2b2-query-type"
    val joinColumn = "asldasd"
    val aggregateOperator = "asdasdasdasdasd"
    val operator = "efuzsdkfljsdlfasdasd"
    val spanOperator = "efuzsdkfljsdlfasdasd"
    val spanValue = "asl;dkasd"
    val spanUnits = "light-years"
    val constraints: I2b2SubQueryConstraints = I2b2SubQueryConstraints(operator, I2b2SubQueryConstraint(sqId0, joinColumn, aggregateOperator), I2b2SubQueryConstraint(sqId1, joinColumn, aggregateOperator), Some(I2b2QuerySpan(spanOperator, spanValue, spanUnits)))
    val subQueries = Seq(I2b2QueryDefinition("sq0", t2), I2b2QueryDefinition("sq1", t3))

    val queryDef = I2b2QueryDefinition("blarg", Some(t1), timing, Some(i2b2Id), Some(i2b2QueryType), Seq(constraints), subQueries)

    val expected = Utility.trim {
      <queryDefinition>
        <name>blarg</name>
        <expr>
          <term>
            <value>{ t1.value }</value>
            <name>{ t1.name }</name>
          </term>
        </expr>
        <timing>{ timing.get }</timing>
        <id>{ i2b2Id }</id>
        <type>{ i2b2QueryType }</type>
        <i2b2SubQueryConstraints>
          <i2b2SubQueryConstraint index="0">
            <id>{ sqId0 }</id>
            <joinColumn>{ joinColumn }</joinColumn>
            <aggregateOperator>{ aggregateOperator }</aggregateOperator>
          </i2b2SubQueryConstraint>
          <operator>{ operator }</operator>
          <i2b2SubQueryConstraint index="1">
            <id>{ sqId1 }</id>
            <joinColumn>{ joinColumn }</joinColumn>
            <aggregateOperator>{ aggregateOperator }</aggregateOperator>
          </i2b2SubQueryConstraint>
          <i2b2QuerySpan>
            <operator>{ spanOperator }</operator>
            <value>{ spanValue }</value>
            <units>{ spanUnits }</units>
          </i2b2QuerySpan>
        </i2b2SubQueryConstraints>
        { subQueries.map(_.toXml.head).map(XmlUtil.renameRootTag("subQuery")) }
      </queryDefinition>
    }

    queryDef.toXmlString should equal(expected.toString)
  }

  def now: XMLGregorianCalendar = XmlDateHelper.now

  @Test
  def testFromXml():Unit = {
    val startDate = Some(now)
    val endDate = Some(now)

    val expr = And(OccuranceLimited(99, DateBounded(startDate, endDate, Not(t1))),
      OccuranceLimited(88, DateBounded(startDate, endDate, Not(t2))),
      OccuranceLimited(77, DateBounded(startDate, endDate, Not(t3))))

    val queryDef = I2b2QueryDefinition("foo", expr)

    val unmarshalled = I2b2QueryDefinition.fromXml(queryDef.toXml).get

    unmarshalled should equal(queryDef)
  }

  @Test
  def testFromXmlAllOptionalFields1Span():Unit = {
    val timing = Some(QueryTiming.SameInstanceNum)
    val i2b2Id = "i2b2-id"
    val sqId0 = "i2b2-sq-0"
    val sqId1 = "i2b2-sq-1"
    val i2b2QueryType = "i2b2-query-type"
    val joinColumn = "asldasd"
    val aggregateOperator = "asdasdasdasdasd"
    val operator = "efuzsdkfljsdlfasdasd"
    val spanOperator = "efuzsdkfljsdlfasdasd"
    val spanValue = "asl;dkasd"
    val spanUnits = "light-years"
    val constraints = I2b2SubQueryConstraints(operator, I2b2SubQueryConstraint(sqId0, joinColumn, aggregateOperator), I2b2SubQueryConstraint(sqId1, joinColumn, aggregateOperator), Some(I2b2QuerySpan(spanOperator, spanValue, spanUnits)))
    val subQueries = Seq(I2b2QueryDefinition("sq0", t2), I2b2QueryDefinition("sq1", t3))

    val queryDef = I2b2QueryDefinition("blarg", Some(t1), timing, Some(i2b2Id), Some(i2b2QueryType), Seq(constraints), subQueries)

    val unmarshalled = I2b2QueryDefinition.fromXml(queryDef.toXml).get

    unmarshalled should equal(queryDef)
  }

  // DOES NOT WORK
  @Test
  def testFromXmlAllOptionalFields2Spans(): Unit = {
    val timing = Some(QueryTiming.SameInstanceNum)
    val i2b2Id = "i2b2-id"
    val sqId0 = "i2b2-sq-0"
    val sqId1 = "i2b2-sq-1"
    val i2b2QueryType = "i2b2-query-type"
    val joinColumn = "asldasd"
    val aggregateOperator = "asdasdasdasdasd"
    val operator = "efuzsdkfljsdlfasdasd"
    val spanOperator1 = "aaa"
    val spanValue1 = "bbb"
    val spanUnits1 = "ccc"
    val spanOperator2 = "111"
    val spanValue2 = "222"
    val spanUnits2 = "333"
    val constraints = I2b2SubQueryConstraints(
      operator = operator,
      first = I2b2SubQueryConstraint(sqId0, joinColumn, aggregateOperator),
      second = I2b2SubQueryConstraint(sqId1, joinColumn, aggregateOperator),
      primarySpan = Some(I2b2QuerySpan(spanOperator1, spanValue1, spanUnits1)),
      secondarySpan = Some(I2b2QuerySpan(spanOperator2, spanValue2, spanUnits2)))
    val subQueries = Seq(I2b2QueryDefinition("sq0", t2),
      I2b2QueryDefinition("sq1", t3))

    val queryDef = I2b2QueryDefinition("blarg", Some(t1), timing, Some(i2b2Id), Some(i2b2QueryType), Seq(constraints), subQueries)
    val queryDefXml = queryDef.toXml
    val unmarshalled: I2b2QueryDefinition = I2b2QueryDefinition.fromXml(queryDefXml).get
    unmarshalled should equal(queryDef)
  }

  @Test
  def testFromXmlModifiedTerm():Unit = {
    val expr = Or(t1, query.Constrained(t2, modifiers, valueConstraint))

    val queryDef = I2b2QueryDefinition("foo", expr)

    val unmarshalled = I2b2QueryDefinition.fromXml(queryDef.toXml).get

    unmarshalled should equal(queryDef)
  }

  @Test
  def testFromXmlConstrainedTerm():Unit = {
    val expr = Or(t1, query.Constrained(t2, modifiers, valueConstraint))

    val queryDef = I2b2QueryDefinition("foo", expr)

    val unmarshalled = I2b2QueryDefinition.fromXml(queryDef.toXml).get

    unmarshalled should equal(queryDef)
  }

  @Test
  def testFromPrettyPrintedXml():Unit = {

    val expr = OccuranceLimited(99, And(Not(t1), Or(t2, t3, And(t4, t5))))

    val queryDef = I2b2QueryDefinition("foo", expr)

    val prettyPrinted = XmlUtil.loadString(XmlUtil.prettyPrint(queryDef.toXml.head))

    I2b2QueryDefinition.fromXml(prettyPrinted).get should equal(queryDef)
  }

  private val i2b2Xml: NodeSeq = makeI2b2Xml(QueryTiming.Any)

  private def makeI2b2Xml(timing: QueryTiming): NodeSeq = {
    <query_definition><query_name>foo</query_name><query_timing>{ timing.name }</query_timing><specificity_scale>0</specificity_scale><use_shrine>1</use_shrine>{ I2b2QueryDefinition.toPanels(expr).map(_.toI2b2) }</query_definition>
  }

  private val i2b2XmlConstrainedTerm: NodeSeq = {
    <query_definition><query_name>foo</query_name><specificity_scale>0</specificity_scale><use_shrine>1</use_shrine>{ I2b2QueryDefinition.toPanels(exprConstrainedTerm).map(_.toI2b2) }</query_definition>
  }

  private val i2b2XmlSameFinancialEncounter: NodeSeq = XmlUtil.stripWhitespace {
    <query_definition>
      <query_name>blarg</query_name>
      <query_timing>SAMEVISIT</query_timing>
      <specificity_scale>0</specificity_scale>
      <panel>
        <panel_number>1</panel_number>
        <panel_accuracy_scale>100</panel_accuracy_scale>
        <invert>0</invert>
        <panel_timing>SAMEVISIT</panel_timing>
        <total_item_occurrences>1</total_item_occurrences>
        <item>
          <hlevel>3</hlevel>
          <item_name>{ t7.name }</item_name>
          <item_key>{ t7.value }</item_key>
          <tooltip>Demographic \ Gender \ Male</tooltip>
          <class>ENC</class>
          <item_icon>LA</item_icon>
          <item_is_synonym>false</item_is_synonym>
        </item>
      </panel>
      <panel>
        <panel_number>2</panel_number>
        <panel_accuracy_scale>100</panel_accuracy_scale>
        <invert>0</invert>
        <panel_timing>ANY</panel_timing>
        <total_item_occurrences>1</total_item_occurrences>
        <item>
          <hlevel>3</hlevel>
          <item_name>{ t8.name }</item_name>
          <item_key>{ t8.value }</item_key>
          <tooltip>Demographic \ Gender \ not recorded</tooltip>
          <class>ENC</class>
          <item_icon>MA</item_icon>
          <item_is_synonym>false</item_is_synonym>
        </item>
      </panel>
    </query_definition>
  }

  private val i2b2XmlDefineSeqOfEvents: NodeSeq = XmlUtil.stripWhitespace {
    <query_definition>
      <query_name>blarg</query_name>
      <query_timing>ANY</query_timing>
      <specificity_scale>0</specificity_scale>
      <panel>
        <panel_number>1</panel_number>
        <panel_accuracy_scale>100</panel_accuracy_scale>
        <invert>0</invert>
        <panel_timing>ANY</panel_timing>
        <total_item_occurrences>1</total_item_occurrences>
        <item>
          <hlevel>3</hlevel>
          <item_name>{ t7.name }</item_name>
          <item_key>{ t7.value }</item_key>
          <tooltip>Demographic \ Gender \ Male</tooltip>
          <class>ENC</class>
          <item_icon>LA</item_icon>
          <item_is_synonym>false</item_is_synonym>
        </item>
      </panel>
      <use_shrine>1</use_shrine>
      <subquery_constraint>
        <first_query>
          <query_id>Event 1</query_id>
          <join_column>STARTDATE</join_column>
          <aggregate_operator>ANY</aggregate_operator>
        </first_query>
        <operator>LESS</operator>
        <second_query>
          <query_id>Event 2</query_id>
          <join_column>STARTDATE</join_column>
          <aggregate_operator>ANY</aggregate_operator>
        </second_query>
        <span>
          <operator>span-operator</operator>
          <span_value>span-value</span_value>
          <units>span-units</units>
        </span>
      </subquery_constraint>
      <subquery>
        <query_id>Event 1</query_id>
        <query_type>EVENT</query_type>
        <query_name>Event 1</query_name>
        <query_timing>SAMEINSTANCENUM</query_timing>
        <specificity_scale>0</specificity_scale>
        <panel>
          <panel_number>1</panel_number>
          <panel_accuracy_scale>100</panel_accuracy_scale>
          <invert>0</invert>
          <panel_timing>SAMEINSTANCENUM</panel_timing>
          <total_item_occurrences>1</total_item_occurrences>
          <item>
            <hlevel>3</hlevel>
            <item_name>{ t9.name }</item_name>
            <item_key>{ t9.value }</item_key>
            <tooltip>Diagnoses \ Circulatory system \ Hypertensive disease</tooltip>
            <class>ENC</class>
            <item_icon>FA</item_icon>
            <item_is_synonym>false</item_is_synonym>
          </item>
        </panel>
      </subquery>
      <subquery>
        <query_id>Event 2</query_id>
        <query_type>EVENT</query_type>
        <query_name>Event 2</query_name>
        <query_timing>SAMEINSTANCENUM</query_timing>
        <specificity_scale>0</specificity_scale>
        <panel>
          <panel_number>1</panel_number>
          <panel_accuracy_scale>100</panel_accuracy_scale>
          <invert>0</invert>
          <panel_timing>SAMEINSTANCENUM</panel_timing>
          <total_item_occurrences>1</total_item_occurrences>
          <item>
            <hlevel>3</hlevel>
            <item_name>{ t10.name }</item_name>
            <item_key>{ t10.value }</item_key>
            <tooltip>Diagnoses \ Circulatory system \ Ischemic heart disease</tooltip>
            <class>ENC</class>
            <item_icon>FA</item_icon>
            <item_is_synonym>false</item_is_synonym>
          </item>
        </panel>
      </subquery>
    </query_definition>
  }

  private val i2b2XmlDefineSeqOfEventsNoPanels: NodeSeq = XmlUtil.stripWhitespace {
    <query_definition>
      <query_name>blarg</query_name>
      <query_timing>ANY</query_timing>
      <specificity_scale>0</specificity_scale>
      <subquery_constraint>
        <first_query>
          <query_id>Event 1</query_id>
          <join_column>STARTDATE</join_column>
          <aggregate_operator>ANY</aggregate_operator>
        </first_query>
        <operator>LESS</operator>
        <second_query>
          <query_id>Event 2</query_id>
          <join_column>STARTDATE</join_column>
          <aggregate_operator>ANY</aggregate_operator>
        </second_query>
        <span>
          <operator>span-operator</operator>
          <span_value>span-value</span_value>
          <units>span-units</units>
        </span>
      </subquery_constraint>
      <subquery>
        <query_id>Event 1</query_id>
        <query_type>EVENT</query_type>
        <query_name>Event 1</query_name>
        <query_timing>SAMEINSTANCENUM</query_timing>
        <specificity_scale>0</specificity_scale>
        <panel>
          <panel_number>1</panel_number>
          <panel_accuracy_scale>100</panel_accuracy_scale>
          <invert>0</invert>
          <panel_timing>SAMEINSTANCENUM</panel_timing>
          <total_item_occurrences>1</total_item_occurrences>
          <item>
            <hlevel>3</hlevel>
            <item_name>{ t9.name }</item_name>
            <item_key>{ t9.value }</item_key>
            <tooltip>Diagnoses \ Circulatory system \ Hypertensive disease</tooltip>
            <class>ENC</class>
            <item_icon>FA</item_icon>
            <item_is_synonym>false</item_is_synonym>
          </item>
        </panel>
      </subquery>
      <subquery>
        <query_id>Event 2</query_id>
        <query_type>EVENT</query_type>
        <query_name>Event 2</query_name>
        <query_timing>SAMEINSTANCENUM</query_timing>
        <specificity_scale>0</specificity_scale>
        <panel>
          <panel_number>1</panel_number>
          <panel_accuracy_scale>100</panel_accuracy_scale>
          <invert>0</invert>
          <panel_timing>SAMEINSTANCENUM</panel_timing>
          <total_item_occurrences>1</total_item_occurrences>
          <item>
            <hlevel>3</hlevel>
            <item_name>{ t10.name }</item_name>
            <item_key>{ t10.value }</item_key>
            <tooltip>Diagnoses \ Circulatory system \ Ischemic heart disease</tooltip>
            <class>ENC</class>
            <item_icon>FA</item_icon>
            <item_is_synonym>false</item_is_synonym>
          </item>
        </panel>
      </subquery>
    </query_definition>
  }

  @Test
  def testFromI2b2SameFinancialEncounter(): Unit = {
    val queryDef = I2b2QueryDefinition.fromI2b2(i2b2XmlSameFinancialEncounter).get

    queryDef.name should be("blarg")
    queryDef.expr.get should be(And(WithTiming(PanelTiming.SameVisit, t7), t8))
    queryDef.constraints should be(Seq.empty)
    queryDef.id should be(None)
    queryDef.queryType should be(None)
    queryDef.subQueries should be(Nil)
    queryDef.timing.get should be(QueryTiming.SameVisit)
  }

  @Test
  def testFromI2b2DefineSequenceOfEvents(): Unit = {
    val queryDef = I2b2QueryDefinition.fromI2b2(i2b2XmlDefineSeqOfEvents).get

    queryDef.name should be("blarg")
    queryDef.expr.get should be(t7)

    val expectedConstraints = Seq(I2b2SubQueryConstraints(
      "LESS",
      I2b2SubQueryConstraint("Event 1", "STARTDATE", "ANY"),
      I2b2SubQueryConstraint("Event 2", "STARTDATE", "ANY"),
      Some(I2b2QuerySpan("span-operator", "span-value", "span-units"))))

    queryDef.constraints should be(expectedConstraints)

    queryDef.id should be(None)
    queryDef.queryType should be(None)
    queryDef.timing.get should be(QueryTiming.Any)

    val expectedSubQueries = Seq(
      I2b2QueryDefinition("Event 1", Some(query.WithTiming(PanelTiming.SameInstanceNum, t9)), Some(QueryTiming.SameInstanceNum), Some("Event 1"), Some("EVENT")),
      I2b2QueryDefinition("Event 2", Some(query.WithTiming(PanelTiming.SameInstanceNum, t10)), Some(QueryTiming.SameInstanceNum), Some("Event 2"), Some("EVENT")))

    queryDef.subQueries should be(expectedSubQueries)
  }

  @Test
  def testFromI2b2DefineSequenceOfEventsNoPanels(): Unit = {
    val queryDef = I2b2QueryDefinition.fromI2b2(i2b2XmlDefineSeqOfEventsNoPanels).get

    queryDef.name should be("blarg")
    queryDef.expr should be(None)

    val expectedConstraints = Seq(I2b2SubQueryConstraints(
      "LESS",
      I2b2SubQueryConstraint("Event 1", "STARTDATE", "ANY"),
      I2b2SubQueryConstraint("Event 2", "STARTDATE", "ANY"),
      Some(I2b2QuerySpan("span-operator", "span-value", "span-units"))))

    queryDef.constraints.size should be (1)
    queryDef.constraints should be(expectedConstraints)

    queryDef.id should be(None)
    queryDef.queryType should be(None)
    queryDef.timing.get should be(QueryTiming.Any)

    val expectedSubQueries = Seq(
      I2b2QueryDefinition("Event 1", Some(query.WithTiming(PanelTiming.SameInstanceNum, t9)), Some(QueryTiming.SameInstanceNum), Some("Event 1"), Some("EVENT")),
      I2b2QueryDefinition("Event 2", Some(query.WithTiming(PanelTiming.SameInstanceNum, t10)), Some(QueryTiming.SameInstanceNum), Some("Event 2"), Some("EVENT")))

    queryDef.subQueries should be(expectedSubQueries)
  }

  @Test
  def testFromI2b2String(): Unit = {
    val unmarshalled = I2b2QueryDefinition.fromI2b2(i2b2Xml.toString).get

    unmarshalled.name should equal("foo")
    unmarshalled.expr.get should equal(expr)

    unmarshalled.constraints should be(Seq.empty)
    unmarshalled.id should be(None)
    unmarshalled.queryType should be(None)
    unmarshalled.subQueries should be(Nil)
    unmarshalled.timing.get should be(QueryTiming.Any)
  }

  @Test
  def testFromI2b2StringConstrainedTerm(): Unit = {
    val unmarshalled = I2b2QueryDefinition.fromI2b2(i2b2XmlConstrainedTerm.toString).get

    unmarshalled.name should equal("foo")
    unmarshalled.expr.get should equal(exprConstrainedTerm)

    unmarshalled.constraints should be(Seq.empty)
    unmarshalled.id should be(None)
    unmarshalled.queryType should be(None)
    unmarshalled.subQueries should be(Nil)
    unmarshalled.timing should be(None)
  }

  @Test
  def testFromI2b2(): Unit = {
    for {
      queryTiming <- QueryTiming.values
    } {
      val unmarshalled = I2b2QueryDefinition.fromI2b2(makeI2b2Xml(queryTiming)).get

      unmarshalled.name should equal("foo")
      unmarshalled.expr.get should equal(expr)
      unmarshalled.timing.get should equal(queryTiming)

      unmarshalled.constraints should be(Seq.empty)
      unmarshalled.id should be(None)
      unmarshalled.queryType should be(None)
      unmarshalled.subQueries should be(Nil)
    }
  }

  @Test
  def testFromI2b2ConstrainedTerm():Unit = {
    val unmarshalled = I2b2QueryDefinition.fromI2b2(i2b2XmlConstrainedTerm).get

    unmarshalled.name should equal("foo")
    unmarshalled.expr.get should equal(exprConstrainedTerm)
  }

  @Test
  def testIsAllTerms():Unit = {
    import net.shrine.protocol.i2b2.query.I2b2QueryDefinition.isAllTerms

    isAllTerms(Nil) should be(false)

    isAllTerms(Seq(t1)) should be(true)
    isAllTerms(Seq(t1, t2, t3)) should be(true)

    isAllTerms(Seq(And(t1, t2))) should be(false)
    isAllTerms(Seq(t1, And(), t2)) should be(false)
    isAllTerms(Seq(Not(t1), Not(t2))) should be(false)
  }

  private def doI2b2RoundTrip(xml: NodeSeq): Unit = {
    val queryDef = I2b2QueryDefinition.fromI2b2(xml).get

    val roundTripped = I2b2QueryDefinition.fromI2b2(queryDef.toI2b2).get

    roundTripped should equal(queryDef)
  }

  @Test
  def testToI2b2SameFinancialEncounter(): Unit = doI2b2RoundTrip(i2b2XmlSameFinancialEncounter)

  @Test
  def testToI2b2DefineSequenceOfEvents(): Unit = doI2b2RoundTrip(i2b2XmlDefineSeqOfEvents)

  @Test
  def testToI2b2():Unit = {
    val xml = q1.toI2b2

    xml.head.label should equal("query_definition")

    //query_name    
    (xml \ "query_name").text should equal(q1.name)

    for {
      queryTiming <- QueryTiming.notAny
    } {
      (q1.copy(timing = Option(queryTiming)).toI2b2 \ "query_timing").text should equal(queryTiming.name)
    }

    //query_timing
    (xml \ "query_timing").text should equal(QueryTiming.Any.name)

    //defaults
    (xml \ "specificity_scale").text should equal("0")
    (xml \ "use_shrine").text should equal("1")

    //panels
    import net.shrine.protocol.i2b2.query.I2b2QueryDefinition.toPanels

    (xml \ "panel").toString should equal(toPanels(t1).head.toI2b2.toString)

    val Seq(panel1, panel2) = q1.copy(expr = Some(And(t1, t2))).toI2b2 \\ "panel"

    panel1.toString should equal(toPanels(t1).head.toI2b2.toString)
    panel2.toString should equal(toPanels(t2).head.copy(number = 2).toI2b2.toString)
  }

  @Test
  def testToPanels():Unit = {
    import net.shrine.protocol.i2b2.query.I2b2QueryDefinition.toPanels

    //A plain Term
    {
      val Seq(panel1) = toPanels(t1)

      panel1.number should be(1)
      panel1.isExcluded should be(false)
      panel1.minOccurrences should be(1)
      panel1.start should be(None)
      panel1.end should be(None)
      panel1.terms.toSet should be(Set(t1))
    }

    //WithTiming (default timing)
    {
      val Seq(panel1) = toPanels(query.WithTiming(PanelTiming.Any, t1))

      panel1.number should be(1)
      panel1.isExcluded should be(false)
      panel1.minOccurrences should be(1)
      panel1.start should be(None)
      panel1.end should be(None)
      panel1.terms.toSet should be(Set(t1))
      panel1.timing should equal(PanelTiming.Any)
    }

    //WithTiming (other timing)
    {
      val Seq(panel1) = toPanels(query.WithTiming(PanelTiming.SameInstanceNum, t1))

      panel1.number should be(1)
      panel1.isExcluded should be(false)
      panel1.minOccurrences should be(1)
      panel1.start should be(None)
      panel1.end should be(None)
      panel1.terms.toSet should be(Set(t1))
      panel1.timing should equal(PanelTiming.SameInstanceNum)
    }

    //WithTiming (other timing, more complex)
    {
      val expr = And(t1, query.Constrained(t2, modifiers, valueConstraint))

      import PanelTiming._

      for {
        timing <- Seq(SameInstanceNum, SameVisit)
      } {
        val Seq(panel1, panel2) = toPanels(query.WithTiming(timing, expr))

        panel1.number should be(1)
        panel1.isExcluded should be(false)
        panel1.minOccurrences should be(1)
        panel1.start should be(None)
        panel1.end should be(None)
        panel1.terms.toSet should be(Set(t1))
        panel1.timing should equal(timing)
        panel1.termsWithValueConstraints should equal(Nil)

        panel2.number should be(2)
        panel2.isExcluded should be(false)
        panel2.minOccurrences should be(1)
        panel2.start should be(None)
        panel2.end should be(None)
        panel2.terms.toSet should be(Set.empty)
        panel2.timing should equal(timing)
        panel2.termsWithValueConstraints should equal(Seq(query.Constrained(t2, modifiers, valueConstraint)))
      }
    }

    //WithTiming (more complex, different timings)
    {
      import PanelTiming._

      val expr = And(query.WithTiming(SameInstanceNum, t1), query.WithTiming(SameVisit, t2))

      val Seq(panel1, panel2) = toPanels(expr)

      panel1.number should be(1)
      panel1.isExcluded should be(false)
      panel1.minOccurrences should be(1)
      panel1.start should be(None)
      panel1.end should be(None)
      panel1.terms.toSet should be(Set(t1))
      panel1.timing should equal(SameInstanceNum)
      panel1.termsWithValueConstraints should equal(Nil)

      panel2.number should be(2)
      panel2.isExcluded should be(false)
      panel2.minOccurrences should be(1)
      panel2.start should be(None)
      panel2.end should be(None)
      panel2.terms.toSet should be(Set(t2))
      panel2.timing should equal(SameVisit)
      panel2.termsWithValueConstraints should equal(Nil)
    }

    //Not
    {
      val Seq(panel1) = toPanels(Not(t1))

      panel1.number should be(1)
      panel1.isExcluded should be(true)
      panel1.minOccurrences should be(1)
      panel1.start should be(None)
      panel1.end should be(None)
      panel1.terms.toSet should be(Set(t1))

      //normalized?
      val Seq(panel2) = toPanels(Not(Not(t1)))

      panel2.number should be(1)
      panel2.isExcluded should be(false)
      panel2.minOccurrences should be(1)
      panel2.start should be(None)
      panel2.end should be(None)
      panel2.terms.toSet should be(Set(t1))
    }

    //Or
    {
      //Or'ed Terms give a panel 
      val Seq(panel1) = toPanels(Or(t1, t2, t3))

      panel1.number should be(1)
      panel1.isExcluded should be(false)
      panel1.minOccurrences should be(1)
      panel1.start should be(None)
      panel1.end should be(None)
      panel1.terms.toSet should be(Set(t1, t2, t3))

      //Should blow up on an Or that doesn't contain only Terms
      intercept[IllegalArgumentException] {
        toPanels(Or(t1, Not(t2), t3))
      }

      //empty Or gives no panels
      toPanels(Or()) should be(Nil)

      //normalized?
      val Seq(panel2) = toPanels(Or(t1, Or(t2, t3), Or()))

      panel2.number should be(1)
      panel2.isExcluded should be(false)
      panel2.minOccurrences should be(1)
      panel2.start should be(None)
      panel2.end should be(None)
      panel2.terms.toSet should be(Set(t1, t2, t3))
    }

    //And
    {
      val Seq(panel1, panel2, panel3) = toPanels(And(t1, And(t2, t3)))

      panel1.number should be(1)
      panel1.isExcluded should be(false)
      panel1.minOccurrences should be(1)
      panel1.start should be(None)
      panel1.end should be(None)
      panel1.terms should be(Seq(t1))

      panel2.number should be(2)
      panel2.isExcluded should be(false)
      panel2.minOccurrences should be(1)
      panel2.start should be(None)
      panel2.end should be(None)
      panel2.terms should be(Seq(t2))

      panel3.number should be(3)
      panel3.isExcluded should be(false)
      panel3.minOccurrences should be(1)
      panel3.start should be(None)
      panel3.end should be(None)
      panel3.terms.toSet should be(Set(t3))
    }

    val time = now

    //DateBounded
    {
      val Seq(panel1) = toPanels(DateBounded(Some(time), None, t1))

      panel1.number should be(1)
      panel1.isExcluded should be(false)
      panel1.minOccurrences should be(1)
      panel1.start should be(Some(time))
      panel1.end should be(None)
      panel1.terms.toSet should be(Set(t1))
    }

    {
      val Seq(panel1) = toPanels(DateBounded(None, Some(time), t1))

      panel1.number should be(1)
      panel1.isExcluded should be(false)
      panel1.minOccurrences should be(1)
      panel1.start should be(None)
      panel1.end should be(Some(time))
      panel1.terms.toSet should be(Set(t1))
    }

    {
      val Seq(panel1) = toPanels(DateBounded(Some(time), Some(time), t1))

      panel1.number should be(1)
      panel1.isExcluded should be(false)
      panel1.minOccurrences should be(1)
      panel1.start should be(Some(time))
      panel1.end should be(Some(time))
      panel1.terms.toSet should be(Set(t1))
    }

    //OccuranceLimited
    {
      val Seq(panel1) = toPanels(OccuranceLimited(99, t1))

      panel1.number should be(1)
      panel1.isExcluded should be(false)
      panel1.minOccurrences should be(99)
      panel1.start should be(None)
      panel1.end should be(None)
      panel1.terms.toSet should be(Set(t1))
    }

    //Combo
    {
      val Seq(panel1, panel2, panel3, panel4) = toPanels(DateBounded(Some(time), Some(time), OccuranceLimited(99, And(t1, t2, t3, Not(Or(t4, t5, t6))))))

      panel1.number should be(1)
      panel1.isExcluded should be(false)
      panel1.minOccurrences should be(99)
      panel1.start should be(Some(time))
      panel1.end should be(Some(time))
      panel1.terms should be(Seq(t1))

      panel2.number should be(2)
      panel2.isExcluded should be(false)
      panel2.minOccurrences should be(99)
      panel2.start should be(Some(time))
      panel2.end should be(Some(time))
      panel2.terms should be(Seq(t2))

      panel3.number should be(3)
      panel3.isExcluded should be(false)
      panel3.minOccurrences should be(99)
      panel3.start should be(Some(time))
      panel3.end should be(Some(time))
      panel3.terms should be(Seq(t3))

      panel4.number should be(4)
      panel4.isExcluded should be(true)
      panel4.minOccurrences should be(99)
      panel4.start should be(Some(time))
      panel4.end should be(Some(time))
      panel4.terms.toSet should be(Set(t4, t5, t6))
    }

    //Constrained
    {
      val expr = Or(t1, Constrained(t2, Some(modifiers), None))

      val Seq(panel1) = toPanels(expr)

      panel1.number should be(1)
      panel1.isExcluded should be(false)
      panel1.minOccurrences should be(1)
      panel1.start should be(None)
      panel1.end should be(None)
      panel1.terms should be(Seq(t1))
      panel1.termsWithValueConstraints should equal(Seq(Constrained(t2, Some(modifiers), None)))
    }

    //Constrained ONLY
    {
      val expr = Constrained(t2, None, Some(valueConstraint))

      val Seq(panel1) = toPanels(expr)

      panel1.number should be(1)
      panel1.isExcluded should be(false)
      panel1.minOccurrences should be(1)
      panel1.start should be(None)
      panel1.end should be(None)
      panel1.terms should be(Nil)
      panel1.termsWithValueConstraints should equal(Seq(Constrained(t2, None, Some(valueConstraint))))
    }
  }

  //A failing case reported by Ben C.
  private val failingI2b2Xml = {
    <query_definition>
      <query_name>(t) (493.90) Asthma(250.00) Diabet@15:32:58</query_name>
      <query_timing>ANY</query_timing>
      <specificity_scale>0</specificity_scale>
      <use_shrine>1</use_shrine>
      <subquery_constraint>
        <first_query>
          <query_id>Event 1</query_id>
          <join_column>STARTDATE</join_column>
          <aggregate_operator>ANY</aggregate_operator>
        </first_query>
        <operator>LESS</operator>
        <second_query>
          <query_id>Event 2</query_id>
          <join_column>STARTDATE</join_column>
          <aggregate_operator>ANY</aggregate_operator>
        </second_query>
        <span>
          <operator>GREATEREQUAL</operator>
          <span_value>365</span_value>
          <units>DAY</units>
        </span>
      </subquery_constraint>
      <subquery>
        <query_id>Event 1</query_id>
        <query_type>EVENT</query_type>
        <query_name>Event 1</query_name>
        <query_timing>SAMEINSTANCENUM</query_timing>
        <specificity_scale>0</specificity_scale>
        <use_shrine>1</use_shrine>
        <panel>
          <panel_number>1</panel_number>
          <panel_accuracy_scale>100</panel_accuracy_scale>
          <invert>0</invert>
          <panel_timing>SAMEINSTANCENUM</panel_timing>
          <total_item_occurrences>1</total_item_occurrences>
          <item>
            <hlevel>6</hlevel>
            <item_name>(493.90) Asthma, unspecified type, unspecified</item_name>
            <item_key>\\SHRINE\SHRINE\Diagnoses\Diseases of the respiratory system (460-519.99)\Chronic obstructive pulmonary disease and allied conditions (490-496.99)\Asthma (493)\Asthma, unspecified (493.9)\(493.90) Asthma, unspecified type, unspecified\</item_key>
            <tooltip>Diagnoses\Diseases of the respiratory system (460-519.99)\Chronic obstructive pulmonary disease and allied conditions (490-496.99)\Asthma (493)\Asthma, unspecified (493.9)\(493.90) Asthma, unspecified type, unspecified\</tooltip>
            <class>ENC</class>
            <item_icon>LA</item_icon>
            <item_is_synonym>false</item_is_synonym>
          </item>
          <item>
            <hlevel>6</hlevel>
            <item_name>(493.91) Asthma, unspecified type, with status asthmaticus</item_name>
            <item_key>\\SHRINE\SHRINE\Diagnoses\Diseases of the respiratory system (460-519.99)\Chronic obstructive pulmonary disease and allied conditions (490-496.99)\Asthma (493)\Asthma, unspecified (493.9)\(493.91) Asthma, unspecified type, with status asthmaticus\</item_key>
            <tooltip>Diagnoses\Diseases of the respiratory system (460-519.99)\Chronic obstructive pulmonary disease and allied conditions (490-496.99)\Asthma (493)\Asthma, unspecified (493.9)\(493.91) Asthma, unspecified type, with status asthmaticus\</tooltip>
            <class>ENC</class>
            <item_icon>LA</item_icon>
            <item_is_synonym>false</item_is_synonym>
          </item>
          <item>
            <hlevel>6</hlevel>
            <item_name>(493.92) Asthma, unspecified type, with (acute) exacerbation</item_name>
            <item_key>\\SHRINE\SHRINE\Diagnoses\Diseases of the respiratory system (460-519.99)\Chronic obstructive pulmonary disease and allied conditions (490-496.99)\Asthma (493)\Asthma, unspecified (493.9)\(493.92) Asthma, unspecified type, with (acute) exacerbation\</item_key>
            <tooltip>Diagnoses\Diseases of the respiratory system (460-519.99)\Chronic obstructive pulmonary disease and allied conditions (490-496.99)\Asthma (493)\Asthma, unspecified (493.9)\(493.92) Asthma, unspecified type, with (acute) exacerbation\</tooltip>
            <class>ENC</class>
            <item_icon>LA</item_icon>
            <item_is_synonym>false</item_is_synonym>
          </item>
        </panel>
      </subquery>
      <subquery>
        <query_id>Event 2</query_id>
        <query_type>EVENT</query_type>
        <query_name>Event 2</query_name>
        <query_timing>SAMEINSTANCENUM</query_timing>
        <specificity_scale>0</specificity_scale>
        <use_shrine>1</use_shrine>
        <panel>
          <panel_number>1</panel_number>
          <panel_accuracy_scale>100</panel_accuracy_scale>
          <invert>0</invert>
          <panel_timing>SAMEINSTANCENUM</panel_timing>
          <total_item_occurrences>1</total_item_occurrences>
          <item>
            <hlevel>6</hlevel>
            <item_name>(250.00) Diabetes mellitus without mention of complication, type II or unspecified type, not stated as uncontrolled</item_name>
            <item_key>\\SHRINE\SHRINE\Diagnoses\Endocrine, nutritional and metabolic diseases, and immunity disorders (240-279.99)\Diseases of other endocrine glands (249-259.99)\Diabetes mellitus (250)\Diabetes mellitus without mention of complication (250.0)\(250.00) Diabetes mellitus without mention of complication, type II or unspecified type, not stated as uncontrolled\</item_key>
            <tooltip>Diagnoses\Endocrine, nutritional and metabolic diseases, and immunity disorders (240-279.99)\Diseases of other endocrine glands (249-259.99)\Diabetes mellitus (250)\Diabetes mellitus without mention of complication (250.0)\(250.00) Diabetes mellitus without mention of complication, type II or unspecified type, not stated as uncontrolled\</tooltip>
            <class>ENC</class>
            <item_icon>LA</item_icon>
            <item_is_synonym>false</item_is_synonym>
          </item>
          <item>
            <hlevel>6</hlevel>
            <item_name>(530.81) Esophageal reflux</item_name>
            <item_key>\\SHRINE\SHRINE\Diagnoses\Diseases of the digestive system (520-579.99)\Diseases of esophagus, stomach, and duodenum (530-539.99)\Diseases of esophagus (530)\Other specified disorders of esophagus (530.8)\(530.81) Esophageal reflux\</item_key>
            <tooltip>Diagnoses\Diseases of the digestive system (520-579.99)\Diseases of esophagus, stomach, and duodenum (530-539.99)\Diseases of esophagus (530)\Other specified disorders of esophagus (530.8)\(530.81) Esophageal reflux\</tooltip>
            <class>ENC</class>
            <item_icon>LA</item_icon>
            <item_is_synonym>false</item_is_synonym>
          </item>
        </panel>
      </subquery>
    </query_definition>
  }
  
  private val (expectedSubQuery0, expectedSubQuery1) = {
    val expectedSubQuery0 = I2b2QueryDefinition(
      "Event 1",
      Some(query.WithTiming(PanelTiming.SameInstanceNum, Or(
        Term("""\\SHRINE\SHRINE\Diagnoses\Diseases of the respiratory system (460-519.99)\Chronic obstructive pulmonary disease and allied conditions (490-496.99)\Asthma (493)\Asthma, unspecified (493.9)\(493.90) Asthma, unspecified type, unspecified\""", "(493.90) Asthma, unspecified type, unspecified"),
        Term("""\\SHRINE\SHRINE\Diagnoses\Diseases of the respiratory system (460-519.99)\Chronic obstructive pulmonary disease and allied conditions (490-496.99)\Asthma (493)\Asthma, unspecified (493.9)\(493.91) Asthma, unspecified type, with status asthmaticus\""", "(493.91) Asthma, unspecified type, with status asthmaticus"),
        Term("""\\SHRINE\SHRINE\Diagnoses\Diseases of the respiratory system (460-519.99)\Chronic obstructive pulmonary disease and allied conditions (490-496.99)\Asthma (493)\Asthma, unspecified (493.9)\(493.92) Asthma, unspecified type, with (acute) exacerbation\""", "(493.92) Asthma, unspecified type, with (acute) exacerbation")))),
      Some(QueryTiming.SameInstanceNum),
      Some("Event 1"),
      Some("EVENT"))

    val expectedSubQuery1 = I2b2QueryDefinition(
      "Event 2",
      Some(query.WithTiming(PanelTiming.SameInstanceNum, Or(
        Term("""\\SHRINE\SHRINE\Diagnoses\Endocrine, nutritional and metabolic diseases, and immunity disorders (240-279.99)\Diseases of other endocrine glands (249-259.99)\Diabetes mellitus (250)\Diabetes mellitus without mention of complication (250.0)\(250.00) Diabetes mellitus without mention of complication, type II or unspecified type, not stated as uncontrolled\""", "(250.00) Diabetes mellitus without mention of complication, type II or unspecified type, not stated as uncontrolled"),
        Term("""\\SHRINE\SHRINE\Diagnoses\Diseases of the digestive system (520-579.99)\Diseases of esophagus, stomach, and duodenum (530-539.99)\Diseases of esophagus (530)\Other specified disorders of esophagus (530.8)\(530.81) Esophageal reflux\""", """(530.81) Esophageal reflux""")))),
      Some(QueryTiming.SameInstanceNum),
      Some("Event 2"),
      Some("EVENT"))

    (expectedSubQuery0, expectedSubQuery1)
  }

  @Test
  def testFromI2b2FailingXmlQueryWithSubQueries(): Unit = {
    import net.shrine.protocol.i2b2.query.I2b2QueryDefinition.fromI2b2

    val queryDef = fromI2b2(failingI2b2Xml).get

    queryDef.name should equal("(t) (493.90) Asthma(250.00) Diabet@15:32:58")
    queryDef.timing.get should equal(QueryTiming.Any)
    queryDef.constraints should equal {Seq(
      I2b2SubQueryConstraints(
        "LESS",
        I2b2SubQueryConstraint("Event 1", "STARTDATE", "ANY"),
        I2b2SubQueryConstraint("Event 2", "STARTDATE", "ANY"),
        Some(I2b2QuerySpan("GREATEREQUAL", "365", "DAY")))
    )}

    queryDef.subQueries should equal(Seq(expectedSubQuery0, expectedSubQuery1))
  }

  @Test
  def testXmlRoundTripFailingXmlQueryWithSubQueries(): Unit = {
    val expected = I2b2QueryDefinition(
      "(t) (493.90) Asthma(250.00) Diabet@15:32:58",
      expr = None,
      timing = Some(QueryTiming.Any),
      constraints = Seq(I2b2SubQueryConstraints(
        "LESS",
        I2b2SubQueryConstraint("Event 1", "STARTDATE", "ANY"),
        I2b2SubQueryConstraint("Event 2", "STARTDATE", "ANY"),
        Some(I2b2QuerySpan("GREATEREQUAL", "365", "DAY")))),
      subQueries = Seq(expectedSubQuery0, expectedSubQuery1))

    //Shrine format
    val roundTripped = I2b2QueryDefinition.fromXml(expected.toXml).get

    roundTripped should equal(expected)
  }
  
  @Test
  def testFromI2b2ValueQuery(): Unit = {
    val term = Term("foo", "Sodium SerPl-sCnc")
    
    val i2b2Xml = {
      <query_definition>
        <query_name>Sodium SerPl-sC@12:01:11</query_name>
        <query_timing>ANY</query_timing>
        <specificity_scale>0</specificity_scale>
        <panel>
          <panel_number>1</panel_number>
          <panel_accuracy_scale>100</panel_accuracy_scale>
          <invert>0</invert>
          <panel_timing>ANY</panel_timing>
          <total_item_occurrences>1</total_item_occurrences>
          <item>
            <hlevel>5</hlevel>
            <item_name>Sodium SerPl-sCnc</item_name>
            <item_key>{ term.value }</item_key>
            <tooltip>Labs\LP31388-9\LP19403-2\LP15099-2\2951-2\</tooltip>
            <class>ENC</class>
            <item_icon>LA</item_icon>
            <item_is_synonym>false</item_is_synonym>
            <constrain_by_value>
              <value_type>NUMBER</value_type>
              <value_unit_of_measure>mmol/L</value_unit_of_measure>
              <value_operator>EQ</value_operator>
              <value_constraint>130</value_constraint>
            </constrain_by_value>
          </item>
        </panel>
      </query_definition>
    }
    
    val queryDef = I2b2QueryDefinition.fromI2b2(i2b2Xml).get
    
    queryDef.name should equal("Sodium SerPl-sC@12:01:11")
    queryDef.expr should not be None
    
    val expr = queryDef.expr.get
    
    val expected = Constrained(term, None, Some(ValueConstraint("NUMBER", Some("mmol/L"), "EQ", "130")))
    
    expr should equal(expected)
  }

  @Test
  def testTwoConceptGroupsFromV2():Unit = {
    val expectedXml: Node = XmlUtil.surroundWith("i2b2"){
      <query_definition>
        <query_name>Men With Hypertension</query_name>
        <query_timing>ANY</query_timing>
        <specificity_scale>0</specificity_scale>
        <use_shrine>1</use_shrine>
        <panel>
          <panel_number>1</panel_number>
          <panel_accuracy_scale>100</panel_accuracy_scale>
          <invert>0</invert>
          <panel_timing>ANY</panel_timing>
          <total_item_occurrences>1</total_item_occurrences>
          <item>
            <hlevel>0</hlevel>
            <item_name>45-55</item_name>
            <item_key>/path/to/middle/aged</item_key>
            <tooltip>/path/to/middle/aged</tooltip>
            <class>ENC</class>
            <constrain_by_date></constrain_by_date>
            <item_icon>LA</item_icon>
            <item_is_synonym>false</item_is_synonym>
          </item>
        </panel>
        <panel>
          <panel_number>2</panel_number>
          <panel_accuracy_scale>100</panel_accuracy_scale>
          <invert>0</invert>
          <panel_timing>ANY</panel_timing>
          <total_item_occurrences>1</total_item_occurrences>
          <item>
            <hlevel>0</hlevel>
            <item_name>male</item_name>
            <item_key>/path/to/male</item_key>
            <tooltip>/path/to/male</tooltip>
            <class>ENC</class>
            <constrain_by_date></constrain_by_date>
            <item_icon>LA</item_icon>
            <item_is_synonym>false</item_is_synonym>
          </item>
        </panel>
        <panel>
          <panel_number>3</panel_number>
          <panel_accuracy_scale>100</panel_accuracy_scale>
          <invert>0</invert>
          <panel_timing>ANY</panel_timing>
          <total_item_occurrences>1</total_item_occurrences>
          <item>
            <hlevel>0</hlevel>
            <item_name>hypertension Diagnosis</item_name>
            <item_key>/path/to/hypertension/diagnosis</item_key>
            <tooltip>/path/to/hypertension/diagnosis</tooltip>
            <class>ENC</class>
            <constrain_by_date></constrain_by_date>
            <item_icon>LA</item_icon>
            <item_is_synonym>false</item_is_synonym>
          </item>
          <item>
            <hlevel>0</hlevel>
            <item_name>hypertension Test</item_name>
            <item_key>/path/to/hypertension/test</item_key>
            <tooltip>/path/to/hypertension/test</tooltip>
            <class>ENC</class>
            <constrain_by_date></constrain_by_date>
            <constrain_by_value>
              <value_type>FLAG</value_type>
              <value_operator>EQ</value_operator>
              <value_constraint>H</value_constraint>
            </constrain_by_value>
            <item_icon>LA</item_icon>
            <item_is_synonym>false</item_is_synonym>
          </item>
          <item>
            <hlevel>0</hlevel>
            <item_name>hypertension meds</item_name>
            <item_key>/path/to/hypertension/meds</item_key>
            <tooltip>/path/to/hypertension/meds</tooltip>
            <class>ENC</class>
            <constrain_by_date></constrain_by_date>
            <constrain_by_value>
              <value_type>NUMBER</value_type>
              <value_operator>BETWEEN</value_operator>
              <value_constraint>3.0 and 3000.0</value_constraint>
            </constrain_by_value>
            <item_icon>LA</item_icon>
            <item_is_synonym>false</item_is_synonym>
          </item>
        </panel>
      </query_definition>
    }

    val i2b2Query: I2b2QueryDefinition = I2b2QueryDefinition.fromShrineV2(QueryDefinitionTest.twoConceptGroupQuery)
    val resultXml:Node = XmlUtil.surroundWith("i2b2")(i2b2Query.toI2b2)

    assertResult(XmlUtil.prettyPrint(expectedXml))(XmlUtil.prettyPrint(resultXml))
  }

  @Test
  def testSameEncounterQueryFromV2(): Unit = {
    val expectedXml: Node = XmlUtil.surroundWith("i2b2") {
      <query_definition>
        <query_name>Two Therapies Same Encounter</query_name>
        <query_timing>ANY</query_timing>
        <specificity_scale>0</specificity_scale>
        <use_shrine>1</use_shrine>
        <panel>
          <panel_number>1</panel_number>
          <panel_accuracy_scale>100</panel_accuracy_scale>
          <invert>0</invert>
          <panel_timing>SAMEVISIT</panel_timing>
          <total_item_occurrences>1</total_item_occurrences>
          <item>
            <hlevel>0</hlevel>
            <item_name>snake oil</item_name>
            <item_key>/path/to/snake/oil</item_key>
            <tooltip>/path/to/snake/oil</tooltip>
            <class>ENC</class>
            <constrain_by_date></constrain_by_date>
            <item_icon>LA</item_icon>
            <item_is_synonym>false</item_is_synonym>
          </item>
        </panel>
        <panel>
          <panel_number>2</panel_number>
          <panel_accuracy_scale>100</panel_accuracy_scale>
          <invert>0</invert>
          <panel_timing>SAMEVISIT</panel_timing>
          <total_item_occurrences>1</total_item_occurrences>
          <item>
            <hlevel>0</hlevel>
            <item_name>bee stings</item_name>
            <item_key>/path/to/be/stings</item_key>
            <tooltip>/path/to/be/stings</tooltip>
            <class>ENC</class>
            <constrain_by_date></constrain_by_date>
            <item_icon>LA</item_icon>
            <item_is_synonym>false</item_is_synonym>
          </item>
        </panel>
      </query_definition>
    }

    val i2b2Query: I2b2QueryDefinition = I2b2QueryDefinition.fromShrineV2(QueryDefinitionTest.sameEncounterQuery)
    val resultXml: Node = XmlUtil.surroundWith("i2b2")(i2b2Query.toI2b2)

    assertResult(XmlUtil.prettyPrint(expectedXml))(XmlUtil.prettyPrint(resultXml))
  }

  @Test
  def testSameInstanceQueryFromV2(): Unit = {
    val expectedXml: Node = XmlUtil.surroundWith("i2b2") {
      <query_definition>
        <query_name>Two Therapies Same Instance</query_name>
        <query_timing>ANY</query_timing>
        <specificity_scale>0</specificity_scale>
        <use_shrine>1</use_shrine>
        <panel>
          <panel_number>1</panel_number>
          <panel_accuracy_scale>100</panel_accuracy_scale>
          <invert>0</invert>
          <panel_timing>SAMEINSTANCENUM</panel_timing>
          <total_item_occurrences>1</total_item_occurrences>
          <item>
            <hlevel>0</hlevel>
            <item_name>snake oil</item_name>
            <item_key>/path/to/snake/oil</item_key>
            <tooltip>/path/to/snake/oil</tooltip>
            <class>ENC</class>
            <constrain_by_date></constrain_by_date>
            <item_icon>LA</item_icon>
            <item_is_synonym>false</item_is_synonym>
          </item>
        </panel>
        <panel>
          <panel_number>2</panel_number>
          <panel_accuracy_scale>100</panel_accuracy_scale>
          <invert>0</invert>
          <panel_timing>SAMEINSTANCENUM</panel_timing>
          <total_item_occurrences>1</total_item_occurrences>
          <item>
            <hlevel>0</hlevel>
            <item_name>bee stings</item_name>
            <item_key>/path/to/be/stings</item_key>
            <tooltip>/path/to/be/stings</tooltip>
            <class>ENC</class>
            <constrain_by_date></constrain_by_date>
            <item_icon>LA</item_icon>
            <item_is_synonym>false</item_is_synonym>
          </item>
        </panel>
      </query_definition>
    }

    val i2b2Query: I2b2QueryDefinition = I2b2QueryDefinition.fromShrineV2(QueryDefinitionTest.sameInstanceQuery)
    val resultXml: Node = XmlUtil.surroundWith("i2b2")(i2b2Query.toI2b2)

    assertResult(XmlUtil.prettyPrint(expectedXml))(XmlUtil.prettyPrint(resultXml))
  }
  @Test
  def testTwoEventTimelineFromV2():Unit = {
    val expectedXml: Node = XmlUtil.surroundWith("i2b2"){
      <query_definition>
        <query_name>Men With Hypertension Who Got Better</query_name>
        <query_timing>ANY</query_timing>
        <specificity_scale>0</specificity_scale>
        <use_shrine>1</use_shrine>
        <panel>
          <panel_number>1</panel_number>
          <panel_accuracy_scale>100</panel_accuracy_scale>
          <invert>0</invert>
          <panel_timing>ANY</panel_timing>
          <total_item_occurrences>1</total_item_occurrences>
          <item>
            <hlevel>0</hlevel>
            <item_name>45-55</item_name>
            <item_key>/path/to/middle/aged</item_key>
            <tooltip>/path/to/middle/aged</tooltip>
            <class>ENC</class>
            <constrain_by_date></constrain_by_date>
            <item_icon>LA</item_icon>
            <item_is_synonym>false</item_is_synonym>
          </item>
        </panel>
        <panel>
          <panel_number>2</panel_number>
          <panel_accuracy_scale>100</panel_accuracy_scale>
          <invert>0</invert>
          <panel_timing>ANY</panel_timing>
          <total_item_occurrences>1</total_item_occurrences>
          <item>
            <hlevel>0</hlevel>
            <item_name>male</item_name>
            <item_key>/path/to/male</item_key>
            <tooltip>/path/to/male</tooltip>
            <class>ENC</class>
            <constrain_by_date></constrain_by_date>
            <item_icon>LA</item_icon>
            <item_is_synonym>false</item_is_synonym>
          </item>
        </panel>
        <subquery_constraint>
          <first_query>
            <query_id>Timeline 0</query_id>
            <join_column>STARTDATE</join_column>
            <aggregate_operator>ANY</aggregate_operator>
          </first_query>
          <operator>LESS</operator>
          <second_query>
            <query_id>Timeline 1</query_id>
            <join_column>STARTDATE</join_column>
            <aggregate_operator>ANY</aggregate_operator>
          </second_query>
        </subquery_constraint>
        <subquery>
          <query_id>Timeline 0</query_id>
          <query_type>EVENT</query_type>
          <query_name>Timeline 0</query_name>
          <query_timing>SAMEINSTANCENUM</query_timing>
          <specificity_scale>0</specificity_scale>
          <use_shrine>1</use_shrine>
          <panel>
            <panel_number>1</panel_number>
            <panel_accuracy_scale>100</panel_accuracy_scale>
            <invert>0</invert>
            <panel_timing>SAMEINSTANCENUM</panel_timing>
            <total_item_occurrences>1</total_item_occurrences>
            <item>
              <hlevel>0</hlevel>
              <item_name>hypertension Diagnosis</item_name>
              <item_key>/path/to/hypertension/diagnosis</item_key>
              <tooltip>/path/to/hypertension/diagnosis</tooltip>
              <class>ENC</class>
              <constrain_by_date></constrain_by_date>
              <item_icon>LA</item_icon>
              <item_is_synonym>false</item_is_synonym>
            </item>
            <item>
              <hlevel>0</hlevel>
              <item_name>hypertension Test</item_name>
              <item_key>/path/to/hypertension/test</item_key>
              <tooltip>/path/to/hypertension/test</tooltip>
              <class>ENC</class>
              <constrain_by_date></constrain_by_date>
              <constrain_by_value>
                <value_type>FLAG</value_type>
                <value_operator>EQ</value_operator>
                <value_constraint>H</value_constraint>
              </constrain_by_value>
              <item_icon>LA</item_icon>
              <item_is_synonym>false</item_is_synonym>
            </item>
            <item>
              <hlevel>0</hlevel>
              <item_name>hypertension meds</item_name>
              <item_key>/path/to/hypertension/meds</item_key>
              <tooltip>/path/to/hypertension/meds</tooltip>
              <class>ENC</class>
              <constrain_by_date></constrain_by_date>
              <constrain_by_value>
                <value_type>NUMBER</value_type>
                <value_operator>BETWEEN</value_operator>
                <value_constraint>3.0 and 3000.0</value_constraint>
              </constrain_by_value>
              <item_icon>LA</item_icon>
              <item_is_synonym>false</item_is_synonym>
            </item>
          </panel>
        </subquery>
        <subquery>
          <query_id>Timeline 1</query_id>
          <query_type>EVENT</query_type>
          <query_name>Timeline 1</query_name>
          <query_timing>SAMEINSTANCENUM</query_timing>
          <specificity_scale>0</specificity_scale>
          <use_shrine>1</use_shrine>
          <panel>
            <panel_number>1</panel_number>
            <panel_accuracy_scale>100</panel_accuracy_scale>
            <invert>1</invert>
            <panel_timing>SAMEINSTANCENUM</panel_timing>
            <total_item_occurrences>1</total_item_occurrences>
            <item>
              <hlevel>0</hlevel>
              <item_name>hypertension Diagnosis</item_name>
              <item_key>/path/to/hypertension/diagnosis</item_key>
              <tooltip>/path/to/hypertension/diagnosis</tooltip>
              <class>ENC</class>
              <constrain_by_date></constrain_by_date>
              <item_icon>LA</item_icon>
              <item_is_synonym>false</item_is_synonym>
            </item>
            <item>
              <hlevel>0</hlevel>
              <item_name>hypertension Test</item_name>
              <item_key>/path/to/hypertension/test</item_key>
              <tooltip>/path/to/hypertension/test</tooltip>
              <class>ENC</class>
              <constrain_by_date></constrain_by_date>
              <constrain_by_value>
                <value_type>NUMBER</value_type>
                <value_unit_of_measure>hyperOs</value_unit_of_measure>
                <value_operator>LE</value_operator>
                <value_constraint>42.0</value_constraint>
              </constrain_by_value>
              <item_icon>LA</item_icon>
              <item_is_synonym>false</item_is_synonym>
            </item>
            <item>
              <hlevel>0</hlevel>
              <item_name>hypertension meds</item_name>
              <item_key>/path/to/hypertension/meds</item_key>
              <tooltip>/path/to/hypertension/meds</tooltip>
              <class>ENC</class>
              <constrain_by_date></constrain_by_date>
              <constrain_by_value>
                <value_type>NUMBER</value_type>
                <value_operator>BETWEEN</value_operator>
                <value_constraint>3.0 and 3000.0</value_constraint>
              </constrain_by_value>
              <item_icon>LA</item_icon>
              <item_is_synonym>false</item_is_synonym>
            </item>
          </panel>
        </subquery>
      </query_definition>
    }

    val i2b2Query: I2b2QueryDefinition = I2b2QueryDefinition.fromShrineV2(QueryDefinitionTest.twoEventTimelineQuery)
    val resultXml:Node = XmlUtil.surroundWith("i2b2")(i2b2Query.toI2b2)

    assertResult(XmlUtil.prettyPrint(expectedXml))(XmlUtil.prettyPrint(resultXml))
  }

  @Test
  def testThreeEventTimelineFromV2():Unit = {
    val i2b2Query: I2b2QueryDefinition = I2b2QueryDefinition.fromShrineV2(QueryDefinitionTest.threeEventTimelineQuery)
    val resultXml:Node = XmlUtil.surroundWith("i2b2")(i2b2Query.toI2b2)

    val expectedXml: Node = XmlUtil.surroundWith("i2b2"){
      <query_definition>
        <query_name>Men With Hypertension Who Took Snake Oil And Got Better</query_name>
        <query_timing>ANY</query_timing>
        <specificity_scale>0</specificity_scale>
        <use_shrine>1</use_shrine>
        <panel>
          <panel_number>1</panel_number>
          <panel_accuracy_scale>100</panel_accuracy_scale>
          <invert>0</invert>
          <panel_timing>ANY</panel_timing>
          <total_item_occurrences>1</total_item_occurrences>
          <item>
            <hlevel>0</hlevel>
            <item_name>45-55</item_name>
            <item_key>/path/to/middle/aged</item_key>
            <tooltip>/path/to/middle/aged</tooltip>
            <class>ENC</class>
            <constrain_by_date></constrain_by_date>
            <item_icon>LA</item_icon>
            <item_is_synonym>false</item_is_synonym>
          </item>
        </panel>
        <panel>
          <panel_number>2</panel_number>
          <panel_accuracy_scale>100</panel_accuracy_scale>
          <invert>0</invert>
          <panel_timing>ANY</panel_timing>
          <total_item_occurrences>1</total_item_occurrences>
          <item>
            <hlevel>0</hlevel>
            <item_name>male</item_name>
            <item_key>/path/to/male</item_key>
            <tooltip>/path/to/male</tooltip>
            <class>ENC</class>
            <constrain_by_date></constrain_by_date>
            <item_icon>LA</item_icon>
            <item_is_synonym>false</item_is_synonym>
          </item>
        </panel>
        <subquery_constraint>
          <first_query>
            <query_id>Timeline 0</query_id>
            <join_column>STARTDATE</join_column>
            <aggregate_operator>ANY</aggregate_operator>
          </first_query>
          <operator>LESS</operator>
          <second_query>
            <query_id>Timeline 1</query_id>
            <join_column>STARTDATE</join_column>
            <aggregate_operator>ANY</aggregate_operator>
          </second_query>
        </subquery_constraint>
        <subquery_constraint>
          <first_query>
            <query_id>Timeline 1</query_id>
            <join_column>STARTDATE</join_column>
            <aggregate_operator>ANY</aggregate_operator>
          </first_query>
          <operator>LESS</operator>
          <second_query>
            <query_id>Timeline 2</query_id>
            <join_column>STARTDATE</join_column>
            <aggregate_operator>ANY</aggregate_operator>
          </second_query>
        </subquery_constraint>
        <subquery>
          <query_id>Timeline 0</query_id>
          <query_type>EVENT</query_type>
          <query_name>Timeline 0</query_name>
          <query_timing>SAMEINSTANCENUM</query_timing>
          <specificity_scale>0</specificity_scale>
          <use_shrine>1</use_shrine>
          <panel>
            <panel_number>1</panel_number>
            <panel_accuracy_scale>100</panel_accuracy_scale>
            <invert>0</invert>
            <panel_timing>SAMEINSTANCENUM</panel_timing>
            <total_item_occurrences>1</total_item_occurrences>
            <item>
              <hlevel>0</hlevel>
              <item_name>hypertension Diagnosis</item_name>
              <item_key>/path/to/hypertension/diagnosis</item_key>
              <tooltip>/path/to/hypertension/diagnosis</tooltip>
              <class>ENC</class>
              <constrain_by_date></constrain_by_date>
              <item_icon>LA</item_icon>
              <item_is_synonym>false</item_is_synonym>
            </item>
            <item>
              <hlevel>0</hlevel>
              <item_name>hypertension Test</item_name>
              <item_key>/path/to/hypertension/test</item_key>
              <tooltip>/path/to/hypertension/test</tooltip>
              <class>ENC</class>
              <constrain_by_date></constrain_by_date>
              <constrain_by_value>
                <value_type>FLAG</value_type>
                <value_operator>EQ</value_operator>
                <value_constraint>H</value_constraint>
              </constrain_by_value>
              <item_icon>LA</item_icon>
              <item_is_synonym>false</item_is_synonym>
            </item>
            <item>
              <hlevel>0</hlevel>
              <item_name>hypertension meds</item_name>
              <item_key>/path/to/hypertension/meds</item_key>
              <tooltip>/path/to/hypertension/meds</tooltip>
              <class>ENC</class>
              <constrain_by_date></constrain_by_date>
              <constrain_by_value>
                <value_type>NUMBER</value_type>
                <value_operator>BETWEEN</value_operator>
                <value_constraint>3.0 and 3000.0</value_constraint>
              </constrain_by_value>
              <item_icon>LA</item_icon>
              <item_is_synonym>false</item_is_synonym>
            </item>
          </panel>
        </subquery>
        <subquery>
          <query_id>Timeline 1</query_id>
          <query_type>EVENT</query_type>
          <query_name>Timeline 1</query_name>
          <query_timing>SAMEINSTANCENUM</query_timing>
          <specificity_scale>0</specificity_scale>
          <use_shrine>1</use_shrine>
          <panel>
            <panel_number>1</panel_number>
            <panel_accuracy_scale>100</panel_accuracy_scale>
            <invert>0</invert>
            <panel_timing>SAMEINSTANCENUM</panel_timing>
            <total_item_occurrences>1</total_item_occurrences>
            <item>
              <hlevel>0</hlevel>
              <item_name>snake oil</item_name>
              <item_key>/path/to/snake/oil</item_key>
              <tooltip>/path/to/snake/oil</tooltip>
              <class>ENC</class>
              <constrain_by_date></constrain_by_date>
              <item_icon>LA</item_icon>
              <item_is_synonym>false</item_is_synonym>
            </item>
          </panel>
        </subquery>
        <subquery>
          <query_id>Timeline 2</query_id>
          <query_type>EVENT</query_type>
          <query_name>Timeline 2</query_name>
          <query_timing>SAMEINSTANCENUM</query_timing>
          <specificity_scale>0</specificity_scale>
          <use_shrine>1</use_shrine>
          <panel>
            <panel_number>1</panel_number>
            <panel_accuracy_scale>100</panel_accuracy_scale>
            <invert>1</invert>
            <panel_timing>SAMEINSTANCENUM</panel_timing>
            <total_item_occurrences>1</total_item_occurrences>
            <item>
              <hlevel>0</hlevel>
              <item_name>hypertension Diagnosis</item_name>
              <item_key>/path/to/hypertension/diagnosis</item_key>
              <tooltip>/path/to/hypertension/diagnosis</tooltip>
              <class>ENC</class>
              <constrain_by_date></constrain_by_date>
              <item_icon>LA</item_icon>
              <item_is_synonym>false</item_is_synonym>
            </item>
            <item>
              <hlevel>0</hlevel>
              <item_name>hypertension Test</item_name>
              <item_key>/path/to/hypertension/test</item_key>
              <tooltip>/path/to/hypertension/test</tooltip>
              <class>ENC</class>
              <constrain_by_date></constrain_by_date>
              <constrain_by_value>
                <value_type>NUMBER</value_type>
                <value_unit_of_measure>hyperOs</value_unit_of_measure>
                <value_operator>LE</value_operator>
                <value_constraint>42.0</value_constraint>
              </constrain_by_value>
              <item_icon>LA</item_icon>
              <item_is_synonym>false</item_is_synonym>
            </item>
            <item>
              <hlevel>0</hlevel>
              <item_name>hypertension meds</item_name>
              <item_key>/path/to/hypertension/meds</item_key>
              <tooltip>/path/to/hypertension/meds</tooltip>
              <class>ENC</class>
              <constrain_by_date></constrain_by_date>
              <constrain_by_value>
                <value_type>NUMBER</value_type>
                <value_operator>BETWEEN</value_operator>
                <value_constraint>3.0 and 3000.0</value_constraint>
              </constrain_by_value>
              <item_icon>LA</item_icon>
              <item_is_synonym>false</item_is_synonym>
            </item>
          </panel>
        </subquery>
      </query_definition>
    }
    assertResult(XmlUtil.prettyPrint(expectedXml))(XmlUtil.prettyPrint(resultXml))
  }

  @Test
  def testTwoEventTimelineFromV2WithEndOfConstraints(): Unit = {
    val expectedXml: Node = XmlUtil.surroundWith("i2b2") {
      <query_definition>
        <query_name>Men With Hypertension Who Got Better - with an End constraint</query_name>
        <query_timing>ANY</query_timing>
        <specificity_scale>0</specificity_scale>
        <use_shrine>1</use_shrine>
        <panel>
          <panel_number>1</panel_number>
          <panel_accuracy_scale>100</panel_accuracy_scale>
          <invert>0</invert>
          <panel_timing>ANY</panel_timing>
          <total_item_occurrences>1</total_item_occurrences>
          <item>
            <hlevel>0</hlevel>
            <item_name>45-55</item_name>
            <item_key>/path/to/middle/aged</item_key>
            <tooltip>/path/to/middle/aged</tooltip>
            <class>ENC</class>
            <constrain_by_date></constrain_by_date>
            <item_icon>LA</item_icon>
            <item_is_synonym>false</item_is_synonym>
          </item>
        </panel>
        <panel>
          <panel_number>2</panel_number>
          <panel_accuracy_scale>100</panel_accuracy_scale>
          <invert>0</invert>
          <panel_timing>ANY</panel_timing>
          <total_item_occurrences>1</total_item_occurrences>
          <item>
            <hlevel>0</hlevel>
            <item_name>male</item_name>
            <item_key>/path/to/male</item_key>
            <tooltip>/path/to/male</tooltip>
            <class>ENC</class>
            <constrain_by_date></constrain_by_date>
            <item_icon>LA</item_icon>
            <item_is_synonym>false</item_is_synonym>
          </item>
        </panel>
        <subquery_constraint>
          <first_query>
            <query_id>Timeline 0</query_id>
            <join_column>ENDDATE</join_column>
            <aggregate_operator>ANY</aggregate_operator>
          </first_query>
          <operator>LESS</operator>
          <second_query>
            <query_id>Timeline 1</query_id>
            <join_column>STARTDATE</join_column>
            <aggregate_operator>ANY</aggregate_operator>
          </second_query>
        </subquery_constraint>
        <subquery>
          <query_id>Timeline 0</query_id>
          <query_type>EVENT</query_type>
          <query_name>Timeline 0</query_name>
          <query_timing>SAMEINSTANCENUM</query_timing>
          <specificity_scale>0</specificity_scale>
          <use_shrine>1</use_shrine>
          <panel>
            <panel_number>1</panel_number>
            <panel_accuracy_scale>100</panel_accuracy_scale>
            <invert>0</invert>
            <panel_timing>SAMEINSTANCENUM</panel_timing>
            <total_item_occurrences>1</total_item_occurrences>
            <item>
              <hlevel>0</hlevel>
              <item_name>hypertension Diagnosis</item_name>
              <item_key>/path/to/hypertension/diagnosis</item_key>
              <tooltip>/path/to/hypertension/diagnosis</tooltip>
              <class>ENC</class>
              <constrain_by_date></constrain_by_date>
              <item_icon>LA</item_icon>
              <item_is_synonym>false</item_is_synonym>
            </item>
            <item>
              <hlevel>0</hlevel>
              <item_name>hypertension Test</item_name>
              <item_key>/path/to/hypertension/test</item_key>
              <tooltip>/path/to/hypertension/test</tooltip>
              <class>ENC</class>
              <constrain_by_date></constrain_by_date>
              <constrain_by_value>
                <value_type>FLAG</value_type>
                <value_operator>EQ</value_operator>
                <value_constraint>H</value_constraint>
              </constrain_by_value>
              <item_icon>LA</item_icon>
              <item_is_synonym>false</item_is_synonym>
            </item>
            <item>
              <hlevel>0</hlevel>
              <item_name>hypertension meds</item_name>
              <item_key>/path/to/hypertension/meds</item_key>
              <tooltip>/path/to/hypertension/meds</tooltip>
              <class>ENC</class>
              <constrain_by_date></constrain_by_date>
              <constrain_by_value>
                <value_type>NUMBER</value_type>
                <value_operator>BETWEEN</value_operator>
                <value_constraint>3.0 and 3000.0</value_constraint>
              </constrain_by_value>
              <item_icon>LA</item_icon>
              <item_is_synonym>false</item_is_synonym>
            </item>
          </panel>
        </subquery>
        <subquery>
          <query_id>Timeline 1</query_id>
          <query_type>EVENT</query_type>
          <query_name>Timeline 1</query_name>
          <query_timing>SAMEINSTANCENUM</query_timing>
          <specificity_scale>0</specificity_scale>
          <use_shrine>1</use_shrine>
          <panel>
            <panel_number>1</panel_number>
            <panel_accuracy_scale>100</panel_accuracy_scale>
            <invert>1</invert>
            <panel_timing>SAMEINSTANCENUM</panel_timing>
            <total_item_occurrences>1</total_item_occurrences>
            <item>
              <hlevel>0</hlevel>
              <item_name>hypertension Diagnosis</item_name>
              <item_key>/path/to/hypertension/diagnosis</item_key>
              <tooltip>/path/to/hypertension/diagnosis</tooltip>
              <class>ENC</class>
              <constrain_by_date></constrain_by_date>
              <item_icon>LA</item_icon>
              <item_is_synonym>false</item_is_synonym>
            </item>
            <item>
              <hlevel>0</hlevel>
              <item_name>hypertension Test</item_name>
              <item_key>/path/to/hypertension/test</item_key>
              <tooltip>/path/to/hypertension/test</tooltip>
              <class>ENC</class>
              <constrain_by_date></constrain_by_date>
              <constrain_by_value>
                <value_type>NUMBER</value_type>
                <value_unit_of_measure>hyperOs</value_unit_of_measure>
                <value_operator>LE</value_operator>
                <value_constraint>42.0</value_constraint>
              </constrain_by_value>
              <item_icon>LA</item_icon>
              <item_is_synonym>false</item_is_synonym>
            </item>
            <item>
              <hlevel>0</hlevel>
              <item_name>hypertension meds</item_name>
              <item_key>/path/to/hypertension/meds</item_key>
              <tooltip>/path/to/hypertension/meds</tooltip>
              <class>ENC</class>
              <constrain_by_date></constrain_by_date>
              <constrain_by_value>
                <value_type>NUMBER</value_type>
                <value_operator>BETWEEN</value_operator>
                <value_constraint>3.0 and 3000.0</value_constraint>
              </constrain_by_value>
              <item_icon>LA</item_icon>
              <item_is_synonym>false</item_is_synonym>
            </item>
          </panel>
        </subquery>
      </query_definition>
    }

    val i2b2Query: I2b2QueryDefinition = I2b2QueryDefinition.fromShrineV2(QueryDefinitionTest.twoEventTimelineEndConstraintQuery)
    val resultXml: Node = XmlUtil.surroundWith("i2b2")(i2b2Query.toI2b2)

    assertResult(XmlUtil.prettyPrint(expectedXml))(XmlUtil.prettyPrint(resultXml))
  }

  @Test
  def testTwoEventTimelineFromV2WithRelationshipConstraint(): Unit = {
    val expectedXml: Node = XmlUtil.surroundWith("i2b2") {
      <query_definition>
        <query_name>Men With Hypertension Who Got Better - During diagnosis</query_name>
        <query_timing>ANY</query_timing>
        <specificity_scale>0</specificity_scale>
        <use_shrine>1</use_shrine>
        <panel>
          <panel_number>1</panel_number>
          <panel_accuracy_scale>100</panel_accuracy_scale>
          <invert>0</invert>
          <panel_timing>ANY</panel_timing>
          <total_item_occurrences>1</total_item_occurrences>
          <item>
            <hlevel>0</hlevel>
            <item_name>45-55</item_name>
            <item_key>/path/to/middle/aged</item_key>
            <tooltip>/path/to/middle/aged</tooltip>
            <class>ENC</class>
            <constrain_by_date></constrain_by_date>
            <item_icon>LA</item_icon>
            <item_is_synonym>false</item_is_synonym>
          </item>
        </panel>
        <panel>
          <panel_number>2</panel_number>
          <panel_accuracy_scale>100</panel_accuracy_scale>
          <invert>0</invert>
          <panel_timing>ANY</panel_timing>
          <total_item_occurrences>1</total_item_occurrences>
          <item>
            <hlevel>0</hlevel>
            <item_name>male</item_name>
            <item_key>/path/to/male</item_key>
            <tooltip>/path/to/male</tooltip>
            <class>ENC</class>
            <constrain_by_date></constrain_by_date>
            <item_icon>LA</item_icon>
            <item_is_synonym>false</item_is_synonym>
          </item>
        </panel>
        <subquery_constraint>
          <first_query>
            <query_id>Timeline 0</query_id>
            <join_column>STARTDATE</join_column>
            <aggregate_operator>ANY</aggregate_operator>
          </first_query>
          <operator>EQUAL</operator>
          <second_query>
            <query_id>Timeline 1</query_id>
            <join_column>STARTDATE</join_column>
            <aggregate_operator>ANY</aggregate_operator>
          </second_query>
        </subquery_constraint>
        <subquery>
          <query_id>Timeline 0</query_id>
          <query_type>EVENT</query_type>
          <query_name>Timeline 0</query_name>
          <query_timing>SAMEINSTANCENUM</query_timing>
          <specificity_scale>0</specificity_scale>
          <use_shrine>1</use_shrine>
          <panel>
            <panel_number>1</panel_number>
            <panel_accuracy_scale>100</panel_accuracy_scale>
            <invert>0</invert>
            <panel_timing>SAMEINSTANCENUM</panel_timing>
            <total_item_occurrences>1</total_item_occurrences>
            <item>
              <hlevel>0</hlevel>
              <item_name>hypertension Diagnosis</item_name>
              <item_key>/path/to/hypertension/diagnosis</item_key>
              <tooltip>/path/to/hypertension/diagnosis</tooltip>
              <class>ENC</class>
              <constrain_by_date></constrain_by_date>
              <item_icon>LA</item_icon>
              <item_is_synonym>false</item_is_synonym>
            </item>
            <item>
              <hlevel>0</hlevel>
              <item_name>hypertension Test</item_name>
              <item_key>/path/to/hypertension/test</item_key>
              <tooltip>/path/to/hypertension/test</tooltip>
              <class>ENC</class>
              <constrain_by_date></constrain_by_date>
              <constrain_by_value>
                <value_type>FLAG</value_type>
                <value_operator>EQ</value_operator>
                <value_constraint>H</value_constraint>
              </constrain_by_value>
              <item_icon>LA</item_icon>
              <item_is_synonym>false</item_is_synonym>
            </item>
            <item>
              <hlevel>0</hlevel>
              <item_name>hypertension meds</item_name>
              <item_key>/path/to/hypertension/meds</item_key>
              <tooltip>/path/to/hypertension/meds</tooltip>
              <class>ENC</class>
              <constrain_by_date></constrain_by_date>
              <constrain_by_value>
                <value_type>NUMBER</value_type>
                <value_operator>BETWEEN</value_operator>
                <value_constraint>3.0 and 3000.0</value_constraint>
              </constrain_by_value>
              <item_icon>LA</item_icon>
              <item_is_synonym>false</item_is_synonym>
            </item>
          </panel>
        </subquery>
        <subquery>
          <query_id>Timeline 1</query_id>
          <query_type>EVENT</query_type>
          <query_name>Timeline 1</query_name>
          <query_timing>SAMEINSTANCENUM</query_timing>
          <specificity_scale>0</specificity_scale>
          <use_shrine>1</use_shrine>
          <panel>
            <panel_number>1</panel_number>
            <panel_accuracy_scale>100</panel_accuracy_scale>
            <invert>1</invert>
            <panel_timing>SAMEINSTANCENUM</panel_timing>
            <total_item_occurrences>1</total_item_occurrences>
            <item>
              <hlevel>0</hlevel>
              <item_name>hypertension Diagnosis</item_name>
              <item_key>/path/to/hypertension/diagnosis</item_key>
              <tooltip>/path/to/hypertension/diagnosis</tooltip>
              <class>ENC</class>
              <constrain_by_date></constrain_by_date>
              <item_icon>LA</item_icon>
              <item_is_synonym>false</item_is_synonym>
            </item>
            <item>
              <hlevel>0</hlevel>
              <item_name>hypertension Test</item_name>
              <item_key>/path/to/hypertension/test</item_key>
              <tooltip>/path/to/hypertension/test</tooltip>
              <class>ENC</class>
              <constrain_by_date></constrain_by_date>
              <constrain_by_value>
                <value_type>NUMBER</value_type>
                <value_unit_of_measure>hyperOs</value_unit_of_measure>
                <value_operator>LE</value_operator>
                <value_constraint>42.0</value_constraint>
              </constrain_by_value>
              <item_icon>LA</item_icon>
              <item_is_synonym>false</item_is_synonym>
            </item>
            <item>
              <hlevel>0</hlevel>
              <item_name>hypertension meds</item_name>
              <item_key>/path/to/hypertension/meds</item_key>
              <tooltip>/path/to/hypertension/meds</tooltip>
              <class>ENC</class>
              <constrain_by_date></constrain_by_date>
              <constrain_by_value>
                <value_type>NUMBER</value_type>
                <value_operator>BETWEEN</value_operator>
                <value_constraint>3.0 and 3000.0</value_constraint>
              </constrain_by_value>
              <item_icon>LA</item_icon>
              <item_is_synonym>false</item_is_synonym>
            </item>
          </panel>
        </subquery>
      </query_definition>
    }

    val i2b2Query: I2b2QueryDefinition = I2b2QueryDefinition.fromShrineV2(QueryDefinitionTest.twoEventTimelineRelatedToQuery)
    val resultXml: Node = XmlUtil.surroundWith("i2b2")(i2b2Query.toI2b2)

    assertResult(XmlUtil.prettyPrint(expectedXml))(XmlUtil.prettyPrint(resultXml))
  }

  @Test
  def testTwoEventTimelineFromV2WithPrimaryAndSecondaryTimeConstraint(): Unit = {
    val expectedXml: Node = XmlUtil.surroundWith("i2b2") {
      <query_definition>
        <query_name>Men With Hypertension Who Got Better - During diagnosis between 0 days and 35 months</query_name>
        <query_timing>ANY</query_timing>
        <specificity_scale>0</specificity_scale>
        <use_shrine>1</use_shrine>
        <panel>
          <panel_number>1</panel_number>
          <panel_accuracy_scale>100</panel_accuracy_scale>
          <invert>0</invert>
          <panel_timing>ANY</panel_timing>
          <total_item_occurrences>1</total_item_occurrences>
          <item>
            <hlevel>0</hlevel>
            <item_name>45-55</item_name>
            <item_key>/path/to/middle/aged</item_key>
            <tooltip>/path/to/middle/aged</tooltip>
            <class>ENC</class>
            <constrain_by_date></constrain_by_date>
            <item_icon>LA</item_icon>
            <item_is_synonym>false</item_is_synonym>
          </item>
        </panel>
        <panel>
          <panel_number>2</panel_number>
          <panel_accuracy_scale>100</panel_accuracy_scale>
          <invert>0</invert>
          <panel_timing>ANY</panel_timing>
          <total_item_occurrences>1</total_item_occurrences>
          <item>
            <hlevel>0</hlevel>
            <item_name>male</item_name>
            <item_key>/path/to/male</item_key>
            <tooltip>/path/to/male</tooltip>
            <class>ENC</class>
            <constrain_by_date></constrain_by_date>
            <item_icon>LA</item_icon>
            <item_is_synonym>false</item_is_synonym>
          </item>
        </panel>
        <subquery_constraint>
          <first_query>
            <query_id>Timeline 0</query_id>
            <join_column>STARTDATE</join_column>
            <aggregate_operator>ANY</aggregate_operator>
          </first_query>
          <operator>EQUAL</operator>
          <second_query>
            <query_id>Timeline 1</query_id>
            <join_column>STARTDATE</join_column>
            <aggregate_operator>ANY</aggregate_operator>
          </second_query>
          <span>
            <operator>GREATEREQUAL</operator>
            <span_value>0</span_value>
            <units>DAY</units>
          </span>
          <span>
            <operator>GREATEREQUAL</operator>
            <span_value>35</span_value>
            <units>MONTH</units>
          </span>
        </subquery_constraint>
        <subquery>
          <query_id>Timeline 0</query_id>
          <query_type>EVENT</query_type>
          <query_name>Timeline 0</query_name>
          <query_timing>SAMEINSTANCENUM</query_timing>
          <specificity_scale>0</specificity_scale>
          <use_shrine>1</use_shrine>
          <panel>
            <panel_number>1</panel_number>
            <panel_accuracy_scale>100</panel_accuracy_scale>
            <invert>0</invert>
            <panel_timing>SAMEINSTANCENUM</panel_timing>
            <total_item_occurrences>1</total_item_occurrences>
            <item>
              <hlevel>0</hlevel>
              <item_name>hypertension Diagnosis</item_name>
              <item_key>/path/to/hypertension/diagnosis</item_key>
              <tooltip>/path/to/hypertension/diagnosis</tooltip>
              <class>ENC</class>
              <constrain_by_date></constrain_by_date>
              <item_icon>LA</item_icon>
              <item_is_synonym>false</item_is_synonym>
            </item>
            <item>
              <hlevel>0</hlevel>
              <item_name>hypertension Test</item_name>
              <item_key>/path/to/hypertension/test</item_key>
              <tooltip>/path/to/hypertension/test</tooltip>
              <class>ENC</class>
              <constrain_by_date></constrain_by_date>
              <constrain_by_value>
                <value_type>FLAG</value_type>
                <value_operator>EQ</value_operator>
                <value_constraint>H</value_constraint>
              </constrain_by_value>
              <item_icon>LA</item_icon>
              <item_is_synonym>false</item_is_synonym>
            </item>
            <item>
              <hlevel>0</hlevel>
              <item_name>hypertension meds</item_name>
              <item_key>/path/to/hypertension/meds</item_key>
              <tooltip>/path/to/hypertension/meds</tooltip>
              <class>ENC</class>
              <constrain_by_date></constrain_by_date>
              <constrain_by_value>
                <value_type>NUMBER</value_type>
                <value_operator>BETWEEN</value_operator>
                <value_constraint>3.0 and 3000.0</value_constraint>
              </constrain_by_value>
              <item_icon>LA</item_icon>
              <item_is_synonym>false</item_is_synonym>
            </item>
          </panel>
        </subquery>
        <subquery>
          <query_id>Timeline 1</query_id>
          <query_type>EVENT</query_type>
          <query_name>Timeline 1</query_name>
          <query_timing>SAMEINSTANCENUM</query_timing>
          <specificity_scale>0</specificity_scale>
          <use_shrine>1</use_shrine>
          <panel>
            <panel_number>1</panel_number>
            <panel_accuracy_scale>100</panel_accuracy_scale>
            <invert>1</invert>
            <panel_timing>SAMEINSTANCENUM</panel_timing>
            <total_item_occurrences>1</total_item_occurrences>
            <item>
              <hlevel>0</hlevel>
              <item_name>hypertension Diagnosis</item_name>
              <item_key>/path/to/hypertension/diagnosis</item_key>
              <tooltip>/path/to/hypertension/diagnosis</tooltip>
              <class>ENC</class>
              <constrain_by_date></constrain_by_date>
              <item_icon>LA</item_icon>
              <item_is_synonym>false</item_is_synonym>
            </item>
            <item>
              <hlevel>0</hlevel>
              <item_name>hypertension Test</item_name>
              <item_key>/path/to/hypertension/test</item_key>
              <tooltip>/path/to/hypertension/test</tooltip>
              <class>ENC</class>
              <constrain_by_date></constrain_by_date>
              <constrain_by_value>
                <value_type>NUMBER</value_type>
                <value_unit_of_measure>hyperOs</value_unit_of_measure>
                <value_operator>LE</value_operator>
                <value_constraint>42.0</value_constraint>
              </constrain_by_value>
              <item_icon>LA</item_icon>
              <item_is_synonym>false</item_is_synonym>
            </item>
            <item>
              <hlevel>0</hlevel>
              <item_name>hypertension meds</item_name>
              <item_key>/path/to/hypertension/meds</item_key>
              <tooltip>/path/to/hypertension/meds</tooltip>
              <class>ENC</class>
              <constrain_by_date></constrain_by_date>
              <constrain_by_value>
                <value_type>NUMBER</value_type>
                <value_operator>BETWEEN</value_operator>
                <value_constraint>3.0 and 3000.0</value_constraint>
              </constrain_by_value>
              <item_icon>LA</item_icon>
              <item_is_synonym>false</item_is_synonym>
            </item>
          </panel>
        </subquery>
      </query_definition>
    }

    val i2b2Query: I2b2QueryDefinition = I2b2QueryDefinition.fromShrineV2(QueryDefinitionTest.twoEventTimelineRelatedToWithTwoTimeSpanQuery)
    val resultXml: Node = XmlUtil.surroundWith("i2b2")(i2b2Query.toI2b2)

    assertResult(XmlUtil.prettyPrint(expectedXml))(XmlUtil.prettyPrint(resultXml))
  }

  @Test
  def testTimelineQueryWithoutByFromFile(): Unit = {
    val expectedXml: Node = XmlUtil.surroundWith("i2b2") {
        <query_definition>
          <query_name>NoBy</query_name>
          <query_timing>ANY</query_timing>
          <specificity_scale>0</specificity_scale>
          <use_shrine>1</use_shrine>
          <subquery_constraint>
            <first_query>
              <query_id>Timeline 0</query_id>
              <join_column>STARTDATE</join_column>
              <aggregate_operator>ANY</aggregate_operator>
            </first_query>
            <operator>LESS</operator>
            <second_query>
              <query_id>Timeline 1</query_id>
              <join_column>STARTDATE</join_column>
              <aggregate_operator>ANY</aggregate_operator>
            </second_query>
          </subquery_constraint>
          <subquery>
            <query_id>Timeline 0</query_id>
            <query_type>EVENT</query_type>
            <query_name>Timeline 0</query_name>
            <query_timing>SAMEINSTANCENUM</query_timing>
            <specificity_scale>0</specificity_scale>
            <use_shrine>1</use_shrine>
            <panel>
              <panel_number>1</panel_number>
              <panel_accuracy_scale>100</panel_accuracy_scale>
              <invert>0</invert>
              <panel_timing>SAMEINSTANCENUM</panel_timing>
              <total_item_occurrences>1</total_item_occurrences>
              <item>
                <hlevel>6</hlevel>
                <item_name>Cough</item_name>
                <item_key>\\i2b2_DIAG\i2b2\Diagnoses\Symptoms, signs, and ill-defined conditions (780-799)\Symptoms (780-789)\(786) Symptoms involving respirat~\(786-2) Cough\</item_key>
                <tooltip>\\i2b2_DIAG\i2b2\Diagnoses\Symptoms, signs, and ill-defined conditions (780-799)\Symptoms (780-789)\(786) Symptoms involving respirat~\(786-2) Cough\</tooltip>
                <class>ENC</class>
                <constrain_by_date></constrain_by_date>
                <item_icon>LA</item_icon>
                <item_is_synonym>false</item_is_synonym>
              </item>
            </panel>
          </subquery>
          <subquery>
            <query_id>Timeline 1</query_id>
            <query_type>EVENT</query_type>
            <query_name>Timeline 1</query_name>
            <query_timing>SAMEINSTANCENUM</query_timing>
            <specificity_scale>0</specificity_scale>
            <use_shrine>1</use_shrine>
            <panel>
              <panel_number>1</panel_number>
              <panel_accuracy_scale>100</panel_accuracy_scale>
              <invert>0</invert>
              <panel_timing>SAMEINSTANCENUM</panel_timing>
              <total_item_occurrences>1</total_item_occurrences>
              <item>
                <hlevel>5</hlevel>
                <item_name>Acute upper respiratory infections of multiple or unspecified sites</item_name>
                <item_key>\\i2b2_DIAG\i2b2\Diagnoses\Respiratory system (460-519)\Acute respiratory infections (460-466)\(465) Acute upper respiratory inf~\</item_key>
                <tooltip>\\i2b2_DIAG\i2b2\Diagnoses\Respiratory system (460-519)\Acute respiratory infections (460-466)\(465) Acute upper respiratory inf~\</tooltip>
                <class>ENC</class>
                <constrain_by_date></constrain_by_date>
                <item_icon>LA</item_icon>
                <item_is_synonym>false</item_is_synonym>
              </item>
            </panel>
          </subquery>
        </query_definition>
    }

    val i2b2Query: I2b2QueryDefinition = I2b2QueryDefinition.fromShrineV2(QueryDefinitionTest.timelineQueryWithoutByFromFile)
    val resultXml: Node = XmlUtil.surroundWith("i2b2")(i2b2Query.toI2b2)

    assertResult(XmlUtil.prettyPrint(expectedXml))(XmlUtil.prettyPrint(resultXml))
  }

  @Test
  def testTimelineQueryWithByFromFile(): Unit = {
    val expectedXml: Node = XmlUtil.surroundWith("i2b2") {
      <query_definition>
        <query_name>ByGreaterEqual</query_name>
        <query_timing>ANY</query_timing>
        <specificity_scale>0</specificity_scale>
        <use_shrine>1</use_shrine>
        <subquery_constraint>
          <first_query>
            <query_id>Timeline 0</query_id>
            <join_column>STARTDATE</join_column>
            <aggregate_operator>ANY</aggregate_operator>
          </first_query>
          <operator>LESS</operator>
          <second_query>
            <query_id>Timeline 1</query_id>
            <join_column>STARTDATE</join_column>
            <aggregate_operator>ANY</aggregate_operator>
          </second_query>
          <span>
            <operator>GREATER</operator>
            <span_value>0</span_value>
            <units>DAY</units>
          </span>
        </subquery_constraint>
        <subquery>
          <query_id>Timeline 0</query_id>
          <query_type>EVENT</query_type>
          <query_name>Timeline 0</query_name>
          <query_timing>SAMEINSTANCENUM</query_timing>
          <specificity_scale>0</specificity_scale>
          <use_shrine>1</use_shrine>
          <panel>
            <panel_number>1</panel_number>
            <panel_accuracy_scale>100</panel_accuracy_scale>
            <invert>0</invert>
            <panel_timing>SAMEINSTANCENUM</panel_timing>
            <total_item_occurrences>1</total_item_occurrences>
            <item>
              <hlevel>6</hlevel>
              <item_name>Cough</item_name>
              <item_key>\\i2b2_DIAG\i2b2\Diagnoses\Symptoms, signs, and ill-defined conditions (780-799)\Symptoms (780-789)\(786) Symptoms involving respirat~\(786-2) Cough\</item_key>
              <tooltip>\\i2b2_DIAG\i2b2\Diagnoses\Symptoms, signs, and ill-defined conditions (780-799)\Symptoms (780-789)\(786) Symptoms involving respirat~\(786-2) Cough\</tooltip>
              <class>ENC</class>
              <constrain_by_date></constrain_by_date>
              <item_icon>LA</item_icon>
              <item_is_synonym>false</item_is_synonym>
            </item>
          </panel>
        </subquery>
        <subquery>
          <query_id>Timeline 1</query_id>
          <query_type>EVENT</query_type>
          <query_name>Timeline 1</query_name>
          <query_timing>SAMEINSTANCENUM</query_timing>
          <specificity_scale>0</specificity_scale>
          <use_shrine>1</use_shrine>
          <panel>
            <panel_number>1</panel_number>
            <panel_accuracy_scale>100</panel_accuracy_scale>
            <invert>0</invert>
            <panel_timing>SAMEINSTANCENUM</panel_timing>
            <total_item_occurrences>1</total_item_occurrences>
            <item>
              <hlevel>5</hlevel>
              <item_name>Acute upper respiratory infections of multiple or unspecified sites</item_name>
              <item_key>\\i2b2_DIAG\i2b2\Diagnoses\Respiratory system (460-519)\Acute respiratory infections (460-466)\(465) Acute upper respiratory inf~\</item_key>
              <tooltip>\\i2b2_DIAG\i2b2\Diagnoses\Respiratory system (460-519)\Acute respiratory infections (460-466)\(465) Acute upper respiratory inf~\</tooltip>
              <class>ENC</class>
              <constrain_by_date></constrain_by_date>
              <item_icon>LA</item_icon>
              <item_is_synonym>false</item_is_synonym>
            </item>
          </panel>
        </subquery>
      </query_definition>
    }
    val i2b2Query: I2b2QueryDefinition = I2b2QueryDefinition.fromShrineV2(QueryDefinitionTest.timelineQueryWithByFromFile)
    val resultXml: Node = XmlUtil.surroundWith("i2b2")(i2b2Query.toI2b2)

    assertResult(XmlUtil.prettyPrint(expectedXml))(XmlUtil.prettyPrint(resultXml))
  }
}
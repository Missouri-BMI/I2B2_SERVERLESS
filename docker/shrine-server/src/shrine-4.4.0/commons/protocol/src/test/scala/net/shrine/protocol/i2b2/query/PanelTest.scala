package net.shrine.protocol.i2b2.query

import net.shrine.protocol.i2b2.query
import net.shrine.util.ShouldMatchersForJUnit
import net.shrine.xml.{XmlDateHelper, XmlUtil}
import org.junit.Test

import scala.xml.NodeSeq

/**
 * @author clint
 * @since ~ Jan 24, 2012
 */
final class PanelTest extends ShouldMatchersForJUnit {
  private val t1 = Term("foo", "fooName")

  private val term1 = Term("\\\\SHRINE\\SHRINE\\Diagnoses\\Congenital anomalies\\Cardiac and circulatory congenital anomalies\\Aortic valve stenosis\\Congenital stenosis of aortic valve\\",
                           "Congenital stenosis of aortic valve")
  private val term2 = Term("\\\\SHRINE\\SHRINE\\Demographics\\Language\\Bosnian\\",
                           "Bosnian")
  private val term3 = Term("\\\\SHRINE\\SHRINE\\Demographics\\Age\\18-34 years old\\30 years old\\",
                           "30 years old")
  private val query1 = I2b2Query("12345")

  private val modifiers = Modifiers("foo", "bar", "baz")
  private val valueConstraint = ValueConstraint("foo", Some("bar"), "baz", "nuh")

  private val p1 = Panel(1, isExcluded = true, 1, None, None, PanelTiming.Any, Nil, Seq(query.Constrained(t1, modifiers, valueConstraint)))

  private val p2 = query.Panel(1, isExcluded = false, 1, None, None, PanelTiming.Any, Seq(t1))

  private def now = XmlDateHelper.now

  private val startDate = Some(now)
  private val endDate = Some(now)

  private val p3 = query.Panel(3, isExcluded = true, 99, startDate, endDate, PanelTiming.Any, Seq(term1, term2, term3))

  private val p4 = query.Panel(4, isExcluded = false, 0, startDate, endDate, PanelTiming.Any, Seq(term1, term2, term3))

  private val p5 = query.Panel(1, isExcluded = false, 1, None, None, PanelTiming.Any, Seq(term1, query1))

  private val p6 = query.Panel(1, isExcluded = true, 1, None, None, PanelTiming.Any, Seq(term2), Seq(Constrained(t1, Option(modifiers), None)))

  private val p7 = query.Panel(1, isExcluded = true, 1, None, None, PanelTiming.Any, Seq(query1), Seq(Constrained(t1, None, Option(valueConstraint))))

  @Test
  def testToExpression(): Unit = {
    query.Panel(1, isExcluded = false, 0, None, None, PanelTiming.Any, Seq(t1)).toExpression should equal(t1)

    p1.toExpression should equal(Not(query.Constrained(t1, modifiers, valueConstraint)))

    p2.toExpression should equal (t1)

    p3.toExpression should equal(OccuranceLimited(99, DateBounded(startDate, endDate, Not(Or(term1, term2, term3)))))

    p4.toExpression should equal(DateBounded(startDate, endDate, Or(term1, term2, term3)))
    
    val withNonDefaultTiming = p2.copy(timing = PanelTiming.SameVisit)
    
    withNonDefaultTiming.toExpression should equal(WithTiming(PanelTiming.SameVisit, t1))
  }

  @Test
  def testFromI2b2(): Unit = {
    def roundTrip(panel: Panel): Unit = {
      val i2b2Xml = panel.toI2b2

      val unmarshalled = Panel.fromI2b2(i2b2Xml).get

      (panel eq unmarshalled) should equal(false)
      
      unmarshalled should equal(panel)
    }
    
    roundTrip(p1)
    roundTrip(p2) 
    roundTrip(p3) 
    roundTrip(p4) 
    roundTrip(p5) 
    roundTrip(p6.copy(timing = PanelTiming.SameVisit))
    roundTrip(p7.copy(timing = PanelTiming.SameInstanceNum))
  }

  @Test
  def testPanelGuards(): Unit = {
    intercept[IllegalArgumentException] {
      query.Panel(1, isExcluded = true, 1, None, None, PanelTiming.Any, Nil)
    }

    query.Panel(1, isExcluded = true, 1, None, None, PanelTiming.Any, Seq(t1))
  }

  @Test
  def testInvert(): Unit = {
    p1.invert.isExcluded should be(false)
    p2.invert.isExcluded should be(true)

    p1.invert.invert should equal(p1)
  }

  @Test
  def testWithStart(): Unit = {
    val time = XmlDateHelper.now

    val withStart = p1.withStart(Some(time))

    withStart should not be p1

    withStart.start should equal(Some(time))

    withStart.end should equal(p1.end)

    withStart.number should equal(p1.number)
    withStart.isExcluded should equal(p1.isExcluded)
    withStart.minOccurrences should equal(p1.minOccurrences)
    withStart.terms should equal(p1.terms)
  }

  @Test
  def testWithEnd(): Unit = {
    val time = XmlDateHelper.now

    val withEnd = p1.withEnd(Some(time))

    withEnd should not be p1

    withEnd.start should equal(p1.start)

    withEnd.end should equal(Some(time))

    withEnd.number should equal(p1.number)
    withEnd.isExcluded should equal(p1.isExcluded)
    withEnd.minOccurrences should equal(p1.minOccurrences)
    withEnd.terms should equal(p1.terms)
  }

  @Test
  def testWithMinOccurrences(): Unit = {
    val min = 99

    val withMin = p1.withMinOccurrences(min)

    withMin should not be p1

    withMin.minOccurrences should equal(min)

    withMin.start should equal(p1.start)
    withMin.end should equal(p1.end)
    withMin.number should equal(p1.number)
    withMin.isExcluded should equal(p1.isExcluded)
    withMin.terms should equal(p1.terms)
  }

  @Test
  def testToI2B2(): Unit = {
    val p1I2b2Xml = p1.toI2b2
    
    p1I2b2Xml.head.label should equal("panel")

    //panel_number
    (p1I2b2Xml \ "panel_number").text should be("1")
    (p1.copy(number = 99).toI2b2 \ "panel_number").text should be("99")

    //panel date defaults
    (p1I2b2Xml \ "panel_start") should equal(NodeSeq.Empty)
    (p1I2b2Xml \ "panel_end") should equal(NodeSeq.Empty)

    //item date defaults
    (p1I2b2Xml \ "item" \ "constrain_by_date").toString should equal(XmlUtil.stripWhitespace(<constrain_by_date></constrain_by_date>).toString)
    (p1I2b2Xml \ "item" \ "constrain_by_date" \ "date_from") should equal(NodeSeq.Empty)
    (p1I2b2Xml \ "item" \ "constrain_by_date" \ "date_to") should equal(NodeSeq.Empty)

    //Query-in-query
    {
      val referencesQuery = p5.toI2b2

      (referencesQuery \ "item").map(i => (i \ "item_key").text) should equal(Seq(term1.value, query1.value))
    }

    //dates
    {
      val time = XmlDateHelper.now

      val withStart = p1.withStart(Some(time)).toI2b2

      (withStart \ "panel_date_from").text should equal(time.toString)
      (withStart \ "item" \ "constrain_by_date" \ "date_from").text should equal(time.toString)
      (withStart \ "panel_date_to") should equal(NodeSeq.Empty)
      (withStart \ "item" \ "constrain_by_date" \ "date_to") should equal(NodeSeq.Empty)

      val withEnd = p1.withEnd(Some(time)).toI2b2

      (withEnd \ "panel_date_from") should equal(NodeSeq.Empty)
      (withEnd \ "item" \ "constrain_by_date" \ "date_from") should equal(NodeSeq.Empty)
      (withEnd \ "panel_date_to").text should equal(time.toString)
      (withEnd \ "item" \ "constrain_by_date" \ "date_to").text should equal(time.toString)

      val withStartAndEnd = p1.withStart(Some(time)).withEnd(Some(time)).toI2b2

      (withStartAndEnd \ "panel_date_from").text should equal(time.toString)
      (withStartAndEnd \ "panel_date_to").text should equal(time.toString)

      (withStartAndEnd \ "item" \ "constrain_by_date" \ "date_from").text should equal(time.toString)
      (withStartAndEnd \ "item" \ "constrain_by_date" \ "date_to").text should equal(time.toString)
    }

    //invert
    (p1I2b2Xml \ "invert").text should equal("1")
    (p1.invert.toI2b2 \ "invert").text should equal("0")

    //panel_timing
    (p1I2b2Xml \ "panel_timing").text should equal(PanelTiming.Any.name)
    
    //total_item_occurrences
    (p1I2b2Xml \ "total_item_occurrences").text should equal("1")
    (p1.withMinOccurrences(99).toI2b2 \ "total_item_occurrences").text should equal("99")

    //item defaults
    (p1I2b2Xml \ "item" \ "class").text should equal("ENC")
    (p1I2b2Xml \ "item" \ "item_icon").text should equal("LA")
    (p1I2b2Xml \ "item" \ "item_is_synonym").text should equal("false")

    //item/hlevel
    (p1I2b2Xml \ "item" \ "hlevel").text should equal("0")
    
    val i2b2Xml = p1.withTerms(Seq(term1)).toI2b2
    val xml = p1.withTerms(Seq(term1)).toI2b2 \ "item" \ "hlevel"
    
    (p1.withOnlyTerms(Seq(term1)).toI2b2 \ "item" \ "hlevel").text should equal("5")
    (p1.withOnlyTerms(Seq(term2)).toI2b2 \ "item" \ "hlevel").text should equal("3")
    (p1.withOnlyTerms(Seq(term3)).toI2b2 \ "item" \ "hlevel").text should equal("4")

    // item/item_name
    (p1I2b2Xml \ "item" \ "item_name").text should equal(t1.name)
    (p1.withOnlyTerms(Seq(term1)).toI2b2 \ "item" \ "item_name").text should equal(term1.name)

    // item/item_key
    (p1I2b2Xml \ "item" \ "item_key").text should equal(t1.value)
    (p1.withOnlyTerms(Seq(term1)).toI2b2 \ "item" \ "item_key").text should equal(term1.value)

    // item/tooltip
    (p1I2b2Xml \ "item" \ "tooltip").text should equal(t1.value)
    (p1.withOnlyTerms(Seq(term1)).toI2b2 \ "item" \ "tooltip").text should equal(term1.value)

    //multiple items
    val with2Items = p1.withTerms(Seq(term2)).withConstrainedTerms(Seq(query.Constrained(term1, modifiers, valueConstraint))).toI2b2

    (with2Items \\ "item").size should equal(2)

    val Seq(item1, item2) = (with2Items \\ "item").sortBy(xml => (xml \ "item_name").text).reverse

    (item1 \ "item_name").text should equal(term1.name)
    (item1 \ "item_key").text should equal(term1.value)
    (item1 \ "tooltip").text should equal(term1.value)

    (item2 \ "item_name").text should equal(term2.name)
    (item2 \ "item_key").text should equal(term2.value)
    (item2 \ "tooltip").text should equal(term2.value)

    //modifiers
    Modifiers.fromI2b2(item1 \ "constrain_by_modifier").get should equal(modifiers)
    Modifiers.fromI2b2(item2 \ "constrain_by_modifier").isFailure should be(true)
    
    //valueConstraints
    ValueConstraint.fromI2b2(item1 \ "constrain_by_value").get should equal(valueConstraint)
    ValueConstraint.fromI2b2(item2 \ "constrain_by_value").isFailure should be(true)
  }
}
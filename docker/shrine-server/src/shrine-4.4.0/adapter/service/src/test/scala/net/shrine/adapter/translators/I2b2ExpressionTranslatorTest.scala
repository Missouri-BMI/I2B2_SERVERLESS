package net.shrine.adapter.translators

import net.shrine.protocol.i2b2.query.{And, Constrained, CouldNotMapAllTermsException, DateBounded, I2b2Expression, I2b2QueryDefinition, Modifiers, Not, OccuranceLimited, Or, PanelTiming, Term, ValueConstraint, WithTiming}
import org.junit.Test
import net.shrine.util.ShouldMatchersForJUnit
import net.shrine.xml.XmlDateHelper

import scala.util.Try

/**
 * @author Clint Gilbert
 * @since Mar 1, 2012
 *
 */
final class I2b2ExpressionTranslatorTest extends ShouldMatchersForJUnit {
  private val localTerms = Set("localTerm1", "localTerm2")

  private val networkTermWithTwoMatches = "twoMatches"

  private val networkTermWithOneMatch = "oneMatch"

  private val mappings = Map(networkTermWithTwoMatches -> localTerms, networkTermWithOneMatch -> Set("localTerm3"))

  private val n1 = Term("n1", "n1Name")
  private val n2 = Term("n2", "n2Name")
  private val n3 = Term("n3", "n3Name")
  private val unmapped1 = Term("n4", "n4Name")
  private val unmapped2 = Term("n4", "n4Name")

  private val l1 = Term("l1", "n1Name")
  private val l2 = Term("l2", "n2Name")
  private val l3 = Term("l3", "n3Name")

  private val valueConstraint = ValueConstraint("x", Some("y"), "z", "blarg")

  private def doTestTranslate(Op: I2b2Expression => I2b2Expression):Unit = {
    val translator = ExpressionTranslator(mappings)

    val translated = translator.translate(Op(Term(networkTermWithTwoMatches, networkTermWithTwoMatches)))

    translated should equal(Op(Or(localTerms.toSeq.map(Term(_, networkTermWithTwoMatches)): _*)).normalize)

    intercept[CouldNotMapAllTermsException] {
      translator.translate(Op(Term("alskjklasdjl", networkTermWithTwoMatches)))
    }

    val oneMatch = translator.translate(Op(Term(networkTermWithOneMatch, networkTermWithOneMatch)))

    oneMatch should equal(Op(Term("localTerm3", networkTermWithOneMatch)).normalize)
  }

  @Test
  def testTranslateTerm(): Unit = doTestTranslate(x => x)

  @Test
  def testTranslateNot(): Unit = doTestTranslate(Not)

  @Test
  def testTranslateWithTiming(): Unit = {
    doTestTranslate(WithTiming(PanelTiming.SameVisit, _))

    doTestTranslate(WithTiming(PanelTiming.SameInstanceNum, _))

    doTestTranslate(WithTiming(PanelTiming.Any, _))
  }

  @Test
  def testTranslateAnd(): Unit = {
    doTestTranslate(And(_))

    intercept[Exception] {
      ExpressionTranslator(mappings).translate(And())
    }
  }

  @Test
  def testTranslateOr(): Unit = doTestTranslate(Or(_))

  private def doTestTranslateMultiExprs(Op: Seq[I2b2Expression] => I2b2Expression) = {
    val translator = ExpressionTranslator(mappings)

    val expr = Op(Seq(Term(networkTermWithTwoMatches, "foo"), Term(networkTermWithTwoMatches, "foo")))

    val translated = translator.translate(expr)

    val expectedOr = Or(localTerms.toSeq.map(Term(_, "foo")): _*)

    translated should equal(Op(Seq(expectedOr, expectedOr)).normalize)
  }

  @Test
  def testTranslateAndMultiExprs():Unit = doTestTranslateMultiExprs(exprs => And(exprs: _*))

  @Test
  def testTranslateOrMultiExprs():Unit = doTestTranslateMultiExprs(exprs => Or(exprs: _*))

  private val now = Some(XmlDateHelper.now)

  @Test
  def testTranslateDateBounded(): Unit = doTestTranslate(DateBounded(now, now, _))

  @Test
  def testTranslateOccurranceLimited(): Unit = doTestTranslate(OccuranceLimited(99, _))

  @Test
  def testTranslateComplexExpr(): Unit = doTestTranslate(expr => Or(And(DateBounded(now, now, OccuranceLimited(99, expr)))))

  @Test
  def testTranslateSomeFailedMappings():Unit = {
    val mappings = Map(n1.value -> Set(l1.value), n2.value -> Set(l2.value), n3.value -> Set(l3.value))

    val translator = ExpressionTranslator(mappings)

    import translator.translate

    //Terms
    translate(n1) should be(l1)

    intercept[Exception] {
      translate(unmapped1)
    }

    //Not
    translate(Not(n1)) should be(Not(l1))

    intercept[Exception] {
      translate(Not(unmapped1))
    }

    //DateBounded
    translate(DateBounded(now, now, n1)) should be(DateBounded(now, now, l1))

    intercept[Exception] {
      translate(DateBounded(now, now, unmapped1))
    }

    //OccuranceLimited
    translate(OccuranceLimited(42, n1)) should be(OccuranceLimited(42, l1))

    intercept[Exception] {
      translate(OccuranceLimited(42, unmapped1))
    }

    //Or
    translate(Or(n1)) should be(l1)
    translate(Or(n1, n2)) should be(Or(l1, l2))

    intercept[Exception] {
      translate(Or(n1, unmapped1, n2))
    }

    intercept[Exception] {
      translate(Or())
    }

    intercept[Exception] {
      translate(Or(unmapped1))
    }

    intercept[Exception] {
      translate(Or(unmapped1, unmapped2))
    }

    //And
    translate(And(n1)) should be(l1)
    translate(And(n1, n2)) should be(And(l1, l2))

    intercept[Exception] {
      translate(And(n1, unmapped1, n2))
    }

    intercept[Exception] {
      translate(And())
    }

    intercept[Exception] {
      translate(And(unmapped1))
    }

    intercept[Exception] {
      translate(And(unmapped1, unmapped2))
    }

    //For Ands, any invalid sub-expression makes the whole thing invalid
    intercept[Exception] {
      translate(And(Or(unmapped1, unmapped2), Or(n1)))
    }

    //For Ors, any invalid sub-expression makes the whole thing invalid
    intercept[Exception] {
      translate(Or(And(unmapped1, unmapped2), And(n1, n2))) should be(And(l1, l2))
    }
  }

  @Test
  def translateModifiedTerm(): Unit = {
    val name = "nameasdasfsdf"
    val appliedPath = "appliedPathaskljlaksjf"

    val modifiers = Modifiers(name, appliedPath, n2.value)

    val constrained = Constrained(n1, Some(modifiers), None)

    val mappings = Map(n1.value -> Set(l1.value), n2.value -> Set(l2.value))

    val translator = ExpressionTranslator(mappings)

    val translated = translator.translate(constrained).asInstanceOf[Constrained]

    translated.term should equal(l1)
    translated.modifiers.get.name should equal(name)
    translated.modifiers.get.appliedPath should equal(appliedPath)
    translated.modifiers.get.key should equal(l2.value)
    translated.valueConstraint should be(None)
  }

  @Test
  def translateOrOfModifiedTerms(): Unit = {
    val name = "nameasdasfsdf"
    val appliedPath = "appliedPathaskljlaksjf"

    val modifiers = Modifiers(name, appliedPath, n3.value)

    val modified = Or(Constrained(n1, modifiers, valueConstraint), Constrained(n2, modifiers, valueConstraint))

    val mappings = Map(n1.value -> Set(l1.value), n2.value -> Set(l2.value), n3.value -> Set(l3.value))

    val translator = ExpressionTranslator(mappings)

    val translated = translator.translate(modified).asInstanceOf[Or]

    val Seq(cons1: Constrained, cons2: Constrained) = translated.exprs

    cons1.term should equal(l1)
    cons1.modifiers.get.name should equal(name)
    cons1.modifiers.get.appliedPath should equal(appliedPath)
    cons1.modifiers.get.key should equal(l3.value)
    cons1.valueConstraint.get should be(valueConstraint)

    cons2.term should equal(l2)
    cons2.modifiers.get.name should equal(name)
    cons2.modifiers.get.appliedPath should equal(appliedPath)
    cons2.modifiers.get.key should equal(l3.value)
    cons2.valueConstraint.get should be(valueConstraint)
  }

  @Test
  def translateOrOfModifiedAndUnModifiedTerms(): Unit = {
    val name = "nameasdasfsdf"
    val appliedPath = "appliedPathaskljlaksjf"

    val modifiers = Modifiers(name, appliedPath, n3.value)

    val constrained = Or(Constrained(n1, Some(modifiers), None), n2)

    val mappings = Map(n1.value -> Set(l1.value), n2.value -> Set(l2.value), n3.value -> Set(l3.value))

    val translator = ExpressionTranslator(mappings)

    val translated = translator.translate(constrained).asInstanceOf[Or]

    val Seq(e1: Constrained, e2: Term) = translated.exprs

    e1.term should equal(l1)
    e1.modifiers.get.name should equal(name)
    e1.modifiers.get.appliedPath should equal(appliedPath)
    e1.modifiers.get.key should equal(l3.value)
    e1.valueConstraint should be(None)

    e2 should equal(l2)
  }

  @Test
  def translateOrOfModifiedAndUnMappedUnModifiedTerms(): Unit = {
    val name = "nameasdasfsdf"
    val appliedPath = "appliedPathaskljlaksjf"

    val modifiers = Modifiers(name, appliedPath, n3.value)

    val constrained = Or(Constrained(n1, Some(modifiers), None), unmapped1)
    val goodConstrained = Or(Constrained(n1, Some(modifiers), None))

    val mappings = Map(n1.value -> Set(l1.value), n2.value -> Set(l2.value), n3.value -> Set(l3.value))

    val translator = ExpressionTranslator(mappings)

    intercept[Exception] {
      translator.translate(constrained)
    }

    val translated = translator.translate(goodConstrained).asInstanceOf[Constrained]
    translated.term should equal(l1)
    translated.modifiers.get.name should equal(name)
    translated.modifiers.get.appliedPath should equal(appliedPath)
    translated.modifiers.get.key should equal(l3.value)
    translated.valueConstraint should be(None)
  }

  @Test
  def translateOrOfUnMappedModifiedAndUnModifiedTerms(): Unit = {
    val name = "nameasdasfsdf"
    val appliedPath = "appliedPathaskljlaksjf"

    val modifiers = Modifiers(name, appliedPath, n3.value)

    val constrained = Or(Constrained(unmapped1, modifiers, valueConstraint), n1)
    val goodConstrained = Or(n1)

    val mappings = Map(n1.value -> Set(l1.value), n2.value -> Set(l2.value), n3.value -> Set(l3.value))

    val translator = ExpressionTranslator(mappings)

    intercept[Exception] {
      translator.translate(constrained)
    }

    val translated = translator.translate(goodConstrained).asInstanceOf[Term]

    translated should equal(l1)
  }

  @Test
  def translateModifiedTermTermIsUnMapped(): Unit = {
    val name = "nameasdasfsdf"
    val appliedPath = "appliedPathaskljlaksjf"

    val modifiers = Modifiers(name, appliedPath, n2.value)

    val constrained = Constrained(n3, modifiers, valueConstraint)

    val mappings = Map(n1.value -> Set(l1.value), n2.value -> Set(l2.value))

    val translator = ExpressionTranslator(mappings)

    intercept[Exception] {
      translator.translate(constrained)
    }
  }

  @Test
  def translateModifiedTermModifierKeyIsUnMapped(): Unit = {
    val name = "nameasdasfsdf"
    val appliedPath = "appliedPathaskljlaksjf"

    val modifiers = Modifiers(name, appliedPath, n3.value)

    val constrained = Constrained(n1, modifiers, valueConstraint)

    val mappings = Map(n1.value -> Set(l1.value), n2.value -> Set(l2.value))

    val translator = ExpressionTranslator(mappings)

    intercept[Exception] {
      translator.translate(constrained)
    }
  }

  @Test
  def translateModifiedTermModifierKeyIsMappedToMultipleLocalTerms(): Unit = {
    val name = "nameasdasfsdf"
    val appliedPath = "appliedPathaskljlaksjf"

    val modifiers = Modifiers(name, appliedPath, n2.value)

    val constrained = Constrained(n1, modifiers, valueConstraint)

    val mappings = Map(n1.value -> Set(l1.value), n2.value -> Set(l2.value, l3.value))

    val translator = ExpressionTranslator(mappings)

    intercept[Exception] {
      translator.translate(constrained)
    }
  }

  @Test
  def translateModifiedTermTermAndModifierKeyIsUnMapped(): Unit = {
    val name = "nameasdasfsdf"
    val appliedPath = "appliedPathaskljlaksjf"

    val modifiers = Modifiers(name, appliedPath, n3.value)

    val constrained = Constrained(n3, modifiers, valueConstraint)

    val mappings = Map(n1.value -> Set(l1.value), n2.value -> Set(l2.value))

    val translator = ExpressionTranslator(mappings)

    intercept[Exception] {
      translator.translate(constrained)
    }
  }

  @Test
  def translateModifiedTermMappedToMultiupleLocals(): Unit = {
    val name = "nameasdasfsdf"
    val appliedPath = "appliedPathaskljlaksjf"

    val modifiers = Modifiers(name, appliedPath, n3.value)

    val constrained = Constrained(n1, modifiers, valueConstraint)

    val mappings = Map(n1.value -> Set(l1.value, l2.value), n3.value -> Set(l3.value))

    val translator = ExpressionTranslator(mappings)

    val translated = translator.translate(constrained).asInstanceOf[Or]

    val Seq(e1: Constrained, e2: Constrained) = translated.exprs

    e1.term should equal(l1)
    e1.modifiers.get.name should equal(name)
    e1.modifiers.get.appliedPath should equal(appliedPath)
    e1.modifiers.get.key should equal(l3.value)
    e1.valueConstraint.get should be(valueConstraint)

    e2.term should equal(Term(l2.value, n1.name))
    e2.modifiers.get.name should equal(name)
    e2.modifiers.get.appliedPath should equal(appliedPath)
    e2.modifiers.get.key should equal(l3.value)
    e2.valueConstraint.get should be(valueConstraint)
  }

  @Test
  def translateComplexModifiedExpr(): Unit = {
    val name = "nameasdasfsdf"
    val appliedPath = "appliedPathaskljlaksjf"

    val modifiers = Modifiers(name, appliedPath, n3.value)

    val constrained = Constrained(n1, modifiers, valueConstraint)

    val n4 = Term("n4", "n4Name")
    val l4 = Term("l4", "l4Name")
    val n5 = Term("n5", "n5Name")
    val l5 = Term("l5", "l5Name")

    val mappings = Map(n1.value -> Set(l1.value, l2.value), n3.value -> Set(l3.value), n4.value -> Set(l4.value), n5.value -> Set(l5.value))

    val translator = ExpressionTranslator(mappings)

    val now = Some(XmlDateHelper.now)

    val expr = And(DateBounded(now, now, Not(n4)), Or(n5, constrained))

    val translated = translator.translate(expr).asInstanceOf[And]

    val Seq(a1: DateBounded, a2: Or) = translated.exprs

    a1.start should equal(now)
    a1.end should equal(now)
    a1.expr should equal(Not(Term(l4.value, n4.name)))

    val Seq(t1: Term, m2: Constrained, m3: Constrained) = a2.exprs

    t1 should equal(Term(l5.value, n5.name))

    m2.term should equal(l1)
    m2.modifiers.get.name should equal(name)
    m2.modifiers.get.appliedPath should equal(appliedPath)
    m2.modifiers.get.key should equal(l3.value)
    m2.valueConstraint.get should be(valueConstraint)

    m3.term should equal(Term(l2.value, n1.name))
    m3.modifiers.get.name should equal(name)
    m3.modifiers.get.appliedPath should equal(appliedPath)
    m3.modifiers.get.key should equal(l3.value)
    m3.valueConstraint.get should be(valueConstraint)
  }

  @Test
  def testTryTranslate(): Unit = {
    val mappings = Map(n1.value -> Set(l1.value), n2.value -> Set(l2.value))

    val translator = ExpressionTranslator(mappings)
    def tryTranslate(expr: I2b2Expression): Try[I2b2Expression] = Try(translator.translate(expr))


    //mapped terms are valid
    tryTranslate(n1).get should be(l1)
    //unmapped terms aren't
    tryTranslate(n3).isFailure should be(true)

    // If any are unmapped the entire Or is invalid.
    tryTranslate(Or(n1, n3)).isFailure should be(true)
    tryTranslate(Or(n1)).get should be(l1)
    tryTranslate(Or(n1, n3, Or(n3), Or(n1, n2, And(n1, n2, Not(n1))))).isFailure should be(true)

    //no valid terms means the whole Or is invalid
    tryTranslate(Or(n3)).isFailure should be(true)
    tryTranslate(Or()).isFailure should be(true)

    //All terms must be valid for an And to be valid
    tryTranslate(And(n1)).get should be(l1)
    tryTranslate(And(n1, n2)).get should be(And(l1, l2))
    tryTranslate(And(n1, n2, Or(n2, n3), Or(n1, n2, And(n1, n2, Not(n1))))).isFailure should be(true)
    //todo there's a pair of l2, l2, that should normalize out, but i2b2 I don't think can understand this many levels of logic
    tryTranslate(And(n1, n2, Or(n2), Or(n1, n2, And(n1, n2, Not(n1))))).get should be(And(l1, l2, l2, Or(l1, l2, And(l1, l2, Not(l1)))))

    //No valid terms means the And is invalid
    tryTranslate(And()).isFailure should be(true)
    tryTranslate(And(n1, n2, n3)).isFailure should be(true)

    tryTranslate(Not(n1)).get should be(Not(l1))
    tryTranslate(Not(n3)).isFailure should be(true)

    tryTranslate(OccuranceLimited(42, n1)).get should be(OccuranceLimited(42, l1))
    tryTranslate(OccuranceLimited(42, n3)).isFailure should be(true)

    val now = Option(XmlDateHelper.now)

    tryTranslate(DateBounded(now, now, n1)).get should be(DateBounded(now, now, l1))
    tryTranslate(DateBounded(now, now, n3)).isFailure should be(true)

    val validModifiers = Modifiers("n", "ap", n1.value)
    val invalidModifiers = Modifiers("n", "ap", n3.value)

    //valid term, valid modifiers
    tryTranslate(Constrained(n1, Some(validModifiers), None)).get should be(Constrained(l1, Some(validModifiers.copy(key = l1.value)), None))
    //invalid term, valid modifiers
    tryTranslate(Constrained(n3, validModifiers, valueConstraint)).isFailure should be(true)
    //valid term, invalid modifiers
    tryTranslate(Constrained(n1, invalidModifiers, valueConstraint)).isFailure should be(true)
    //invalid term, invalid modifiers
    tryTranslate(Constrained(n3, invalidModifiers, valueConstraint)).isFailure should be(true)
  }

  @Test
  def testUnmappableException(): Unit = {

    val unmappedExpr = Try(ExpressionTranslator(Map[String, Set[String]]()).translate(Or(n1, n2, n3)))
    unmappedExpr.isFailure should be (true)
    unmappedExpr.failed.map{
      case e: CouldNotMapAllTermsException =>
        e.unmappable should be(Set(n1.name, n2.name, n3.name))
      case _ => throw new AssertionError("Unmapped Terms should throw a MappingException.")
    }
  }
  
  @Test
  def testTranslateI2b2ValueQuery(): Unit = {
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
            <item_name>{ n1.name }</item_name>
            <item_key>{ n1.value }</item_key>
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
    
    val expected = Constrained(n1, None, Some(ValueConstraint("NUMBER", Some("mmol/L"), "EQ", "130")))
    
    expr should equal(expected)
    
    val mappings = Map(n1.value -> Set(l1.value))

    val translator = ExpressionTranslator(mappings)
    
    val translated = translator.translate(expr)
    
    translated should equal(Constrained(l1, None, Some(ValueConstraint("NUMBER", Some("mmol/L"), "EQ", "130"))))
  }
}
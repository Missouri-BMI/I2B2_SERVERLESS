package net.shrine.adapter.translators

import net.shrine.protocol.i2b2.query.{CouldNotMapAllTermsException, I2b2Expression, I2b2QueryDefinition, I2b2QuerySpan, I2b2SubQueryConstraint, I2b2SubQueryConstraints, Or, QueryTiming, Term}
import org.junit.Test
import net.shrine.util.ShouldMatchersForJUnit

/**
 * @author Clint Gilbert
 * @since Mar 1, 2012
 *
 */
final class I2b2QueryDefinitionTranslatorTest extends ShouldMatchersForJUnit {
  private val localTerms = Set("localTerm1", "localTerm2")

  private val mappings = Map("twoMatches" -> localTerms, "oneMatch" -> Set("localTerm3"))

  private def queryDef(expr: I2b2Expression) = I2b2QueryDefinition("foo", expr)

  private def queryDefWithOptionalFields(expr: I2b2Expression): I2b2QueryDefinition = {
    val constraints = I2b2SubQueryConstraints(
      "some-op",
      I2b2SubQueryConstraint("x", "y", "z"),
      I2b2SubQueryConstraint("a", "b", "c"),
      Some(I2b2QuerySpan("asdasd", "asdjkjlsad", "sadlasd")))

    val subQueries = Seq(queryDef(expr), queryDef(expr))

    I2b2QueryDefinition("foo", Option(expr), Some(QueryTiming.SameVisit), Some("id"), Some("queryType"), Seq(constraints), subQueries)
  }

  @Test
  def testTranslate(): Unit = {
    val translator = new QueryDefinitionTranslator(ExpressionTranslator(mappings))

    translator.translate(queryDef(Term("oneMatch", "name1"))) should equal(queryDef(Term("localTerm3", "name1")))
    translator.translate(queryDef(Term("twoMatches", "name2"))) should equal(queryDef(Or(Term("localTerm1", "name2"), Term("localTerm2", "name2"))))
  }
  
  @Test
  def testTranslateNoExpr(): Unit = {
    val translator = new QueryDefinitionTranslator(ExpressionTranslator(mappings))

    val noExpr = I2b2QueryDefinition("foo", None)
    
    translator.translate(noExpr) should equal(noExpr)
    
    val noExprButSubqueries = I2b2QueryDefinition("foo", expr = None, subQueries = Seq(queryDef(Term("oneMatch", "namesDon'tChange"))))
    val expected = I2b2QueryDefinition("foo", expr = None, subQueries = Seq(queryDef(Term("localTerm3", "namesDon'tChange"))))
    
    translator.translate(noExprButSubqueries) should equal(expected)
  }

  @Test
  def testTranslateQueryDefWithOptionalFields(): Unit = {
    val translator = new QueryDefinitionTranslator(ExpressionTranslator(mappings))

    translator.translate(queryDefWithOptionalFields(Term("oneMatch", "namesDon'tChange"))) should equal(queryDefWithOptionalFields(Term("localTerm3", "namesDon'tChange")))
    translator.translate(queryDefWithOptionalFields(Term("twoMatches", "namesDon'tChange2"))) should equal(queryDefWithOptionalFields(Or(Term("localTerm1", "namesDon'tChange2"), Term("localTerm2", "namesDon'tChange2"))))
  }

  @Test
  def testOnFailedMapping(): Unit = {
    val unmappedTerm = Term("alskjklasdjl", "alskjklasdjlName")
    val mappedTerm = Term("oneMatch", "oneMatchName")
    
    val unmapped = queryDef(unmappedTerm)
    
    //expr is unmapped, subqueries' exprs aren't
    val unmappedWithOptionalFields0 = queryDefWithOptionalFields(unmappedTerm)
    //expr is unmapped, subqueries' exprs are and aren't
    val unmappedWithOptionalFields1 = queryDefWithOptionalFields(unmappedTerm).copy(subQueries = Seq(queryDef(mappedTerm), queryDef(unmappedTerm)))
    //expr is unmapped, subqueries' exprs are
    val unmappedWithOptionalFields2 = queryDefWithOptionalFields(unmappedTerm).copy(subQueries = Seq(queryDef(mappedTerm), queryDef(mappedTerm)))
    //expr is mapped, subqueries' exprs aren't
    val unmappedWithOptionalFields3 = queryDefWithOptionalFields(unmappedTerm).copy(expr = Some(mappedTerm))

    val translator = new QueryDefinitionTranslator(ExpressionTranslator(mappings))

    intercept[CouldNotMapAllTermsException] {
      translator.translate(unmapped)
    }
    
    intercept[CouldNotMapAllTermsException] {
      translator.translate(unmappedWithOptionalFields0)
    }

    intercept[CouldNotMapAllTermsException] {
      translator.translate(unmappedWithOptionalFields1)
    }
    
    intercept[CouldNotMapAllTermsException] {
      translator.translate(unmappedWithOptionalFields2)
    }
    
    intercept[CouldNotMapAllTermsException] {
      translator.translate(unmappedWithOptionalFields3)
    }
  }
}
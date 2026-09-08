package net.shrine.protocol.i2b2.query

import net.shrine.protocol.i2b2
import net.shrine.util.ShouldMatchersForJUnit
import org.junit.Test

/**
 * @author clint
 * @since Dec 17, 2012
 */
final class CompoundPlanTest extends ShouldMatchersForJUnit {
  private val t1 = Term("1", "1")
  private val t2 = Term("2", "2")
  private val t3 = Term("3", "3")
  private val t4 = Term("4", "4")
  
  private val q1 = I2b2Query("12345")
  
  @Test
  def testToString(): Unit = {
    val plans = Seq(SimplePlan(t1), i2b2.query.SimplePlan(t2))
    
    CompoundPlan.Or(plans: _*).toString should equal("CompoundPlan.Or(" + plans.mkString(",") + ")")
    CompoundPlan.And(plans: _*).toString should equal("CompoundPlan.And(" + plans.mkString(",") + ")")
  }
  
  @Test
  def testCombineAndOr(): Unit = {
    //NB: Also exercises CompoundPlan.{and, or}
    
    CompoundPlan.Or(i2b2.query.SimplePlan(t1)).combine(I2b2Conjunction.Or)(i2b2.query.SimplePlan(t2)) should equal(CompoundPlan.Or(CompoundPlan.Or(i2b2.query.SimplePlan(t1)), i2b2.query.SimplePlan(t2)))
    
    CompoundPlan.And(i2b2.query.SimplePlan(t1)).combine(I2b2Conjunction.Or)(i2b2.query.SimplePlan(t2)) should equal(CompoundPlan.Or(CompoundPlan.And(i2b2.query.SimplePlan(t1)), i2b2.query.SimplePlan(t2)))
    
    CompoundPlan.Or(i2b2.query.SimplePlan(t1)).combine(I2b2Conjunction.And)(i2b2.query.SimplePlan(t2)) should equal(CompoundPlan.And(CompoundPlan.Or(i2b2.query.SimplePlan(t1)), i2b2.query.SimplePlan(t2)))
    
    CompoundPlan.And(i2b2.query.SimplePlan(t1)).combine(I2b2Conjunction.And)(i2b2.query.SimplePlan(t2)) should equal(CompoundPlan.And(CompoundPlan.And(i2b2.query.SimplePlan(t1)), i2b2.query.SimplePlan(t2)))
  }
  
  @Test
  def testIsSimpleIsCompound(): Unit = {
    CompoundPlan.Or().isSimple should be(false)
    CompoundPlan.Or().isCompound should be(true)
    
    CompoundPlan.And().isSimple should be(false)
    CompoundPlan.And().isCompound should be(true)
  }
  
  @Test
  def testIsSame(): Unit = {
    CompoundPlan.Or().isSame(I2b2Conjunction.And) should be(false)
    CompoundPlan.Or().isSame(I2b2Conjunction.Or) should be(true)
    
    CompoundPlan.And().isSame(I2b2Conjunction.And) should be(true)
    CompoundPlan.And().isSame(I2b2Conjunction.Or) should be(false)
  }

  //NB: ------- Companion object methods follow --------
  
  @Test
  def testOr(): Unit = {
    for(exprs <- Seq[Seq[ExecutionPlan]](Nil, Seq(i2b2.query.SimplePlan(t1)), Seq(i2b2.query.SimplePlan(t1), i2b2.query.SimplePlan(t2)))) {
      val plan = CompoundPlan.Or(exprs: _*)
      
      plan.conjunction should equal(I2b2Conjunction.Or)
      
      plan.components should equal(exprs)
    }
  }
  
  @Test
  def testAnd(): Unit = {
    for(exprs <- Seq[Seq[ExecutionPlan]](Nil, Seq(i2b2.query.SimplePlan(t1)), Seq(i2b2.query.SimplePlan(t1), i2b2.query.SimplePlan(t2)))) {
      val plan = CompoundPlan.And(exprs: _*)
      
      plan.conjunction should equal(I2b2Conjunction.And)
      
      plan.components should equal(exprs)
    }
  }
  
  @Test
  def testAllSimple(): Unit = {
    import net.shrine.protocol.i2b2.query.CompoundPlan.Helpers.allSimple
    
    allSimple(Nil) should be(true)
    
    allSimple(Seq(i2b2.query.SimplePlan(t1))) should be(true)
    allSimple(Seq(i2b2.query.SimplePlan(t1), i2b2.query.SimplePlan(t2))) should be(true)
    
    allSimple(Seq(CompoundPlan.Or(i2b2.query.SimplePlan(t1)))) should be(false)
    allSimple(Seq(CompoundPlan.And(i2b2.query.SimplePlan(t1)))) should be(false)
    
    allSimple(Seq(i2b2.query.SimplePlan(t1), CompoundPlan.Or(i2b2.query.SimplePlan(t2)))) should be(false)
    allSimple(Seq(i2b2.query.SimplePlan(t1), CompoundPlan.And(i2b2.query.SimplePlan(t2)))) should be(false)
    
    allSimple(Seq(CompoundPlan.Or(i2b2.query.SimplePlan(t1)), i2b2.query.SimplePlan(t2))) should be(false)
    allSimple(Seq(CompoundPlan.And(i2b2.query.SimplePlan(t1)), i2b2.query.SimplePlan(t2))) should be(false)
  }
  
  @Test
  def testAnds(): Unit = {
    import net.shrine.protocol.i2b2.query.CompoundPlan.Helpers.ands
    
    ands(Nil) should equal(Nil)
    ands(Seq(i2b2.query.SimplePlan(Or(t1, t2)))) should equal(Nil)
    ands(Seq(i2b2.query.SimplePlan(And(t1, t2)))) should equal(Seq(And(t1, t2)))
    ands(Seq(i2b2.query.SimplePlan(And(t1, t2)), i2b2.query.SimplePlan(And(q1, t3)))) should equal(Seq(And(t1, t2), And(q1, t3)))
    ands(Seq(i2b2.query.SimplePlan(i2b2.query.Or(t1, t2)), i2b2.query.SimplePlan(And(q1, t3)), i2b2.query.SimplePlan(t4))) should equal(Seq(And(q1, t3)))
  }
  
  @Test
  def testOrs(): Unit = {
    import net.shrine.protocol.i2b2.query.CompoundPlan.Helpers.ors
    
    ors(Nil) should equal(Nil)
    ors(Seq(i2b2.query.SimplePlan(And(t1, t2)))) should equal(Nil)
    ors(Seq(i2b2.query.SimplePlan(i2b2.query.Or(t1, t2)))) should equal(Seq(i2b2.query.Or(t1, t2)))
    ors(Seq(i2b2.query.SimplePlan(i2b2.query.Or(t1, t2)), i2b2.query.SimplePlan(i2b2.query.Or(q1, t3)))) should equal(Seq(i2b2.query.Or(t1, t2), i2b2.query.Or(q1, t3)))
    ors(Seq(i2b2.query.SimplePlan(And(t1, t2)), i2b2.query.SimplePlan(i2b2.query.Or(q1, t3)), i2b2.query.SimplePlan(t4))) should equal(Seq(i2b2.query.Or(q1, t3)))
  }
  
  @Test
  def testNeitherAndsNorOrs(): Unit = {
    import net.shrine.protocol.i2b2.query.CompoundPlan.Helpers.neitherAndsNorOrs
    
    neitherAndsNorOrs(Nil) should equal(Nil)
    neitherAndsNorOrs(Seq(i2b2.query.SimplePlan(And(t1, t2)))) should equal(Nil)
    neitherAndsNorOrs(Seq(i2b2.query.SimplePlan(i2b2.query.Or(t1, t2)))) should equal(Nil)
    neitherAndsNorOrs(Seq(i2b2.query.SimplePlan(i2b2.query.Or(t1, t2)), i2b2.query.SimplePlan(i2b2.query.Or(q1, t3)))) should equal(Nil)
    neitherAndsNorOrs(Seq(i2b2.query.SimplePlan(i2b2.query.Or(t1, t2)), i2b2.query.SimplePlan(i2b2.query.Or(q1, t3)), i2b2.query.SimplePlan(t4))) should equal(Seq(i2b2.query.SimplePlan(t4)))
  }
  
  @Test
  def testAndHasZero(): Unit = {
    CompoundPlan.Helpers.HasZero.andHasZero.zero.isInstanceOf[And] should be(true)
    CompoundPlan.Helpers.HasZero.andHasZero.zero.exprs.isEmpty should be(true)
  }
  
  @Test
  def testOrHasZero(): Unit = {
    CompoundPlan.Helpers.HasZero.orHasZero.zero.isInstanceOf[Or] should be(true)
    CompoundPlan.Helpers.HasZero.orHasZero.zero.exprs.isEmpty should be(true)
  }
  
  @Test
  def testFlatten(): Unit = {
    import net.shrine.protocol.i2b2.query.CompoundPlan.Helpers.flatten
    
    flatten[And](Nil) should equal(And())
    flatten(Seq(And(), And())) should equal(And())
    flatten(Seq(And(), And(t1, q1))) should equal(And(t1, q1))
    flatten(Seq(And(t1, q1), And())) should equal(And(t1, q1))
    flatten(Seq(And(t1), And(q1))) should equal(And(t1, q1))
    flatten(Seq(And(t1), And(q1), And(t2, t3))) should equal(And(t1, q1, t2, t3))
    
    flatten[Or](Nil) should equal(i2b2.query.Or())
    flatten(Seq(i2b2.query.Or(), i2b2.query.Or())) should equal(i2b2.query.Or())
    flatten(Seq(i2b2.query.Or(), i2b2.query.Or(t1, q1))) should equal(i2b2.query.Or(t1, q1))
    flatten(Seq(i2b2.query.Or(t1, q1), i2b2.query.Or())) should equal(i2b2.query.Or(t1, q1))
    flatten(Seq(i2b2.query.Or(t1), i2b2.query.Or(q1))) should equal(i2b2.query.Or(t1, q1))
    flatten(Seq(i2b2.query.Or(t1), i2b2.query.Or(q1), i2b2.query.Or(t2, t3))) should equal(i2b2.query.Or(t1, q1, t2, t3))
  }
  
  @Test
  def testToPlans(): Unit = {
    import net.shrine.protocol.i2b2.query.CompoundPlan.Helpers.toPlans
    
    toPlans(Nil) should equal(Nil)
    toPlans(Seq(t1, q1)) should equal(Seq(i2b2.query.SimplePlan(t1), i2b2.query.SimplePlan(q1)))
    
    val Seq(CompoundPlan(actualConj, components @ _*)) = toPlans(Seq(i2b2.query.Or(t1, And(q1, t2))))
    
    actualConj should equal(I2b2Conjunction.Or)
    components.toSet should equal(Set(i2b2.query.SimplePlan(t1), i2b2.query.SimplePlan(And(q1, t2))))
  }
}
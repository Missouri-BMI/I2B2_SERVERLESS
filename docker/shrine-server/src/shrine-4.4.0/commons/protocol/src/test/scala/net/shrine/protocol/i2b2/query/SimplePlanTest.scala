package net.shrine.protocol.i2b2.query

import net.shrine.protocol.i2b2.query
import net.shrine.util.ShouldMatchersForJUnit
import org.junit.Test

/**
 * @author clint
 * @since Dec 17, 2012
 */
final class SimplePlanTest extends ShouldMatchersForJUnit {
  //NB: Tests for SimplePlan.normalize handled by ExpressionTest.test*ToExecutionPlan*()
  
  private val t1 = Term("1", "1")
  private val t2 = Term("2", "2")
  private val t3 = Term("3", "3")
  
  @Test
  def testOr(): Unit = {
    val plan = SimplePlan(t1)
    
    plan.or(query.SimplePlan(t2)) should equal(query.SimplePlan(Or(t1, t2)))
    
    plan.or(CompoundPlan.Or(query.SimplePlan(t2), query.SimplePlan(t3))) should equal(CompoundPlan.Or(query.SimplePlan(t1), CompoundPlan.Or(query.SimplePlan(t2), query.SimplePlan(t3))))
    plan.or(CompoundPlan.And(query.SimplePlan(t2), query.SimplePlan(t3))) should equal(CompoundPlan.Or(query.SimplePlan(t1), CompoundPlan.And(query.SimplePlan(t2), query.SimplePlan(t3))))
  }
  
  @Test
  def testAnd(): Unit = {
    val plan = query.SimplePlan(t1)
    
    plan.and(query.SimplePlan(t2)) should equal(query.SimplePlan(And(t1, t2)))
    
    plan.and(CompoundPlan.Or(query.SimplePlan(t2), query.SimplePlan(t3))) should equal(CompoundPlan.And(query.SimplePlan(t1), CompoundPlan.Or(query.SimplePlan(t2), query.SimplePlan(t3))))
    plan.and(CompoundPlan.And(query.SimplePlan(t2), query.SimplePlan(t3))) should equal(CompoundPlan.And(query.SimplePlan(t1), CompoundPlan.And(query.SimplePlan(t2), query.SimplePlan(t3))))
  }

  //NB: Tests for SimplePlan.combine handled by testOr() and testAnd() 
  
  @Test
  def testWithExpr(): Unit = {
    val plan1 = query.SimplePlan(t1)
    
    (plan1 eq plan1.withExpr(t1)) should be(true)
    
    val plan2 = plan1.withExpr(t2)
    
    (plan1 eq plan2) should be(false)
    
    plan1.expr should equal(t1)
    plan2.expr should equal(t2)
  }
  
  @Test
  def testIsSimpleIsCompound(): Unit = {
    query.SimplePlan(t1).isSimple should be(true)
    query.SimplePlan(t1).isCompound should be(false)
  }
}
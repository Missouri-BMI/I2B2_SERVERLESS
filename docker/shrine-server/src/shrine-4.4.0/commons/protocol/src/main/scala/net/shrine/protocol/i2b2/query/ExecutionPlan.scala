package net.shrine.protocol.i2b2.query

/**
 * @author clint
 * @since Nov 29, 2012
 */
sealed trait ExecutionPlan {
  def or(other: ExecutionPlan): ExecutionPlan

  def and(other: ExecutionPlan): ExecutionPlan

  def combine(conjunction: I2b2Conjunction)(other: ExecutionPlan): ExecutionPlan

  def normalize: ExecutionPlan

  def isSimple: Boolean

  final def isCompound: Boolean = !isSimple
}

/**
 * @author clint
 * @since Nov 29, 2012
 */
final case class SimplePlan(expr: I2b2Expression) extends ExecutionPlan {
  override def or(other: ExecutionPlan): ExecutionPlan = combine(I2b2Conjunction.Or)(other)

  override def and(other: ExecutionPlan): ExecutionPlan = combine(I2b2Conjunction.And)(other)

  override def combine(conjunction: I2b2Conjunction)(other: ExecutionPlan): ExecutionPlan = other match {
    case SimplePlan(otherExpr) => SimplePlan(conjunction.combine(Seq(expr, otherExpr)).normalize)
    case _: CompoundPlan => CompoundPlan(conjunction, this, other)
  }

  def withExpr(e: I2b2Expression): SimplePlan = {
    if(e == expr) this
    else SimplePlan(e)
  }
  
  override def normalize: ExecutionPlan = withExpr(expr.normalize)

  override def isSimple: Boolean = true
}

/**
 * @author clint
 * @since Nov 29, 2012
 */
final case class CompoundPlan(conjunction: I2b2Conjunction, components: ExecutionPlan*) extends ExecutionPlan {
  override def toString: String = "CompoundPlan." + conjunction + "(" + components.mkString(",") + ")"

  override def or(other: ExecutionPlan): ExecutionPlan = CompoundPlan.Or(this, other)

  override def and(other: ExecutionPlan): ExecutionPlan = CompoundPlan.And(this, other)

  override def combine(conj: I2b2Conjunction)(other: ExecutionPlan): ExecutionPlan = conj match {
    case I2b2Conjunction.Or => or(other)
    case I2b2Conjunction.And => and(other)
  }

  override def isSimple: Boolean = false

  private[query] def isSame(conj: I2b2Conjunction) = conj == this.conjunction

  override def normalize: ExecutionPlan = {
    import CompoundPlan.Helpers._

    components match {
      case Seq(singlePlan) => singlePlan.normalize
      case _ => CompoundPlan(conjunction, components.flatMap {
        case CompoundPlan(conj, comps @ _*) if allSimple(comps) && isSame(conj) =>

          val asSimplePlans = comps.collect { case p: SimplePlan => p }

          val simpleQueriesGroupedByExprType: Map[Class[_], Seq[SimplePlan]] = asSimplePlans.groupBy(_.expr.getClass)

          val andPlans = simpleQueriesGroupedByExprType.getOrElse(classOf[And], Seq.empty)

          val orPlans = simpleQueriesGroupedByExprType.getOrElse(classOf[Or], Seq.empty)

          val otherPlans = (simpleQueriesGroupedByExprType - classOf[And] - classOf[Or]).values.flatten.toSeq

          val otherExprs = otherPlans.map(_.expr)

          val (orExprs: Seq[Or], andExprs: Seq[And]) = conj match {
            case I2b2Conjunction.Or =>
              val consolidatedOrExpr = flatten(ors(orPlans))

              (Seq(consolidatedOrExpr ++ otherExprs), ands(andPlans))
            case I2b2Conjunction.And =>
              val consolidatedAndExpr = flatten(ands(andPlans))

              (ors(orPlans), Seq(consolidatedAndExpr ++ otherExprs))
          }

          toPlans(orExprs) ++ toPlans(andExprs) ++ otherPlans
        case c => Seq(c)
      }: _*)
    }
  }
}

object CompoundPlan {
  def Or(components: ExecutionPlan*): CompoundPlan = CompoundPlan(I2b2Conjunction.Or, components: _*)

  def And(components: ExecutionPlan*): CompoundPlan = CompoundPlan(I2b2Conjunction.And, components: _*)

  private[query] object Helpers {
    def allSimple(plans: Seq[ExecutionPlan]): Boolean = plans.forall(_.isSimple)

    def ands(plans: Seq[SimplePlan]): Seq[And] = plans.collect { case SimplePlan(a: And) => a }

    def ors(plans: Seq[SimplePlan]): Seq[Or] = plans.collect { case SimplePlan(o: Or) => o }

    def neitherAndsNorOrs(plans: Seq[SimplePlan]): Seq[SimplePlan] = {
      import ExpressionHelpers.is

      def isNeitherAndNorOr(expr: I2b2Expression) = !is[And](expr) && !is[Or](expr)

      plans.collect { case plan @ SimplePlan(expr) if isNeitherAndNorOr(expr) => plan }
    }

    private[query] trait HasZero[T] {
      def zero: T
    }

    private[query] object HasZero {

      import net.shrine.protocol.i2b2.query.{And => AndExpr, Or => OrExpr}

      implicit val andHasZero: HasZero[AndExpr] = new HasZero[AndExpr] {
        override def zero: AndExpr = AndExpr()
      }

      implicit val orHasZero: HasZero[OrExpr] = new HasZero[OrExpr] {
        override def zero: OrExpr = OrExpr()
      }
    }

    def flatten[T <: ComposeableI2b2Expression[T] : HasZero](exprs: Seq[T]): T = {
      exprs.foldLeft(implicitly[HasZero[T]].zero)(_ merge _)
    }

    def toPlans(es: Seq[I2b2Expression]): Seq[ExecutionPlan] = es.map(_.toExecutionPlan)
  }
}

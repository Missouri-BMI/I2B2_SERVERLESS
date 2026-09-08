package net.shrine.protocol.i2b2.query

import net.shrine.protocol.i2b2.query.I2b2Expression.mappingFailure
import net.shrine.log.{Log, Loggable}
import net.shrine.protocol.i2b2.serialization.{XmlMarshaller, XmlUnmarshaller}
import net.shrine.util.Tries
import net.shrine.xml.{XmlDateHelper, XmlUtil}

import javax.xml.datatype.XMLGregorianCalendar
import scala.util.matching.Regex
import scala.util.{Failure, Success, Try}
import scala.xml.{Node, NodeSeq, Utility}

/**
 *
 * @author Clint Gilbert
 * @since Jan 24, 2012
 *
 * @see http://cbmi.med.harvard.edu
 *
 * This software is licensed under the LGPL
 * @see http://www.gnu.org/licenses/lgpl.html
 *
 * Classes to form expression trees representing Shrine queries
 */
sealed trait I2b2Expression extends XmlMarshaller {
  def normalize: I2b2Expression = this

  def hasDirectI2b2Representation: Boolean

  def toExecutionPlan: ExecutionPlan

  def isTerm: Boolean = false

  /** perform fn on every term in the expression (including modifiers) and collect the result starting from base. */
  private[query] def foldTerms[A](base: A)(fn: (Term, A) => A): A

  /**  Given translated terms (possibly multiple), return an expression with those terms substituted. */
  private[query] def translate(translated: Map[String, Set[String]]): Try[I2b2Expression]
}

object I2b2Expression extends XmlUnmarshaller[Try[I2b2Expression]] {

  import Tries.sequence

  def fromXml(nodeSeq: NodeSeq): Try[I2b2Expression] = {
    def dateFromXml(dateString: String): Option[XMLGregorianCalendar] = {
      XmlDateHelper.parseXmlTime(dateString).toOption
    }

    if (nodeSeq.isEmpty) { Try(Or()) }
    else {
      import net.shrine.xml.NodeSeqEnrichments.Strictness._

      val outerTag = Utility.trim(nodeSeq.head)

      val childTags:Seq[Node] = outerTag.child

      outerTag.label match {
        case "term" => for {
          value <- outerTag.withChild("value").map(XmlUtil.trim)
          name <- outerTag.withChild("name").map(XmlUtil.trim)
        } yield Term(value, name)

        case "withTiming" =>
          for {
            timing <- outerTag.withChild("timing").map(XmlUtil.trim).flatMap(PanelTiming.tryValueOf)
            expr <- outerTag.withChild("expr").map(_.head.child.head).flatMap(I2b2Expression.fromXml)
          } yield WithTiming(timing, expr)
        case "constrainedTerm" =>
          for {
            term <- fromXml(outerTag \ "term").map(_.asInstanceOf[Term])
            modifiers = outerTag.withChild("modifier").flatMap(Modifiers.fromXml).toOption
            constraint = outerTag.withChild("valueConstraint").flatMap(ValueConstraint.fromXml).toOption
          } yield {
            Constrained(term, modifiers, constraint)
          }
        case "query" => Try(I2b2Query(XmlUtil.trim(outerTag)))
        //childTags.head because only one child expr of <not> is allowed
        case "not" => fromXml(outerTag \ "_").map(Not)
        case "and" =>
          sequence(childTags.map(fromXml)).map(ex => And(ex : _*))
        case "or" =>
          sequence(childTags.map(fromXml)).map(ex => Or(ex : _*))
        case "dateBounded" =>
          for {
            //drop(2) to lose <start> and <end>
            //childTags.drop(2).head because only one child expr of <dateBounded> is allowed
            expr <- fromXml(childTags.drop(2).head)
            start = dateFromXml(XmlUtil.trim(nodeSeq \ "start"))
            end = dateFromXml(XmlUtil.trim(nodeSeq \ "end"))
          } yield DateBounded(start, end, expr)
        case "occurs" =>
          for {
            min <- nodeSeq.withChild("min").map(XmlUtil.toInt)
            //drop(1) to lose <min>
            //childTags.drop(2).head because only one child expr of <occurs> is allowed
            expr <- fromXml(childTags.drop(1).head)
          } yield OccuranceLimited(min, expr)
        case _ => Failure(new Exception(s"Cannot parse xml: '${nodeSeq.toString}'")) //TODO some sort of unmarshalling exception
      }
    }
  }

  def translate(expr: I2b2Expression, lookup: Seq[String] => Map[String, Set[String]]): I2b2Expression = {
    val mapped = verifyMappings(expr, lookup(termsToTranslate(expr)))
    val translated = expr.translate(mapped).get
    Log.debug(s"translated is $translated")
    translated
  }

  private def verifyMappings(expr:I2b2Expression, mappings: Map[String, Set[String]]): Map[String, Set[String]] = {
    val unmapped = mappings.filter(_._2.isEmpty)
    if (unmapped.nonEmpty) {
      throw CouldNotMapAllTermsException(termNamesForKeys(expr, unmapped.keySet))
    } else {
      Log.debug(s"unmapped is empty $unmapped")
      mappings
    }
  }

  private def termsToTranslate(expr: I2b2Expression): Seq[String] =
    expr.foldTerms(Seq[String]()) { (t: Term, acc: Seq[String]) =>
      t.value +: acc
    }

  private def termNamesForKeys(expr: I2b2Expression, keys: Set[String]): Set[String] = {
    expr.foldTerms(Set[String]()) { (t: Term, acc: Set[String]) =>
      if (keys.contains(t.value)) acc + t.name
      else acc
    }
  }

  def mappingFailure[T](message: String): Failure[T] =
    Failure(MappingException(message))
}

trait HasSimpleRepresentation { self: I2b2Expression =>
  override def hasDirectI2b2Representation = true

  override def toExecutionPlan: SimplePlan = SimplePlan(this)
}

trait SimpleI2b2Expression extends I2b2Expression with HasSimpleRepresentation {
  def value: String
  def name: String

  def computeHLevel: Try[Int]
}

trait KnowsOwnType {
  type MyType <: I2b2Expression
}

trait MappableExpression { self: I2b2Expression with KnowsOwnType =>
  def map(f: I2b2Expression => I2b2Expression): MyType
}

trait HasSingleSubExpression { self: I2b2Expression with KnowsOwnType =>
  val expr: I2b2Expression

  def withExpr(newExpr: I2b2Expression): MyType

  override def foldTerms[A](base: A)(fn: (Term, A) => A): A =
    expr.foldTerms(base)(fn)

  override def translate(translated: Map[String, Set[String]]): Try[I2b2Expression] = expr.translate(translated).map(withExpr)
}

trait HasSubExpressions { self: I2b2Expression =>
  val exprs: Seq[I2b2Expression]
}

trait HasHLevel { self: SimpleI2b2Expression =>
  override def computeHLevel: Try[Int] = {
    //Super-dumb way: calculate nesting level by dropping prefix and counting \'s
    Try(value.drop("\\\\SHRINE\\SHRINE\\".length).count(_ == '\\'))
  }
}

//NOTE - refactoring the field name value will break json deserialization for this case class
final case class Term(override val value: String, override val name: String) extends I2b2Expression with SimpleI2b2Expression with HasHLevel with HasSimpleRepresentation with Loggable {
  override def toXml: NodeSeq = XmlUtil.stripWhitespace(
    <term>
      <value>{value}</value>
      <name>{name}</name>
    </term>
  )

  override def isTerm = true

  override def foldTerms[A](base: A)(fn: (Term, A) => A): A = fn(this, base)

  override def translate(translated: Map[String, Set[String]]): Try[I2b2Expression] = {
    val localTerms = translated.get(value).fold {
      throw new IllegalStateException("String value of Term must be present in translated terms map.")
    }{terms => terms}

    localTerms.size match {
      case 0 => mappingFailure(s"No local terms mapped to '$value'")
      case 1 => Try(if (localTerms.head == value) this else Term(localTerms.head, name))
      case _ => Try(Or(localTerms.map(Term(_, name)).toSeq: _*))
    }
  }
}

final case class I2b2Query(localMasterId: String) extends I2b2Expression with SimpleI2b2Expression with HasSimpleRepresentation {
  override val value = s"${I2b2Query.prefix}$localMasterId"
  override val name = s"${I2b2Query.prefix}$localMasterId"

  override def toXml: NodeSeq = XmlUtil.stripWhitespace(<query>{ localMasterId }</query>)

  override def computeHLevel: Try[Int] = Success(0)

  override private[query] def foldTerms[A](base: A)(fn: (Term, A) => A): A = base

  override def translate(translated: Map[String, Set[String]]): Try[I2b2Expression] = Success(this)
}

object I2b2Query {
  val prefix = "masterid:"

  val prefixRegex: Regex = (s"^$prefix(.+?)").r

  def fromString(value: String): Option[I2b2Query] = value match {
    case null => None
    case prefixRegex(masterId) => Some(I2b2Query(masterId))
    case _ => None
  }
}

final case class Constrained(term: Term, modifiers: Option[Modifiers], valueConstraint: Option[ValueConstraint]) extends I2b2Expression with HasSimpleRepresentation with Loggable { self: I2b2Expression =>
  def toTuple: (Term, Option[Modifiers], Option[ValueConstraint]) = (term, modifiers, valueConstraint)

  override def toXml: NodeSeq = XmlUtil.stripWhitespace {
    <constrainedTerm>
      { term.toXml }
      { modifiers.map(_.toXml).orNull }
      { valueConstraint.map(_.toXml).orNull }
    </constrainedTerm>
  }

  override def normalize: I2b2Expression = {
    if (modifiers.isEmpty && valueConstraint.isEmpty) { term.normalize }
    else { this }
  }

  override def isTerm = true

  def withTerm(newTerm: Term): Constrained = copy(term = newTerm)

  def withModifiers(newMods: Modifiers): Constrained = copy(modifiers = Option(newMods))
  def withModifiers(newMods: Option[Modifiers]): Constrained = copy(modifiers = newMods)

  def withValueConstraint(newConstraint: ValueConstraint): Constrained = copy(valueConstraint = Option(newConstraint))

  override private[query] def foldTerms[A](base: A)(fn: (Term, A) => A): A =
    modifiers.fold(term.foldTerms(base)(fn)) { mods: Modifiers =>
      term.foldTerms(Term(mods.key, mods.name).foldTerms(base)(fn))(fn)
    }

  override def translate(translated: Map[String, Set[String]]): Try[I2b2Expression] =  {
    def constrainTranslatedExpression(translatedExpr: I2b2Expression, translatedModifiersToApply: Option[Modifiers]): Try[I2b2Expression] = {
      def makeExprFrom(translatedTerm: Term): Constrained = this.withTerm(translatedTerm).withModifiers(translatedModifiersToApply)

      translatedExpr match {
        case translatedTerm: Term => Try(makeExprFrom(translatedTerm))
        case translatedOr: Or =>
          debug("Applying modifiers to multi-term translation result")

          Try(translatedOr.map {
            case t: Term => makeExprFrom(t)
            case e: I2b2Expression => e
          })
        //NB: Intentionally fail loudly
        case unexpected => mappingFailure(s"Unexpected translation result: '$unexpected'")
      }
    }

    def applyModifiersToSubexpressions(translatedExpr: I2b2Expression, translatedModifierKeyExpr: I2b2Expression): Try[I2b2Expression] = {
      val translatedModifierKey = translatedModifierKeyExpr.asInstanceOf[Term].value
      val translatedModifiers = this.modifiers.map(_.copy(key = translatedModifierKey))

      constrainTranslatedExpression(translatedExpr, translatedModifiers)
    }

    def translateModifierKey(key: String, name: String, translated: Map[String, Set[String]]): Try[I2b2Expression] = {
      Term(key, name).translate(translated) match {
        case s @ Success(_) => s
        case _ => mappingFailure(s"Couldn't map modifier key '$key'; it must be mapped to a single local term")
      }
    }

    modifiers match {
      case None =>
        for {
          translatedExpr <- term.translate(translated)
          result <- constrainTranslatedExpression(translatedExpr, None)
        } yield result
      case Some(mods) =>
        for {
          // pop the modifiers then let the term pop itself.
          translatedModifierKeyExpr <- translateModifierKey(mods.key, mods.name, translated)
          translatedExpr <- term.translate(translated)
          result <- applyModifiersToSubexpressions(translatedExpr, translatedModifierKeyExpr)
        } yield {
          result
        }
    }
  }
}

object Constrained {
  def apply(term: Term, modifiers: Modifiers, valueConstraint: ValueConstraint) = new Constrained(term, Option(modifiers), Option(valueConstraint))
}

final case class WithTiming(timing: PanelTiming, expr: I2b2Expression) extends I2b2Expression with MappableExpression with HasSingleSubExpression with KnowsOwnType {
  override type MyType = WithTiming

  override def withExpr(newExpr: I2b2Expression): WithTiming = if (newExpr eq expr) this else this.copy(expr = newExpr)

  override def toXml: NodeSeq = XmlUtil.stripWhitespace {
    <withTiming>
      <timing>{ timing }</timing>
      <expr>
        { expr.toXml }
      </expr>
    </withTiming>
  }

  override def normalize: I2b2Expression = {
    val normalizedExpr = expr.normalize

    timing match {
      case PanelTiming.Any => normalizedExpr
      case _ => withExpr(normalizedExpr)
    }
  }

  override def hasDirectI2b2Representation: Boolean = expr.hasDirectI2b2Representation

  override def toExecutionPlan: ExecutionPlan = SimplePlan(this.normalize)

  override def map(f: I2b2Expression => I2b2Expression): WithTiming = withExpr(f(expr))

}

final case class Not(expr: I2b2Expression) extends I2b2Expression with MappableExpression with HasSingleSubExpression with KnowsOwnType {
  type MyType = Not

  override def withExpr(newExpr: I2b2Expression): Not = if (newExpr eq expr) this else this.copy(expr = newExpr)

  override def toXml: NodeSeq = XmlUtil.stripWhitespace(<not>{ expr.toXml }</not>)

  override def normalize: I2b2Expression = {
    expr match {
      //Collapse repeated Nots: Not(Not(e)) => e
      case Not(e) => e.normalize
      case _ => this.withExpr(expr.normalize)
    }
  }

  override def hasDirectI2b2Representation: Boolean = expr.hasDirectI2b2Representation

  override def toExecutionPlan: ExecutionPlan = SimplePlan(this.normalize)

  override def map(f: I2b2Expression => I2b2Expression): Not = withExpr(f(expr))
}

abstract class ComposeableI2b2Expression[T <: ComposeableI2b2Expression[T]: Manifest](Op: Seq[I2b2Expression] => T, override val exprs: I2b2Expression*) extends I2b2Expression with HasSubExpressions with MappableExpression with KnowsOwnType {
  override type MyType = T

  import ExpressionHelpers.is

  def containsA[E: Manifest]: Boolean = exprs.exists(is[E])

  def ++(es: Iterable[I2b2Expression]): T = Op(exprs ++ es)

  def merge(other: T): T = Op(exprs ++ other.exprs)

  private[query] lazy val empty: T = Op(Seq.empty)

  private[query] def toIterable(e: I2b2Expression): Iterable[I2b2Expression] = e match {
    case op: T if is[T](op) => op.exprs
    case _ => Seq(e)
  }

  override def map(f: I2b2Expression => I2b2Expression): T = Op(exprs.map(f))

  def filter(p: I2b2Expression => Boolean): T = Op(exprs.filter(p))

  override def normalize: I2b2Expression = {
    val result = exprs.map(_.normalize) match {
      case x if x.isEmpty => this
      case Seq(expr) => expr
      case es => es.foldLeft(empty)((accumulator, expr) => accumulator ++ toIterable(expr))
    }

    result match {
      case op: T if is[T](op) && op.containsA[T] => op.normalize
      case _ => result
    }
  }

  override private[query] def foldTerms[A](base: A)(fn: (Term, A) => A): A =
    exprs.foldRight(base) {
      (expr: I2b2Expression, accumulator: A) =>
        expr.foldTerms(accumulator)(fn)
    }

  override def translate(translated: Map[String, Set[String]]): Try[I2b2Expression] = {
    if (exprs.isEmpty) { mappingFailure(s"Empty Composeable-expressions are considered invalid") }
    else {
      val translatedSubExprAttempts: Seq[Try[I2b2Expression]] = exprs.map(_.translate(translated))

      //All sub-expressions must be translatable for a composeable expression to be translatable.
      //Util.Tries.sequence will return the first failure, if any
      for {
        translatedSubExprs <- Tries.sequence(translatedSubExprAttempts)
      } yield Op(translatedSubExprs)
    }
  }
}

final case class And(override val exprs: I2b2Expression*) extends ComposeableI2b2Expression[And](And.apply, exprs: _*) {

  override def toString: String = "And(" + exprs.mkString(",") + ")"

  override def toXml: NodeSeq = XmlUtil.stripWhitespace(<and>{ exprs.map(_.toXml) }</and>)

  override def hasDirectI2b2Representation: Boolean = exprs.forall(_.hasDirectI2b2Representation)

  override def toExecutionPlan: ExecutionPlan = {
    if (hasDirectI2b2Representation) {
      SimplePlan(this.normalize)
    } else {
      CompoundPlan.And(exprs.map(_.toExecutionPlan): _*).normalize
    }
  }
}

final case class Or(override val exprs: I2b2Expression*) extends ComposeableI2b2Expression[Or](Or.apply, exprs: _*) {

  override def toString: String = "Or(" + exprs.mkString(",") + ")"

  override def toXml: NodeSeq = XmlUtil.stripWhitespace(<or>{ exprs.map(_.toXml) }</or>)

  import ExpressionHelpers.is

  override def hasDirectI2b2Representation: Boolean = exprs.forall(e => !is[And](e) && e.hasDirectI2b2Representation)

  override def toExecutionPlan: ExecutionPlan = {
    if (hasDirectI2b2Representation) {
      SimplePlan(this.normalize)
    } else {
      val (ands, notAnds) = exprs.partition(is[And])

      val andPlans = ands.map(_.toExecutionPlan)

      val andCompound = CompoundPlan.Or(andPlans: _*)

      if (notAnds.isEmpty) {
        andCompound
      } else {
        val notAndPlans = notAnds.map(_.toExecutionPlan)

        val consolidatedNotAndPlan = notAndPlans.reduce(_ or _)

        val components: Seq[ExecutionPlan] = andPlans.size match {
          case 1 => andPlans :+ consolidatedNotAndPlan
          case _ => if (ands.isEmpty) Seq(consolidatedNotAndPlan) else Seq(andCompound, consolidatedNotAndPlan)
        }

        val result = components match {
          case Seq(plan: CompoundPlan) => plan
          case _ => CompoundPlan.Or(components: _*)
        }

        result.normalize
      }
    }
  }
}

final case class DateBounded(start: Option[XMLGregorianCalendar], end: Option[XMLGregorianCalendar], expr: I2b2Expression) extends I2b2Expression with MappableExpression with HasSingleSubExpression with KnowsOwnType {

  override type MyType = DateBounded

  override def withExpr(newExpr: I2b2Expression): DateBounded = if (newExpr eq expr) this else this.copy(expr = newExpr)

  override def toXml: NodeSeq = XmlUtil.stripWhitespace {
    <dateBounded>
      { start.map(x => <start>{ x }</start>).getOrElse(<start/>) }
      { end.map(x => <end>{ x }</end>).getOrElse(<end/>) }
      { expr.toXml }
    </dateBounded>
  }

  override def normalize: I2b2Expression = {

    if (start.isEmpty && end.isEmpty) {
      expr.normalize
    } else {
      copy(expr = expr.normalize)
    }
  }

  override def toExecutionPlan: ExecutionPlan = SimplePlan(this.normalize)

  override def hasDirectI2b2Representation: Boolean = expr.hasDirectI2b2Representation

  override def map(f: I2b2Expression => I2b2Expression): DateBounded = withExpr(f(expr))
}

object DateBounded {
  def xmlDateToLong(xmlDate: XMLGregorianCalendar): Long = {
    xmlDate.toGregorianCalendar.getTimeInMillis
  }
}

final case class OccuranceLimited(min: Int, expr: I2b2Expression) extends I2b2Expression with MappableExpression with HasSingleSubExpression with KnowsOwnType {

  require(min >= 1)

  override type MyType = OccuranceLimited

  override def withExpr(newExpr: I2b2Expression): OccuranceLimited = if (newExpr eq expr) this else this.copy(expr = newExpr)

  override def toXml: NodeSeq = XmlUtil.stripWhitespace {
    <occurs>
      <min>{ min }</min>
      { expr.toXml }
    </occurs>
  }

  override def normalize: I2b2Expression = if (min == 1) expr.normalize else this.withExpr(expr.normalize)

  override def toExecutionPlan: ExecutionPlan = SimplePlan(this.normalize)

  override def hasDirectI2b2Representation: Boolean = expr.hasDirectI2b2Representation

  override def map(f: I2b2Expression => I2b2Expression): OccuranceLimited = this.copy(expr = f(expr))
}

final case class MappingException(message: String) extends RuntimeException(message)

final case class CouldNotMapAllTermsException(unmappable: Set[String]) extends RuntimeException(unmappable.mkString(", "))
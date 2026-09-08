package net.shrine.protocol.i2b2.query

import net.shrine.protocol.i2b2.serialization.I2b2Marshaller
import net.shrine.protocol.version.QueryId
import net.shrine.protocol.version.v2.querydefinition.{Concept, ConceptGroup, LinkedBy}
import net.shrine.xml.{OptionEnrichments, XmlDateHelper, XmlUtil}

import javax.xml.datatype.XMLGregorianCalendar
import scala.util.Try
import scala.xml.NodeSeq

/**
 *
 * @author Clint Gilbert
 * @since Jan 25, 2012
 *
 * @see http://cbmi.med.harvard.edu
 *
 * This software is licensed under the LGPL
 * @see http://www.gnu.org/licenses/lgpl.html
 *
 */
final case class Panel(
                        number: Int,
                        isExcluded: Boolean,
                        minOccurrences: Int,
                        start: Option[XMLGregorianCalendar],
                        end: Option[XMLGregorianCalendar],
                        timing: PanelTiming,
                        terms: Seq[SimpleI2b2Expression],
                        termsWithValueConstraints: Seq[Constrained] = Seq.empty) extends I2b2Marshaller {

  require(terms.nonEmpty || termsWithValueConstraints.nonEmpty, s"Panels must have at least one constrained or unconstrained term: plain terms: $terms ; modified terms: $termsWithValueConstraints")

  private[query] def withTerms(newTerms: Seq[SimpleI2b2Expression]) = copy(terms = newTerms)
  private[query] def withConstrainedTerms(newTerms: Seq[Constrained]) = copy(termsWithValueConstraints = newTerms)

  private[query] def withOnlyTerms(newTerms: Seq[SimpleI2b2Expression]) = withTerms(newTerms).withConstrainedTerms(Nil)
  private[query] def withOnlyConstrainedTerms(newTerms: Seq[Constrained]) = withConstrainedTerms(newTerms).withTerms(Nil)

  def invert: Panel = this.copy(isExcluded = !this.isExcluded)

  def withStart(startDate: Option[XMLGregorianCalendar]): Panel = this.copy(start = startDate)

  def withEnd(endDate: Option[XMLGregorianCalendar]): Panel = this.copy(end = endDate)

  def withMinOccurrences(min: Int): Panel = this.copy(minOccurrences = min)

  def withNumber(number:Int):Panel = this.copy(number = number)

  def withTiming(panelTiming: PanelTiming): Panel = this.copy(timing = panelTiming)
  def withPanelTimingFromV2(linkedBy:LinkedBy): Panel = {
    val panelTiming = linkedBy match {
      case LinkedBy.SameEncounter => PanelTiming.SameVisit
      case LinkedBy.SameInstance => PanelTiming.SameInstanceNum
    }
    this.copy(timing = panelTiming)
  }

  private def allTerms: Seq[(SimpleI2b2Expression, Option[Modifiers], Option[ValueConstraint])] = {
    val termsWithNoConstraints = terms.map(t => (t, None, None))

    val termsWithSomeConstraints = termsWithValueConstraints.map(_.toTuple)

    termsWithNoConstraints ++ termsWithSomeConstraints
  }

  //TODO: Do dates have to be in UTC?  Ones sent by web client seem to be
  //TODO: <date_{from,to}> vs <panel_date_{from,to}>?? web client uses both
  //TODO: <class>ENC</class> on items: is it ever anything else?
  //TODO: Are <item_name>, <tooltip>, <item_icon>, and <item_is_synonym> on items needed?
  override def toI2b2: NodeSeq = XmlUtil.stripWhitespace {
    import OptionEnrichments._

    <panel>
      <panel_number>{ number }</panel_number>
      { start.map(s => <panel_date_from>{ s.toString }</panel_date_from>).getOrElse(Nil) }
      { end.map(e => <panel_date_to>{ e.toString }</panel_date_to>).getOrElse(Nil) }
      <panel_accuracy_scale>100</panel_accuracy_scale>
      <invert>{ if (isExcluded) 1 else 0 }</invert>
      <panel_timing>{ timing }</panel_timing>
      <total_item_occurrences>{ minOccurrences }</total_item_occurrences>
      {
        allTerms.map {
          case (expr, modifierOption, constraintOption) =>
            <item>
              <hlevel>{ expr.computeHLevel.getOrElse(0) }</hlevel>
              <item_name>{ expr.name }</item_name>
              <item_key>{ expr.value }</item_key>
              <tooltip>{ expr.value }</tooltip>
              <class>ENC</class>
              <constrain_by_date>
                { start.map(s => <date_from>{ s.toString }</date_from>).orNull }
                { end.map(e => <date_to>{ e.toString }</date_to>).orNull }
              </constrain_by_date>
              { modifierOption.toXml(_.toI2b2) }
              { constraintOption.toXml(_.toI2b2) }
              <item_icon>LA</item_icon>
              <item_is_synonym>false</item_is_synonym>
            </item>
        }
      }
    </panel>
  }

  def toExpression: I2b2Expression = {
    def addTiming(expr: I2b2Expression) = if (timing.isAny) expr else WithTiming(timing, expr)

    def limit(expr: I2b2Expression) = if (minOccurrences != 0) OccuranceLimited(minOccurrences, expr) else expr

    def dateBound(expr: I2b2Expression): I2b2Expression = {
      if (start.isDefined || end.isDefined) DateBounded(start, end, expr) else expr
    }

    def negate(expr: I2b2Expression) = if (isExcluded) Not(expr) else expr

    val exprs: Seq[I2b2Expression] = {
      allTerms.collect {
        case (expr: Term, modifiersOption, valueConstraintOption) => Constrained(expr, modifiersOption, valueConstraintOption)
        case (expr, None, None) => expr
      }
    }
    
    addTiming(limit(dateBound(negate(Or(exprs: _*))))).normalize
  }
}

object Panel {

  def fromShrineV2(conceptGroup: ConceptGroup,panelNumber:Int,queryId:QueryId,timelinePart:Boolean = false):Panel = {
    val panelTiming = if(timelinePart) PanelTiming.SameInstanceNum
                      else PanelTiming.Any

    if(!(conceptGroup.concepts.isNoneOf || conceptGroup.concepts.isAnyOf))
      throw CouldNotTranslateQueryDefinitionException(s"Can not translate query ${queryId} to i2b2. ${conceptGroup.concepts} must be noneOf or anyOf.")

    val isExcluded: Boolean = conceptGroup.concepts.isNoneOf
    val minOccurrences: Int = conceptGroup.occursAtLeast

    val start: Option[XMLGregorianCalendar] = conceptGroup.startDate.map(date => XmlDateHelper.toXmlGregorianCalendar(date.underlying))
    val end: Option[XMLGregorianCalendar] = conceptGroup.endDate.map(date => XmlDateHelper.toXmlGregorianCalendar(date.underlying))

    if(!conceptGroup.concepts.possibilities.forall(_.isInstanceOf[Concept]))
      throw CouldNotTranslateQueryDefinitionException(s"Can not translate query ${queryId} to i2b2. All subexpressions of a ConceptGroup must be Concepts. ${conceptGroup.concepts} .")

    val concepts: Seq[Concept] = conceptGroup.concepts.possibilities.collect{case c:Concept => c}

    val (unconstrained,constrained) = concepts.partition(_.constraint.isEmpty)
    val unConstrainedTerms: Seq[Term] = unconstrained.map(c => Term(c.termPath, c.displayName))
    val constrainedTerms: Seq[Constrained] = constrained.flatMap { c =>
      val term: Term = Term(c.termPath, c.displayName)
      c.constraint.map(c => Constrained(term, None, Option(ValueConstraint.fromShrineV2(c))))
    }

    Panel(panelNumber, isExcluded, minOccurrences, start, end, panelTiming, unConstrainedTerms, constrainedTerms)
  }


  def fromI2b2(xml: NodeSeq): Try[Panel] = {

    import net.shrine.xml.XmlUtil.{toInt, trim}

    def toXmlGcOption(dateXml: NodeSeq): Option[XMLGregorianCalendar] = {
      XmlDateHelper.parseXmlTime(trim(dateXml)).toOption
    }

    import net.shrine.xml.NodeSeqEnrichments.Strictness._

    for {
      outerTag <- Try(xml.head)
      number <- (outerTag withChild "panel_number").map(toInt)
      inverted <- (outerTag withChild "invert").map(toInt).map(_ == 1)
      minOccurrences <- (outerTag withChild "total_item_occurrences").map(toInt)
      start = toXmlGcOption(outerTag \ "panel_date_from")
      end = toXmlGcOption(outerTag \ "panel_date_to")
      itemXmls = outerTag \ "item"
      timing = PanelTiming.valueOfOrElse(trim(outerTag \ "panel_timing"))(PanelTiming.Any)
    } yield {
      val termsToConstraints: Seq[(SimpleI2b2Expression, Option[Constrained])] = for {
        itemXml <- itemXmls
        termOrQuery: SimpleI2b2Expression = XmlUtil.trim(itemXml \ "item_key") match {
          case I2b2Query.prefixRegex(id) => I2b2Query(id)
          case x => Term(x, XmlUtil.trim(itemXml \ "item_name"))
        }
        modifierOption = Modifiers.fromI2b2(itemXml \ "constrain_by_modifier").toOption
        valueConstraintOption = ValueConstraint.fromI2b2(itemXml \ "constrain_by_value").toOption
        termOption = Option(termOrQuery).collect { case t: Term => t }
        constrainedOption = {
          if(modifierOption.isEmpty && valueConstraintOption.isEmpty) { None } 
          else { termOption.map(t => Constrained(t, modifierOption, valueConstraintOption)) } 
        }
      } yield {
        termOrQuery -> constrainedOption
      }

      val termsWithNoConstraints: Seq[SimpleI2b2Expression] = termsToConstraints.collect { case (expr, None) => expr }
      val termsWithSomeConstraints: Seq[Constrained] = termsToConstraints.collect { case (_, Some(constrained)) => constrained }

      Panel(number, inverted, minOccurrences, start, end, timing, termsWithNoConstraints, termsWithSomeConstraints)
    }
  }
}
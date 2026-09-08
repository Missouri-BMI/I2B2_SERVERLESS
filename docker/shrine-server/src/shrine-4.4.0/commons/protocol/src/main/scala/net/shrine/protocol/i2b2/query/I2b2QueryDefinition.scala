package net.shrine.protocol.i2b2.query

import net.shrine.log.Loggable
import net.shrine.protocol.i2b2.serialization.{I2b2Marshaller, XmlMarshaller, XmlUnmarshaller}
import net.shrine.protocol.version.QueryId
import net.shrine.protocol.version.v2.Query
import net.shrine.protocol.version.v2.querydefinition.TimeConstraintUnit.{Day, Month, Year}
import net.shrine.protocol.version.v2.querydefinition._
import net.shrine.util.Tries

import scala.util.Try
import scala.xml.{Elem, NodeSeq, Utility}

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
 * Classes to form expression trees representing Shrine queries
 */
final case class I2b2QueryDefinition(
                                      private val unTrimmedName: String,
                                      expr: Option[I2b2Expression],
                                      timing: Option[QueryTiming] = Some(QueryTiming.Any),
                                      id: Option[String] = None,
                                      queryType: Option[String] = None,
                                      constraints: Seq[I2b2SubQueryConstraints] = Seq.empty,
                                      subQueries: Seq[I2b2QueryDefinition] = Seq.empty
                                    ) extends I2b2Marshaller with XmlMarshaller {

  //TODO: Enforce some invariant regarding the now-optional expr field?  That field can be None, but then
  //*some* of the remaining, optional fields should be filled in.  For now, I don't know what combinations
  //are valid.  -Clint Jan 7, 2015

  import I2b2QueryDefinition._
  import net.shrine.xml.OptionEnrichments._
  import net.shrine.xml.XmlUtil._

  def name: String = unTrimmedName.trim

  def transform(f: I2b2Expression => I2b2Expression): I2b2QueryDefinition = this.copy(expr = this.expr.map(f), subQueries = subQueries.map(_.transform(f)))

  private def membersForEqualityAndHashing: Seq[Any] = {
    copy(unTrimmedName = name).productIterator.toIndexedSeq
  }

  override def equals(other: Any): Boolean = other match {
    case that: I2b2QueryDefinition => this.membersForEqualityAndHashing == that.membersForEqualityAndHashing
    case _ => false
  }

  override def hashCode: Int = this.membersForEqualityAndHashing.hashCode

  override def toXml: NodeSeq = stripWhitespace {
    import net.shrine.xml.OptionEnrichments._

    renameRootTag(rootTagName) {
      <placeholder>
        <name>{ name }</name>
        { expr.toXml(<expr/>, _.toXml) }
        { timing.filterNot(_.isAny).toXml(<timing/>) /* Don't include 'Any' */ }
        { id.toXml(<id/>) }
        { queryType.toXml(<type/>) }
        { constraints.map(_.toXml) }
        { subQueries.map(_.toXml.head).map(renameRootTag(subQueryTagName)) }
      </placeholder>
    }
  }

  //TODO: Will <use_shrine> and <specificity_scale> ever change?
  override def toI2b2: NodeSeq = stripWhitespace {
    <query_definition>
      { id.toXml(<query_id/>) }
      { queryType.toXml(<query_type/>) }
      <query_name>{ name }</query_name>
      <query_timing>{ timing.getOrElse(QueryTiming.Any) }</query_timing>
      <specificity_scale>0</specificity_scale>
      <use_shrine>1</use_shrine>
      {
        /* Produce a sequence of <panel>s */
        val panelXmls = for {
          e <- expr.toSeq
          panel <- toPanels(e).map(_.toI2b2)
        } yield panel

        panelXmls.foldLeft(NodeSeq.Empty) { _ ++ _ }
      }
      { constraints.map(_.toI2b2) }
      { subQueries.map(_.toI2b2.head).map(renameRootTag("subquery")) }
    </query_definition>
  }
}

object I2b2QueryDefinition extends XmlUnmarshaller[Try[I2b2QueryDefinition]] with Loggable {

  //NB: For backward-compatibility.  Note that multiple apply() overloads with
  //default args are not allowed. :\
  def apply(unTrimmedName: String, expr: I2b2Expression): I2b2QueryDefinition = I2b2QueryDefinition(unTrimmedName, Option(expr))

  val rootTagName = "queryDefinition"

  val subQueryTagName = "subQuery"

  private[this] def trim(xml: NodeSeq): String = xml.text.trim

  override def fromXml(nodeSeq: NodeSeq): Try[I2b2QueryDefinition] = {
    import net.shrine.xml.NodeSeqEnrichments.Strictness._

    val outerTag = Utility.trim(nodeSeq.head)

    val nameAttempt = (outerTag withChild "name").map(trim)

    val exprXmlAttempt = (outerTag withChild "expr").map(_.head.asInstanceOf[Elem])

    val innerExprXmlAttempt = exprXmlAttempt.map(_.child.head)

    for {
      name <- nameAttempt
      expr = innerExprXmlAttempt.flatMap(I2b2Expression.fromXml).toOption
      timing = QueryTiming.valueOfOrElse((outerTag \ "timing").text)(QueryTiming.Any)
      id = (outerTag \ "id").headOption.map(trim)
      queryType = (outerTag \ "type").headOption.map(trim)
      constraints = (outerTag \ I2b2SubQueryConstraints.rootTagName).flatMap(I2b2SubQueryConstraints.fromXml(_).toOption)
      subQueries: Seq[I2b2QueryDefinition] = (outerTag \ subQueryTagName).flatMap(I2b2QueryDefinition.fromXml(_).toOption)
    } yield {
      I2b2QueryDefinition(name, expr, Option(timing), id, queryType, constraints, subQueries)
    }
  }

  def fromShrineV2(query:Query):I2b2QueryDefinition = {
    fromShrineV2(query.queryDefinition, query.id, query.queryName)
  }

  def fromShrineV2(queryDefinition:QueryDefinition, queryId: QueryId, queryName: String):I2b2QueryDefinition = {

    val linkedConceptGroups: Seq[LinkedConceptGroups] = queryDefinition.topLevelLinkedConceptGroups
    val conceptGroups: Seq[ConceptGroup] = queryDefinition.topLevelConceptGroups
    val timelines: Seq[Timeline] = queryDefinition.topLevelTimelines

    val remains = queryDefinition.expression match {
      case conjunction: Conjunction => conjunction.possibilities.toSet -- (conceptGroups ++ timelines ++ linkedConceptGroups)
      case _ => //already checked getting the concept groups and timelines
        throw CouldNotTranslateQueryDefinitionException(s"Can not translate query ${queryId} to i2b2. ${queryDefinition.expression} is not an allOf conjunction")
    }
    if(remains.nonEmpty) throw CouldNotTranslateQueryDefinitionException(s"Can not translate query ${queryId} to i2b2. $remains are not ConceptGroups, LinkedConceptGroups , or Timelines in $queryDefinition")

    def conceptGroupsToI2b2Panel(cgs:Seq[ConceptGroup]): Seq[Panel] = {
      cgs.zipWithIndex.map(cgi => Panel.fromShrineV2(cgi._1,cgi._2,queryId))
    }

    val linkedConceptGroupI2b2Panels: Seq[Panel] = {
      val linkToLinkedConceptGroups: Map[LinkedBy, Seq[LinkedConceptGroups]] = linkedConceptGroups.groupBy(_.marker)
      linkToLinkedConceptGroups.map{linkAndGroups => linkAndGroups._2 match {
        case Seq(cgs) => conceptGroupsToI2b2Panel(cgs.groups).map(_.withPanelTimingFromV2(linkAndGroups._1))
        case Seq() => Seq.empty
        //One could translate multiple linked groups using i2b2 sub queries, but is not currently possible to express in shrine's UI
        case _ => throw CouldNotTranslateQueryDefinitionException(s"Can not translate query ${queryId} to i2b2. It contains ${linkAndGroups._2.size} linked concept groups with the same link ${linkAndGroups._1}")
      }}
    }.flatten.toSeq

    val conceptGroupPanels = linkedConceptGroupI2b2Panels ++ conceptGroupsToI2b2Panel(conceptGroups)

    //renumber all the panels
    val conceptGroupExpressions: Seq[I2b2Expression] = conceptGroupPanels.zipWithIndex.map(pi => pi._1.withNumber(pi._2)).map(_.toExpression)

    val conceptGroupExpression: Option[I2b2Expression] = conceptGroupExpressions match {
      case Seq() => None
      case Seq(ex) => Some(ex)
      case _ => Some(And(conceptGroupExpressions: _*))
    }

    val normalizedConceptGroupExpression: Option[I2b2Expression] = conceptGroupExpression.map(_.normalize)

    val (subqueries,subqueryConstraints): (Seq[I2b2QueryDefinition], Seq[I2b2SubQueryConstraints]) = timelines match {
      case Seq(timeline) => v2TimelineToSubqueriesAndConstraints(timeline,queryId) //here's where we can support multiple timelines should shrine's UI add them
      case Seq() => (Seq.empty,Seq.empty)
      case _ => throw CouldNotTranslateQueryDefinitionException(s"Can not translate query ${queryId} to i2b2. It contains ${timelines.size} timelines")
    }

    I2b2QueryDefinition(
      unTrimmedName = queryName,
      expr = normalizedConceptGroupExpression,
      constraints = subqueryConstraints,
      subQueries = subqueries
    )
  }

  private def v2TimelineToSubqueriesAndConstraints(timeline: Timeline,query:Query):(Seq[I2b2QueryDefinition],Seq[I2b2SubQueryConstraints]) = {
    v2TimelineToSubqueriesAndConstraints(timeline, query.id)
  }

  private def v2TimelineToSubqueriesAndConstraints(timeline: Timeline,queryId:QueryId):(Seq[I2b2QueryDefinition],Seq[I2b2SubQueryConstraints]) = {
    val prefix = "Timeline " //todo to support multiple timelines, use a different prefix for each

    val firstEvent = new I2b2QueryDefinition(
      unTrimmedName = s"$prefix 0",
      expr = Option(Panel.fromShrineV2(timeline.first,0,queryId,timelinePart = true).toExpression),
      timing = Option(QueryTiming.SameInstanceNum),
      id = Option(s"$prefix 0"),
      queryType = Option("EVENT"),
      constraints = Seq.empty,
      subQueries = Seq.empty
    )

    val subsequentWithIndex: Seq[(TimelineEvent, Int)] = timeline.subsequent.zipWithIndex
    val subsequentEvents: Seq[I2b2QueryDefinition] = subsequentWithIndex.map{ eventAndIndex:(TimelineEvent,Int) =>
      val panelNumber = eventAndIndex._2+1
      val name = s"$prefix $panelNumber"
      new I2b2QueryDefinition(
        unTrimmedName = name,
        expr = Option(Panel.fromShrineV2(eventAndIndex._1.conceptGroup,panelNumber,queryId,timelinePart = true).toExpression),
        timing = Option(QueryTiming.SameInstanceNum),
        id = Option(name),
        queryType = Option("EVENT"),
        constraints = Seq.empty,
        subQueries = Seq.empty
      )
    }
    val i2b2Events: Seq[I2b2QueryDefinition] = Seq(firstEvent).appendedAll(subsequentEvents)

    val constraints: Seq[I2b2SubQueryConstraints] = subsequentWithIndex.map{ eventAndIndex:(TimelineEvent,Int) =>
      val event1Id = s"$prefix ${eventAndIndex._2}"
      val event2Id = s"$prefix ${eventAndIndex._2+1}"

      def joinColumnFromBoundary(boundary: EventBoundary): String = {
        boundary match {
          case EventBoundary.START => "STARTDATE"
          case EventBoundary.END => "ENDDATE"
        }
      }
      val event1JoinColumn = joinColumnFromBoundary(eventAndIndex._1.previousEventConstraint.boundary)
      val event2JoinColumn = joinColumnFromBoundary(eventAndIndex._1.thisEventConstraint.boundary)

      def operatorFromRelationship(relationship:Relationship): String = { // can be LESS, LESSEQUAL or EQUAL
        relationship match {
          case Relationship.Before => "LESS"
          case Relationship.BeforeOrSimultaneous => "LESSEQUAL"
          case Relationship.Simultaneous => "EQUAL"
        }
      }

      def timeConstraintOperatorName(co:TimeConstraintOperator) = co match {
        case TimeConstraintOperator.GREATEREQUAL => "GREATEREQUAL"
        case TimeConstraintOperator.GREATER => "GREATER"
        case TimeConstraintOperator.EQUAL => "EQUAL"
        case TimeConstraintOperator.LESSEQUAL => "LESSEQUAL"
        case TimeConstraintOperator.LESS => "LESS"
      }

      def timeConstraintUnit(cu:TimeConstraintUnit):String = {
        cu match{
          case Day => "DAY"
          case Month => "MONTH"
          case Year => "YEAR"
        }
      }

      //todo most of the back-end translation for the SHRINE2020-1271 Epic goes in here. If it gets too mangy, split out a new method
      I2b2SubQueryConstraints(
        operator = operatorFromRelationship(eventAndIndex._1.relationship),
        first = I2b2SubQueryConstraint(event1Id, event1JoinColumn, EventAnchor.toCapsName(eventAndIndex._1.previousEventConstraint.anchor)),
        second = I2b2SubQueryConstraint(event2Id, event2JoinColumn, EventAnchor.toCapsName(eventAndIndex._1.thisEventConstraint.anchor)),
        primarySpan = eventAndIndex._1.primaryTimeConstraint.map(c =>
          I2b2QuerySpan(timeConstraintOperatorName(c.operator),c.value.toString,timeConstraintUnit(c.timeUnit))
        ),
        secondarySpan = eventAndIndex._1.secondaryTimeConstraint.map(c =>
          I2b2QuerySpan(timeConstraintOperatorName(c.operator),c.value.toString,timeConstraintUnit(c.timeUnit))
        )
      )
    }

    (i2b2Events,constraints)
  }

  def fromI2b2(i2b2Xml: String): Try[I2b2QueryDefinition] = {
    import net.shrine.xml.StringEnrichments._

    i2b2Xml.tryToXml.flatMap(fromI2b2)
  }

  //I2b2 query definition XML => Expression (kinda sorta)
  def fromI2b2(xml: NodeSeq): Try[I2b2QueryDefinition] = {
    val outerTag = xml.head

    val name = trim(outerTag \ "query_name")

    val timing = QueryTiming.valueOf(trim(outerTag \ "query_timing"))

    def optionalTag(name: String): Option[String] = (outerTag \ name).headOption.map(trim)

    val idOption = optionalTag("query_id")

    val queryTypeOption = optionalTag("query_type")

    val panelsXml: NodeSeq = outerTag \ "panel"

    import Tries.sequence

    val panelsAttempt: Try[Seq[Panel]] = sequence(panelsXml.map(Panel.fromI2b2)).map(_.toSeq)

    val exprsAttempt: Try[Seq[I2b2Expression]] = panelsAttempt.map(ps => ps.map(_.toExpression))

    val consolidatedExprAttempt: Try[Option[I2b2Expression]] = exprsAttempt.map {
      case es if es.isEmpty => None
      case es if es.size == 1 => Some(es.head)
      case es => Some(And(es: _*))
    }

    val constraintsXml = outerTag \ "subquery_constraint"
    val constraintsAttempt: Try[Seq[I2b2SubQueryConstraints]] = sequence(constraintsXml.map(I2b2SubQueryConstraints.fromI2b2)).map(_.toSeq)

    val subQueriesXml = outerTag \ "subquery"

    val subQueriesAttempt: Try[Seq[I2b2QueryDefinition]] = sequence(subQueriesXml.map(fromI2b2)).map(_.toSeq)

    for {
      consolidated <- consolidatedExprAttempt
      subQueries <- subQueriesAttempt
      constraints <- constraintsAttempt
    } yield {
      I2b2QueryDefinition(name, consolidated.map(_.normalize), timing, idOption, queryTypeOption, constraints, subQueries)
    }
  }

  private[query] def isAllTerms(exprs: Seq[I2b2Expression]) = exprs.nonEmpty && exprs.forall(_.isTerm)

  def toPanels(expr: I2b2Expression): Seq[Panel] = {
    //NB: Revisit default PanelTiming here

    def panelWithDefaults(terms: Seq[SimpleI2b2Expression], constrainedTerms: Seq[Constrained] = Nil, timing: PanelTiming = PanelTiming.Any): Panel = Panel(1, isExcluded = false, 1, None, None, timing, terms, constrainedTerms)

    //noinspection ScalaUnusedSymbol
    val resultPanels = expr.normalize match {
      case t: Term => Seq(panelWithDefaults(Seq(t)))
      case constrained @ Constrained(expr, modifierOption, valueConstraintOption) =>
        Seq(panelWithDefaults(Nil, Seq(constrained)))

      case WithTiming(panelTiming, expr) => toPanels(expr).map(_.withTiming(panelTiming))
      case Not(e) => toPanels(e).map(_.invert)
      case And(exprs @ _*) => exprs.flatMap(toPanels)
      case Or(exprs @ _*) => exprs match {
        case Nil => Nil
        case _ =>
          //Or-expressions must be comprised of terms only, dues to limitations in i2b2's query representation
          require(isAllTerms(exprs), "Or-expressions must be comprised of Terms *only*.  Sorry.")

          val constraints = exprs.collect { case c: Constrained => c }

          //Terms only :\
          Seq(panelWithDefaults(exprs.collect { case um: SimpleI2b2Expression => um }, constraints))
      }
      case DateBounded(start, end, e) =>
        toPanels(e).map(_.withStart(start)).map(_.withEnd(end))
      case OccuranceLimited(min, e) => toPanels(e).map(_.withMinOccurrences(min))
      case q: I2b2Query =>
        //TODO: implement for query-in-query

        val message = s"Couldn't turn query-in-query expressions '$q' into i2b2 panels"

        error(message)

        sys.error(message)
      case unexpected =>
        //We should never get here, but fail loudly just in case.  Also fixes compiler
        //warning about unhandled cases for abstract, non-instantiatable Expression subtypes
        val message = s"Unexpected expression '$unexpected' can't be turned into i2b2 panels"

        error(message)

        sys.error(message)
    }

    //Assign indicies; i2b2 indicies start from 1 
    resultPanels.size match {
      case 0 | 1 => resultPanels
      case _ => resultPanels.zipWithIndex.map {
        case (panel, index) => panel.copy(number = index + 1)
      }
    }
  }
}

case class CouldNotTranslateQueryDefinitionException(queryDefinitionText:String, maybeCause:Option[Throwable] = None)
  extends Exception(s"Could not interpret query definition text of '$queryDefinitionText' ",maybeCause.orNull)
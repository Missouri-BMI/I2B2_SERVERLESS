package net.shrine.protocol.version.v2.querydefinition

import io.circe.Codec
import io.circe.generic.extras.semiauto.deriveEnumerationCodec
import net.shrine.protocol.version.DateStamp
import net.shrine.protocol.version.v2.querydefinition.EventAnchor.ANY

case class QueryDefinition(
                            expression: Expression
                          ){
  def topLevelConceptGroups:Seq[ConceptGroup] = {
    expression match {
      case conjunction: Conjunction if conjunction.isAllOf =>
        //collect all the ConceptGroups at this level
        conjunction.possibilities.collect { case cg: ConceptGroup => cg }
      case _ => throw new UnsupportedOperationException(s"$expression is not an allOf conjunction")
    }
  }

  def topLevelLinkedConceptGroups: Seq[LinkedConceptGroups] = {
    expression match {
      case conjunction: Conjunction if conjunction.isAllOf =>
        //collect all the ConceptGroups at this level
        conjunction.possibilities.collect { case cg: LinkedConceptGroups => cg }
      case _ => throw new UnsupportedOperationException(s"$expression is not an allOf conjunction")
    }
  }

  def topLevelTimelines:Seq[Timeline] = {
    expression match {
      case conjunction: Conjunction if conjunction.isAllOf =>
        //collect all the ConceptGroups at this level
        conjunction.possibilities.collect { case tl: Timeline => tl }
      case _ => throw new UnsupportedOperationException(s"$expression is not an allOf conjunction")
    }
  }
}

object QueryDefinition {
  def allOf(groups:Seq[Group]):QueryDefinition = {
    QueryDefinition(Conjunction.allOf(groups))
  }
  def standIn: QueryDefinition = QueryDefinition.allOf(Seq.empty)
}

sealed trait Expression {
  def subexpressions:Seq[Expression]

  /**
   * @return a flat seq of all the concepts used in this expression
   */
  def flattenedConcepts:Seq[Concept]
}

case class Concept(
                    displayName: String,
                    termPath: String,
                    constraint: Option[ConceptConstraint] = None
                  ) extends Expression {
  override def subexpressions: Seq[Expression] = Seq.empty

  override def flattenedConcepts: Seq[Concept] = Seq(this)
}

/** The unity of big colored query element group blocks manipulated by the UI: currently ConceptGroup (red and green)
 * and Timeline (yellow). It's "QueryTermGroup" in the UI code
 * */
sealed trait Group extends Expression

case class ConceptGroup private(
                                 concepts:Conjunction,
                                 startDate: Option[DateStamp] = None,
                                 endDate: Option[DateStamp] = None,
                                 occursAtLeast:Int = 1,
                               ) extends Group {
  override def subexpressions: Seq[Expression] = Seq(concepts)

  //todo probably won't use these in QueryRunner, maybe delete in SHRINE2020-1099 - maybe clean this up with SHRINE2020-1360
  def withStartDate(date:DateStamp): ConceptGroup = copy(startDate = Option(date))
  def withEndDate(date:DateStamp): ConceptGroup = copy(endDate = Option(date))
  def withOccursAtLeast(count:Int): ConceptGroup = copy(occursAtLeast = count)

  def collectConcepts:Seq[Concept] = {
    concepts.possibilities.collect{case c:Concept => c}
  }

  override def flattenedConcepts: Seq[Concept] = concepts.flattenedConcepts
}

object ConceptGroup {
  def atLeastOneOf(concepts:Seq[Concept]): ConceptGroup = ConceptGroup(Conjunction.anyOf(concepts))
  def noneOf(concepts:Seq[Concept]): ConceptGroup = ConceptGroup(Conjunction.noneOf(concepts))
}

case class Conjunction(nMustBeTrue:Int, compare:Conjunction.Comparison, possibilities:Seq[Expression]) extends Expression {
  override def subexpressions: Seq[Expression] = possibilities

  import net.shrine.protocol.version.v2.querydefinition.Conjunction.{AtLeast, AtMost}

  def isAnyOf:Boolean = nMustBeTrue == 1 && compare == AtLeast
  def isAllOf:Boolean = nMustBeTrue == possibilities.size && compare == AtLeast
  def isNoneOf:Boolean = nMustBeTrue == 0 && compare == AtMost

  override def flattenedConcepts: Seq[Concept] = possibilities.flatMap(_.flattenedConcepts)
}

object Conjunction {
  def anyOf(possibilities:Seq[Expression]): Conjunction = Conjunction(1,AtLeast,possibilities)
  def allOf(possibilities:Seq[Expression]): Conjunction = Conjunction(possibilities.size,AtLeast,possibilities)
  def noneOf(possibilities:Seq[Expression]): Conjunction = Conjunction(0,AtMost,possibilities)

  sealed trait Comparison

  implicit val codec: Codec[Comparison] = deriveEnumerationCodec[Comparison]

  case object AtLeast extends Comparison
  case object Exactly extends Comparison
  case object AtMost extends Comparison
}

case class LinkedConceptGroups (groups:Seq[ConceptGroup],marker:LinkedBy) extends Group {
  override def subexpressions: Seq[Expression] = groups

  /**
   * @return a flat seq of all the concepts used in this expression
   */
  override def flattenedConcepts: Seq[Concept] = groups.flatMap(_.flattenedConcepts)
}

sealed trait LinkedBy

object LinkedBy {
  case object SameEncounter extends LinkedBy
  case object SameInstance extends LinkedBy
}

//todo once shrine 4.2.0-M1 settles down - move all the timeline business to its own file
case class Timeline private(first:ConceptGroup,subsequent:Seq[TimelineEvent] = Seq.empty) extends Group {
  override def subexpressions: Seq[Expression] = Seq(first).appendedAll(subsequent.map(_.conceptGroup))

  def appendWithEvent(
                       event:ConceptGroup,
                       previousEventConstraint: EventConstraint,
                       thisEventConstraint: EventConstraint,
                       relationship: Relationship = Relationship.Before,
                       primaryTimeConstraint:Option[TimeConstraint] = None,
                       secondaryTimeConstraint:Option[TimeConstraint] = None
                     ):Timeline =
    copy(subsequent = subsequent.appended(TimelineEvent(
      conceptGroup = event,
      previousEventConstraint = previousEventConstraint,
      thisEventConstraint = thisEventConstraint,
      relationship = relationship,
      primaryTimeConstraint = primaryTimeConstraint,
      secondaryTimeConstraint = secondaryTimeConstraint)
    ))

  def conceptGroup(index:Int):ConceptGroup = {
    if(index == 0) first
    else subsequent(index-1).conceptGroup
  }

  override def flattenedConcepts: Seq[Concept] = subexpressions.flatMap(_.flattenedConcepts)
}

//todo break out TimeLine to be a new file. This file is mostly big ideas. But not until after the SHRINE2020-1271 Timeline work to avoid merge woes
case class TimelineEvent(
                          conceptGroup:ConceptGroup,
                          previousEventConstraint: EventConstraint,
                          thisEventConstraint: EventConstraint,
                          relationship:Relationship = Relationship.Before,
                          primaryTimeConstraint: Option[TimeConstraint],
                          secondaryTimeConstraint: Option[TimeConstraint]
                       )

sealed case class EventConstraint(
                                    boundary:EventBoundary = EventBoundary.START,
                                    anchor:EventAnchor = ANY
                                     )


sealed abstract class EventBoundary

object EventBoundary {

  implicit val codec: Codec[EventBoundary] = deriveEnumerationCodec[EventBoundary]

  case object START extends EventBoundary
  case object END extends EventBoundary
}



sealed abstract class EventAnchor

object EventAnchor {

  implicit val codec: Codec[EventAnchor] = deriveEnumerationCodec[EventAnchor]

  case object FIRST extends EventAnchor
  case object ANY extends EventAnchor
  case object LAST extends EventAnchor

  def toCapsName(anchor:EventAnchor):String = {
    anchor match {
      case EventAnchor.FIRST => "FIRST"
      case EventAnchor.ANY => "ANY"
      case EventAnchor.LAST => "LAST"
    }
  }
}
sealed abstract class Relationship

object Relationship {
  implicit val codec: Codec[Relationship] = deriveEnumerationCodec[Relationship]

  case object Before extends Relationship
  case object BeforeOrSimultaneous extends Relationship
  case object Simultaneous extends Relationship
}


case class TimeConstraint(
                              operator: TimeConstraintOperator = TimeConstraintOperator.GREATEREQUAL,
                              value: Int = 0,
                              timeUnit: TimeConstraintUnit = TimeConstraintUnit.Day
                            )

sealed abstract class TimeConstraintOperator

object TimeConstraintOperator {
  implicit val codec: Codec[TimeConstraintOperator] = deriveEnumerationCodec[TimeConstraintOperator]

  case object GREATEREQUAL extends TimeConstraintOperator
  case object GREATER extends TimeConstraintOperator
  case object EQUAL extends TimeConstraintOperator
  case object LESSEQUAL extends TimeConstraintOperator
  case object LESS extends TimeConstraintOperator
}

sealed abstract class TimeConstraintUnit

object TimeConstraintUnit {
  implicit val codec: Codec[TimeConstraintUnit] = deriveEnumerationCodec[TimeConstraintUnit]

  case object Day extends TimeConstraintUnit
  case object Month extends TimeConstraintUnit
  case object Year extends TimeConstraintUnit
}

package net.shrine.qep

import net.shrine.api.ontology.{CodeCategory, OntologyPath, OntologyTerm}
import net.shrine.protocol.version.v2.querydefinition.{TimeConstraintOperator, TimeConstraintUnit, ConceptGroup => V2ConceptGroup, EventBoundary => V2EventBoundary, EventAnchor => V2EventAnchor, EventConstraint => V2EventConstraint, Relationship => V2Relationship, TimeConstraint => V2TimeConstraint, Timeline => V2Timeline, TimelineEvent => V2TimelineEvent}
import io.circe.Codec
import io.circe.generic.extras.semiauto.deriveEnumerationCodec

/**
 * Holds links between concept groups that define events in a query
 *
 * @since 3.1
 */
case class Timeline(
                     timelineEvents:Seq[ConceptGroup], // must be one more event than links - todo check this - SHRINE2020-1180
                     timelineLinks:Seq[TimelineLink]
                   ) {
  def toV2:V2Timeline = {
    //todo better fix is to add an optional "must be first" boolean to ConceptGroup and remove it from TimeLineLink - which will propagate into UI code

    val subsequentEventsAndLinks: Seq[(ConceptGroup, TimelineLink)] = timelineEvents.tail.zip(timelineLinks)
    val v2Timeline: V2Timeline = V2Timeline(timelineEvents.head.toV2)
    subsequentEventsAndLinks.foldLeft(v2Timeline){ (soFar,next: (ConceptGroup, TimelineLink)) =>
      soFar.appendWithEvent(
        event = next._1.toV2,
        previousEventConstraint = next._2.previousEventConstraint.toV2,
        thisEventConstraint = next._2.thisEventConstraint.toV2,
        relationship = next._2.relationship.toV2,
        primaryTimeConstraint = next._2.primaryTimeSpan.map(_.toV2),
        secondaryTimeConstraint = next._2.secondaryTimeSpan.map(_.toV2)
      )
    }
  }

  def htmlQueryText(pathToCodeCategoryMap: Map[OntologyPath, CodeCategory]):String = {

    //Special text for first two events
    val firstEventText = s"${ConceptGroup.panelSeparatorOpenTag}when " +
      s"${timelineLinks.head.previousEventConstraint.htmlText} ${ConceptGroup.panelSeparatorCloseTag}"+
      s"${ConceptGroup.htmlConceptGroupsText(Seq(timelineEvents.head),pathToCodeCategoryMap)}${ConceptGroup.newLine}"
    val secondEventText= s"${ConceptGroup.panelSeparatorOpenTag}occurs ${timelineLinks.head.htmlQueryText} ${timelineLinks.head.thisEventConstraint.htmlText}" +
      s" ${ConceptGroup.panelSeparatorCloseTag}${ConceptGroup.htmlConceptGroupsText(Seq(timelineEvents.tail.head),pathToCodeCategoryMap)}"

    val previousLinkAndCurrentTrios: Seq[(ConceptGroup, TimelineLink, ConceptGroup)] = timelineEvents.drop(1).zip(timelineLinks.drop(1)).zip(timelineEvents.drop(2)).map(x => (x._1._1,x._1._2,x._2))

    val subsequentText = previousLinkAndCurrentTrios.map{ previousLinkAnCurrent: (ConceptGroup, TimelineLink, ConceptGroup) =>
      val previousGroup = previousLinkAnCurrent._1
      val link = previousLinkAnCurrent._2
      val currentGroup = previousLinkAnCurrent._3

      s"${ConceptGroup.newLine}${ConceptGroup.panelSeparatorOpenTag}and when " +
        s"${link.previousEventConstraint.htmlText} ${ConceptGroup.panelSeparatorCloseTag}"+
        s"${ConceptGroup.htmlConceptGroupsText(Seq(previousGroup),pathToCodeCategoryMap)}${ConceptGroup.newLine}" +
        s"${ConceptGroup.panelSeparatorOpenTag}occurs ${link.htmlQueryText} ${link.thisEventConstraint.htmlText}" +
        s"${ConceptGroup.panelSeparatorCloseTag}${ConceptGroup.htmlConceptGroupsText(Seq(currentGroup),pathToCodeCategoryMap)}"
    }.mkString(ConceptGroup.newLine)

    firstEventText + secondEventText + subsequentText
  }
}

object Timeline {

  def fromV2(v2Timeline: V2Timeline):Timeline = {
    val v2Events: Seq[V2ConceptGroup] = v2Timeline.subsequent.map(_.conceptGroup).prepended(v2Timeline.first)
    val v2EventLinkEvent: Seq[(V2ConceptGroup, V2TimelineEvent)] = v2Events.zip(v2Timeline.subsequent)

    v2Timeline.subsequent match{
      case Seq() => throw UnsupportedQueryFeatureException(s"The UI only supports timelines with two events, not ${v2Timeline.subsequent.size + 1}")
      case _ => Timeline(
        v2Events.map(ConceptGroup.fromV2),
        v2EventLinkEvent.map(eLe => TimelineLink.fromV2(eLe._2))
      )
    }
  }
}

case class TimelineWithOntologyFields(
                                       timelineEvents:Seq[ConceptGroupWithOntologyFields],
                                       timelineLinks:Seq[TimelineLink]
                                     )

object TimelineWithOntologyFields {
  def apply(timeline: Timeline,ontologyMap: Map[String, Option[OntologyTerm]]):TimelineWithOntologyFields = {
    TimelineWithOntologyFields(
      timeline.timelineEvents.map(ConceptGroupWithOntologyFields(_,ontologyMap)),
      timeline.timelineLinks
    )
  }
}


sealed trait TimelineLink { //todo don't need this trait anymore, but it goes with a front-end change

  def previousEventConstraint:EventConstraint
  def thisEventConstraint:EventConstraint
  def relationship: Relationship
  def primaryTimeSpan: Option[TimeSpan]
  def secondaryTimeSpan: Option[TimeSpan]
  def htmlQueryText:String
}

object TimelineLink {
  def fromV2(v2TimelineEvent:V2TimelineEvent):TimelineLink = {
    BasicTimelineLink(
      previousEventConstraint = EventConstraint.fromV2(v2TimelineEvent.previousEventConstraint),
      thisEventConstraint = EventConstraint.fromV2(v2TimelineEvent.thisEventConstraint),
      relationship = Relationship.fromV2(v2TimelineEvent.relationship),
      primaryTimeSpan= v2TimelineEvent.primaryTimeConstraint.map(TimeSpan.fromV2),
      secondaryTimeSpan = v2TimelineEvent.secondaryTimeConstraint.map(TimeSpan.fromV2)
    )
  }
}

/**
 * A link where the first instance of event1 happens before any instance of event2
 */
case class BasicTimelineLink(
                              previousEventConstraint: EventConstraint,
                              thisEventConstraint: EventConstraint,
                              relationship: Relationship,
                              primaryTimeSpan: Option[TimeSpan] = None,
                              secondaryTimeSpan: Option[TimeSpan] = None
                             ) extends TimelineLink {

  override def htmlQueryText: String = {
    val primaryTimeSpanText = primaryTimeSpan.map(_.htmlQueryText).getOrElse("")
    val secondaryTimeSpanText = secondaryTimeSpan.map(s => s"and ${s.htmlQueryText}").getOrElse("")

    s"$primaryTimeSpanText$secondaryTimeSpanText${relationship.htmlText}"
  }
}

sealed case class EventConstraint(
                                    boundary:EventBoundary,
                                    anchor:EventAnchor
                                  ) {
  def toV2:V2EventConstraint = {
    V2EventConstraint(
      boundary = boundary.toV2,
      anchor = anchor.toV2
    )
  }

  def htmlText:String = s"${boundary.htmlText} ${anchor.htmlText}"
}

object EventConstraint {

  def fromV2(eventConstraint: V2EventConstraint):EventConstraint = {
    EventConstraint(
      boundary = EventBoundary.fromV2(eventConstraint.boundary),
      anchor = EventAnchor.fromV2(eventConstraint.anchor)
    )
  }
}

sealed trait EventBoundary{
  val htmlText:String
  val toV2:V2EventBoundary
}

object EventBoundary {
  case object START extends EventBoundary {
    override val htmlText: String = "the start of"
    override val toV2: V2EventBoundary = V2EventBoundary.START
  }
  case object END extends EventBoundary {
    override val htmlText: String = "the end of"
    override val toV2: V2EventBoundary = V2EventBoundary.END
  }

  implicit val codec: Codec[EventBoundary] = deriveEnumerationCodec[EventBoundary]

  def fromV2(boundary: V2EventBoundary): EventBoundary = {
    boundary match {
      case V2EventBoundary.START => START
      case V2EventBoundary.END => END
    }
  }
}

sealed trait EventAnchor{
  val htmlText:String
  val toV2:V2EventAnchor
}

object EventAnchor {

  implicit val codec: Codec[EventAnchor] = deriveEnumerationCodec[EventAnchor]

  case object FIRST extends EventAnchor {
    override val htmlText: String = "the first"
    override val toV2: V2EventAnchor = V2EventAnchor.FIRST
  }
  case object ANY extends EventAnchor {
    override val htmlText: String = "any"
    override val toV2: V2EventAnchor = V2EventAnchor.ANY
  }
  case object LAST extends EventAnchor { //todo can this be LATEST ?
    override val htmlText: String = "the last"
    override val toV2: V2EventAnchor = V2EventAnchor.LAST
  }

  def fromV2(eventAnchor: V2EventAnchor):EventAnchor = {
    eventAnchor match {
      case V2EventAnchor.FIRST => FIRST
      case V2EventAnchor.ANY => ANY
      case V2EventAnchor.LAST => LAST
    }
  }
}

sealed trait Relationship{
  val htmlText:String
  val toV2:V2Relationship
}

object Relationship {
  case object Before extends Relationship {
    override val htmlText: String = "before"
    override val toV2: V2Relationship = V2Relationship.Before
  }
  case object BeforeOrSimultaneous extends Relationship{
    override val htmlText:String = "before or simultaneously with"
    override val toV2: V2Relationship = V2Relationship.BeforeOrSimultaneous
  }
  case object Simultaneous extends Relationship {
    override val htmlText: String = "simultaneously with"
    override val toV2: V2Relationship = V2Relationship.Simultaneous
  }

  implicit val codec: Codec[Relationship] = deriveEnumerationCodec[Relationship]

  def fromV2(rt: V2Relationship): Relationship = {
    rt match {
      case V2Relationship.Before => Relationship.Before
      case V2Relationship.BeforeOrSimultaneous => Relationship.BeforeOrSimultaneous
      case V2Relationship.Simultaneous => Relationship.Simultaneous
    }
  }
}

sealed case class TimeSpan (
                            operator: TimeSpanOperator = TimeSpanOperator.GREATEREQUAL,
                            value: Int,
                            unit: TimeSpanUnit
                          ){
  def toV2:V2TimeConstraint = {
    V2TimeConstraint(
      operator = TimeSpanOperator.toV2(operator),
      value = value,
      timeUnit = TimeSpanUnit.toV2(unit)
    )
  }

  def htmlQueryText:String = s"${operator.htmlText} $value ${unit.displayText(value)} "
}

object TimeSpan {
  def fromV2(v2TimeConstraint: V2TimeConstraint):TimeSpan = {
    TimeSpan(
      operator = TimeSpanOperator.fromV2(v2TimeConstraint.operator),
      value = v2TimeConstraint.value,
      unit = TimeSpanUnit.fromV2(v2TimeConstraint.timeUnit)
    )
  }
}

sealed trait TimeSpanOperator {
  val htmlText:String
}

object TimeSpanOperator {

  implicit val codec: Codec[TimeSpanOperator] = deriveEnumerationCodec[TimeSpanOperator]

  case object GREATER extends TimeSpanOperator {
    override val htmlText: String = "&gt;"
  }
  case object GREATEREQUAL extends TimeSpanOperator {
    override val htmlText: String = "&gt;="
  }
  case object EQUAL extends TimeSpanOperator {
    override val htmlText: String = "="
  }
  case object LESSEQUAL extends TimeSpanOperator {
    override val htmlText: String = "&lt;="
  }
  case object LESS extends TimeSpanOperator {
    override val htmlText: String = "&lt;"
  }

  val toV2: Map[TimeSpanOperator, TimeConstraintOperator] = Map(
    GREATER -> TimeConstraintOperator.GREATER,
    GREATEREQUAL -> TimeConstraintOperator.GREATEREQUAL,
    EQUAL -> TimeConstraintOperator.EQUAL,
    LESSEQUAL -> TimeConstraintOperator.LESSEQUAL,
    LESS -> TimeConstraintOperator.LESS
  )

  val fromV2: Map[TimeConstraintOperator, TimeSpanOperator] = for ((k, v) <- toV2) yield (v, k)
}

sealed trait TimeSpanUnit {
  def displayText(number: Int): String = {
    if (number == 1) this.singular
    else this.plural
  }

  def singular: String
  def plural:String
}

object TimeSpanUnit {

  implicit val codec: Codec[TimeSpanUnit] = deriveEnumerationCodec[TimeSpanUnit]

  case object DAY extends TimeSpanUnit {
    override val singular: String = "day"
    override val plural: String = "days"
  }

  case object MONTH extends TimeSpanUnit {
    override val singular: String = "month"
    override val plural: String = "months"
  }

  case object YEAR extends TimeSpanUnit {
    override val singular: String = "year"
    override val plural: String = "years"
  }

  val toV2: Map[TimeSpanUnit, TimeConstraintUnit] = Map(
    DAY -> TimeConstraintUnit.Day,
    MONTH -> TimeConstraintUnit.Month,
    YEAR -> TimeConstraintUnit.Year
  )

  val fromV2: Map[TimeConstraintUnit, TimeSpanUnit] = for ((k, v) <- toV2) yield (v, k)

}

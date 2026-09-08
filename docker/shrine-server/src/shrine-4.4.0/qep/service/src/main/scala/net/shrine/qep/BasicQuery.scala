package net.shrine.qep

import net.shrine.api.ontology.{CodeCategory, OntologyPath, OntologyTerm}
import net.shrine.log.Loggable
import net.shrine.protocol.i2b2.ResultOutputType
import net.shrine.protocol.version.v2.{Query => V2Query}
import net.shrine.protocol.version.v2.querydefinition.{Group, LinkedConceptGroups, QueryDefinition}

case class BasicQuery(
                       name: String,
                       notes: Option[String] = None,
                       faved: Boolean = false,
                       dataDistributionTypes: Set[ResultOutputType],
                       conceptGroups: Seq[ConceptGroup],
                       timeline: Option[Timeline] = None
                     ) {
  TextInputValidator.validate("name", name)
  TextInputValidator.validate("notes", notes.getOrElse(""), allowReturns = true, allowEmpty = true, max = 1000)

  def toV2QueryDefinition: QueryDefinition = {
    val conceptGroupsGroupedByLink: Map[Option[LinkedBy], Seq[ConceptGroup]] = conceptGroups.groupBy(_.options.linkedBy)
    val v2Groups: Seq[Group] = conceptGroupsGroupedByLink.flatMap { maybeLinkToCgs: (Option[LinkedBy], Seq[ConceptGroup]) =>
      maybeLinkToCgs match {
        case (None, cgs) => cgs.map(_.toV2)
        case (Some(link), cgs) => Seq(LinkedConceptGroups(cgs.map(_.toV2), link.toV2))
      }
    }.toSeq

    timeline.fold(v2Groups)(t => v2Groups.appended(t.toV2))

    QueryDefinition.allOf(timeline.fold(v2Groups)(t => v2Groups.appended(t.toV2)))
  }

  def htmlQueryText(pathToCodeCategoryMap: Map[OntologyPath, CodeCategory]):String = {
    val preamble = s"${ConceptGroup.panelSeparatorOpenTag}Find patients${ConceptGroup.panelSeparatorCloseTag} "

    val conceptGroupSegment = s"${ConceptGroup.htmlConceptGroupsText(conceptGroups, pathToCodeCategoryMap)}"

    val timelineSegment = timeline.fold("")(
      { //may need a new line before the timeline segment - to offset from the concept groups
        if(conceptGroups.nonEmpty) ConceptGroup.newLine
        else ""
      } +
        _.htmlQueryText(pathToCodeCategoryMap)
      )
    preamble + conceptGroupSegment + timelineSegment
  }
}

object BasicQuery extends Loggable
{
  def fromV2Query(query:V2Query):BasicQuery = {
    val linkedConceptGroups = query.queryDefinition.topLevelLinkedConceptGroups.flatMap { lcg =>
      lcg.groups.map(ConceptGroup.fromV2).map(_.withLinkedBy(LinkedBy.fromV2(lcg.marker)))
    }

    val unlinkedConceptGroups: Seq[ConceptGroup] = query.queryDefinition.topLevelConceptGroups.map{ cg => ConceptGroup.fromV2(cg)}
    val maybeTimeline = query.queryDefinition.topLevelTimelines match {
      case Seq(tl) => Option(Timeline.fromV2(tl))
      case Seq() => None
      case moreThanOne => throw UnsupportedQueryFeatureException(s"Only queries with one timeline are supported, not ${moreThanOne.size}.")
    }

    BasicQuery(
      name = query.queryName,
      notes = query.queryNotes,
      dataDistributionTypes = query.breakdownNames.map(ResultOutputType.breakdownOrFallbackForName).toSet,
      conceptGroups = linkedConceptGroups ++ unlinkedConceptGroups,
      timeline = maybeTimeline
    )
  }
}

case class QueryNameAndConcepts(
                                      name: String,
                                      conceptGroups: Seq[ConceptGroupWithOntologyFields],
                                      timeline: Option[TimelineWithOntologyFields],
                                      hasUnsupportedFeatures: Boolean
                                    )

// TODO-XH : validation logic for query names was changed to be identical to
//  that in the front-end. Need to confirm this is ok.
object TextInputValidator {
  def validate(fieldName:String, value:String, allowReturns:Boolean=false, allowEmpty:Boolean=false, max:Int=250): Unit = {
    value match {
      // TODO-XH : If we want to mimic the validation done in the front-end, we must prevent leading spaces
      // but we should allow trailing spaces. That's why we're not trimming the input name above
      case x: String if !allowEmpty && x.isEmpty => throw InvalidTextInputException(s"$fieldName must not be empty", x)
      case x: String if x.length >= max => throw InvalidTextInputException(s"$fieldName must be less than $max characters", x)
      case x: String if x.matches("^[\\s]+?[\\s\\S]*") => throw InvalidTextInputException(s"$fieldName must not start with a space", x)
      case x: String if !allowReturns && x.matches(".*[\\n]+?.*") => throw InvalidTextInputException(s"$fieldName must not contain new lines", x)
      case _ =>
    }
  }
}

object QueryNameAndConcepts {

  def apply(basicQuery: BasicQuery,ontologyMap: Map[String, Option[OntologyTerm]]):QueryNameAndConcepts = {
    QueryNameAndConcepts(
      name = basicQuery.name,
      conceptGroups = basicQuery.conceptGroups
        .map(conceptGroup => ConceptGroupWithOntologyFields(conceptGroup, ontologyMap))
        .filterNot(conceptGroup => conceptGroup.concepts.isEmpty),
      timeline = basicQuery.timeline.map(TimelineWithOntologyFields(_,ontologyMap)),
      hasUnsupportedFeatures = false //todo rethink this
    )
  }

  def hasUnsupportedFeatures(name:String): QueryNameAndConcepts =
    QueryNameAndConcepts(
      name = name,
      conceptGroups = Seq.empty,
      timeline = None,
      hasUnsupportedFeatures = true)
}

case class UnsupportedQueryFeatureException(message:String) extends Exception(message)
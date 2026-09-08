package net.shrine.qep

import java.text.{DateFormat, SimpleDateFormat}
import java.util.Date
import cats.effect.IO
import io.circe.Codec
import io.circe.generic.extras.semiauto.deriveEnumerationCodec
import net.shrine.api.ontology.{CodeCategory, LuceneSearcher, OntologyPath, OntologyTerm}
import net.shrine.protocol.version.v2.querydefinition.{FlagConstraints, Concept => V2Concept, ConceptConstraint => V2ConceptConstraint, ConceptGroup => V2ConceptGroup, LinkedBy => V2LinkedBy}
import net.shrine.protocol.version.DateStamp
import net.shrine.protocol.version.v2.querydefinition.FlagConstraints.{High, Low, Normal}

case class ConceptGroup(
                        concepts:  List[Concept],
                        isExcluded: Boolean,
                        options: ConceptGroupOptions,
                       ) {
  def toV2:V2ConceptGroup = {
    val v2Concepts:Seq[V2Concept] = concepts.map(_.toV2)

    val v2ConceptGroup = if(isExcluded) {
      V2ConceptGroup.noneOf(v2Concepts)
    } else {
      V2ConceptGroup.atLeastOneOf(v2Concepts)
    }

    val groupWithStartDate = options.startDate.fold(v2ConceptGroup){l => v2ConceptGroup.withStartDate(new DateStamp(l))}
    val groupWithEndDate = options.endDate.fold(groupWithStartDate){l => groupWithStartDate.withEndDate(new DateStamp(l))}
    val groupWithOccurs = if(options.occurrences > 1) groupWithEndDate.withOccursAtLeast(options.occurrences)
                          else groupWithEndDate

    groupWithOccurs
  }

  def withLinkedBy(linkedBy:LinkedBy): ConceptGroup = {
    this.copy(options = options.copy(linkedBy = Option(linkedBy)))
  }
}

object ConceptGroup {
  def fromV2(conceptGroup: V2ConceptGroup):ConceptGroup = {
    val concepts:List[Concept] = conceptGroup.collectConcepts.map(Concept.fromV2).toList
    ConceptGroup(
      concepts = concepts,
      isExcluded = conceptGroup.concepts.isNoneOf,
      options = ConceptGroupOptions(
        startDate = conceptGroup.startDate.map(_.underlying),
        endDate = conceptGroup.endDate.map(_.underlying),
        occurrences = conceptGroup.occursAtLeast
      )
    )
  }

  /**
   * Get a map from a concept's path to it's OntologyTerm for every concept in a given
   * list of concept groups.
   */
  def getOntologyMapIOForConceptGroups(conceptGroups: Seq[ConceptGroup]): IO[Map[String, Option[OntologyTerm]]] = {
    import cats.implicits._

    val allPathsAndDisplayNames: Seq[(String, String)] = conceptGroups.flatMap(conceptGroup => {
      conceptGroup.concepts.map(concept => (concept.path, concept.displayName))
    })

    val pathToOntTermIOList: Seq[(String, IO[Option[OntologyTerm]])] = allPathsAndDisplayNames.map(pathAndName => {
      val (path, displayName) = pathAndName
      path -> LuceneSearcher.getSingleTermByPathAndDisplayName(path, displayName)
    })

    val pathToOntTermListIO: IO[Seq[(String, Option[OntologyTerm])]] = pathToOntTermIOList
      .traverse[IO, (String, Option[OntologyTerm])]{ case (path, ontTermIO) =>
        ontTermIO.map(ontTerm => (path, ontTerm))
      }

    pathToOntTermListIO.map(_.toMap)
  }

  val panelSeparatorOpenTag: String = "<span class=\"criteriaPanelSeparator\">"
  val panelSeparatorCloseTag: String = "</span>"
  val newLine: String = "<br/>"
  def htmlConceptGroupsText(conceptGroups: Seq[ConceptGroup], codeCategoryMap: Map[OntologyPath, CodeCategory]): String = {

    val conceptGroupsGroupedByLink: Map[Option[LinkedBy], Seq[ConceptGroup]] = conceptGroups.groupBy(_.options.linkedBy)

    def linkedConceptGroupHtmlText(maybeLinkedConceptGroups: Seq[ConceptGroup]):String = {
      def linkedByPhrase(conceptGroup: ConceptGroup): String = {
        conceptGroup.options.linkedBy.map(lb => s" in the same ${lb.htmlQueryText}").getOrElse("")
      }

      maybeLinkedConceptGroups.map { conceptGroup =>
        val prefix: String =
          s"$panelSeparatorOpenTag${if (conceptGroup.isExcluded) " without" else " with"}${linkedByPhrase(conceptGroup)}$panelSeparatorCloseTag"
        s"$prefix ${getQueryCriteriaFromConcepts(conceptGroup.concepts, codeCategoryMap)}${conceptGroup.options.toDisplayableString}"
      }.mkString(s"$newLine${panelSeparatorOpenTag}and$panelSeparatorCloseTag ")
    }

    val conceptGroupStrings: Seq[String] = for {
      maybeLinkedBy <- LinkedBy.possibleLinks
      someConceptGroups <- conceptGroupsGroupedByLink.get(maybeLinkedBy)
    } yield {
      linkedConceptGroupHtmlText(someConceptGroups)
    }
    conceptGroupStrings.mkString(s"$newLine${panelSeparatorOpenTag}and$panelSeparatorCloseTag ")
  }

  private def getQueryCriteriaFromConcepts(concepts: List[Concept], codeCategoryMap: Map[OntologyPath, CodeCategory]): String = {
    concepts.map(concept => {
      val codeCategoryString: String = codeCategoryMap.find(c => concept.path.startsWith(c._1.path)).map(_._2.name).getOrElse("")
      val constraintString: String = concept.constraint.map(constraint => constraint.toHtmlString).getOrElse("")
      val termString: String = s"$codeCategoryString ${concept.displayName} $constraintString".trim
      s"($termString)"
    }).mkString(" or ")
  }
}

case class ConceptGroupWithOntologyFields(concepts:  List[ConceptWithOntologyFields],
                                          isExcluded: Boolean,
                                          options: ConceptGroupOptions)
object ConceptGroupWithOntologyFields {
  def apply(conceptGroup: ConceptGroup, pathToOntologyTerms: Map[String, Option[OntologyTerm]]): ConceptGroupWithOntologyFields = {
    val reloadableConcepts: List[ConceptWithOntologyFields] = conceptGroup.concepts
      .filter(concept => pathToOntologyTerms(concept.path).isDefined)
      .map(concept => {
        val ontologyTerm = pathToOntologyTerms(concept.path).get
        ConceptWithOntologyFields(concept.displayName,
          concept.path,
          ontologyTerm.conceptCategory,
          ontologyTerm.isLab,
          concept.constraint
        )
      })
    ConceptGroupWithOntologyFields(reloadableConcepts, conceptGroup.isExcluded, conceptGroup.options)
  }
}


case class Concept(
                    displayName: String,
                    path: String,
                    constraint: Option[ConceptValueConstraint]
                  ) {
  def toV2:V2Concept = {
    V2Concept(displayName = displayName, termPath = path, constraint = constraint.map(_.toV2))
  }
}

object Concept {
  def fromV2(v2Concept: V2Concept):Concept = {
    Concept(
      displayName = v2Concept.displayName,
      path = v2Concept.termPath,
      constraint = v2Concept.constraint.map(ConceptValueConstraint.fromV2)
    )
  }
}

case class ConceptWithOntologyFields(displayName: String,
                                     path: String,
                                     conceptCategory: String,
                                     isLab: Boolean,
                                     constraint: Option[ConceptValueConstraint])


case class ConceptGroupOptions(
                                startDate: Option[Long],
                                endDate: Option[Long],
                                occurrences: Int,
                                linkedBy: Option[LinkedBy] = None,
                              ) {
  (startDate, endDate) match {
    case (Some(s), Some(e)) if s >= e =>
      throw InvalidConceptGroupOptionsException(s"Start date: $s must be before endDate: $e.")
    case _ => ;
  }

  def toDisplayableString: String = {
    val datePattern: String = "MM/dd/yyyy"
    val dateFormat: DateFormat = new SimpleDateFormat(datePattern)

    val start: Option[String] = startDate.map(long => dateFormat.format(new Date(long)))
    val end: Option[String] = endDate.map(long => dateFormat.format(new Date(long)))
    val dateLimitString: String = (start, end) match {
      case (Some(s), Some(e)) => s" starting from $s to $e"
      case (Some(s), None) => s" starting from $s"
      case (None, Some(e)) => s" to $e"
      case _ => ""
    }

    val occurLimitString: String = occurrences match {
      case o if o > 1 => s" occurs at least $o times"
      case _ => ""
    }

    (dateLimitString, occurLimitString) match {
      case ("", "") => ""
      case ("", occurs) => occurs
      case (dates, "") => dates
      case (dates, occurs) => s"$dates and$occurs"
    }
  }
}

sealed trait LinkedBy{
  def toV2:V2LinkedBy

  def htmlQueryText:String
}

object LinkedBy {
  implicit val codec: Codec[LinkedBy] = deriveEnumerationCodec[LinkedBy]

  case object SameEncounter extends LinkedBy {
    override def toV2: V2LinkedBy = V2LinkedBy.SameEncounter

    override def htmlQueryText: String = "encounter"
  }
  case object SameInstance extends LinkedBy {
    override def toV2: V2LinkedBy = V2LinkedBy.SameInstance

    override def htmlQueryText: String = "instance"
  }

  val possibleLinks: Seq[Option[LinkedBy]] = Seq(Option(SameEncounter),Option(SameInstance),None)

  def fromV2(v2LinkedBy: V2LinkedBy): LinkedBy = {
    v2LinkedBy match {
      case V2LinkedBy.SameEncounter => SameEncounter
      case V2LinkedBy.SameInstance => SameInstance
    }
  }
}

case class ConceptValueConstraint(constraintType: String, value: Seq[String], unit: Option[String]){

  private val constraintDetails: ConstraintDetails = ConstraintDetails(constraintType, value, unit)

  //noinspection ZeroIndexToHead
  def toV2:V2ConceptConstraint = {
    import net.shrine.protocol.version.v2.querydefinition.{FlagConstraint,NumberConstraint,SingleNumberConstraint,DoubleNumberConstraint}

    def operatorToV2(o:ConstraintOperator):NumberConstraint.SingleNumberOperator = {
      import net.shrine.qep.ConstraintOperator._

      o match {
        case EQ_OPERATOR => NumberConstraint.Equal
        case LT_OPERATOR => NumberConstraint.LessThan
        case GT_OPERATOR => NumberConstraint.GreaterThan
        case LE_OPERATOR => NumberConstraint.LessThanOrEqual
        case GE_OPERATOR => NumberConstraint.GreaterThanOrEqual
        case BETWEEN_OPERATOR => throw new IllegalArgumentException("Between is not going to work as a single-number operator")
      }
    }

    constraintDetails match {
      case _:LOW_FLAG => FlagConstraints.Low
      case _:NORMAL_FLAG => FlagConstraints.Normal
      case _:HIGH_FLAG => FlagConstraints.High
      case _:SINGLE_NUMBER => SingleNumberConstraint(
                                                        operator = operatorToV2(constraintDetails.operator),
                                                        value = constraintDetails.value.toDouble,
                                                        unit = constraintDetails.unit
                                                      )
      case _:BETWEEN_NUMBER => DoubleNumberConstraint(
                                                        value1 = value(0).toDouble,
                                                        value2 = value(1).toDouble,
                                                        unit = constraintDetails.unit
                                                      )
    }
  }

  def toHtmlString: String = {
    import net.shrine.qep.ConstraintOperator._
    import net.shrine.qep.ConstraintType._

    constraintDetails.constraintType match {
      case FLAG_CONSTRAINT => s"abnormal flag ${constraintType.toLowerCase}"
      case NUMBER_CONSTRAINT =>
        val valueString: String = s"${constraintDetails.value.replace("and", "-")} ${constraintDetails.unit.get}"
        val operatorString: String = constraintDetails.operator match {
          case EQ_OPERATOR => "equal (=)"
          case LT_OPERATOR => "less than (&lt;)"
          case GT_OPERATOR => "greater than (&gt;)"
          case LE_OPERATOR => "less than or equal to (&lt;=)"
          case GE_OPERATOR => "greater than or equal to (&gt;=)"
          case BETWEEN_OPERATOR => "between"
        }
        s"$operatorString $valueString"
    }
  }
}

object ConceptValueConstraint {

  def fromV2(conceptConstraint: V2ConceptConstraint):ConceptValueConstraint = {
    import net.shrine.protocol.version.v2.querydefinition.{FlagConstraint,NumberConstraint,SingleNumberConstraint,DoubleNumberConstraint}

    conceptConstraint match {
      case flag:FlagConstraint => ConceptValueConstraint(
        constraintType = flag match {
          case Low => "LOW"
          case Normal => "NORMAL"
          case High => "HIGH"
        },
        value = flag match {
          case Low => Seq("L")
          case Normal => Seq("@")
          case High => Seq("H")
        },
        unit = None
      )
      case singleNumberConstraint:SingleNumberConstraint => ConceptValueConstraint(
        constraintType = singleNumberConstraint.operator match {
          case NumberConstraint.GreaterThan => "GT"
          case NumberConstraint.GreaterThanOrEqual => "GE"
          case NumberConstraint.Equal => "EQ"
          case NumberConstraint.LessThanOrEqual => "LE"
          case NumberConstraint.LessThan => "LT"
        },
        value = Seq(singleNumberConstraint.value.toString),
        unit = singleNumberConstraint.unit
      )
      case doubleNumberConstraint: DoubleNumberConstraint => ConceptValueConstraint(
        constraintType = "BETWEEN",
        value = Seq(doubleNumberConstraint.value1.toString,doubleNumberConstraint.value2.toString),
        unit = doubleNumberConstraint.unit
      )
    }
  }
}

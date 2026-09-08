package net.shrine.protocol.version.v2.querydefinition

import net.shrine.protocol.version.v2.querydefinition.FlagConstraints.High
import net.shrine.protocol.version.v2.{Query, QueryProgress, V2JsonTest}
import net.shrine.protocol.version.{JsonText, NodeId, ResearcherId}
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class QueryDefinitionTest {

  @Test
  def testTwoConceptGroups():Unit = {
    val queryJson: JsonText = QueryDefinitionTest.twoConceptGroupQuery.asJsonText
    val result = Query.tryRead(queryJson).get
    assertEquals(QueryDefinitionTest.twoConceptGroupQuery,result)
  }

  @Test
  def testTwoEventTimeline():Unit = {
    val queryJson: JsonText = QueryDefinitionTest.twoEventTimelineQuery.asJsonText
    val result = Query.tryRead(queryJson).get
    assertEquals(QueryDefinitionTest.twoEventTimelineQuery,result)
  }


  @Test
  def testThreeEventTimeline():Unit = {
    val queryJson: JsonText = QueryDefinitionTest.threeEventTimelineQuery.asJsonText
    val result = Query.tryRead(queryJson).get
    assertEquals(QueryDefinitionTest.threeEventTimelineQuery,result)
  }

  @Test
  def testTwoTimeConstraintsTimeline():Unit = {
    val queryJson: JsonText = QueryDefinitionTest.threeEventTimelineQuery.asJsonText
    val result = Query.tryRead(queryJson).get
    assertEquals(QueryDefinitionTest.threeEventTimelineQuery,result)
  }

  @Test
  def testSameEncounter(): Unit = {
    val queryJson: JsonText = QueryDefinitionTest.sameEncounterQuery.asJsonText
    val result = Query.tryRead(queryJson).get
    assertEquals(QueryDefinitionTest.sameEncounterQuery, result)
  }

  @Test
  def testSameInstance(): Unit = {
    val queryJson: JsonText = QueryDefinitionTest.sameInstanceQuery.asJsonText
    val result = Query.tryRead(queryJson).get
    assertEquals(QueryDefinitionTest.sameInstanceQuery, result)
  }

}

object QueryDefinitionTest {
  val men: ConceptGroup = ConceptGroup.atLeastOneOf(List(Concept("male","/path/to/male")))
  val middleAged: ConceptGroup = ConceptGroup.atLeastOneOf(List(Concept("45-55","/path/to/middle/aged")))
  val hypertensionDiagnosis: Concept = Concept("hypertension Diagnosis","/path/to/hypertension/diagnosis")
  val hypertensionTestResultsHigh: Concept = Concept("hypertension Test","/path/to/hypertension/test",Option(High))
  val hypertensionTestResults42: Concept = Concept("hypertension Test","/path/to/hypertension/test",Option(SingleNumberConstraint(NumberConstraint.LessThanOrEqual,42,Option("hyperOs"))))
  val hypertensionMeds: Concept = Concept("hypertension meds","/path/to/hypertension/meds",Option(DoubleNumberConstraint(3,3000,None)))

  val hypertension: ConceptGroup = ConceptGroup.atLeastOneOf(List(hypertensionDiagnosis,hypertensionTestResultsHigh,hypertensionMeds))

  val noHypertension: ConceptGroup = ConceptGroup.noneOf(List(hypertensionDiagnosis,hypertensionTestResults42,hypertensionMeds))

  val menWithHypertension: QueryDefinition = QueryDefinition(Conjunction.allOf(List(middleAged,men,hypertension)))

  val hypertensionGotBetter: Timeline = Timeline(hypertension).appendWithEvent(noHypertension,EventConstraint(),EventConstraint())
  val menWithHypertensionGotBetter: QueryDefinition = QueryDefinition.allOf(List(middleAged,men,hypertensionGotBetter))

  val hypertensionGotBetterEnd: Timeline = Timeline(hypertension).appendWithEvent(noHypertension, EventConstraint(EventBoundary.END), EventConstraint())
  val menWithHypertensionGotBetterEnd: QueryDefinition = QueryDefinition.allOf(List(middleAged, men, hypertensionGotBetterEnd))

  val hypertensionGotBetterRelatedTo: Timeline = Timeline(hypertension).appendWithEvent(
      event = noHypertension,
      previousEventConstraint = EventConstraint(),
      thisEventConstraint = EventConstraint(),
      relationship = Relationship.Simultaneous
  )

  val hypertensionGotBetterRelatedToAndBetween: Timeline = Timeline(hypertension).appendWithEvent(
    event = noHypertension,
    previousEventConstraint = EventConstraint(),
    thisEventConstraint = EventConstraint(),
    relationship = Relationship.Simultaneous,
    primaryTimeConstraint = Option(TimeConstraint()),
    secondaryTimeConstraint = Option(TimeConstraint(value=35, timeUnit=TimeConstraintUnit.Month)),
  )

  val menWithHypertensionGotBetterRelatedTo: QueryDefinition = QueryDefinition.allOf(List(middleAged, men, hypertensionGotBetterRelatedTo))

  val menWithHypertensionGotBetterRelatedToWithOneSpan: QueryDefinition = QueryDefinition.allOf(List(middleAged, men, hypertensionGotBetterRelatedTo))
  val menWithHypertensionGotBetterRelatedToWithTwoSpan: QueryDefinition = QueryDefinition.allOf(List(middleAged, men, hypertensionGotBetterRelatedToAndBetween))

  val hypertensionCure: ConceptGroup = ConceptGroup.atLeastOneOf(List(Concept("snake oil","/path/to/snake/oil")))
  val beeTherapyCure: ConceptGroup = ConceptGroup.atLeastOneOf(Seq(Concept("bee stings","/path/to/be/stings")))
  val twoTherapiesSameEncounter: QueryDefinition = QueryDefinition.allOf(Seq(LinkedConceptGroups(Seq(hypertensionCure,beeTherapyCure),LinkedBy.SameEncounter)))

  val twoTherapiesSameInstance: QueryDefinition = QueryDefinition.allOf(Seq(LinkedConceptGroups(Seq(hypertensionCure,beeTherapyCure),LinkedBy.SameInstance)))

  val hypertensionCured: Timeline = Timeline(hypertension)
    .appendWithEvent(hypertensionCure,EventConstraint(),EventConstraint())
    .appendWithEvent(noHypertension,EventConstraint(),EventConstraint())
  val menTreatedForHypertensionGotBetter: QueryDefinition = QueryDefinition.allOf(List(middleAged,men,hypertensionCured))

  val twoConceptGroupQuery: QueryProgress = Query.create(
    queryDefinition = QueryDefinitionTest.menWithHypertension,
    breakdownNames = Seq.empty,
    queryName = "Men With Hypertension",
    queryNotes = Some("Men With Hypertension, notes"),
    queryFaved = true,
    nodeOfOriginId = new NodeId(0L),
    researcherId = new ResearcherId(1L),
  )

  val sameEncounterQuery = Query.create(
    queryDefinition = QueryDefinitionTest.twoTherapiesSameEncounter,
    breakdownNames = Seq.empty,
    queryName = "Two Therapies Same Encounter",
    queryNotes = Some("Two Therapies Same Encounter, some notes"),
    queryFaved = true,
    nodeOfOriginId = new NodeId(0L),
    researcherId = new ResearcherId(1L),
  )

  val sameInstanceQuery = Query.create(
    queryDefinition = QueryDefinitionTest.twoTherapiesSameInstance,
    breakdownNames = Seq.empty,
    queryName = "Two Therapies Same Instance",
    queryNotes = Some("Two Therapies Same Instance, notes"),
    queryFaved = true,
    nodeOfOriginId = new NodeId(0L),
    researcherId = new ResearcherId(1L),
  )

  val twoEventTimelineQuery: QueryProgress = Query.create(
    queryDefinition = QueryDefinitionTest.menWithHypertensionGotBetter,
    breakdownNames = Seq.empty,
    queryName = "Men With Hypertension Who Got Better",
    queryNotes = Some("Men With Hypertension Who Got Better, notes"),
    queryFaved = true,
    nodeOfOriginId = new NodeId(0L),
    researcherId = new ResearcherId(1L),
  )

  val twoEventTimelineEndConstraintQuery: QueryProgress = Query.create(
    queryDefinition = QueryDefinitionTest.menWithHypertensionGotBetterEnd,
    breakdownNames = Seq.empty,
    queryName = "Men With Hypertension Who Got Better - with an End constraint",
    queryNotes = Some("Men With Hypertension Who Got Better - with an End constraint, notes"),
    queryFaved = true,
    nodeOfOriginId = new NodeId(0L),
    researcherId = new ResearcherId(1L),
  )

  val twoEventTimelineRelatedToQuery: QueryProgress = Query.create(
    queryDefinition = QueryDefinitionTest.menWithHypertensionGotBetterRelatedTo,
    breakdownNames = Seq.empty,
    queryName = "Men With Hypertension Who Got Better - During diagnosis",
    queryNotes = Some("Men With Hypertension Who Got Better - During diagnosis, notes"),
    queryFaved = true,
    nodeOfOriginId = new NodeId(0L),
    researcherId = new ResearcherId(1L),
  )

  val twoEventTimelineRelatedToWithTwoTimeSpanQuery: QueryProgress = Query.create(
    queryDefinition = QueryDefinitionTest.menWithHypertensionGotBetterRelatedToWithTwoSpan,
    breakdownNames = Seq.empty,
    queryName = "Men With Hypertension Who Got Better - During diagnosis between 0 days and 35 months",
    queryNotes = Some("Men With Hypertension Who Got Better - During diagnosis between 0 days and 35 months. notes"),
    queryFaved = true,
    nodeOfOriginId = new NodeId(0L),
    researcherId = new ResearcherId(1L),
  )

  val threeEventTimelineQuery: QueryProgress = Query.create(
    queryDefinition = QueryDefinitionTest.menTreatedForHypertensionGotBetter,
    breakdownNames = Seq.empty,
    queryName = "Men With Hypertension Who Took Snake Oil And Got Better",
    queryNotes = Some("Men With Hypertension Who Took Snake Oil And Got Better, notes"),
    queryFaved = true,
    nodeOfOriginId = new NodeId(0L),
    researcherId = new ResearcherId(1L),
  )

  lazy val timelineQueryWithoutByFromFile: Query = Query.tryRead(new JsonText(
  """{
    |  "id": 494140624001778309,
    |  "versionInfo": {
    |    "protocolVersion": 2,
    |    "shrineVersion": "4.2.0-SNAPSHOT",
    |    "itemVersion": 6,
    |    "createDate": 1691524877097,
    |    "changeDate": 1691525177174
    |  },
    |  "status": "SentToAdapters",
    |  "queryDefinition": {
    |    "expression": {
    |      "nMustBeTrue": 1,
    |      "compare": "AtLeast",
    |      "possibilities": [
    |        {
    |          "first": {
    |            "concepts": {
    |              "nMustBeTrue": 1,
    |              "compare": "AtLeast",
    |              "possibilities": [
    |                {
    |                  "displayName": "Cough",
    |                  "termPath": "\\\\i2b2_DIAG\\i2b2\\Diagnoses\\Symptoms, signs, and ill-defined conditions (780-799)\\Symptoms (780-789)\\(786) Symptoms involving respirat~\\(786-2) Cough\\",
    |                  "constraint": null,
    |                  "encodedClass": "Concept"
    |                }
    |              ]
    |            },
    |            "startDate": null,
    |            "endDate": null,
    |            "occursAtLeast": 1
    |          },
    |          "subsequent": [
    |            {
    |              "conceptGroup": {
    |                "concepts": {
    |                  "nMustBeTrue": 1,
    |                  "compare": "AtLeast",
    |                  "possibilities": [
    |                    {
    |                      "displayName": "Acute upper respiratory infections of multiple or unspecified sites",
    |                      "termPath": "\\\\i2b2_DIAG\\i2b2\\Diagnoses\\Respiratory system (460-519)\\Acute respiratory infections (460-466)\\(465) Acute upper respiratory inf~\\",
    |                      "constraint": null,
    |                      "encodedClass": "Concept"
    |                    }
    |                  ]
    |                },
    |                "startDate": null,
    |                "endDate": null,
    |                "occursAtLeast": 1
    |              },
    |              "previousEventConstraint": {
    |                "boundary": "START",
    |                "anchor": "ANY"
    |              },
    |              "thisEventConstraint": {
    |                "boundary": "START",
    |                "anchor": "ANY"
    |              },
    |              "relationship": "Before",
    |              "primaryTimeConstraint": null,
    |              "secondaryTimeConstraint": null
    |            }
    |          ],
    |          "encodedClass": "Timeline"
    |        }
    |      ],
    |      "encodedClass": "Conjunction"
    |    }
    |  },
    |  "breakdownNames": [],
    |  "queryName": "NoBy",
    |  "queryNotes": "notes",
    |  "queryFaved": false,
    |  "nodeOfOriginId": 601196408267489082,
    |  "researcherId": -1346861970,
    |  "encodedClass": "QueryProgress"
    |}""".stripMargin)).get

  val timelineQueryWithByFromFile: Query = Query.tryRead(new JsonText(
    """{
      |  "id": 2271823641032368396,
      |  "versionInfo": {
      |    "protocolVersion": 2,
      |    "shrineVersion": "4.2.0-SNAPSHOT",
      |    "itemVersion": 6,
      |    "createDate": 1691524955094,
      |    "changeDate": 1691525196923
      |  },
      |  "status": "SentToAdapters",
      |  "queryDefinition": {
      |    "expression": {
      |      "nMustBeTrue": 1,
      |      "compare": "AtLeast",
      |      "possibilities": [
      |        {
      |          "first": {
      |            "concepts": {
      |              "nMustBeTrue": 1,
      |              "compare": "AtLeast",
      |              "possibilities": [
      |                {
      |                  "displayName": "Cough",
      |                  "termPath": "\\\\i2b2_DIAG\\i2b2\\Diagnoses\\Symptoms, signs, and ill-defined conditions (780-799)\\Symptoms (780-789)\\(786) Symptoms involving respirat~\\(786-2) Cough\\",
      |                  "constraint": null,
      |                  "encodedClass": "Concept"
      |                }
      |              ]
      |            },
      |            "startDate": null,
      |            "endDate": null,
      |            "occursAtLeast": 1
      |          },
      |          "subsequent": [
      |            {
      |              "conceptGroup": {
      |                "concepts": {
      |                  "nMustBeTrue": 1,
      |                  "compare": "AtLeast",
      |                  "possibilities": [
      |                    {
      |                      "displayName": "Acute upper respiratory infections of multiple or unspecified sites",
      |                      "termPath": "\\\\i2b2_DIAG\\i2b2\\Diagnoses\\Respiratory system (460-519)\\Acute respiratory infections (460-466)\\(465) Acute upper respiratory inf~\\",
      |                      "constraint": null,
      |                      "encodedClass": "Concept"
      |                    }
      |                  ]
      |                },
      |                "startDate": null,
      |                "endDate": null,
      |                "occursAtLeast": 1
      |              },
      |              "previousEventConstraint": {
      |                "boundary": "START",
      |                "anchor": "ANY"
      |              },
      |              "thisEventConstraint": {
      |                "boundary": "START",
      |                "anchor": "ANY"
      |              },
      |              "relationship": "Before",
      |              "primaryTimeConstraint": {
      |                "operator": "GREATER",
      |                "value": 0,
      |                "timeUnit": "Day"
      |              },
      |              "secondaryTimeConstraint": null
      |            }
      |          ],
      |          "encodedClass": "Timeline"
      |        }
      |      ],
      |      "encodedClass": "Conjunction"
      |    }
      |  },
      |  "breakdownNames": [],
      |  "queryName": "ByGreaterEqual",
      |  "queryNotes": "ByGreaterEqual notes",
      |  "queryFaved": false,
      |  "nodeOfOriginId": 601196408267489082,
      |  "researcherId": -1346861970,
      |  "encodedClass": "QueryProgress"
      |}""".stripMargin
  )).get

  /*
  When add new queries to this test, consider adding new tests of those features to
  I2b2QueryDefinitionTest and BasicQueryTest.

  When you use these queries in other tests, please add those tests to the above comment.
   */
}

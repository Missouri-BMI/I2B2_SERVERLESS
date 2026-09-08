package net.shrine.qep

import cats.effect.unsafe.implicits.global

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}
import io.circe.syntax.EncoderOps
import net.shrine.api.ontology.LuceneSearcher
import net.shrine.protocol.version.v2.querydefinition.Conjunction.AtLeast
import net.shrine.protocol.version.v2.querydefinition.FlagConstraints.High
import net.shrine.protocol.version.v2.querydefinition.NumberConstraint.GreaterThan
import net.shrine.protocol.version.v2.querydefinition.{Conjunction, DoubleNumberConstraint, QueryDefinition, QueryDefinitionTest, SingleNumberConstraint, Concept => V2Concept, ConceptGroup => V2ConceptGroup}
import net.shrine.qep.TimeSpanOperator.{GREATEREQUAL, LESS}
import net.shrine.qep.TimeSpanUnit.DAY
import org.junit.jupiter.api.Assertions.{assertEquals, fail}
import org.junit.jupiter.api.Test

import scala.annotation.unused

class BasicQueryTest {

  val basicQuery: BasicQuery = BasicQuery(
    name = "test query",
    notes = None,
    faved = false,
    dataDistributionTypes = Set.empty,
    conceptGroups = List(
      ConceptGroup(
        concepts = List(
          Concept(
            displayName = "Female",
            path = "\\\\SHRINE\\SHIRNE\\Demographics\\Sex\\Female\\",
            constraint = Option(ConceptValueConstraint(
              constraintType = "LT",
              unit = Option("mmol/L"),
              value = List("1")
            )
            )
          )
        ),
        isExcluded = false,
        options = ConceptGroupOptions(None,None,1)
      )
    )
  )

  val sameEncounterQuery: BasicQuery = BasicQuery(
    name = "same encounter query",
    notes = None,
    faved = false,
    dataDistributionTypes = Set.empty,
    conceptGroups = List(
      ConceptGroup(
        concepts = List(
          Concept(
            displayName = "Hit by comet",
            path = "\\\\SHRINE\\SHIRNE\\Hit\\by\\comet\\",
            constraint = None
          )
        ),
        isExcluded = false,
        options = ConceptGroupOptions(None, None, 1, Option(LinkedBy.SameEncounter))
      ),
      ConceptGroup(
        concepts = List(
          Concept(
            displayName = "Broken hip",
            path = "\\\\SHRINE\\SHIRNE\\broken\\hip\\bone\\",
            constraint = None
          )
        ),
        isExcluded = false,
        options = ConceptGroupOptions(None, None, 1, Option(LinkedBy.SameEncounter))
      ),
    ),
  )

  val sameInstanceQuery: BasicQuery = BasicQuery(
    name = "same instance query",
    notes = Some("same instance query notes"),
    dataDistributionTypes = Set.empty,
    conceptGroups = List(
      ConceptGroup(
        concepts = List(
          Concept(
            displayName = "Hit by comet",
            path = "\\\\SHRINE\\SHIRNE\\Hit\\by\\comet\\",
            constraint = None
          )
        ),
        isExcluded = false,
        options = ConceptGroupOptions(None, None, 1, Option(LinkedBy.SameInstance))
      ),
      ConceptGroup(
        concepts = List(
          Concept(
            displayName = "Broken hip",
            path = "\\\\SHRINE\\SHIRNE\\broken\\hip\\bone\\",
            constraint = None
          )
        ),
        isExcluded = false,
        options = ConceptGroupOptions(None, None, 1, Option(LinkedBy.SameInstance))
      ),
    ),
  )

  val event1: ConceptGroup = ConceptGroup(
    concepts = List(
      Concept(
        displayName = "Spider bite",
        path = "\\\\animal\\bite\\spider\\radio\\active\\",
        constraint = Option(ConceptValueConstraint(
          constraintType = "GT",
          unit = Option("REM"),
          value = List("1")
        )
        )
      )
    ),
    isExcluded = false,
    options = ConceptGroupOptions(None,None,1)
  )

  val event2: ConceptGroup = ConceptGroup(
    concepts = List(
      Concept(
        displayName = "Shoot webs out of wrists",
        path = "\\\\superpower\\shoot\\webs\\spider\\bothWrists\\",
        constraint = None
      )
    ),
    isExcluded = false,
    options = ConceptGroupOptions(None,None,1)
  )

  val spiderWomanQuery: BasicQuery = basicQuery.copy(timeline = Option(Timeline(
    Seq(event1,event2),
    Seq(BasicTimelineLink(
      previousEventConstraint = EventConstraint(
        boundary = EventBoundary.START,
        anchor = EventAnchor.ANY
      ),
      thisEventConstraint = EventConstraint(
        boundary = EventBoundary.START,
        anchor = EventAnchor.ANY
      ),
      relationship = Relationship.Before
    ))
  )))

  val timelineAndNoTimeSpanQuery: BasicQuery = basicQuery.copy(timeline = Option(Timeline(
    Seq(event1, event2),
    Seq(BasicTimelineLink(
      previousEventConstraint = EventConstraint(boundary = EventBoundary.START, anchor = EventAnchor.ANY) ,
      thisEventConstraint = EventConstraint(boundary = EventBoundary.START, anchor = EventAnchor.ANY) ,
      relationship = Relationship.Simultaneous,
    )),
  )))

  val event1of3: ConceptGroup = ConceptGroup(
    concepts = List(
      Concept(
        displayName = "Neoplasms (140-239.99)",
        path = "\\\\ACT_DX_ICD9_2018\\ACT\\Diagnosis\\ICD9\\V2_2018AA\\A18090800\\A8359006\\A8352677\\",
        constraint = None
      )
    ),
    isExcluded = false,
    options = ConceptGroupOptions(None,None,1)
  )
  val event2of3: ConceptGroup = ConceptGroup(
    concepts = List(
      Concept(
        displayName = "Aminoglycosides (AM300)",
        path = "\\\\ACT_MED_VA_2018\\ACT\\Medications\\MedicationsByVaClass\\V2_09302018\\VA000\\AM000\\AM300\\",
        constraint = None
      )
    ),
    isExcluded = false,
    options = ConceptGroupOptions(None,None,1)
  )
  val event3of3: ConceptGroup = ConceptGroup(
    concepts = List(
      Concept(
        displayName = "240-279.99 Endocrine, Nutritional And Metabolic Diseases, And Immunity Disorders",
        path = "\\\\ACT_DX_ICD9_2018\\ACT\\Diagnosis\\ICD9\\V2_2018AA\\A18090800\\A8359006\\A8359307\\",
        constraint = None
      )
    ),
    isExcluded = false,
    options = ConceptGroupOptions(None,None,1)
  )

  val oneEventTimelineAnd2TimeSpanQuery: BasicQuery = BasicQuery(
    name = "Neoplasms (140-239.99)@10:15:25",
    notes = None,
    faved = false,
    dataDistributionTypes = Set.empty,
    conceptGroups = List.empty,
    timeline = Option(Timeline(
      timelineEvents = Seq(event1of3),
      timelineLinks = Seq(
        BasicTimelineLink(
          previousEventConstraint = EventConstraint(
            boundary = EventBoundary.START,
            anchor = EventAnchor.ANY
          ),
          thisEventConstraint = EventConstraint(
            boundary = EventBoundary.START,
            anchor = EventAnchor.ANY
          ),
          relationship = Relationship.Before,
          primaryTimeSpan = Option(TimeSpan(
            operator = TimeSpanOperator.GREATEREQUAL,
            value = 5,
            unit = TimeSpanUnit.MONTH
          )),
          secondaryTimeSpan = Option(TimeSpan(
            operator = TimeSpanOperator.EQUAL,
            value = 15,
            unit = TimeSpanUnit.YEAR
          ))
        )
        )
      )
    ))

  val oneEventTimelineAnd1TimeSpanQuery: BasicQuery = BasicQuery(
    name = "Neoplasms (140-239.99)@10:15:25",
    notes = None,
    faved = false,
    dataDistributionTypes = Set.empty,
    conceptGroups = List.empty,
    timeline = Option(Timeline(
      timelineEvents = Seq(event1of3),
      timelineLinks = Seq(
        BasicTimelineLink(
          previousEventConstraint = EventConstraint(
            boundary = EventBoundary.START,
            anchor = EventAnchor.ANY
          ),
          thisEventConstraint = EventConstraint(
            boundary = EventBoundary.START,
            anchor = EventAnchor.ANY
          ),
          relationship = Relationship.Before,
          primaryTimeSpan = Option(TimeSpan(
            operator = TimeSpanOperator.GREATEREQUAL,
            value = 5,
            unit = TimeSpanUnit.MONTH
          ))
        )
      )
    )
    ))

  val oneEventTimelineAnd0TimeSpanQuery: BasicQuery = BasicQuery(
    name = "Neoplasms (140-239.99)@10:15:25",
    notes = None,
    faved = false,
    dataDistributionTypes = Set.empty,
    conceptGroups = List.empty,
    timeline = Option(Timeline(
      timelineEvents = Seq(event1of3),
      timelineLinks = Seq(
        BasicTimelineLink(
          previousEventConstraint = EventConstraint(
            boundary = EventBoundary.START,
            anchor = EventAnchor.ANY
          ),
          thisEventConstraint = EventConstraint(
            boundary = EventBoundary.START,
            anchor = EventAnchor.ANY
          ),
          relationship = Relationship.Before
          ))
        )
      )
    )

  val threeEventTimelineAnd2x2TimeSpanQuery: BasicQuery = BasicQuery(
    name = "Neoplasms (140-239.99)@10:15:25",
    notes = None,
    faved = false,
    dataDistributionTypes = Set.empty,
    conceptGroups = List.empty,
    timeline = Option(Timeline(
      timelineEvents = Seq(event1of3,event2of3,event3of3),
      timelineLinks = Seq(
        BasicTimelineLink(
          previousEventConstraint = EventConstraint(
            boundary = EventBoundary.START,
            anchor = EventAnchor.ANY
          ),
          thisEventConstraint = EventConstraint(
            boundary = EventBoundary.START,
            anchor = EventAnchor.ANY
          ),
          relationship = Relationship.Before,
          primaryTimeSpan = Option(TimeSpan(
            operator = TimeSpanOperator.GREATEREQUAL,
            value = 5,
            unit = TimeSpanUnit.MONTH
          )),
          secondaryTimeSpan = Option(TimeSpan(
            operator = TimeSpanOperator.EQUAL,
            value = 15,
            unit = TimeSpanUnit.YEAR
          ))
        ),
        BasicTimelineLink(
          previousEventConstraint = EventConstraint(
            boundary = EventBoundary.START,
            anchor = EventAnchor.ANY
          ),
          thisEventConstraint = EventConstraint(
            boundary = EventBoundary.START,
            anchor = EventAnchor.ANY
          ),
          relationship = Relationship.Before,
          Some(
            TimeSpan(
              GREATEREQUAL,
              0,
              DAY
            )
          ),
          Some(
            TimeSpan(
              LESS,
              1,
              DAY
            )
          )
        )
      )
    )
  ))

  lazy val basicQueryText: String = readFile("basicQuery.json")

  //TODO replace with call to readFile("queryWithTimeline.json") - Fails in Bamboo
  val spiderWomanText:String = """{
                    |  "name" : "test query",
                    |  "notes" : null,
                    |  "faved" : false,
                    |  "dataDistributionTypes" : [],
                    |  "conceptGroups" : [
                    |    {
                    |      "concepts" : [
                    |        {
                    |          "displayName" : "Female",
                    |          "path" : "\\\\SHRINE\\SHIRNE\\Demographics\\Sex\\Female\\",
                    |          "constraint" : {
                    |            "constraintType" : "LT",
                    |            "value" : [
                    |              "1"
                    |            ],
                    |            "unit" : "mmol/L"
                    |          }
                    |        }
                    |      ],
                    |      "panelTiming" : null,
                    |      "isExcluded" : false,
                    |      "options" : {
                    |        "startDate" : null,
                    |        "endDate" : null,
                    |        "occurrences" : 1
                    |      }
                    |    }
                    |  ],
                    |  "timeline" : {
                    |    "timelineEvents" : [
                    |    {
                    |      "concepts" : [
                    |        {
                    |          "displayName" : "Spider bite",
                    |          "path" : "\\\\animal\\bite\\spider\\radio\\active\\",
                    |          "constraint" : {
                    |            "constraintType" : "GT",
                    |            "value" : [
                    |              "1"
                    |            ],
                    |            "unit" : "REM"
                    |          }
                    |        }
                    |      ],
                    |      "panelTiming" : null,
                    |      "isExcluded" : false,
                    |      "options" : {
                    |        "startDate" : null,
                    |        "endDate" : null,
                    |        "occurrences" : 1
                    |      }
                    |    },
                    |    {
                    |      "concepts" : [
                    |        {
                    |          "displayName" : "Shoot webs out of wrists",
                    |          "path" : "\\\\superpower\\shoot\\webs\\spider\\bothWrists\\",
                    |          "constraint" : null
                    |        }
                    |      ],
                    |      "panelTiming" : null,
                    |      "isExcluded" : false,
                    |      "options" : {
                    |        "startDate" : null,
                    |        "endDate" : null,
                    |        "occurrences" : 1
                    |      }
                    |    }
                    |    ],
                    |    "timelineLinks" : [
                    |    {
                    |      "BasicTimelineLink" : {
                    |        "previousEventConstraint" : {
                    |          "boundary" : "START",
                    |          "anchor" : "ANY"
                    |        },
                    |        "thisEventConstraint" : {
                    |          "boundary" : "START",
                    |          "anchor" : "ANY"
                    |        },
                    |        "relationship" : "Before"
                    |      }
                    |    }
                    |    ]
                    |  }
                    |}""".stripMargin

  lazy val queryWithTimelineAndNoTimeSpan:String = readFile("queryWithTimelineAndNoTimeSpan.json")
  lazy val queryWith3EventTimelineAndNotTimeSpan:String = readFile("queryWith3EventTimelineAndNoTimeSpan.json")
  lazy val queryWith3EventTimelineAnd2x2TimeSpans:String = readFile("queryWith3EventTimelineAnd2x2TimeSpan.json")
  lazy val queryWith1EventTimelineAnd2TimeSpans:String = readFile("queryWith1EventTimelineAnd2TimeSpan.json")
  lazy val queryWith1EventTimelineAnd1TimeSpans:String = readFile("queryWith1EventTimelineAnd1TimeSpan.json")
  lazy val queryWith1EventTimelineAnd0TimeSpans:String = readFile("queryWith1EventTimelineAnd0TimeSpan.json")

  val basicQueryWithTimeConstraint: BasicQuery = BasicQuery(
    name = "test query",
    notes = None,
    faved = false,
    dataDistributionTypes = Set.empty,
    conceptGroups = List(
      ConceptGroup(
        concepts = List(
          Concept(
            displayName = "Female",
            path = "\\\\ACT_DEMO\\ACT\\Demographics\\Sex\\",
            constraint = Option(ConceptValueConstraint(
              constraintType = "LT",
              unit = Option("mmol/L"),
              value = List("1")
            )
            )
          )
        ),
        isExcluded = false,
        options = ConceptGroupOptions(None,None,1)
      )
    ),
    timeline = Option(
      Timeline(
        timelineEvents = Seq(
          ConceptGroup(
            concepts = List(
              Concept(
                displayName = "786.2 Cough",
                path = "\\\\ACT_DX_ICD9_2018\\ACT\\Diagnosis\\ICD9\\V2_2018AA\\A18090800\\A8359006\\A8363086\\A8363085\\A8348720\\A8340193\\",
                constraint = None
              )
            ),
            isExcluded = false,
            options = ConceptGroupOptions(None,None,1)
          ),
          ConceptGroup(
            concepts = List(
              Concept(
                displayName = "465.9 Acute upper respiratory infections of unspecified site",
                path = "\\\\ACT_DX_ICD9_2018\\ACT\\Diagnosis\\ICD9\\V2_2018AA\\A18090800\\A8359006\\A8354357\\A8357702\\A8345529\\A8345532\\",
                constraint = None
              )
            ),
            isExcluded = false,
            options = ConceptGroupOptions(None,None,1)
          )
        ),
        timelineLinks = Seq(
          BasicTimelineLink(
            previousEventConstraint = EventConstraint(
              boundary = EventBoundary.START,
              anchor = EventAnchor.ANY
            ),
            thisEventConstraint = EventConstraint(
              boundary = EventBoundary.START,
              anchor = EventAnchor.ANY
            ),
            relationship = Relationship.Before
          )
        )
      )
    )
  )

  @Test
  def testBasicQueryRoundTrip(): Unit = {

    import io.circe.generic.auto.exportEncoder
    val jsonText: String = basicQuery.asJson.toString()

    import io.circe.generic.auto.exportDecoder
    import io.circe.parser.parse
    exportDecoder[BasicQuery].instance.decodeJson(parse(jsonText)
      .getOrElse(fail(s"Could not parse $jsonText")))
      .fold({df => fail(df.message)},assertEquals(basicQuery,_))
  }

  @Test
  def testSameEncounterQueryRoundTrip(): Unit = {

    import io.circe.generic.auto.exportEncoder
    val jsonText: String = sameEncounterQuery.asJson.toString()

    import io.circe.generic.auto.exportDecoder
    import io.circe.parser.parse
    exportDecoder[BasicQuery].instance.decodeJson(parse(jsonText)
      .getOrElse(fail(s"Could not parse $jsonText")))
      .fold({ df => fail(df.message) }, assertEquals(sameEncounterQuery, _))
  }

  @Test
  def testSameInstanceQueryRoundTrip(): Unit = {

    import io.circe.generic.auto.exportEncoder
    val jsonText: String = sameInstanceQuery.asJson.toString()

    import io.circe.generic.auto.exportDecoder
    import io.circe.parser.parse
    exportDecoder[BasicQuery].instance.decodeJson(parse(jsonText)
        .getOrElse(fail(s"Could not parse $jsonText")))
      .fold({ df => fail(df.message) }, assertEquals(sameInstanceQuery, _))
  }
  @Test
  def testBasicQueryFromJsonString(): Unit = {

    import io.circe.generic.auto.exportDecoder
    import io.circe.parser.parse
    exportDecoder[BasicQuery].instance.decodeJson(parse(basicQueryText)
      .getOrElse(fail(s"Could not parse $basicQueryText")))
      .fold({df => fail(df.message)},assertEquals(basicQuery,_))
  }

  @Test
  def testBasicQueryWithTimelineRoundTrip(): Unit = {

    import io.circe.generic.auto.exportEncoder
    val jsonText: String = spiderWomanQuery.asJson.toString()

    import io.circe.generic.auto.exportDecoder
    import io.circe.parser.parse
    exportDecoder[BasicQuery].instance.decodeJson(parse(jsonText)
      .getOrElse(fail(s"Could not parse $jsonText")))
      .fold({df => fail(df.message)},assertEquals(spiderWomanQuery,_))
  }

  @Test
  def testQueryWithTimelineFromJson():Unit = {
    import io.circe.generic.auto.exportDecoder
    import io.circe.parser.parse
    exportDecoder[BasicQuery].instance.decodeJson(parse(spiderWomanText)
      .getOrElse(fail(s"Could not parse $spiderWomanText")))
      .fold({df => fail(df.message)},assertEquals(spiderWomanQuery,_))
  }

  @Test
  def testQueryWithTimelineAndTimeSpanFromJson():Unit = {

    import io.circe.generic.auto.exportDecoder
    import io.circe.parser.parse

    exportDecoder[BasicQuery].instance.decodeJson(parse(queryWithTimelineAndNoTimeSpan)
      .getOrElse(fail(s"Could not parse $queryWithTimelineAndNoTimeSpan")))
      .fold({df => fail(df.message)},assertEquals(timelineAndNoTimeSpanQuery,_))
  }

  @Test
  def testRealQueryWithTimelineRoundTrip():Unit = {
    import io.circe.generic.auto.exportEncoder
    val jsonText: String = basicQueryWithTimeConstraint.asJson.toString()

    import io.circe.generic.auto.exportDecoder
    import io.circe.parser.parse
    exportDecoder[BasicQuery].instance.decodeJson(parse(jsonText)
      .getOrElse(fail(s"Could not parse $jsonText")))
      .fold({df => fail(df.message)},assertEquals(basicQueryWithTimeConstraint,_))
  }

  @Test
  def testRealQueryWithTimelineFromJson():Unit = {
    val jsonText = readFile("realQueryWithTimeline.json")

    import io.circe.generic.auto.exportDecoder
    import io.circe.parser.parse

     exportDecoder[BasicQuery].instance.decodeJson(parse(jsonText)
      .getOrElse(fail(s"Could not parse $jsonText")))
      .fold({df => fail(df.message)},assertEquals(basicQueryWithTimeConstraint,_))
  }

  @Test
  def testDecodeStartQueryWithConstraints(): Unit = {
    val body = readFile("queryWithConstraints.json")

    import io.circe.generic.auto.exportDecoder
    import io.circe.parser.decode

    val expectedV2QueryDefinition = QueryDefinition(
      Conjunction(1,AtLeast,List(
        V2ConceptGroup(Conjunction(1,AtLeast,List(
          V2Concept("Bicarbonate in Blood","""\\ACT\Labs\LP40271-6\LP19403-2\LP46218-1\1959-6\""",Option(High)),
          V2Concept("Bicarbonate in Plasma","""\\ACT\Labs\LP40271-6\LP19403-2\LP46218-1\1962-0\""",Option(SingleNumberConstraint(GreaterThan,6.0,Some("meq/L")))),
          V2Concept("Bicarbonate in Serum","""\\ACT\Labs\LP40271-6\LP19403-2\LP46218-1\1963-8\""",None),
          V2Concept("Carbon dioxide in Serum or Plasma","""\\ACT\Labs\LP40271-6\LP19403-2\LP46218-1\2028-9\""",Some(DoubleNumberConstraint(4.0,5.0,Some("mmol/L")))))
        ),None,None))
      )
    )

    decode[BasicQuery](body).fold(_ => throw new AssertionError(s"Could not decode $body"), { bq =>
      assertEquals(expectedV2QueryDefinition,bq.toV2QueryDefinition)
    })
  }

  @Test
  def testTimeline(): Unit = {
    val basicQueryWithTimeline = {
      val bogusTimeline = Timeline(
        timelineEvents = Seq(
          ConceptGroup(
            concepts = List(
              Concept(
                displayName = "786.2 Cough",
                path = "\\\\ACT_DX_ICD9_2018\\ACT\\Diagnosis\\ICD9\\V2_2018AA\\A18090800\\A8359006\\A8363086\\A8363085\\A8348720\\A8340193\\",
                constraint = None
              )
            ),
            isExcluded = false,
            options = ConceptGroupOptions(None,None,1)
          ),
          ConceptGroup(
            concepts = List(
              Concept(
                displayName = "465.9 Acute upper respiratory infections of unspecified site",
                path = "\\\\ACT_DX_ICD9_2018\\ACT\\Diagnosis\\ICD9\\V2_2018AA\\A18090800\\A8359006\\A8354357\\A8357702\\A8345529\\A8345532\\",
                constraint = None
              )
            ),
            isExcluded = false,
            options = ConceptGroupOptions(None,None,occurrences = 1)
          )
        ),
        timelineLinks = Seq(
          BasicTimelineLink(
            previousEventConstraint = EventConstraint(boundary = EventBoundary.START, anchor = EventAnchor.ANY),
            thisEventConstraint = EventConstraint(boundary = EventBoundary.START, anchor = EventAnchor.ANY),
            primaryTimeSpan = None,
            relationship = Relationship.Before
          )
        )
      )
      basicQuery.copy(timeline = Some(bogusTimeline))
    }
    @unused
    val v2Query = basicQueryWithTimeline.toV2QueryDefinition
  }

  val bogusConstraintJson: String = readFile("queryWithBogusConstraint.json")

  @Test
  def testReadBogusConstraintJson():Unit = {
    import io.circe.generic.auto.exportDecoder
    import io.circe.parser.decode

    decode[BasicQuery](bogusConstraintJson).getOrElse(throw new AssertionError(s"Could not decode $bogusConstraintJson"))
  }

  def readFile(fileName: String): String = {
    val path = Paths.get(ClassLoader.getSystemResource(fileName).toURI)
    Files.readString(path, StandardCharsets.UTF_8)
  }
  @Test
  def testQueryOnlyTimelineRoundTrip():Unit = {
    val onlyTimelineQuery = basicQueryWithTimeConstraint.copy(conceptGroups = List.empty)

    import io.circe.generic.auto.exportEncoder
    val jsonText: String = onlyTimelineQuery.asJson.toString()

    import io.circe.generic.auto.exportDecoder
    import io.circe.parser.parse
    exportDecoder[BasicQuery].instance.decodeJson(parse(jsonText)
      .getOrElse(fail(s"Could not parse $jsonText")))
      .fold({df => fail(df.message)},assertEquals(onlyTimelineQuery,_))
  }

  val onlyTimelineQueryReadableText = "<span class=\"criteriaPanelSeparator\">Find patients</span> <span class=\"criteriaPanelSeparator\">when the start of any </span><span class=\"criteriaPanelSeparator\"> with</span> (Diagnoses 786.2 Cough)<br/><span class=\"criteriaPanelSeparator\">occurs before the start of any </span><span class=\"criteriaPanelSeparator\"> with</span> (Diagnoses 465.9 Acute upper respiratory infections of unspecified site)"
  @Test
  def testQueryOnlyTimelineAsReadableText():Unit = {
    val onlyTimelineQuery = basicQueryWithTimeConstraint.copy(conceptGroups = List.empty)
    val codeCategories = LuceneSearcher.getCodeCategoriesMap.unsafeRunSync()

    val text = onlyTimelineQuery.htmlQueryText(codeCategories)
    assertEquals(onlyTimelineQueryReadableText,text)
  }

  @Test
  def testQueryOnlyTimelineAsValidQuery():Unit = {
    val onlyTimelineQuery = basicQueryWithTimeConstraint.copy(conceptGroups = List.empty)
    val codeCategories = LuceneSearcher.getCodeCategoriesMap.unsafeRunSync()

    val text = Option(onlyTimelineQuery.htmlQueryText(codeCategories))

    assertEquals(Some(onlyTimelineQueryReadableText),text)
  }

  @Test
  def testTwoConceptGroupQueryRoundTrip():Unit = {
    val query = QueryDefinitionTest.twoConceptGroupQuery
    val basicQuery = BasicQuery.fromV2Query(query)
    val queryFromBasicQuery = basicQuery.toV2QueryDefinition

    assertEquals(query.queryDefinition,queryFromBasicQuery)
  }

  @Test
  def testSameEncounterRoundTrip(): Unit = {
    val query = QueryDefinitionTest.sameEncounterQuery
    val basicQuery = BasicQuery.fromV2Query(query)
    val queryFromBasicQuery = basicQuery.toV2QueryDefinition

    assertEquals(query.queryDefinition, queryFromBasicQuery)
  }
  @Test
  def testTwoEventTimelineQueryRoundTrip():Unit = {
    val query = QueryDefinitionTest.twoEventTimelineQuery
    val basicQuery = BasicQuery.fromV2Query(query)
    val queryFromBasicQuery = basicQuery.toV2QueryDefinition

    assertEquals(query.queryDefinition,queryFromBasicQuery)
  }

  @Test
  def testTwoEventTimelineQueryWithRelatedToRoundTrip(): Unit = {
    val query = QueryDefinitionTest.twoEventTimelineRelatedToQuery
    val basicQuery = BasicQuery.fromV2Query(query)
    val queryFromBasicQuery = basicQuery.toV2QueryDefinition

    assertEquals(query.queryDefinition, queryFromBasicQuery)
  }

  @Test
  def testTwoEventTimelineQueryWithRelatedToAndPrimaryAndSecondaryTimeConstraintRoundTrip(): Unit = {
    val query = QueryDefinitionTest.twoEventTimelineRelatedToWithTwoTimeSpanQuery
    val basicQuery = BasicQuery.fromV2Query(query)
    val queryFromBasicQuery = basicQuery.toV2QueryDefinition

    assertEquals(query.queryDefinition, queryFromBasicQuery)
  }

  @Test
  def testThreeEventTimelineQueryRoundTrip():Unit = {
    val query = QueryDefinitionTest.threeEventTimelineQuery
    val basicQuery = BasicQuery.fromV2Query(query)
    val queryFromBasicQuery = basicQuery.toV2QueryDefinition

    assertEquals(query.queryDefinition,queryFromBasicQuery)
  }

  @Test
  def testThreeEventTimelineReadableText():Unit = {
    val expectedHtmlQueryText = """<span class="criteriaPanelSeparator">Find patients</span> <span class="criteriaPanelSeparator">when the start of any </span><span class="criteriaPanelSeparator"> with</span> (Diagnoses Neoplasms (140-239.99))<br/><span class="criteriaPanelSeparator">occurs &gt;= 5 months and = 15 years before the start of any </span><span class="criteriaPanelSeparator"> with</span> (Medications Aminoglycosides (AM300))<br/><span class="criteriaPanelSeparator">and when the start of any </span><span class="criteriaPanelSeparator"> with</span> (Medications Aminoglycosides (AM300))<br/><span class="criteriaPanelSeparator">occurs &gt;= 0 days and &lt; 1 day before the start of any</span><span class="criteriaPanelSeparator"> with</span> (Diagnoses 240-279.99 Endocrine, Nutritional And Metabolic Diseases, And Immunity Disorders)"""

    val codeCategories = LuceneSearcher.getCodeCategoriesMap.unsafeRunSync()
    assertEquals(expectedHtmlQueryText, threeEventTimelineAnd2x2TimeSpanQuery.htmlQueryText(codeCategories))
  }

  @Test
  def testThreeEventTimelineWith2x2TimespanJson():Unit = {
    import io.circe.generic.auto.exportDecoder
    import io.circe.parser.parse

    import io.circe.generic.auto.exportEncoder
    println(threeEventTimelineAnd2x2TimeSpanQuery.asJson.toString())
    exportDecoder[BasicQuery].instance.decodeJson(parse(queryWith3EventTimelineAnd2x2TimeSpans)
      .getOrElse(fail(s"Could not parse $queryWith3EventTimelineAnd2x2TimeSpans")))
      .fold({df => fail(df.message)},assertEquals(threeEventTimelineAnd2x2TimeSpanQuery,_))
  }

  @Test
  def testThreeEventTimelineWith2TimespanJson(): Unit = {
    import io.circe.generic.auto.exportDecoder
    import io.circe.parser.parse

    import io.circe.generic.auto.exportEncoder

    exportDecoder[BasicQuery].instance.decodeJson(parse(queryWith1EventTimelineAnd2TimeSpans)
      .getOrElse(fail(s"Could not parse $queryWith1EventTimelineAnd2TimeSpans")))
      .fold({ df => fail(df.message) }, assertEquals(oneEventTimelineAnd2TimeSpanQuery,_))
  }

  @Test
  def testOneEventTimelineWith1TimespanJson(): Unit = {
    import io.circe.generic.auto.exportDecoder
    import io.circe.parser.parse

    exportDecoder[BasicQuery].instance.decodeJson(parse(queryWith1EventTimelineAnd1TimeSpans)
      .getOrElse(fail(s"Could not parse $queryWith1EventTimelineAnd1TimeSpans")))
      .fold({ df => fail(df.message) }, assertEquals(oneEventTimelineAnd1TimeSpanQuery, _))
  }

  @Test
  def testOneEventTimelineWith0TimespanJson(): Unit = {
    import io.circe.generic.auto.exportDecoder
    import io.circe.parser.parse

    exportDecoder[BasicQuery].instance.decodeJson(parse(queryWith1EventTimelineAnd0TimeSpans)
      .getOrElse(fail(s"Could not parse $queryWith1EventTimelineAnd0TimeSpans")))
      .fold({ df => fail(df.message) }, assertEquals(oneEventTimelineAnd0TimeSpanQuery, _))
  }

  // ==================================

  val timelineLinkQuery: BasicQuery =
    BasicQuery(
      name = "Test",
      notes = None,
      faved = false,
      dataDistributionTypes = Set.empty,
      conceptGroups = List(),
      timeline = Option(
        Timeline(
          timelineEvents = Seq(
            ConceptGroup(
              concepts = List(
                Concept(
                  displayName = "Certain infectious and parasitic diseases (a00-b99)",
                  path = "\\\\ICD10_ICD9\\Diagnoses\\(A00-B99) Cert~ugmm\\",
                  constraint = None
                )
              ),
              isExcluded = false,
              options = ConceptGroupOptions(None, None, 1)
            ),
            ConceptGroup(
              concepts = List(
                Concept(
                  displayName = "Diseases of the blood and blood-forming organs and certain disorders involving the immune mechanism (d50-d89)",
                  path = "\\\\ICD10_ICD9\\Diagnoses\\(D50-D89) Dise~nx0c\\",
                  constraint = None
                )
              ),
              isExcluded = false,
              options = ConceptGroupOptions(None, None, 1)
            )
          ),
          timelineLinks = Seq(
            BasicTimelineLink(
              previousEventConstraint = EventConstraint(EventBoundary.START, anchor = EventAnchor.ANY),
              thisEventConstraint = EventConstraint(EventBoundary.START, anchor = EventAnchor.ANY),
              primaryTimeSpan = None,
              relationship = Relationship.Before
            )
          )
        )
      )
  )

  @Test
  def testQueryWithTimelineLink(): Unit = {
    import io.circe.generic.auto.exportDecoder
    import io.circe.parser.parse

    val queryWithTimelineLink:String = readFile("queryWithTimelineLink.json")

    exportDecoder[BasicQuery].instance.decodeJson(parse(queryWithTimelineLink)
      .getOrElse(fail(s"Could not parse $queryWithTimelineLink")))
      .fold({ df => fail(df.message) }, assertEquals(timelineLinkQuery, _))
  }

}


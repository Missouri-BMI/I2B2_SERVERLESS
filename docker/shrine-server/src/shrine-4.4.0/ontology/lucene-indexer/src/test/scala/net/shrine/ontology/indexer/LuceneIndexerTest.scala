package net.shrine.ontology.indexer

import java.io.File
import java.nio.charset.StandardCharsets

import net.shrine.log.Log
import net.shrine.ontology.AutoSuggestResult
import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.queryparser.classic.QueryParser
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.search.suggest.Lookup
import org.apache.lucene.search.suggest.analyzing.AnalyzingInfixSuggester
import org.apache.lucene.store.FSDirectory
import org.apache.lucene.util.BytesRef
import org.junit.Test
import org.scalatest.FlatSpec

import scala.jdk.CollectionConverters.ListHasAsScala

class LuceneIndexerTest extends FlatSpec {

  private val testOutputDir = "target/"
  val searchIndexDir: String = testOutputDir + "test_lucene_index"
  val searchIndexZip: String = testOutputDir + "test_lucene_index.zip"
  val suggestIndexDir: String = testOutputDir + "test_suggest_index"
  val suggestIndexZip: String = testOutputDir + "test_suggest_index.zip"

  @Test
  def testCreateSearchIndex(): Unit = {

    val indexDir: File = new File(searchIndexDir)
    val directory: FSDirectory = FSDirectory.open(indexDir.toPath)
    val searcher = new IndexSearcher(DirectoryReader.open(directory))

    val qp = new QueryParser("isRoot", new StandardAnalyzer)
    val query = qp.parse("true")

    val maxHits: Int = 10000
    val foundRootDocs = searcher.search(query, maxHits)

    val rootHits = foundRootDocs.totalHits.value

    assertResult(14)(rootHits)

    val searchHits: Array[String] = for {
      sd <- foundRootDocs.scoreDocs
    } yield {
      searcher.doc(sd.doc).get("displayName")
    }

    // extra spaces in between words are needed, that's what is in the ontology
    val expectedDisplayNames =
      """ACT Laboratory Tests (Provisional)
        |ACT Medications VA Classes
        |ACT Procedures CPT-4
        |ACT Procedures HCPCS
        |ACT Demographics
        |ACT Diagnoses ICD10-ICD9
        |ACT Laboratory Tests
        |ACT Visit Details
        |ACT Medications Alphabetical
        |ACT Procedures   ICD-9-Proc
        |ACT Procedures ICD-10-PCS
        |ACT Diagnoses  ICD-9-CM
        |ACT Diagnoses ICD-10
        |ACT COVID-19""".stripMargin

    val rootDisplayNames = searchHits.mkString("\n")
    assertResult(expectedDisplayNames)(rootDisplayNames)

    val qpChildren = new QueryParser("isRoot", new StandardAnalyzer)
    val queryChildren = qpChildren.parse("false")
    val foundChildren = searcher.search(queryChildren, maxHits)

    val childrenHits = foundChildren.totalHits.value
    assert(childrenHits > 10000)

    val conceptsCategories: Array[String] = for {
      sd <- foundChildren.scoreDocs
    } yield {
      searcher.doc(sd.doc).get("conceptCategory")
    }

    val conceptCategoryHits = conceptsCategories.length
    assertResult(10000)(conceptCategoryHits)

    val codeCategories: Array[String] = for {
      sd <- foundRootDocs.scoreDocs
    } yield {
      searcher.doc(sd.doc).get("codeCategory")
    }

    val expectedCodeCategoriesAsString =
      """Laboratory Tests
        |Medications
        |Procedures
        |Procedures
        |Demographics
        |Diagnoses
        |Laboratory Tests
        |Visit Details
        |Medications
        |Procedures
        |Procedures
        |Diagnoses
        |Diagnoses
        |Covid-19""".stripMargin
    val actualCodeCategories = codeCategories.mkString("\n")
    assertResult(expectedCodeCategoriesAsString)(actualCodeCategories)
  }

  @Test
  def testCreateSearchIndexWithCodeSets(): Unit = {
    val searchIndexDirectoryName = searchIndexDir
    val indexDir: File = new File(searchIndexDirectoryName)
    val directory: FSDirectory = FSDirectory.open(indexDir.toPath)
    val searcher = new IndexSearcher(DirectoryReader.open(directory))

    val qp = new QueryParser("isRoot", new StandardAnalyzer)
    val query = qp.parse("true")

    val maxHits: Int = 10000
    val foundRootDocs = searcher.search(query, maxHits)


    val codeSets: Array[String] = for {
      sd <- foundRootDocs.scoreDocs
    } yield {
      Option(searcher.doc(sd.doc).get("codeSet")).toString
    }
    val expectedCodeSetsAsString =
      """None
        |None
        |Some(Procedures CPT-4)
        |Some(Procedures HCPCS)
        |None
        |Some(Diagnoses ICD10-ICD9)
        |None
        |None
        |None
        |Some(Procedures ICD-9-Proc)
        |Some(Procedures ICD-10-PCS)
        |Some(Diagnoses ICD-9-CM)
        |Some(Diagnoses ICD-10)
        |None""".stripMargin

    val actualCodeSets = codeSets.mkString("\n")
    assertResult(expectedCodeSetsAsString)(actualCodeSets)
  }

  @Test
  def testCreateSuggestIndex(): Unit = {

    val autoSuggestTerm = "procedures dial"
    val expectedSuggestions = List[AutoSuggestResult](
      AutoSuggestResult("dialysis procedures",3,2098565),
      AutoSuggestResult("dialysis procedures services",2,1867827),
      AutoSuggestResult("dialysis miscellaneous procedures",1,792890),
      AutoSuggestResult("dialysis other procedures",1,792854)
    )

    // Create a suggester based on the existing index directory
    val indexDirectory: File = new File(suggestIndexDir)
    val luceneDirectory: FSDirectory = FSDirectory.open(indexDirectory.toPath)
    val analyzer = new StandardAnalyzer
    val suggester = new AnalyzingInfixSuggester(luceneDirectory, analyzer, analyzer, 3, true)

    val contextStr = "all"
    val contexts: java.util.HashSet[BytesRef] = new java.util.HashSet[BytesRef]
    contexts.add(new BytesRef(contextStr.getBytes("UTF8")))
    val results: List[Lookup.LookupResult] = suggester.lookup(autoSuggestTerm, contexts, 1000, true, true).asScala.toList

    val suggestions = for (result <- results) yield {
      val payload = new String(result.payload.bytes, StandardCharsets.UTF_8)
      val occurrences = payload.toLong
      AutoSuggestResult(occurrences = occurrences, weight = result.value, suggestion = result.key.toString)
    }

    Log.debug(s"Expected suggestions: ${expectedSuggestions.length}")
    expectedSuggestions.foreach(s => {
      Log.debug(s.toString)
    })

    Log.debug(s"Actual Suggestions: ${suggestions.length}")
    suggestions.foreach(s => {
      Log.debug(s.toString)
    })

    assertResult(expectedSuggestions)(suggestions)

  }

}

package net.shrine.api.ontology

import java.io.File
import java.nio.charset.StandardCharsets
import java.util.HashSet
import cats.effect.IO
import net.shrine.config.ConfigSource
import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.search.suggest.Lookup
import org.apache.lucene.search.suggest.analyzing.AnalyzingInfixSuggester
import org.apache.lucene.store.FSDirectory
import org.apache.lucene.util.BytesRef

import scala.jdk.CollectionConverters.ListHasAsScala

/**
 * Note: unlike the LuceneIndexer the Lucene suggester does not use stop-words when analyzing (parsing) the query.
 * This is so it can match incoming terms like "of" to words like "official" in the index
 */
object LuceneSuggester {

  private lazy val suggestIndexDirectoryName: String = ConfigSource.config.getString("shrine.lucene.suggestDirectory")
  private lazy val suggestionsReturnedCount: Int = ConfigSource.config.getInt("shrine.lucene.suggestCount")
  private lazy val suggestIndexDirectory: File = new File(suggestIndexDirectoryName)
  private lazy val luceneDirectory: FSDirectory = FSDirectory.open(suggestIndexDirectory.toPath)
  private lazy val analyzer = new StandardAnalyzer()
  private lazy val suggester = new AnalyzingInfixSuggester(luceneDirectory, analyzer, analyzer, 3, true)

  def getSuggestionsIO(suggestQuery: SuggestQuery): IO[List[AutoSuggestResult]] = {

   IO(getSuggestions(suggestQuery))

  }

  /** Return an empty list if the input contains fewer than 3 non-blank characters
   *  Note that if the input contains double-quotes, and empty list will also be returned
   *  because double quotes are not indexed.
   */
  def getSuggestions(suggestQuery: SuggestQuery): List[AutoSuggestResult] = {

    if (
      suggestQuery.suggestString.replace(" ", "").length < 3
    ) {
      List[AutoSuggestResult]()
    }
    else {
      val contextStr = "all"
      val contexts: HashSet[BytesRef] = new HashSet[BytesRef]
      contexts.add(new BytesRef(contextStr.getBytes("UTF8")))

      val results: List[Lookup.LookupResult] = LuceneSuggester.suggester.lookup(suggestQuery.suggestString, contexts, suggestionsReturnedCount, true, true).asScala.toList

      val suggestions = results.slice(0, suggestionsReturnedCount).map{ result => AutoSuggestResult(result) }
      suggestions
    }
  }

}

// Note: the weight attribute is not used in the application, however it is useful for unit tests
case class AutoSuggestResult(suggestion: String, occurrences: Long, weight: Long)
object AutoSuggestResult {
  def apply(result: Lookup.LookupResult): AutoSuggestResult = {
    val payload = new String(result.payload.bytes, StandardCharsets.UTF_8)
    val occurrences = payload.toLong
    new AutoSuggestResult(suggestion = result.key.toString, occurrences = occurrences, weight = result.value)
  }
}

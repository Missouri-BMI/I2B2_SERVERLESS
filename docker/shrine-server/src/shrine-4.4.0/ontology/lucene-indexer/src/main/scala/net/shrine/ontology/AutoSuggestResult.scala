package net.shrine.ontology

import java.nio.charset.StandardCharsets

import org.apache.lucene.search.suggest.Lookup

// Note: the weight attribute is not used in the application, however it is useful for unit tests
case class AutoSuggestResult(suggestion: String, occurrences: Long, weight: Long)
object AutoSuggestResult {
  def apply(result: Lookup.LookupResult): AutoSuggestResult = {
    val payload = new String(result.payload.bytes, StandardCharsets.UTF_8)
    val occurrences = payload.toLong
    new AutoSuggestResult(suggestion = result.key.toString, occurrences = occurrences, weight = result.value)
  }
}
package net.shrine.ontology.indexer

import java.io.{File, FileWriter}
import java.util.HashSet

import net.shrine.log.Log
import org.apache.lucene.analysis.CharArraySet
import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.search.suggest.analyzing.AnalyzingInfixSuggester
import org.apache.lucene.store.Directory
import org.apache.lucene.util.BytesRef

import scala.collection.immutable.ListMap
import scala.collection.mutable.{Map => MutableMap, SortedMap => MutableSortedMap, SortedSet => MutableSortedSet}

object SuggestionIndexer {

  // Stop words are words that Lucene will ignore when either (1) indexing data or (2)
  // parsing a search or suggest query. In the present case the stop words will be filtered out
  // of concept names before they get tokenized and indexed into the suggestion index.
  // So for example the concept "A procedure" will be cleaned-up to "procedure" before getting indexed.
  //
  // From the documentation for Lucene 8,
  // https://lucene.apache.org/core/8_0_0//core/org/apache/lucene/analysis/standard/StandardAnalyzer.html,
  // StandardAnalyzer() without constructor parameters creates an analyzer with no stop words -- whereas
  // older versions created a Standard Analyzer with a default set of stop words:
  // https://lucene.apache.org/core/7_0_0//core/org/apache/lucene/analysis/standard/StandardAnalyzer.html
  // So here we recreated the stopwords "manually" as per
  // https://stackoverflow.com/questions/17527741/what-is-the-default-list-of-stopwords-used-in-lucenes-stopfilter
  import scala.jdk.CollectionConverters.SetHasAsJava
  private val stopWords: CharArraySet = new CharArraySet(Set(
    "a", "an", "and", "are", "as", "at", "be", "but", "by",
    "for", "if", "in", "into", "is", "it",
    "no", "not", "of", "on", "or", "such",
    "that", "the", "their", "then", "there", "these",
    "they", "this", "to", "was", "will", "with").asJava, true)

}

class SuggestionIndexer (
                          maxWordsInSuggestion:Int,
                          suggestIndexDirectory:Directory,
                          suggestOutputDirName:String,
                          suggestOutputZipFileName: String,
                          verbose:Boolean,
                          suggestIndexDumpFilesMap:Map[String,File]
                        )
{

  // suggestionToOccurrencesMap is a mutable map for performance
  val suggestionToOccurrencesMap:MutableMap[String, Int] = MutableMap[String, Int]()

  // mutable for performance
  val suggestionToOccurrencesMapsForCsv:MutableMap[String, MutableMap[String, Int]] = MutableMap[String, MutableMap[String, Int]]()
  for (prefix <- suggestIndexDumpFilesMap.keys) {
    suggestionToOccurrencesMapsForCsv  += (prefix -> MutableMap[String, Int]())
  }

  val conceptNameAnalyzer = new StandardAnalyzer(SuggestionIndexer.stopWords)

  /**
   * Add to the map of suggestion words to occurrences, needed later to create the auto-suggest index
   */
  def generateSuggestionsFromConceptName(conceptName: String): Unit = {

    // Prepare the concept for auto-suggest indexing
    // first, tokenize the concept name (while removing stop words)

    val suggestTokenStream = conceptNameAnalyzer.tokenStream(null, conceptName)
    import org.apache.lucene.analysis.tokenattributes.CharTermAttribute
    val cattr = suggestTokenStream.addAttribute(classOf[CharTermAttribute])
    suggestTokenStream.reset()
    // process the tokens obtained from the concept name
    // first, tokenize the name and save the tokens in a sorted set for further processing
    // mutable for performance
    var wordsInConceptName = MutableSortedSet[String]()
    while ( {suggestTokenStream.incrementToken}) {
      val word = cattr.toString
      wordsInConceptName += word
    }
    if (verbose) Log.info(s"----------------- updated <word-combinations> -> <occurrences> based on the concept name tokens $wordsInConceptName:")

    // Now, go through every combination of 1, 2, or 3 words, always
    // arranged in alphabetical order (so that "word1 word2" and "word2 word1" always
    // both get indexed as "word1 word2"
    val wordsInNameArr: Array[String] = new Array[String](wordsInConceptName.size)
    wordsInConceptName.copyToArray(wordsInNameArr)
    val l = wordsInNameArr.length
    for ( i1 <-0 until l)  {
      val w1 = wordsInNameArr(i1)
      tallySuggestion(List(w1))
      if (maxWordsInSuggestion > 1) {
        for ( i2 <- i1 + 1 until l) {
          val w2 = wordsInNameArr(i2)
          tallySuggestion(List(w1, w2))
          if (maxWordsInSuggestion > 2) {
            for (i3 <- i2 + 1 until l) {
              val w3 = wordsInNameArr(i3)
              tallySuggestion(List(w1, w2, w3))
            }
          }
        }
      }
    }

    suggestTokenStream.end()
    suggestTokenStream.close()

  }

  def tallySuggestion(suggestionWords: List[String]): Unit = {

    // put each token in the suggestionWords map if not in it yet;
    // otherwise increment the occurrence count

    val suggestion = suggestionWords.mkString(" ")
    suggestionToOccurrencesMap.get(suggestion) match {
      case Some(i: Int) => suggestionToOccurrencesMap(suggestion) = i +1
      case None => suggestionToOccurrencesMap += (suggestion -> 1)
    }
    if (verbose) Log.info(s"$suggestion -> ${suggestionToOccurrencesMap(suggestion)}")

    // Add to / update the suggestions maps for the prefix-specific CSV dumps

    if(suggestIndexDumpFilesMap.nonEmpty) {
      suggestionToOccurrencesMapsForCsv.keys.foreach(p => {
        var prefixFound = false
        for (w <- suggestionWords) {
          if (w.startsWith(p)) prefixFound = true
        }
        if (prefixFound) {
          suggestionToOccurrencesMapsForCsv(p).get(suggestion) match {
            case Some(i: Int) => suggestionToOccurrencesMapsForCsv(p)(suggestion) = i + 1
            case None => suggestionToOccurrencesMapsForCsv(p) += (suggestion -> 1)
          }
          if (verbose) Log.info(s"For CSV dump, prefix '$p': $suggestion -> ${suggestionToOccurrencesMapsForCsv(p)(suggestion)}")
        }
      })
    }
  }

  def buildSuggestionIndex(): Unit = {
    // build the lucene index
    val analyzer = new StandardAnalyzer
    val suggester = new AnalyzingInfixSuggester(suggestIndexDirectory, analyzer, analyzer, 3, true)

    try {
      // Prepare weighted data for the suggester

      // mutable for performance
      var occurrenceToSuggestionsMap: MutableSortedMap[Int, MutableSortedSet[String]] = generateOccurrenceToSuggestionsMap()


      Log.info("Ordering suggestions by weight")
      generateOrderedByWeightSuggestionsMap(occurrenceToSuggestionsMap, suggester)

      occurrenceToSuggestionsMap = occurrenceToSuggestionsMap.empty

      Log.info(s"Suggester word count: ${suggester.getCount}")

      // Create a CSV dump for each prefix that was specified
      suggestIndexDumpFilesMap.keys.foreach(p =>
        generateCSV(p, suggestIndexDumpFilesMap(p), suggestionToOccurrencesMapsForCsv(p))
      )

      suggestionToOccurrencesMapsForCsv --= suggestionToOccurrencesMapsForCsv.keys
    } finally {
      suggester.close();
    }
  }

  object DescendingAlphabetOrdering extends Ordering[String] {
    def compare(element1:String, element2:String): Int = element2.compareTo(element1)
  }

  /**
   * generate the map of occurrences to the sorted set of suggestions that have that number
   * of occurrences in the ontology's concept names
   * @return
   */
  def generateOccurrenceToSuggestionsMap(): MutableSortedMap[Int, MutableSortedSet[String]] = {

    Log.debug(s"Generating the occurrence to suggestions map  from ${suggestionToOccurrencesMap.size} suggestions")
    // Generate map of occurrences -> sorted set of suggestions with that number of occurrences
    // mutable for performance
    var occurrenceToSuggestionsMap:MutableSortedMap[Int, MutableSortedSet[String]] = MutableSortedMap.empty
    val suggestionKeys = suggestionToOccurrencesMap.keys
    for (suggestion <- suggestionKeys: Iterable[String]) {
      val occurrences = suggestionToOccurrencesMap(suggestion)
      occurrenceToSuggestionsMap.get(occurrences) match {
        case Some(s) => s += suggestion
        case None =>
          val s = MutableSortedSet[String](suggestion)(DescendingAlphabetOrdering)
          occurrenceToSuggestionsMap += (occurrences -> s)
        case _ =>
      }

      suggestionToOccurrencesMap.remove(suggestion)
    }

    suggestionToOccurrencesMap --= suggestionToOccurrencesMap.keys

    occurrenceToSuggestionsMap
  }

  /**
   * Generate the flat map of weights to suggestions
   */
  def generateOrderedByWeightSuggestionsMap(occurrenceToSuggestionsMap: MutableSortedMap[Int, MutableSortedSet[String]],
                                           suggester: AnalyzingInfixSuggester):Unit = {

    var w: Int = 0

    if (verbose) {
      Log.info("==============================================")
      Log.info("Weighted suggestions:")
      Log.info("weight (#occurrences) : suggestion")
    }

    val occurrenceKeys = occurrenceToSuggestionsMap.keys
    for (occurrences <- occurrenceKeys) {
      val suggestions = occurrenceToSuggestionsMap.remove(occurrences).get

      for (s: String <- suggestions) {
        w += 1

        val contexts = new HashSet[BytesRef]
        contexts.add(new BytesRef("all".getBytes("UTF8")))

        suggester.add(new BytesRef(s.getBytes("UTF8")), contexts, w,  new BytesRef(s"$occurrences".getBytes("UTF8")))
        if (verbose) {
          Log.info(s"$w ($occurrences) : $s")
        }
      }
    }

    suggester.commit()

    suggester.close()

    if (verbose) {
      Log.info("==============================================")
    }
  }

  /**
   * Generate a CSV dump of the suggestionToOccurrencesMap map of suggestions to occurrences, ordered alphabetically
   */
  def generateCSV(prefix:String, suggestIndexDumpFile:File, suggestionToOccurrencesMap:MutableMap[String,Int]): Unit = {

    Log.info(s"Generating CSV dump of all suggestions that contain '$prefix' and their number of occurrences (in ${suggestIndexDumpFile.getName})")

    import com.opencsv.CSVWriter
    val csvWriter:CSVWriter = new CSVWriter(new FileWriter(suggestIndexDumpFile))
    val sortedSuggestions = ListMap(suggestionToOccurrencesMap.toSeq.sortWith(_._1 < _._1): _*)
    for ((suggestion, occurrences) <- sortedSuggestions) {
      val arr = Array(suggestion, occurrences.toString)
      csvWriter.writeNext(arr)
    }
    csvWriter.close()

    Log.info(s"DONE Generating CSV dump of all suggestions matching prefix $prefix and their number of occurrences (in ${suggestIndexDumpFile.getName})")
  }

}

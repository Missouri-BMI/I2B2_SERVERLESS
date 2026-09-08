package net.shrine.api.ontology

import net.shrine.log.Log
import org.junit.Test
import org.scalatest.FlatSpec

/**
 * IMPORTANT:
 *
 * These tests are tailored for a 3-word suggestion index. If the max number of suggestion words is
 * not 3, the tests need to be adjusted accordingly.
 *
 * These unit tests rely on the existence of a suggestion index on the file system.
 * The location of the index is specified in a file called shrine.conf, which should
 * be located in the resources directory associated with this test class. The suggestion index
 * must be generated manually using the Lucene index creation utility found in the tools
 * module of the project, and then committed to git (not as a zip file, which can be too large
 * to be handled by git, but as a directory)
 *
 * One major thing we are testing here is that the query terms are not filtered,
 * i.e. stop words are NOT removed. There is another possible implementation where
 * stop words are removed from the query, and so we want to positively rule out
 * that behavior in the present case
 */

class LuceneSuggesterTest extends FlatSpec {

  /**
   * only 2 non-space characters returns no results because a minimum of 3 characters is needed
   */
  @Test
  def twoLettersReturnNothing(): Unit = {

    val autoSuggestQuery = "p c"
    val expectedSuggestions = List[AutoSuggestResult]()

    Log.debug(s"suggestion query: $autoSuggestQuery")

    val suggestions: List[AutoSuggestResult] = LuceneSuggester.getSuggestions(SuggestQuery(autoSuggestQuery))

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

  /**
   * One word stub with 3 characters returns many one-, two- and three-word suggestions
   */
  @Test
  def threeLettersReturnMany(): Unit = {

    val autoSuggestQuery = "car"

    val expectedSuggestions = List(AutoSuggestResult("care",363,2257860),
      AutoSuggestResult("care other",86,2257673),
      AutoSuggestResult("care health",77,2257620),
      AutoSuggestResult("cardiac",72,2257580),
      AutoSuggestResult("carcinoma",70,2257562),
      AutoSuggestResult("carcinoma situ",67,2257540),
      AutoSuggestResult("care maternal",63,2257499),
      AutoSuggestResult("care health professional",58,2257432),
      AutoSuggestResult("care professional",58,2257431),
      AutoSuggestResult("care health other",54,2257396),
      AutoSuggestResult("care health qualified",52,2257360),
      AutoSuggestResult("care qualified",52,2257359),
      AutoSuggestResult("care services",52,2257358),
      AutoSuggestResult("care physician",51,2257332),
      AutoSuggestResult("care professional qualified",51,2257331),
      AutoSuggestResult("care patient",50,2257304),
      AutoSuggestResult("care health physician",48,2257258),
      AutoSuggestResult("care other professional",46,2257222),
      AutoSuggestResult("care medical",45,2257154),
      AutoSuggestResult("care physician professional",45,2257153))

    Log.debug(s"suggestion query: $autoSuggestQuery")

    val suggestions: List[AutoSuggestResult] = LuceneSuggester.getSuggestions(SuggestQuery(autoSuggestQuery))

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

  /**
   * One full word returns many one-, two- and three-word suggestions
   */
  @Test
  def aFullWordReturnsMany(): Unit = {

    val autoSuggestQuery = "cardiovascular"

    val expectedSuggestions = List(AutoSuggestResult("cardiovascular",31,2256668),
      AutoSuggestResult("cardiovascular system",7,2241839),
      AutoSuggestResult("cardiovascular disorders",6,2236248),
      AutoSuggestResult("cardiovascular perinatal",6,2236247),
      AutoSuggestResult("cardiovascular perinatal period",6,2236246),
      AutoSuggestResult("cardiovascular period",6,2236245),
      AutoSuggestResult("adverse cardiovascular",5,2227612),
      AutoSuggestResult("cardiovascular disorders perinatal",5,2223816),
      AutoSuggestResult("cardiovascular disorders period",5,2223815),
      AutoSuggestResult("cardiovascular procedures",5,2223814),
      AutoSuggestResult("cardiovascular group",4,2193350),
      AutoSuggestResult("cardiovascular group measures",4,2193349),
      AutoSuggestResult("cardiovascular group prevention",4,2193348),
      AutoSuggestResult("cardiovascular measures",4,2193347),
      AutoSuggestResult("cardiovascular measures prevention",4,2193346),
      AutoSuggestResult("cardiovascular originating",4,2193345),
      AutoSuggestResult("cardiovascular originating perinatal",4,2193344),
      AutoSuggestResult("cardiovascular originating period",4,2193343),
      AutoSuggestResult("cardiovascular prevention",4,2193342),
      AutoSuggestResult("cardiovascular respiratory",4,2193341))

    Log.debug(s"suggestion query: $autoSuggestQuery")

    val suggestions: List[AutoSuggestResult] = LuceneSuggester.getSuggestions(SuggestQuery(autoSuggestQuery))

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

  /**
   * Another full word, which is also a valid prefix for another word, returns many two- and three-word suggestions
   */
  @Test
  def anotherFullWordReturnsMany(): Unit = {

    val autoSuggestQuery = "procedures"

    val expectedSuggestions = List(AutoSuggestResult("procedures",423,2257862),
      AutoSuggestResult("anesthesia procedures",113,2257744),
      AutoSuggestResult("procedures surgical",113,2257741),
      AutoSuggestResult("other procedures",50,2257290),
      AutoSuggestResult("diagnostic procedures",33,2256770),
      AutoSuggestResult("procedures system",32,2256693),
      AutoSuggestResult("procedures services",30,2256458),
      AutoSuggestResult("all anesthesia procedures",22,2255795),
      AutoSuggestResult("all procedures",22,2255794),
      AutoSuggestResult("procedures upper",20,2255362),
      AutoSuggestResult("anesthesia procedures upper",18,2255097),
      AutoSuggestResult("lower procedures",17,2254727),
      AutoSuggestResult("anesthesia lower procedures",16,2254611),
      AutoSuggestResult("joint procedures",15,2254047),
      AutoSuggestResult("anesthesia including procedures",14,2253869),
      AutoSuggestResult("including procedures",14,2253621),
      AutoSuggestResult("involving procedures",14,2253597),
      AutoSuggestResult("procedures radiology",14,2253533),
      AutoSuggestResult("procedures surgical system",14,2253532),
      AutoSuggestResult("anesthesia involving procedures",13,2253428))

    Log.debug(s"suggestion query: $autoSuggestQuery")

    val suggestions: List[AutoSuggestResult] = LuceneSuggester.getSuggestions(SuggestQuery(autoSuggestQuery))

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

  /**
   * One full word followed by 3 characters returns only two-word suggestions
   */
  @Test
  def aFullWordAndThreeLettersReturnManyTwoWordSuggestions(): Unit = {

    val autoSuggestQuery = "procedures car"

    val expectedSuggestions = List(AutoSuggestResult("care procedures",8,2245402),
      AutoSuggestResult("care delivery procedures",6,2236168),
      AutoSuggestResult("cardiovascular procedures",5,2223814),
      AutoSuggestResult("care maternity procedures",5,2223609),
      AutoSuggestResult("cardiac procedures",4,2193354),
      AutoSuggestResult("carried encountering procedures",4,2190248),
      AutoSuggestResult("carried health procedures",4,2190242),
      AutoSuggestResult("carried out procedures",4,2190237),
      AutoSuggestResult("carried persons procedures",4,2190231),
      AutoSuggestResult("carried procedures",4,2190228),
      AutoSuggestResult("carried procedures services",4,2190227),
      AutoSuggestResult("carried procedures specific",4,2190226),
      AutoSuggestResult("any cards procedures",2,1984341),
      AutoSuggestResult("any cartridges procedures",2,1984257),
      AutoSuggestResult("cardiovascular other procedures",2,1931514),
      AutoSuggestResult("cards cartridges procedures",2,1931456),
      AutoSuggestResult("cards classes procedures",2,1931432),
      AutoSuggestResult("cards cups procedures",2,1931409),
      AutoSuggestResult("cards date procedures",2,1931387),
      AutoSuggestResult("cards devices procedures",2,1931366))

    Log.debug(s"suggestion query: $autoSuggestQuery")

    val suggestions: List[AutoSuggestResult] = LuceneSuggester.getSuggestions(SuggestQuery(autoSuggestQuery))

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

  /**
   * two full words, each know to return something, return a two-word suggestion when used together
   */
  @Test
  def twoFullWordsReturnATwoWordSuggestions(): Unit = {

    val autoSuggestQuery = "cardiovascular procedures"

    val expectedSuggestions = List(AutoSuggestResult("cardiovascular procedures",5,2223814),
      AutoSuggestResult("cardiovascular other procedures",2,1931514),
      AutoSuggestResult("00.5 cardiovascular procedures",1,1716559),
      AutoSuggestResult("cardiovascular procedures services",1,1129918),
      AutoSuggestResult("cardiovascular procedures surgical",1,1129917),
      AutoSuggestResult("cardiovascular procedures system",1,1129916),
      AutoSuggestResult("cardiovascular procedures therapeutic",1,1129915))

    Log.debug(s"suggestion query: $autoSuggestQuery")

    val suggestions: List[AutoSuggestResult] = LuceneSuggester.getSuggestions(SuggestQuery(autoSuggestQuery))

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

  /**
   * two full words and a three-letter stub, each know to return something, return a three-word suggestion when used together
   */
  @Test
  def twoFullWordsAndAThreeLetterStubReturnAFewTwoWordSuggestions(): Unit = {

    val autoSuggestQuery = "cardiovascular procedures oth"

    val expectedSuggestions = List[AutoSuggestResult](
      AutoSuggestResult("cardiovascular other procedures",2,1931514)
    )

    Log.debug(s"suggestion query: $autoSuggestQuery")

    val suggestions: List[AutoSuggestResult] = LuceneSuggester.getSuggestions(SuggestQuery(autoSuggestQuery))

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

  /**
   * three full words, each know to return something, return a three-word suggestion when used together
   */
  @Test
  def threeFullWordsReturnAFewTwoWordSuggestions(): Unit = {

    val autoSuggestQuery = "cardiovascular procedures other"

    val expectedSuggestions = List[AutoSuggestResult](
      AutoSuggestResult("cardiovascular other procedures",2,1931514)
    )

    Log.debug(s"suggestion query: $autoSuggestQuery")

    val suggestions: List[AutoSuggestResult] = LuceneSuggester.getSuggestions(SuggestQuery(autoSuggestQuery))

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

  /**
   * check that two word stubs where one is a stop word, does not return anything.
   * i.e. the stop word is not filtered out.
   */
  @Test
  def threeLettersPlusStopWordBeforeReturnNothing(): Unit = {

    val autoSuggestQuery = "with pro"

    val expectedSuggestions = List[AutoSuggestResult]()

    Log.debug(s"suggestion query: $autoSuggestQuery")

    val suggestions: List[AutoSuggestResult] = LuceneSuggester.getSuggestions(SuggestQuery(autoSuggestQuery))

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

  /**
   * check that two word stubs where one is a stop word, does not return anything
   * i.e. the stop word is not filtered out.
   */
  @Test
  def threeLettersPlusStopWordAfterReturnNothing(): Unit = {

    val autoSuggestQuery = "pro with"

    val expectedSuggestions = List[AutoSuggestResult]()

    Log.debug(s"suggestion query: $autoSuggestQuery")

    val suggestions: List[AutoSuggestResult] = LuceneSuggester.getSuggestions(SuggestQuery(autoSuggestQuery))

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

  /**
   * check that two word stubs does not return anything
   */
  @Test
  def threeLettersPlusNonStopWordReturnNothing(): Unit = {

    val autoSuggestQuery = "pro car"

    val expectedSuggestions = List[AutoSuggestResult]()

    Log.debug(s"suggestion query: $autoSuggestQuery")

    val suggestions: List[AutoSuggestResult] = LuceneSuggester.getSuggestions(SuggestQuery(autoSuggestQuery))

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

  /**
   * check that stop words are not removed from input
   *
   */
  @Test
  def twoFullWordsWhereOneIsAStopWordReturnSomething(): Unit = {

    val autoSuggestQuery = "procedures with"

    val expectedSuggestions = List(AutoSuggestResult("abnormal procedures without",10,2251002),
      AutoSuggestResult("cause procedures without",10,2250591),
      AutoSuggestResult("complication procedures without",10,2250449),
      AutoSuggestResult("later procedures without",10,2249878),
      AutoSuggestResult("mention procedures without",10,2249800),
      AutoSuggestResult("misadventure procedures without",10,2249783),
      AutoSuggestResult("patient procedures without",10,2249689),
      AutoSuggestResult("procedures reaction without",10,2249632),
      AutoSuggestResult("procedures time without",10,2249629),
      AutoSuggestResult("procedures without",10,2249628),
      AutoSuggestResult("other procedures without",9,2246640),
      AutoSuggestResult("procedure procedures without",8,2243037),
      AutoSuggestResult("procedures surgical without",7,2239426),
      AutoSuggestResult("medical procedures without",5,2215422),
      AutoSuggestResult("operation procedures without",4,2151187),
      AutoSuggestResult("procedures without y83",3,2061090),
      AutoSuggestResult("procedures without y84",3,2061089),
      AutoSuggestResult("e878 procedures without",2,1849970),
      AutoSuggestResult("2 procedures within",1,1680224),
      AutoSuggestResult("48 procedures within",1,1653895))

    Log.debug(s"suggestion query: $autoSuggestQuery")

    val suggestions: List[AutoSuggestResult] = LuceneSuggester.getSuggestions(SuggestQuery(autoSuggestQuery))

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

  /**
   * check that three words plus some stub, each know to return something, return nothing.
   */
  @Test
  def fourWordsReturnNothing(): Unit = {

    val autoSuggestQuery = "patient procedure diagnostic car"

    val expectedSuggestions = List[AutoSuggestResult]()

    Log.debug(s"suggestion query: $autoSuggestQuery")

    val suggestions: List[AutoSuggestResult] = LuceneSuggester.getSuggestions(SuggestQuery(autoSuggestQuery))

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

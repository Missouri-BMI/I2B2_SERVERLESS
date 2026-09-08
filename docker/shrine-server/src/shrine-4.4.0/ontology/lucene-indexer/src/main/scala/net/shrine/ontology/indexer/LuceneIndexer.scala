package net.shrine.ontology.indexer

import java.io.{File, FileInputStream, FileOutputStream, IOException, InputStream, UnsupportedEncodingException}
import java.nio.file.attribute.FileTime
import java.util.{HashSet, Set}
import java.util.zip.{ZipEntry, ZipOutputStream}

import net.shrine.log.Log
import net.shrine.ontology.indexer.parser.{CloseableCsvToBean, OntologyFileParser}
import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.index.{IndexWriter, IndexWriterConfig}
import org.apache.lucene.search.suggest.InputIterator
import org.apache.lucene.store.FSDirectory
import org.apache.lucene.util.BytesRef
import org.apache.commons.text.StringEscapeUtils

import scala.util.control.NonFatal
import cats.effect.Temporal


object LuceneIndexer {

  var parentChildMap : Map[String, Long] = Map.empty
  var pathToTermMap : Map[String, List[String]] = Map.empty
  var pathToOntPathMap : Map[String, List[String]] = Map.empty
  var pathPrefixMap: Map[String, String]  = Map.empty

  private def createSearchIndexWriter(searchIndexDirectory: FSDirectory): IndexWriter = {

    val searchIndexWriterConfig: IndexWriterConfig = new IndexWriterConfig(new StandardAnalyzer).setOpenMode(IndexWriterConfig.OpenMode.CREATE)
    searchIndexWriterConfig.setRAMBufferSizeMB(1024)
    val searchIndexWriter = new IndexWriter(searchIndexDirectory, searchIndexWriterConfig)

    searchIndexWriter
  }

  def createIndexes(filename: String,
                    fileDelimiter: Char,
                    codeCategoryFilename: String,
                    categoryFileDelimiter: Char,
                    searchIndexDirName: String,
                    searchIndexZipFileName: String,
                    suggestIndexDirNameOption: Option[String] = None,
                    suggestIndexZipFileNameOption: Option[String] = None,
                    maxWordsInSuggestion: Int = 3,
                    suggestIndexDumpPrefixesOption: Option[Seq[String]] = Some(Seq[String]()),
                    createAutoSuggest: Boolean = false,
                    includeTooltips: Boolean = false,
                    verbose: Boolean): Unit  = {


    // Create the corresponding Lucene FSDirectory objects

    import org.apache.lucene.store.FSDirectory

    // do the indexing

    if(createAutoSuggest) {
      // Figure out all the CSV file names we will need

      val suggestIndexDirName = suggestIndexDirNameOption.get
      val suggestIndexZipFileName = suggestIndexZipFileNameOption.get
      val suggestIndexDumpPrefixes = suggestIndexDumpPrefixesOption.get

      val suggestIndexDumpNames: Map[String, String] = suggestIndexDumpPrefixes.map(p =>
        p -> s"$suggestIndexDirName.${p.trim}.csv"
      ).toMap.filter(n => !n._1.isEmpty)
      val suggestIndexDir: File = new File(suggestIndexDirName)
      Log.info(s"Creating auto-suggest index in directory $suggestIndexDir")
      val suggestIndexDumpFilesMap: Map[String, File] = suggestIndexDumpNames.map( n => {
        Log.info(s"Creating CSV dump file for prefix '${n._1}': ${n._2}")
        n._1 -> new File(n._2)
      })

      suggestIndexDir.mkdir()
      val suggestIndexDirectory = FSDirectory.open(suggestIndexDir.toPath)

      val suggestionIndexer = new SuggestionIndexer(
        maxWordsInSuggestion,
        suggestIndexDirectory,
        suggestIndexDirName,
        suggestIndexZipFileName,
        verbose = verbose,
        suggestIndexDumpFilesMap
      )

      val suggestIndexInfoOption = Some(SuggestionIndexInfo(
        suggestionIndexer,
        suggestIndexDirName,
        suggestIndexZipFileName
      ))

      indexFromFile(
        None,
        suggestIndexInfoOption,
        filename,
        fileDelimiter)

      // Close the Lucene FSDirectory object
      suggestIndexDirectory.close()

      Log.debug("Finished creating the ontology auto-suggest indices")
    }
    else
    {
      val searchIndexDir: File = new File(searchIndexDirName)
      Log.info(s"Creating search index in directory $searchIndexDir")

      searchIndexDir.mkdir()
      val searchIndexDirectory = FSDirectory.open(searchIndexDir.toPath)
      val searchIndexWriter = createSearchIndexWriter(searchIndexDirectory)

      val searchIndexer = new SearchIndexer(
        searchIndexWriter,
        codeCategoryFilename,
        filename,
        fileDelimiter,
        categoryFileDelimiter,
        includeTooltips
      )

      val searchIndexInfoOption = Some(SearchIndexInfo(
        searchIndexer,
        searchIndexDirName,
        searchIndexZipFileName
      ))

      indexFromFile(
        searchIndexInfoOption,
        suggestionIndexInfoOption = None,
        filename,
        fileDelimiter)
      // Close the Lucene FSDirectory object
      searchIndexDirectory.close()

      Log.debug("Finished creating the ontology search indices")
    }
  }

  case class SuggestionIndexInfo(suggestionIndexer:SuggestionIndexer,
                                 suggestOutputDirName: String,
                                 suggestOutputZipFileName: String
                                )

  case class SearchIndexInfo(searchIndexer:SearchIndexer,
                             searchOutputDirName: String,
                             searchOutputZipFileName: String
                            )

  /*
   * The input file should have \t as the delimiter.
   * Here is an example line in the text file:
   * 9	\\ACT\\UMLS_C0031437\\SNOMED_3947185011\\UMLS_C0242656\\UMLS_C0013227\\EPC_C3536840\\41493\\372754\\152695\\54569581200\\	NDC:54569581200	N	LI 	\N	NDC:54569581200	\N	concept_cd	concept_dimension	concept_path	T	LIKE	\\ACT\\UMLS_C0031437\\SNOMED_3947185011\\UMLS_C0242656\\UMLS_C0013227\\EPC_C3536840\\41493\\372754\\152695\\54569581200\\	\N	ACT Phenotype\\COVID-19 Related Terms\\Course Of Illness\\Medications\\Non-Steroidal Anti-Inflammatory Drugs (NSAIDs)\\Meloxicam\\Meloxicam Oral Tablet\\Meloxicam 15 Mg Oral Tablet\\NDC:54569581200	@	2020-06-24 17:26:51.403748	2020-06-24 17:26:51.403748	2020-06-24 17:26:51.403748	EPC	\N	\N	\N	\N   */
  def indexFromFile(
                     searchIndexInfoOption: Option[SearchIndexInfo],
                     suggestionIndexInfoOption: Option[SuggestionIndexInfo],
                     directory: String,
                     fileDelimiter: Char): Unit = {

    val startTime = System.currentTimeMillis()

    var numDataRead = 0

    Log.debug("starting index of ontology")

    var ontIt: Seq[CloseableCsvToBean] = OntologyFileParser.ontIterator(directory, fileDelimiter)
    ontIt.foreach(closeableCsvToBean => {
      Log.debug(s"Parsing ontology file: ${closeableCsvToBean.fileName}")
      try{
        closeableCsvToBean.csvToBean.iterator().forEachRemaining(ontologyRow => {
            numDataRead = numDataRead + 1

            if (suggestionIndexInfoOption.nonEmpty){
              // generate suggestions from this concept and add them to the suggestion index
              suggestionIndexInfoOption.get.suggestionIndexer.generateSuggestionsFromConceptName(ontologyRow.name)
            }else{
              // add this concept to the search index
              searchIndexInfoOption.get.searchIndexer.createAndAddDocument(ontologyRow)

              if (numDataRead % 200000 == 0) {
                searchIndexInfoOption.get.searchIndexer.commit()
              }
            }

            if (numDataRead % 200000 == 0) {
              Log.debug(s"Read $numDataRead docs")
              val endTime = System.currentTimeMillis()
              val elapsedSeconds = (endTime - startTime) / 1000
              Log.debug(s"Total elapsed time so far: $elapsedSeconds seconds")
            }
        })
      } finally {
        closeableCsvToBean.close()
      }
    })

    ontIt = Seq.empty

    if(searchIndexInfoOption.nonEmpty)
    {
      searchIndexInfoOption.get.searchIndexer.commit()
      compressIndex(searchIndexInfoOption.get.searchOutputDirName, searchIndexInfoOption.get.searchOutputZipFileName)

      Log.info(s"done indexing ontology, read: $numDataRead, " +
        s"assigned concept categories: ${searchIndexInfoOption.get.searchIndexer.mappedConceptCategoriesCount} times," +
        s" assigned code categories: ${searchIndexInfoOption.get.searchIndexer.mappedCodeCategoriesCount} times, " +
        s"and assigned code sets: ${searchIndexInfoOption.get.searchIndexer.mappedCodeSetCount} times")
    }
    else{

      Log.info("Building the suggestion index")
      suggestionIndexInfoOption.get.suggestionIndexer.buildSuggestionIndex()
      compressIndex(suggestionIndexInfoOption.get.suggestOutputDirName, suggestionIndexInfoOption.get.suggestOutputZipFileName)
    }

    val endTime = System.currentTimeMillis()
    val elapsedSeconds = (endTime - startTime) / 1000
    Log.debug(s"Total elapsed time: $elapsedSeconds seconds" )
  }

  def cleanupForIndexing(inStr: String): String = {
    // Add two leading 0's to unicodes with only 2 hexadecimal characters, such as &#xab; --> &#x00ab;
    // Some of the ontology has | characters, encoded as &#x7C;, and which need to be padded with the two 0s
    // and decoded before being displayed in the "medical concepts" list
    StringEscapeUtils
      .unescapeXml(
        inStr.replaceAll("&#x([0-9a-fA-F][0-9a-fA-F]);*", "&#x00$1;")
      )
  }

  def compressIndex(dirName: String, zipFileName: String): Unit = {
    ZipUtil.createZip(zipFileName, dirName)
    Log.debug(s"Created zip file $zipFileName from directory $dirName")
  }

  def main(args: Array[String]): Unit = {

    val startTime = System.currentTimeMillis()

    object ParsedCLIArgs {
      val defaultOntologyDirectory: String = ""
      val defaultCodeCategoryFilename: String = ""
      val defaultSearchIndexDir: String = "lucene_index"
      val defaultSearchIndexZipFile:String = "lucene_index.zip"
      val defaultSuggestIndexDir:String = "suggest_index"
      val defaultSuggestIndexZipFile:String = "suggest_index.zip"
      val defaultMaxWordsInSuggestion:Int = 3
      val defaultSuggestIndexDumpPrefixes:Seq[String] = Seq[String]()
      val defaultVerbose:Boolean = false
      val defaultCreateAutoSuggest:Boolean = false
      val defaultIncludeTooltips:Boolean = false
      val defaultFileDelimiter:String = "|"
      val defaultCategoryFileDelimiter:String = ","
    }

    case class ParsedCLIArgs(
                       ontologyDirectory: String = ParsedCLIArgs.defaultOntologyDirectory,
                       codeCategoryFilename: String = ParsedCLIArgs.defaultCodeCategoryFilename,
                       searchIndexDir: String = ParsedCLIArgs.defaultSearchIndexDir,
                       searchIndexZipFile:String = ParsedCLIArgs.defaultSearchIndexZipFile,
                       suggestIndexDir:String = ParsedCLIArgs.defaultSuggestIndexDir,
                       suggestIndexZipFile:String = ParsedCLIArgs.defaultSuggestIndexZipFile,
                       maxWordsInSuggestion:Int = ParsedCLIArgs.defaultMaxWordsInSuggestion,
                       suggestIndexDumpPrefixes:Seq[String] = ParsedCLIArgs.defaultSuggestIndexDumpPrefixes,
                       verbose:Boolean = ParsedCLIArgs.defaultVerbose,
                       includeTooltips:Boolean = ParsedCLIArgs.defaultIncludeTooltips,
                       createAutoSuggest:Boolean = ParsedCLIArgs.defaultCreateAutoSuggest,
                       fileDelimiter:String = ParsedCLIArgs.defaultFileDelimiter,
                       categoryFileDelimiter:String = ParsedCLIArgs.defaultCategoryFileDelimiter
                      )

    import scopt.OptionParser
    val parser = new OptionParser[ParsedCLIArgs]("java -jar ... net.shrine.ontology.indexer.LuceneIndexer") {
      head("Lucene Indexer", "1.0")

      opt[String]('o', "ontology")
        .action((x, c) => c.copy(ontologyDirectory = x))
        .validate( x =>{
          val file: File = new File(x)
          if (file.exists) success
          else failure(s"Directory ${file.getAbsolutePath} does not exist (default: none)")
        }).required()
        .text(s"name of the directory containing the ontology files")

      opt[String]('p', "delimiter")
        .action((x, c) => c.copy(fileDelimiter = StringContext.processEscapes(x)))
        .validate(x =>
          if (StringContext.processEscapes(x).length == 1) success
          else failure("Delimiter must be a single character (tab can be entered as '\\t'"))
        .text(s"The delimiter used in the ontology files. Default is '|'")

      opt[String]('c', "categories")
        .action((x, c) => c.copy(codeCategoryFilename = x))
        .validate( x => {
          val file: File = new File(x)
          if (file.exists) success
          else
            failure(s"File ${file.getAbsolutePath} does not exist")
        })
        .text(s"name of the category definitions file (default: none)")
        .required()

      opt[String]('r', "categorydelimiter")
        .action((x, c) => c.copy(categoryFileDelimiter = StringContext.processEscapes(x)))
        .validate(x =>
          if (StringContext.processEscapes(x).length == 1) success
          else failure("Delimiter must be a single character (tab can be entered as '\\t'"))
        .text(s"The delimiter used in the category definition file. Default is ','")

      opt[String]('s', "search-dir")
        .action((x, c) => c.copy(searchIndexDir = x))
        .text(s"name of the search index top directory (default: ${ParsedCLIArgs.defaultSearchIndexDir})")

      opt[String]('z', "search-zip")
        .action((x, c) => c.copy(searchIndexZipFile = x))
        .text(s"name of the search index zip file (default: ${ParsedCLIArgs.defaultSearchIndexZipFile})")

      opt[String]('t', "suggest-dir")
        .action((x, c) => c.copy(suggestIndexDir = x))
        .text(s"name of the suggestion index top-level directory (default: ${ParsedCLIArgs.defaultSuggestIndexDir})")

      opt[String]('y', "suggest-zip")
        .action((x, c) => c.copy(suggestIndexZipFile = x))
        .text(s"name of the suggestion index zip file (default: ${ParsedCLIArgs.defaultSuggestIndexZipFile})")

      opt[Int]('m', "max-words")
        .action((x, c) => c.copy(maxWordsInSuggestion = x))
        .validate(x =>
          if (x >= 1 && x <= 3) success
          else failure("value <max-words> must be between 1 and 3 (inclusive)"))
        .text(s"maximum number of words in a suggestion (1, 2 or 3) (default: ${ParsedCLIArgs.defaultMaxWordsInSuggestion})")

      opt[Seq[String]]('d', "csv-dump")
        .valueName("<p1>,<p2>...")
        .action((x, c) => c.copy(suggestIndexDumpPrefixes = x))
        .text(s"list of prefixes for which to generate CSV files " +
          "listing all the suggestions they appear in, separated " +
          "by single spaces and enclosed in double quotes (default: none)")

      opt[Unit]('v', "verbose")
        .action((_, c) => c.copy(verbose = true))
        .text(s"verbose is an option to generate more output while creating the indexes(NOTE: This will significantly increase the runtime) (default: ${ParsedCLIArgs.defaultVerbose})")

      opt[Unit]('a', "createAutoSuggest")
        .action((_, c) => c.copy(createAutoSuggest = true))
        .text(s"Create auto suggestion index")

      opt[Unit]('l', "tooltips")
        .action((_, c) => c.copy(includeTooltips = true))
        .text(s"tooltip is an option to include the tooltip metadata in the indexes) (default: ${ParsedCLIArgs.defaultIncludeTooltips})")
    }

    startAppPulse()

    // OParser.parse returns Option[Config]
    parser.parse(args, ParsedCLIArgs()) match {
      case Some(config) =>
      // do something
        val fileDelimiter = config.fileDelimiter.charAt(0)
        val categoryFileDelimiter = config.categoryFileDelimiter.charAt(0)

        Log.debug("Command line arguments:")
        Log.debug(s"  ontology: ${config.ontologyDirectory}")
        Log.debug(s"  file delimiter: $fileDelimiter")
        Log.debug(s"  categories: ${config.codeCategoryFilename}")
        Log.debug(s"  category file delimiter: ${config.categoryFileDelimiter}")
        Log.debug(s"  search-dir: ${config.searchIndexDir}")
        Log.debug(s"  search-zip: ${config.searchIndexZipFile}")
        Log.debug(s"  suggest-dir: ${config.suggestIndexDir}")
        Log.debug(s"  suggest-zip: ${config.suggestIndexZipFile}")
        Log.debug(s"  createAutoSuggest: ${config.createAutoSuggest}")
        Log.debug(s"  max-words: ${config.maxWordsInSuggestion}")
        Log.debug(s"  csv-dump: ${config.suggestIndexDumpPrefixes}")
        Log.debug(s"  tooltips: ${config.includeTooltips}")
        Log.debug(s"  verbose: ${config.verbose}")

        createIndexes(
          config.ontologyDirectory,
          fileDelimiter,
          config.codeCategoryFilename,
          categoryFileDelimiter,
          config.searchIndexDir,
          config.searchIndexZipFile,
          Some(config.suggestIndexDir),
          Some(config.suggestIndexZipFile),
          config.maxWordsInSuggestion,
          Some(config.suggestIndexDumpPrefixes),
          config.createAutoSuggest,
          config.includeTooltips,
          config.verbose
        )

      case None =>
      // arguments are bad, error message will have been displayed
        System.exit(1)
    }

    val endTime = System.currentTimeMillis()

    val elapsedSeconds = (endTime - startTime) / 1000

    Log.debug(s"TOTAL elapsed time: $elapsedSeconds seconds" )

  }

  private def startAppPulse(): Unit = {
    import fs2.Stream
    import cats.effect.IO
    import scala.concurrent.duration.DurationInt
    import cats.effect.unsafe.implicits.global

    val pulseStream: Stream[IO, Unit] = Stream.awakeEvery[IO](120.second).map(_ => {
      Log.info("........Still Running........")
    })

    pulseStream.compile.toList.unsafeRunAndForget()
  }
}

case class WrongNumberOfArguments(message:String) extends Exception(message)


case class CodeAndConceptCategory(codeCategory: String, conceptCategory: String, codeSet: Option[String] = None)

object ZipUtil {

  def createZip(zipFileName: String, dirName: String): Unit = {
    val fileOutputStream: FileOutputStream = new FileOutputStream(zipFileName)

    val zipStream = new ZipOutputStream(fileOutputStream)

    try {
      val zf = new File(dirName)

      val iter = zf.listFiles().toList
      iter.foreach( file => {
        addToZipFile(s"${zf.getName}/${file.getName}", file.lastModified() , new FileInputStream(file), zipStream)
      })
    } catch {
      case NonFatal(e) => Log.error(s"Error while zipping $zipFileName", e)
        if (zipStream != null) zipStream.close()
    }
    finally {
      zipStream.close()
    }
  }

  private def addToZipFile(inputFileName: String, lastModified: Long, inputStream: InputStream, zipStream: ZipOutputStream): Unit = {
    try {
      // create a new ZipEntry, which is basically another file
      // within the archive. We omit the path from the filename
      val entry = new ZipEntry(inputFileName)
      entry.setCreationTime(FileTime.fromMillis(lastModified))
      entry.setComment("Created by SHRINE")
      zipStream.putNextEntry(entry)
      Log.info("Generated new entry for: " + inputFileName)

      // Now we copy the existing file into the zip archive. To do
      // this we write into the zip stream, the call to putNextEntry
      // above prepared the stream, we now write the bytes for this
      // entry.
      val readBuffer = new Array[Byte](2048)
      var amountRead = 0
      var written = 0
      amountRead = inputStream.read(readBuffer)
      while(amountRead > 0){
        zipStream.write(readBuffer, 0, amountRead)
        written += amountRead
        amountRead = inputStream.read(readBuffer)
      }
      Log.info("Stored " + written + " bytes to " + inputFileName)
    } catch {
      case e: IOException =>
        throw new Exception("Unable to process " + inputFileName, e)
    } finally if (inputStream != null) inputStream.close()
  }
}

case class ErrorParsingLabXml(xml: String, path: String) extends Exception(s"Unable to parse lab xml with path $path:  $xml")

case class SuggestionItem(suggestionText: String, occurrences: Long, weight: Long, contexts: List[String])

class SuggestionItemIterator(val entityIterator: Iterator[SuggestionItem]) extends InputIterator {

  private var currentItem: SuggestionItem = _

  override def hasContexts = true

  override def hasPayloads = true

  override def next: BytesRef = if (entityIterator.hasNext) {
    currentItem = entityIterator.next()
    try
      new BytesRef(currentItem.suggestionText.getBytes("UTF8"))
    catch {
      case e: UnsupportedEncodingException =>
        throw new Error("Couldn't convert to UTF-8",e)
    }
  }
  else { // returning null is fine for lucene...
    null
  }

  override def payload: BytesRef = { // returns null if no payload from Item
    try
      new BytesRef(s"${currentItem.occurrences}".getBytes("UTF8"))
    catch {
      case e: UnsupportedEncodingException =>
        throw new Error("Could not convert to UTF-8",e)
    }
  }

  override def contexts: Set[BytesRef] = { // returns null if no context from Item
    try {
      val contexts = new HashSet[BytesRef]
      for (context <- currentItem.contexts) {
        contexts.add(new BytesRef(context.getBytes("UTF8")))
      }
      contexts
    } catch {
      case e: UnsupportedEncodingException =>
        throw new Error("Couldn't convert to UTF-8",e)
    }
  }

  override def weight: Long = currentItem.weight
}

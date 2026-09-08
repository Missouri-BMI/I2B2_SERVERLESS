package net.shrine.api.ontology

import java.io.File
import cats.effect.IO
import net.shrine.config.ConfigSource
import net.shrine.http4s.catsio.ExecutionContexts
import net.shrine.log.Loggable
import net.shrine.ontology.LabDetail
import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.index.{DirectoryReader, IndexableField, Term}
import org.apache.lucene.queryparser.classic.{QueryParser, QueryParserBase}
import org.apache.lucene.search.highlight.Highlighter
import org.apache.lucene.search.{BooleanClause, BooleanQuery, FieldDoc, IndexSearcher, Query, ScoreDoc, Sort, SortField, TermQuery, TopFieldDocs}
import org.apache.lucene.store.FSDirectory
import org.apache.lucene.util.BytesRef
import org.http4s.EntityDecoder

import scala.collection.immutable.SortedMap
import scala.collection.{MapView, mutable}


object LuceneSearcher extends Loggable {

  private lazy val indexFileLocation: String = ConfigSource.config.getString("shrine.lucene.directory")
  private lazy val indexDir: File = new File(indexFileLocation)
  private lazy val directory: FSDirectory = FSDirectory.open(indexDir.toPath)

  lazy val searcher = new IndexSearcher(DirectoryReader.open(directory))

  private def createCodeCategoryTerm(displayName : String, children: List[OntologyTerm]): CodeCategoryTerm = {
    CodeCategoryTerm(displayName = displayName, children=children)
  }

  def getRootTerms : IO[List[CodeCategoryTerm]] = {
    val foundDocsIO: IO[TopFieldDocs] = getRootFieldDocs
    foundDocsIO.map(foundDocs => {
      val docsToCodeCategories: Map[String, Array[ScoreDoc]] = foundDocs.scoreDocs.groupBy(sd => searcher.doc(sd.doc).get("codeCategory"))

      val sortedDocsToCategories: SortedMap[String, Array[ScoreDoc]] = SortedMap(docsToCodeCategories.toSeq.sortBy(_._1): _*)
      val codeCategoryTerms: Iterable[CodeCategoryTerm] = sortedDocsToCategories.map { case (codeCategory: String, scoreDocs: Array[ScoreDoc]) =>
        val ontologyTermsList: List[OntologyTerm] = for {
          sd <- scoreDocs.toList
        } yield {
          extractOntologyTermFromScoreDoc(sd)
        }

        createCodeCategoryTerm(codeCategory, ontologyTermsList)
      }

      codeCategoryTerms.toList
    })
  }

  /**
   * Retrieves a single term by its ontology path and display name ignoring any children elements.
   */
  def getSingleTermByPathAndDisplayName(path: String, displayName: String): IO[Option[OntologyTerm]] = {
    val pathAndNameQuery = new BooleanQuery.Builder()
      .add(new TermQuery(new Term("path", path)), BooleanClause.Occur.MUST)
      .build()

    val scoreDocsIO : IO[List[ScoreDoc]] = getRawSearchResults(query = pathAndNameQuery)
    scoreDocsIO.map(sds => {
      if (sds.size > 1) {
        warn(s"displayName and path should be unique to an ontology term. " +
          s"For path ($path) and displayName ($displayName) found ${sds.size} results")
        val displayNameOrisRootMatch = sds.find(scoreDoc =>
          searcher.doc(scoreDoc.doc).get("displayName") == displayName || searcher.doc(scoreDoc.doc).get("isRoot").toBoolean)
        //Multiple hits found for a path so take either the first term or the one that has the same display name or is a root term
        displayNameOrisRootMatch.fold(sds.headOption.map(extractOntologyTermFromScoreDoc(_)))(_ => displayNameOrisRootMatch.map(extractOntologyTermFromScoreDoc(_)))
      }else {
        sds.headOption.map(extractOntologyTermFromScoreDoc(_))
      }
    })
  }

  def getFilterOptions: IO[List[FilterOption]] = {
    val defaultOption: FilterOption = FilterOption(NO_FILTER(), "All Concepts", "All Concepts")

    val foundDocsIO: IO[TopFieldDocs] = getRootFieldDocs
    foundDocsIO.map(foundDocs => {
      import scala.collection.mutable.ListBuffer

      val allCodeCategories = foundDocs.scoreDocs.map(sd => searcher.doc(sd.doc).get("codeCategory")).distinct

      // Map code categories to FilterOptions with their displayable name
      val codeCategoryOptions = allCodeCategories.map(codeCategory => {
        val displayableName = codeCategory match {
          case "Diagnoses" => "Diagnoses (All)"
          case "Laboratory Tests" => "Laboratory Tests (LOINC)"
          case "Medications" => "Medications (RxNORM)"
          case "Procedures" => "Procedures (All)"
          case rest => rest
        }
        FilterOption(CODE_CATEGORY(), codeCategory, displayableName)
      }).toList

      // Map from code category to a list of corresponding code set FilterOptions
      val codeSetMap: MapView[String, List[FilterOption]] = foundDocs.scoreDocs.map(sd =>
        (searcher.doc(sd.doc).get("codeCategory"), searcher.doc(sd.doc).get("codeSet"))
      ).filter(c => c._2 != null && c._2.nonEmpty)
        .groupBy(_._1)
        .view.mapValues(arrayOfCodeCategoriesAndCodeSet => {
          arrayOfCodeCategoriesAndCodeSet.map(codeCategoryAndCodeSet => {
            FilterOption(CODE_SET(), codeCategoryAndCodeSet._2, codeCategoryAndCodeSet._2)
          }).toList
        })

      // Combine code set and category lists so that code set options are directly after their code category
      val codeSetAndCategoryOptions: List[FilterOption] = codeCategoryOptions.foldLeft(ListBuffer[FilterOption]())((acc, cur) => {
        acc += cur
        if (codeSetMap.contains(cur.filterValue)) {
          acc ++= codeSetMap(cur.filterValue)
        }
        acc
      }).toList

      val allOptions: List[FilterOption] = codeSetAndCategoryOptions.+:(defaultOption)
      allOptions
    })
  }

  def getCodeCategoriesMap: IO[Map[OntologyPath, CodeCategory]] = {
    val foundDocsIO: IO[TopFieldDocs] = getRootFieldDocs
    foundDocsIO.map(foundDocs => {
      val pathToCodeCategory = foundDocs.scoreDocs.map(sd => {

        val codeCategory = searcher.doc(sd.doc).get("codeCategory")
        val path = searcher.doc(sd.doc).get("path")

        (OntologyPath(path=path), CodeCategory(name=codeCategory))
      }).distinct

      pathToCodeCategory.toMap
    })
  }

  def getRawSearchResults(query: Query): IO[List[ScoreDoc]] = {
    IO.blocking {

      val maxHits: Int = Int.MaxValue

      val foundDocs: TopFieldDocs = searcher.search(query, maxHits, CaseInsensitiveSort("displayName"))

      foundDocs.scoreDocs.toList
    }
  }

  def getChildren(ontologyPath: OntologyPath) : IO[List[OntologyTerm]] = {

    val parentPath = ontologyPath.path.dropRight(1)

    val childrenQuery = new TermQuery(new Term("parentPath", parentPath))

    val scoreDocsIO = getRawSearchResults(childrenQuery)

    scoreDocsIO.map(scoreDocs => {
      val searchHits: List[OntologyTerm] = for {
        sd <- scoreDocs
      } yield {
        extractOntologyTermFromScoreDoc(sd)
      }
      searchHits
    })
  }

  def getConceptInfo(ontologyPath: OntologyPath) : IO[Option[ConceptInfo]] = {

    val pathQuery = new TermQuery(new Term("path", ontologyPath.path))
    val childrenQuery = new TermQuery(new Term("parentPath", ontologyPath.path.dropRight(1)))

    val booleanQuery: BooleanQuery = new BooleanQuery.Builder()
    .add(pathQuery, BooleanClause.Occur.SHOULD)
    .add(childrenQuery, BooleanClause.Occur.SHOULD).build()

    val scoreDocsIO : IO[List[ScoreDoc]] =  getRawSearchResults(query = booleanQuery)

    scoreDocsIO.map(scoreDocs => {

      val childConcepts: List[ConceptInfo] = scoreDocs.filter(sd => searcher.doc(sd.doc).get("path") != ontologyPath.path)
        .map(ConceptInfo(_))

      val scoreDocOption: Option[ScoreDoc] = scoreDocs.find(p => searcher.doc(p.doc).get("path") == ontologyPath.path)

      val conceptInfoTerm: Option[ConceptInfo] = scoreDocOption.map(scoreDoc => {
        val nPath: String = LuceneSearcher.searcher.doc(scoreDoc.doc).get("nPath")
        // Append the  vispath attributes for the top level code category term
        //it is always a Container and Active (CA)
        val visAttrPath: String = s"\\CA${LuceneSearcher.searcher.doc(scoreDoc.doc).get("visPath")}"

        val pathElements: Array[String] = nPath.split("\\\\").filterNot(_ == "")
        val pathElementsReverse: Array[String] = pathElements.reverse
        val visPathElementsReverse: Array[String] = visAttrPath.split("\\\\").filterNot(_ == "").reverse

        val selectedConceptInfo = ConceptInfo(
          nPath,
          pathElementsReverse.head,
          VisualAttributes(visPathElementsReverse.head),
          childrenConceptInfo = Option(childConcepts),
          highlight = true
        )

        val (conceptInfo: ConceptInfo, _: Array[String], _) =
          pathElementsReverse.drop(1).foldLeft((selectedConceptInfo, visPathElementsReverse.drop(1), pathElements.dropRight(1)))((items, pathElem) => {

            val visPaths: Array[String] = items._2
            val nPaths: Array[String] = items._3
            val path: String = s"\\${nPaths.mkString("\\")}\\"

            val childConcepts: List[ConceptInfo] = List(items._1)
            val conceptInfo: ConceptInfo = ConceptInfo(
            path,
            pathElem,
            VisualAttributes(visPaths.head),
            childrenConceptInfo = Option(childConcepts),
            highlight = false
          )

          (conceptInfo, visPaths.drop(1), nPaths.dropRight(1))
        })

        conceptInfo
      })

      conceptInfoTerm
    })
  }

  def extractOntologyTermFromScoreDoc(sd: ScoreDoc, childrenScoreDocs: Option[List[ScoreDoc]] = None): OntologyTerm = {
    val path = searcher.doc(sd.doc).get("path")
    val displayName = searcher.doc(sd.doc).get("displayName")
    val labFieldOption: Option[IndexableField] = Option(searcher.doc(sd.doc).getField("labDetail"))

    val isLab: Boolean = labFieldOption.fold(false)(_ => true)
    val visualAttributes = searcher.doc(sd.doc).get("visualAttributes")

    val conceptCategory = searcher.doc(sd.doc).get("conceptCategory")
    val metadata = Option(searcher.doc(sd.doc).get("tooltip"))

    extractOntologyTerm(
      path=path,
      displayName=displayName,
      visualAttr = visualAttributes,
      conceptCategory = conceptCategory,
      isLab = isLab,
      metadata = metadata,
      childrenScoreDocs = childrenScoreDocs
    )
  }

  def extractOntologyTerm(path: String,
                          displayName: String,
                          visualAttr: String,
                          conceptCategory: String,
                          isLab: Boolean,
                          metadata: Option[String],
                          childrenScoreDocs: Option[List[ScoreDoc]] = None,
                          highlightedName: Option[String] = None): OntologyTerm = {

    val visualAttributes = VisualAttributes(visualAttr)

    val childrenOntologyTerms: List[OntologyTerm] =
      childrenScoreDocs.fold(List[OntologyTerm]())(csd => csd.map(doc => extractOntologyTermFromScoreDoc(doc)))

    val ontTerm = OntologyTerm(
      displayName = displayName,
      highlightedName = highlightedName,
      path = path,
      conceptCategory = conceptCategory,
      conceptType = visualAttributes.conceptType,
      isActive = visualAttributes.isActive,
      metadata = metadata,
      children = Option(childrenOntologyTerms),
      isLab = isLab
    )
    ontTerm
  }

  def searchIO(searchQuery: SearchQuery): IO[SearchResults]= {
    def makeFilteredQuery(baseQuery: Query, filter: FilterData): Query = {
      filter.filterType match {
        case ft: FilterableType =>
          val filterQuery: Query = new TermQuery(new Term(ft.value, filter.filterValue))
          new BooleanQuery.Builder()
            .add(baseQuery, BooleanClause.Occur.MUST)
            .add(filterQuery, BooleanClause.Occur.MUST)
            .build()
        case NO_FILTER() => baseQuery
      }
    }

    val sortTerm: String = "nPath"
    IO.blocking {
      val displayNameQueryParser: QueryParser = new QueryParser("displayName", new StandardAnalyzer)
      displayNameQueryParser.setDefaultOperator(QueryParser.Operator.AND)

      // If the search string ends with a space treat it as a full word, otherwise treat as a partial word
      val escapedSearchString = if (searchQuery.searchString.matches(".*\\s+$")) {
        QueryParserBase.escape(searchQuery.searchString.trim)
      } else {
        s"${QueryParserBase.escape(searchQuery.searchString)}*"
      }

      val displayNameQuery: Query = displayNameQueryParser.parse(escapedSearchString)

      val basecodeQuery: TermQuery = new TermQuery(new Term("basecode", searchQuery.searchString))

      val displayNameAndBasecodeQuery: BooleanQuery = new BooleanQuery.Builder()
        .add(displayNameQuery, BooleanClause.Occur.SHOULD)
        .add(basecodeQuery, BooleanClause.Occur.SHOULD).setMinimumNumberShouldMatch(1).build()

      val filteredQuery: Query = makeFilteredQuery(displayNameAndBasecodeQuery, searchQuery.filterData)

      val maxResults : Int = ConfigSource.config.getInt("shrine.lucene.maxSearchResults")

      val sort: Sort = CaseInsensitiveSort(sortTerm)

      val previousSearchMetadataOption: Option[SearchResultsMetadata] = searchQuery.previousSearchMetadata
      val foundDocs: TopFieldDocs = previousSearchMetadataOption.fold {
        searcher.search(filteredQuery, maxResults, sort)
      }{ searchMetadata =>
        val field: Object = new BytesRef(searchMetadata.sortFieldValue)
        val fields = Array(field)
        val lastScoreDoc = new FieldDoc(searchMetadata.lastDocId, Float.NaN, fields)
        searcher.searchAfter(lastScoreDoc, filteredQuery, maxResults, sort, false)
      }

      val highlighter = HtmlHighlighter.createHighlighter(filteredQuery)
      (foundDocs, highlighter)
    }.flatMap(params => {
      val (allDocs: TopFieldDocs, highlighter: Highlighter) = params

      val scoreDocs: Array[ScoreDoc] = allDocs.scoreDocs
      val totalHits = allDocs.totalHits

      val codeCategoriesToDocsWithNulls: Map[String, Array[ScoreDoc]] = scoreDocs.groupBy(sd => searcher.doc(sd.doc).get("codeCategory"))
      val (codeCategoriesToDocs,nullsToDocs) = codeCategoriesToDocsWithNulls.partition(_._1 != null)
      if(nullsToDocs.nonEmpty) error(s"Some docs have null code categories: ${nullsToDocs(null).mkString(", ")}")

      val sortedDocsToCategories: SortedMap[String, Array[ScoreDoc]] = SortedMap(codeCategoriesToDocs.iterator.toSeq: _*)
      val codeCategoryTerms: Iterable[IO[CodeCategoryTerm]] = sortedDocsToCategories.map {
        case (codeCategory: String, scoreDocs: Array[ScoreDoc]) =>
          val trie: Trie = new Trie(highlighter)
          for (sd <- scoreDocs) yield trie.insert(sd)

          val ontTermsIO = IO(buildOntologyTerms(trie.root))
          ontTermsIO.flatMap(ontTermsList => IO(createCodeCategoryTerm(codeCategory, ontTermsList)))
      }

      import cats.implicits._
      val codeCategoryIO: IO[List[CodeCategoryTerm]] = codeCategoryTerms.toList.sequence
      codeCategoryIO.flatMap(terms => {
        val lastScoreDocOption: Option[ScoreDoc] = scoreDocs.lastOption
        val searchResultsMetadataOption: Option[SearchResultsMetadata] = lastScoreDocOption.map(lastScoreDoc => SearchResultsMetadata(lastScoreDoc.doc, searcher.doc(lastScoreDoc.doc).get(sortTerm)))
        IO(SearchResults(totalHits = totalHits.value, searchResultsMetadata = searchResultsMetadataOption , results = terms))
      })
    })
  }

  def buildOntologyTerms(node: TrieNode, path: String = ""): List[OntologyTerm] = {

    val ontTermResults: Iterable[OntologyTerm] = for (childItem <- node.children) yield {
      val childKey = childItem._1
      val child: TrieNode = node.children(childKey)

      val currentPath: String = s"$path$childKey\\"

      val ontTermOption = child.ontTerm
      val  childOntTerms = buildOntologyTerms(child, s"$currentPath")
      ontTermOption.get.updateChildren(Some(childOntTerms))
    }

    ontTermResults.toList
  }

  def getLabDetailsIO(ontologyPath:  OntologyPath): IO[Option[LabDetail]]= {

    IO.blocking {
      val nPathQuery = new TermQuery(new Term("path", ontologyPath.path))

      val foundDocs = searcher.search(nPathQuery, 1)

      foundDocs.scoreDocs.headOption.flatMap(extractLabDetail)
    }
  }

  private def extractLabDetail(scoreDoc: ScoreDoc): Option[LabDetail]= {

    val labFieldOption: Option[IndexableField] = Option(searcher.doc(scoreDoc.doc).getField("labDetail"))

    import io.circe.generic.auto.exportDecoder
    import org.http4s.circe.jsonOf
    import io.circe.parser.decode

    implicit val labDetailDecoder: EntityDecoder[IO, LabDetail] = jsonOf[IO, LabDetail]

    val ontTerm: Option[LabDetail] = labFieldOption.flatMap(labField => {
      val labDetailJson = labField.stringValue()
      decode[LabDetail](labDetailJson) match {
        case Right(labDetail) => Option(labDetail)
        case Left(_) => None
      }
    })
    ontTerm
  }

  /**
    * Gets a TopFieldDocs object for all root level elements sorted by displayName.
    */
  private def getRootFieldDocs: IO[TopFieldDocs] = {
    IO.blocking {
      val maxHits: Int = Int.MaxValue

      //This is a field in the Lucene index for the tree hierarchy
      val qp = new QueryParser("isRoot", new StandardAnalyzer)
      val rootQuery: Query = qp.parse("true")

      searcher.search(rootQuery, maxHits, CaseInsensitiveSort("displayName"))
    }
  }
}

case class TrieNode(value: String, visAttr: String = "", var ontTerm: Option[OntologyTerm]=None,
                    children: mutable.SortedMap[String, TrieNode] = new mutable.TreeMap()(CaseInsensitivePathOrder),
                    var isLeaf: Boolean = false)

class Trie(highlighter: Highlighter) {

  val root: TrieNode = TrieNode("")

  def insert(scoreDoc: ScoreDoc): Unit = {

    val conceptCategory: String = LuceneSearcher.searcher.doc(scoreDoc.doc).get("conceptCategory")

    val path: String = LuceneSearcher.searcher.doc(scoreDoc.doc).get("path")
    val browsePath: String = LuceneSearcher.searcher.doc(scoreDoc.doc).get("nPath")
    val visAttrPath: String = LuceneSearcher.searcher.doc(scoreDoc.doc).get("visPath")
    val isLabPath: String = LuceneSearcher.searcher.doc(scoreDoc.doc).get("isLabPath")
    val metadata: Option[String] = Option(LuceneSearcher.searcher.doc(scoreDoc.doc).get("tooltip"))

    //This will find the display name of the leaf that is being inserted and will use the highlighter
    // to surround matched terms in html tags.
    val text: String = LuceneSearcher.searcher.doc(scoreDoc.doc).get("displayName")
    val highlightedNameFragments = highlighter.getBestFragments(new StandardAnalyzer(),"displayName", text, 100)
    val highlightedNameOption: Option[String] = if(highlightedNameFragments.nonEmpty) Some(highlightedNameFragments(0)) else None

    var browsePathElements = browsePath.split("\\\\").filterNot(_ == "")
    val pathElems = path.split("\\\\").filterNot(_ == "").toList

    val minPathElemIndex =  (pathElems.length - (browsePathElements.length - 1)) + 1

    val pathSectionsList = if(minPathElemIndex != 0)
    {
      //Need to reconcile that the path to first term could made up of more than
      // one path element i.e. \\\\ACT_LAB\\ACT\\Labs\\ -> ACT Laboratory Test
      val firstElement = pathElems.take(minPathElemIndex).mkString("\\")
      val remainingPathElements = pathElems.drop(minPathElemIndex)
      firstElement :: remainingPathElements
    }
    else
    {  //The browse path for the term has the number of elements/levels as the ontology path
      pathElems
    }

    val visPathElements: Array[String] = visAttrPath.split("\\\\").filterNot(_ == "")
    val isLabPathElements: Array[String] = isLabPath.split("\\\\").filterNot(_ == "")

    var curNode: TrieNode = root

    var currentFullPath: String = s"\\\\"
    val categoryPathElement = browsePathElements.head
    var currentFullBrowsePath: String = s"\\\\$categoryPathElement\\"

    var vispath = ""
    var isLab: Boolean = false

    var index = 0
    browsePathElements = browsePathElements.drop(1)
    pathSectionsList.foreach(pathElement => {

      currentFullPath = s"$currentFullPath$pathElement\\"
      currentFullBrowsePath = s"$currentFullBrowsePath${browsePathElements(index)}\\"

      val children: mutable.SortedMap[String, TrieNode] = curNode.children

      vispath = visPathElements(index)
      isLab = isLabPathElements(index).toBoolean

      // If there is already a child for current path element of given path
      if (children.keys.exists(k => k == currentFullBrowsePath))
        curNode = children(currentFullBrowsePath)
      else // Else create a child
      {
        val ontTerm: Option[OntologyTerm] = Some(LuceneSearcher.extractOntologyTerm(currentFullPath, browsePathElements(index), vispath, conceptCategory, isLab, metadata))
        val temp: TrieNode = TrieNode(currentFullPath.dropRight(1), ontTerm = ontTerm)
        children.put(currentFullBrowsePath, temp)
        curNode = temp
      }

      index = index + 1
    })

    curNode.isLeaf = true
    curNode.ontTerm = Some(LuceneSearcher.extractOntologyTerm(path, browsePathElements.last, vispath, conceptCategory, isLab, metadata, highlightedName = highlightedNameOption))
  }
}

object HtmlHighlighter {
  val preTag = "<span class=\"highlight\">"
  val postTag = "</span>"

  /**
    * Creates a highlighter instance that can highlight text in html
    * matched words with `<span>` tags.
    */
  def createHighlighter(query: Query): Highlighter = {
    import org.apache.lucene.search.highlight.QueryScorer
    import org.apache.lucene.search.highlight.SimpleHTMLFormatter
    import org.apache.lucene.search.highlight.SimpleSpanFragmenter

    val scorer = new QueryScorer(query)
    val formater = new SimpleHTMLFormatter(preTag, postTag)
    val highlighter = new Highlighter(formater, scorer)
    val fragmenter = new SimpleSpanFragmenter(scorer)
    highlighter.setTextFragmenter(fragmenter)
    highlighter
  }

  def highlight(name: String): String = {
     s"$preTag$name$postTag"
  }
}

case class ConceptInfo(
                       nPath: String,
                       displayName: String,
                       highlightedName: Option[String],
                       conceptType: ConceptType,
                       isActive: Boolean,
                       children : Option[List[ConceptInfo]]
                      ){
  def updateChildren(newChildren: Option[List[ConceptInfo]]): ConceptInfo = {
    this.copy( children = newChildren)
  }
}

object ConceptInfo {

  def apply(scoreDoc: ScoreDoc,
            highlight: Boolean = false,
            childConcepts: Option[List[ConceptInfo]]= None) : ConceptInfo = {

    val nPath = LuceneSearcher.searcher.doc(scoreDoc.doc).get("nPath")
    val displayName = LuceneSearcher.searcher.doc(scoreDoc.doc).get("displayName")
    val visualAttr = LuceneSearcher.searcher.doc(scoreDoc.doc).get("visualAttributes")

    ConceptInfo(
      nPath = nPath,
      displayName = displayName,
      visualAttributes = VisualAttributes(visualAttr),
      childrenConceptInfo = childConcepts,
      highlight = highlight
    )
  }

  def apply(
            nPath: String,
            displayName: String,
            visualAttributes: VisualAttributes[ConceptType],
            childrenConceptInfo: Option[List[ConceptInfo]],
            highlight: Boolean
           ): ConceptInfo = {

    val highlightedName = if (highlight) Option(HtmlHighlighter.highlight(displayName)) else None

    ConceptInfo(
      nPath = nPath,
      displayName = displayName,
      highlightedName = highlightedName,
      conceptType = visualAttributes.conceptType,
      isActive = visualAttributes.isActive,
      children = childrenConceptInfo
    )
  }
}

case class CodeCategory(name: String)

object CaseInsensitivePathOrder extends Ordering[String] {
  def compare(str1: String, str2: String): Int = {

    val str1Split: Array[String] = str1.split("\\\\")
    val str2Split: Array[String] = str2.split("\\\\")

    val comp: (Int, Array[String]) = str1Split.foldLeft((0, str2Split))((compAndStr2, strElem) => {

      val str2Items: Array[String] = compAndStr2._2
      val comp = if(compAndStr2._1 == 0 && str2Items.nonEmpty){
        strElem.compareToIgnoreCase(str2Items.head)
      } else {
        compAndStr2._1
      }

      (comp, str2Items.drop(1))
    })

    comp._1 match {
      case c if c == 0 && str1Split.length > str2Split.length => 1
      case c if c == 0 && str1Split.length < str2Split.length => -1
      case x => x
    }
  }
}


object CaseInsensitiveSort {
  def apply(fieldName: String): Sort = {
    val sortField = new SortField(fieldName, new CaseInsensitiveComparatorSource)
    new Sort(sortField)
  }

  import org.apache.lucene.search.FieldComparator
  import org.apache.lucene.search.FieldComparatorSource
  import org.apache.lucene.util.BytesRef

  class CaseInsensitiveComparatorSource extends FieldComparatorSource with Loggable {
    override def newComparator(fieldName: String, numHits: Int, sortPos: Int, reversed: Boolean): FieldComparator[BytesRef] =

      new FieldComparator.TermValComparator(numHits, fieldName, true) {
        override def compareValues(bytesRef1: BytesRef, bytesRef2: BytesRef): Int = {
          if (bytesRef1 != null && bytesRef2 != null) {
            val str1: String = bytesRef1.utf8ToString()
            val str2: String = bytesRef2.utf8ToString()

            if (fieldName == "nPath") CaseInsensitivePathOrder.compare(str1, str2)
            else str1.compareToIgnoreCase(str2)
          }
          else if(bytesRef1 != null) {
            error(s"$fieldName bytesRef2 is null")
            1 // bytesRef1 is not null, so it goes first
          }
          else if(bytesRef2 != null) {
            error(s"$fieldName bytesRef1 is null")
            -1 // bytesRef2 is not null, so it goes first
          }
          else {
            error(s"$fieldName bytesRef1 and bytesRef2 are both null")
            0 // both are null
          }
        }
      }
  }
}
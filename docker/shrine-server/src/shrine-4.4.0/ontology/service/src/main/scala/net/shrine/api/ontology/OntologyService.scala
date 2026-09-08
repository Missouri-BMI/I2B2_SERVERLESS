package net.shrine.api.ontology

import cats.effect.IO
import io.circe.{Decoder, Encoder, Json, KeyEncoder}
import io.circe.generic.auto.exportDecoder
import io.circe.generic.auto.exportEncoder
import io.circe.syntax.EncoderOps
import org.http4s.{EntityDecoder, HttpRoutes, Response}
import org.http4s.dsl.impl.{->, /}
import org.http4s.dsl.io.{GET, NoContent, NotFound, Ok, POST, Root, http4sNoContentSyntax, http4sNotFoundSyntax, http4sOkSyntax}
import org.http4s.circe.jsonOf

case class OntologyService() {
  implicit val ontologyPathDecoder: EntityDecoder[IO, OntologyPath] = jsonOf[IO, OntologyPath]

  implicit val encodeConceptType: Encoder[ConceptType] = Encoder.instance {
    case  container: Container => container.name.asJson
    case folder: Folder => folder.name.asJson
    case leaf: Leaf => leaf.name.asJson
  }

  val service: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case GET -> Root / "ping" => Ok("pong")

    case _@ GET -> Root / "root"  => getRoot

    case _ @ GET -> Root / "filterOptions" => getFilterOptions

    case req @ POST -> Root / "children" =>

      implicit val ontologyPathDecoder: EntityDecoder[IO, OntologyPath] = jsonOf[IO, OntologyPath]

      val ontologyPathIO = req.as[OntologyPath]
      ontologyPathIO.flatMap(getChildren)

    case req @ POST -> Root / "search"=>

      implicit val searchQueryDecoder: EntityDecoder[IO, SearchQuery] = jsonOf[IO, SearchQuery]

      val searchQueryIO = req.as[SearchQuery]
      searchQueryIO.flatMap(search)

    case req @ POST -> Root / "suggest" =>

      implicit val suggestQueryDecoder: EntityDecoder[IO, SuggestQuery] = jsonOf[IO, SuggestQuery]

      val suggestQueryIO = req.as[SuggestQuery]
      suggestQueryIO.flatMap(suggest)

    case req @ POST -> Root / "labDetails"  =>
      val ontologyPathIO = req.as[OntologyPath]
      ontologyPathIO.flatMap(getLabDetails)

    case req @ POST -> Root / "conceptInfo"=>

      implicit val ontologyPathDecoder: EntityDecoder[IO, OntologyPath] = jsonOf[IO, OntologyPath]

      val ontologyPathIO = req.as[OntologyPath]
      ontologyPathIO.flatMap(getConceptInfo)

    case x => NotFound(s"The ontology service does not respond to $x")
  }

  private def getRoot: IO[Response[IO]] = {

    val codeCategoryTermsIO: IO[List[CodeCategoryTerm]] = LuceneSearcher.getRootTerms

    codeCategoryTermsIO.flatMap(codeCategoryTerms => {
      if (codeCategoryTerms.isEmpty) {
        NoContent()
      }
      else {
        val result = codeCategoryTerms.asJson.toString()
        Ok(result)
      }
    })
  }

  private def getFilterOptions: IO[Response[IO]] = {
    val filterOptions: IO[List[FilterOption]] = LuceneSearcher.getFilterOptions
    filterOptions.flatMap(ioResult => {
      val result = ioResult.asJson.toString()
      Ok(result)
    })
  }

  private def getChildren(ontologyPath: OntologyPath): IO[Response[IO]] = {

    val childrenIO: IO[List[OntologyTerm]] = LuceneSearcher.getChildren(ontologyPath)

    childrenIO.flatMap(children => {
      if (children.isEmpty) {
        NoContent()
      }
      else {
        val result = children.asJson.toString()
        Ok(result)
      }
    })
  }

  private def getConceptInfo(ontologyPath: OntologyPath): IO[Response[IO]] = {

    val conceptPathIO: IO[Option[ConceptInfo]] = LuceneSearcher.getConceptInfo(ontologyPath)

    conceptPathIO.flatMap(conceptPathOption => {

      conceptPathOption.fold(NotFound())(conceptPath => {
        val result = conceptPath.asJson.toString()
        Ok(result)
      })
    })
  }

  private def search(searchQuery: SearchQuery): IO[Response[IO]]= {
    val searchResultsIO = LuceneSearcher.searchIO(searchQuery)
    searchResultsIO.flatMap(searchResults => Ok(searchResults.asJson.toString()))
  }

  private def suggest(suggestQuery: SuggestQuery): IO[Response[IO]]= {
    val suggestResultsIO = LuceneSuggester.getSuggestionsIO(suggestQuery)
    suggestResultsIO.flatMap(suggestResults => Ok(suggestResults.asJson.toString()))
  }

  private def getLabDetails(ontologyPath: OntologyPath): IO[Response[IO]] = {
    val labDetailsIO = LuceneSearcher.getLabDetailsIO(ontologyPath)
    labDetailsIO.flatMap(labDetails => {
      labDetails.fold(NoContent())(ld => Ok(ld.asJson.toString()))
    })
  }
}

case class CodeCategoryTerm(displayName: String,
                            conceptType: ConceptType = Container(),
                            isActive: Boolean = true,
                            children : List[OntologyTerm])

case class OntologyTerm(displayName: String,
                        highlightedName: Option[String],
                        path: String,
                        conceptCategory : String,
                        conceptType: ConceptType,
                        isActive: Boolean,
                        isLab: Boolean,
                        metadata: Option[String],
                        children : Option[List[OntologyTerm]])
{
  def updateChildren(newChildren: Option[List[OntologyTerm]]): OntologyTerm = {
    this.copy( children = newChildren)
  }
}

case class OntologyPath(path: String)

case class FilterOption(filterType: FilterType, filterValue: String, displayableName: String)

sealed trait FilterType
case class NO_FILTER() extends FilterType
sealed abstract class FilterableType(val value: String) extends FilterType
case class CODE_SET() extends FilterableType(value = "codeSet")
case class CODE_CATEGORY() extends FilterableType(value = "codeCategory")

/**
 * This object contains all the implicit encoding and decoding logic that is needed
 * to translate between FilterTypes and JSON. It's implicit values are in scope anywhere
 * FilterType is.
 */
object FilterType {
  private def getClassDefinedEncoder[T]: Encoder[T] = x => Json.fromString(x.getClass.getSimpleName)
  private implicit val encodeNoFilter: Encoder[NO_FILTER] = getClassDefinedEncoder
  private implicit val encodeCodeCategory: Encoder[CODE_CATEGORY] = getClassDefinedEncoder
  private implicit val encodeCodeSet: Encoder[CODE_SET] = getClassDefinedEncoder
  private implicit val encoderFilterableType: Encoder[FilterableType] = Encoder.instance {
    case a @ CODE_CATEGORY() => a.asJson
    case b @ CODE_SET() => b.asJson
  }
  implicit val encodeFilterType: Encoder[FilterType] = Encoder.instance {
    case a @ NO_FILTER() => a.asJson
    case b: FilterableType => b.asJson
  }

  private def getClassDefinedDecoder[T](rightReturn: T): Decoder[T] = {
    val className: String = rightReturn.getClass.getSimpleName
    Decoder.decodeString.emap(str => if (str.equals(className)) Right(rightReturn) else Left("Unable to decode as " + className))
  }
  private implicit val decodeCodeSet: Decoder[CODE_SET] = getClassDefinedDecoder(CODE_SET())
  private implicit val decodeCodeCategory: Decoder[CODE_CATEGORY] = getClassDefinedDecoder(CODE_CATEGORY())
  private implicit val decodeNoFilter: Decoder[NO_FILTER] = getClassDefinedDecoder(NO_FILTER())
  private implicit val decodeFilterableType: Decoder[FilterableType] = {
    import cats.syntax.functor._
    List[Decoder[FilterableType]](
      Decoder[CODE_SET].widen,
      Decoder[CODE_CATEGORY].widen
    ).reduceLeft(_ or _)
  }
  implicit val decodeFilterType: Decoder[FilterType] = {
    import cats.syntax.functor._
    List[Decoder[FilterType]](
      Decoder[NO_FILTER].widen,
      Decoder[FilterableType].widen
    ).reduceLeft(_ or _)
  }

  implicit val filterTypeKeyEncoder: KeyEncoder[FilterType] = _.asJson.asString.get
}

case class FilterData(filterType: FilterType, filterValue: String)
case class SearchQuery(searchString: String, filterData: FilterData = FilterData(NO_FILTER(), "All Concepts"), previousSearchMetadata: Option[SearchResultsMetadata] = None)
case class SearchResultsMetadata(lastDocId: Int, sortFieldValue: String)
case class SearchResults(totalHits: Long, searchResultsMetadata: Option[SearchResultsMetadata], results: List[CodeCategoryTerm])

case class SuggestQuery(suggestString: String)

sealed trait ConceptType{
  val name: String
}

case class Container(name: String = "Container") extends ConceptType
case class Folder(name: String = "Folder") extends ConceptType
case class Leaf(name: String = "Leaf") extends ConceptType

case class VisualAttributes[A <: ConceptType](conceptType: A, isActive: Boolean)

object VisualAttributes {

  def apply(visualAttributes: String): VisualAttributes[ConceptType] = {
    val conceptTypeMap = Map(
      'C' -> Container(),
      'F' -> Folder(),
      'L' -> Leaf(),
      'M' -> Leaf()
    )

    val conceptType = conceptTypeMap(visualAttributes(0))
    val isActive = visualAttributes(1) match {
      case 'A' => true
      case 'I' => false
    }

    VisualAttributes(conceptType, isActive)
  }
}


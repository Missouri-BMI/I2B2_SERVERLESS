package net.shrine.api.ontology

import cats.data.OptionT
import cats.effect.IO
import io.circe.Json
import io.circe.generic.auto.exportEncoder
import io.circe.syntax.EncoderOps
import net.shrine.log.Log
import org.http4s.{EntityDecoder, Method, Request, Response, Uri}
import org.http4s.circe.jsonOf
import org.http4s.dsl.io.{NoContent, NotFound, Ok}
import org.junit.Test
import org.scalatest.FlatSpec

import scala.io.Source
import org.http4s.syntax.literals._

class OntologyServiceTest extends FlatSpec {
  import cats.effect.unsafe.implicits.global
  implicit val jsonDecoder: EntityDecoder[IO, Json] = jsonOf[IO, Json]

  def extractResponse(request: Request[IO]): Response[IO] = {
    val responseOptionIo: OptionT[IO, Response[IO]] = OntologyService().service.run(request)

    responseOptionIo.map { r: Response[IO] => r }.fold {
      fail("No response from service")
    } { r: Response[IO] => r
    }.unsafeRunSync()
  }

  @Test
  def replyWithPong(): Unit = {
    val pingRequest: Request[IO] = Request(method = Method.GET, uri = Uri.unsafeFromString(s"/ping"))

    val response: Response[IO] = extractResponse(pingRequest)

    assertResult(Ok)(response.status)
    val entityText = response.as[String].unsafeRunSync()
    assertResult("pong")(entityText)
  }

  @Test
  def replyWithRootData(): Unit = {
    val rootRequest: Request[IO] = Request(method = Method.GET, uri = Uri.unsafeFromString(s"/root"))

    val expectedJson = readJsonFile("/rootOntology.json")

    val response: Response[IO] = extractResponse(rootRequest)

    assertResult(Ok)(response.status)
    val entityText = response.as[String].unsafeRunSync()
    assertResult(expectedJson)(entityText)
  }

  @Test
  def replyWithFilterOptionData(): Unit = {
    val rootRequest: Request[IO] = Request(method = Method.GET, uri = Uri.unsafeFromString(s"/filterOptions"))

    val expectedJson = readJsonFile("/filterOptions.json")

    val response: Response[IO] = extractResponse(rootRequest)

    assertResult(Ok)(response.status)
    val entityText = response.as[String].unsafeRunSync()
    assertResult(expectedJson)(entityText)
  }

  @Test
  def replyWithChildrenData(): Unit = {

    val ontologyPath = OntologyPath(path = "\\\\ACT_DEMO\\ACT\\Demographics\\Age\\")

    val uri = uri"/children"
    val childrenRequest: Request[IO] = Request(method = Method.POST).withUri(uri).withEntity(ontologyPath.asJson.toString)

    val expectedJson = readJsonFile("/demographicsChildren.json")

    val response: Response[IO] = extractResponse(childrenRequest)

    assertResult(Ok)(response.status)
    val entityText = response.as[String].unsafeRunSync()
    assertResult(expectedJson)(entityText)
  }

  @Test
  def replyWith204IfNoChildrenData(): Unit = {

    val ontologyPath = OntologyPath(path = "\\ACT_DEMO\\ACT\\Demographics\\Ages")
    val uri = uri"/children"
    val childrenRequest: Request[IO] = Request(method = Method.POST).withUri(uri).withEntity(ontologyPath.asJson.toString)

    val response: Response[IO] = extractResponse(childrenRequest)

    assertResult(NoContent)(response.status)
  }

  @Test
  def replyWithLabDetail(): Unit = {

    val ontologyPath = OntologyPath(path = "\\\\ACT_LAB\\ACT\\Labs\\LP32744-2\\2889-4\\")

    val uri = uri"/labDetails"
    val childrenRequest: Request[IO] = Request(method = Method.POST).withUri(uri).withEntity(ontologyPath.asJson.toString)

    val expectedJson = readJsonFile("/labDetails.json")

    val response: Response[IO] = extractResponse(childrenRequest)

    assertResult(Ok)(response.status)
    val entityText = response.as[String].unsafeRunSync()
    assertResult(expectedJson)(entityText)
  }

  @Test
  def replyWithLabDetailWithEnum(): Unit = {

    val ontologyPath = OntologyPath(path = "\\\\ACT_LAB\\ACT\\Labs\\LP14855-8\\LP14306-2\\LP41149-3\\22322-2\\")
    val uri = uri"/labDetails"
    val childrenRequest: Request[IO] = Request(method = Method.POST).withUri(uri).withEntity(ontologyPath.asJson.toString)

    val expectedJson = readJsonFile("/labDetailsWithEnum.json")

    val response: Response[IO] = extractResponse(childrenRequest)

    assertResult(Ok)(response.status)
    val entityText = response.as[String].unsafeRunSync()
    assertResult(expectedJson)(entityText)
  }

  @Test
  def replyWith204IfNoLabDetails(): Unit = {

    val ontologyPath = OntologyPath(path = "\\Laboratory Tests\\ACT Laboratory Tests\\")
    val uri = uri"/labDetails"
    val childrenRequest: Request[IO] = Request(method = Method.POST).withUri(uri).withEntity(ontologyPath.asJson.toString)

    val response: Response[IO] = extractResponse(childrenRequest)

    assertResult(NoContent)(response.status)
  }

  def readJsonFile(filename: String): String = {
    val source = Source.fromFile(getClass.getResource(filename).getFile)
    try source.mkString finally source.close()
  }

  @Test
  def replyWithSearchResults(): Unit = {
    val searchResults = SearchQuery(searchString = "left hand")

    val uri = uri"/search"
    val childrenRequest: Request[IO] = Request(method = Method.POST).withUri(uri).withEntity(searchResults.asJson.toString)

    val expectedJson = readJsonFile("/searchResults.json")

    val response: Response[IO] = extractResponse(childrenRequest)

    assertResult(Ok)(response.status)
    val entityText = response.as[String].unsafeRunSync()
    assertResult(expectedJson)(entityText)

    import io.circe.parser.decode

    val searchResultsOption: Option[Json] = decode[Json](entityText) match {
      case Right(s) => Option(s)
      case Left(e) => Log.error(e.getMessage); None
    }

    searchResultsOption.fold(fail())(searchResults => {
      val totalHitsOption: Option[Int] = searchResults.findAllByKey("totalHits").head.asNumber.fold(Option(-1))(p => p.toInt)
      val totalHits: Int = totalHitsOption.getOrElse(-1)
      assert(totalHits > 0)
      searchResults.findAllByKey("displayName").foreach(displayName => {
        val displayNameString = displayName.toString()

        //check that both search terms "left" and "hand" appear in the displayName
        if (displayNameString.contains("left")) {
          assert(displayNameString.contains("left") && displayNameString.contains("hand"))
        }
      })
    })
  }

  @Test
  def replyWithSearchResultsTrailingSpace(): Unit = {
    val searchQuery = SearchQuery(searchString = "left hand ")

    val uri = uri"/search"
    val childrenRequest: Request[IO] = Request(method = Method.POST).withUri(uri).withEntity(searchQuery.asJson.toString)

    val response: Response[IO] = extractResponse(childrenRequest)

    val expectedJson = readJsonFile("/searchResults.json")

    assertResult(Ok)(response.status)
    val entityText = response.as[String].unsafeRunSync()
    assertResult(expectedJson)(entityText)
  }

  @Test
  def replyWithHighlightedSearchResults(): Unit = {
    val searchResults = SearchQuery(searchString = "pro")

    val uri = uri"/search"
    val childrenRequest: Request[IO] = Request(method = Method.POST).withUri(uri).withEntity(searchResults.asJson.toString)

    val expectedJson = readJsonFile("/complexSearchResults.json")

    val response: Response[IO] = extractResponse(childrenRequest)

    assertResult(Ok)(response.status)
    val entityText = response.as[String].unsafeRunSync()
    assertResult(true)(entityText.contains("<span class=\\\"highlight\\\">"))
    assertResult(expectedJson)(entityText)
  }

  @Test
  def replyWithFilteredSearchResults(): Unit = {
    val searchString = "dia"
    val uri = uri"/search"

    val filterDataNone = FilterData(NO_FILTER(), "All Concepts")
    val filterDataCodeCategory = FilterData(CODE_CATEGORY(), "Diagnoses")
    val filterDataCodeSet = FilterData(CODE_SET(), "Diagnoses ICD10-ICD9")

    val a = SearchQuery(searchString, filterDataNone).asJson
    val allConceptsSR: Request[IO] = Request(method = Method.POST)
      .withUri(uri)
      .withEntity(a.toString())

    val b = SearchQuery(searchString, filterDataCodeCategory).asJson
    val filterCodeCategorySR: Request[IO] = Request(method = Method.POST)
      .withUri(uri)
      .withEntity(b.toString())

    val c = SearchQuery(searchString, filterDataCodeSet).asJson
    val filterCodeSetSR: Request[IO] = Request(method = Method.POST)
      .withUri(uri)
      .withEntity(c.toString())

    val allConceptsResponse: Response[IO] = extractResponse(allConceptsSR)
    val filterCodeCategoryResponse: Response[IO] = extractResponse(filterCodeCategorySR)
    val filterCodeSetResponse: Response[IO] = extractResponse(filterCodeSetSR)

    val allConceptsBody = allConceptsResponse.as[Json].unsafeRunSync().asObject.get
    val filterCodeCategoryBody = filterCodeCategoryResponse.as[Json].unsafeRunSync().asObject.get
    val filterCodeSetBody = filterCodeSetResponse.as[Json].unsafeRunSync().asObject.get

    // Adding a filter should reduce the number of results
    assertResult(true)(allConceptsBody("totalHits").get.asNumber.get.toInt.get > filterCodeCategoryBody("totalHits").get.asNumber.get.toInt.get)
    assertResult(true)(filterCodeCategoryBody("totalHits").get.asNumber.get.toInt.get > filterCodeSetBody("totalHits").get.asNumber.get.toInt.get)

    // Filtered results should only have terms from their code category
    val codeCategoryResults = filterCodeCategoryBody("results").get.asArray.get
    val codeSetResults = filterCodeSetBody("results").get.asArray.get
    assertResult(1)(codeCategoryResults.size)
    assertResult(1)(codeSetResults.size)
    assertResult("Diagnoses")(codeCategoryResults(0).asObject.get("displayName").get.asString.get)
    assertResult("Diagnoses")(codeSetResults(0).asObject.get("displayName").get.asString.get)

    // Filtered code set results should only have terms from their code set
    val codeSetChildrenResults = codeSetResults(0).asObject.get("children").get.asArray.get
    assertResult(1)(codeSetChildrenResults.size)
    assertResult(true)(codeSetChildrenResults(0).asObject.get("displayName").get.asString.get.contains("ICD10-ICD9"))
  }

  @Test
  def replyWithNoSearchResults(): Unit = {
    val searchResults = SearchQuery(searchString = "Foobar")

    val uri = uri"/search"
    val childrenRequest: Request[IO] = Request(method = Method.POST).withUri(uri).withEntity(searchResults.asJson.toString)

    val expectedJson = readJsonFile("/noSearchResults.json")

    val response: Response[IO] = extractResponse(childrenRequest)

    assertResult(Ok)(response.status)
    val entityText = response.as[String].unsafeRunSync()
    assertResult(expectedJson)(entityText)
  }

  @Test
  def replyWithConceptInfo(): Unit = {
    val ontologyPath = OntologyPath(path = "\\\\ACT_DEMO\\ACT\\Demographics\\Age\\")

    val uri = uri"/conceptInfo"
    val childrenRequest: Request[IO] = Request(method = Method.POST).withUri(uri).withEntity(ontologyPath.asJson.toString)

    val expectedJson = readJsonFile("/conceptInfo.json")

    val response: Response[IO] = extractResponse(childrenRequest)
    assertResult(Ok)(response.status)
    val entityText = response.as[String].unsafeRunSync()
    assertResult(expectedJson)(entityText)

    val rootOntologyPath = OntologyPath(path = "\\\\ACT_DEMO\\ACT\\Demographics\\")
    val rootChildrenRequest: Request[IO] = Request(method = Method.POST).withUri(uri).withEntity(rootOntologyPath.asJson.toString)

    val expectedRootChildrenJson = readJsonFile("/rootConceptInfo.json")

    val rootChildrenResponse: Response[IO] = extractResponse(rootChildrenRequest)
    assertResult(Ok)(response.status)
    val rootChildrenEntityText = rootChildrenResponse.as[String].unsafeRunSync()
    assertResult(expectedRootChildrenJson)(rootChildrenEntityText)
  }

  @Test
  def replyWith404IfNoConceptInfo(): Unit = {

    val ontologyPath = OntologyPath(path = "\\ACT_DEMO\\ACT\\Demographics\\Ages")

    val uri = uri"/conceptInfo"
    val childrenRequest: Request[IO] = Request(method = Method.POST).withUri(uri).withEntity(ontologyPath.asJson.toString)

    val response: Response[IO] = extractResponse(childrenRequest)

    assertResult(NotFound)(response.status)
  }

  @Test
  def testCustomEncodingAndDecoding(): Unit = {
    val codeCategory: FilterType = CODE_CATEGORY()
    val codeCategoryJson = codeCategory.asJson
    assertResult(true)(codeCategoryJson.isString)
    assertResult("CODE_CATEGORY")(codeCategoryJson.asString.get)
    val decodedCodeCategory = codeCategoryJson.as[FilterType]
    assertResult(true)(decodedCodeCategory.exists(ft => ft.equals(codeCategory)))

    val codeSet: FilterType = CODE_SET()
    val codeSetJson = codeSet.asJson
    assertResult(true)(codeSetJson.isString)
    assertResult("CODE_SET")(codeSetJson.asString.get)
    val decodedCodeSet = codeSetJson.as[FilterType]
    assertResult(true)(decodedCodeSet.exists(ft => ft.equals(codeSet)))

    val noFilter: FilterType = NO_FILTER()
    val noFilterJson = noFilter.asJson
    assertResult(true)(noFilterJson.isString)
    assertResult("NO_FILTER")(noFilterJson.asString.get)
    val decodedNoFilter = noFilterJson.as[FilterType]
    assertResult(true)(decodedNoFilter.exists(ft => ft.equals(noFilter)))
  }

  @Test
  def replyWithSearchResultsInCaseInsensitiveOrder(): Unit = {
    val searchResults = SearchQuery(searchString = "hemoglobin")

    val uri = uri"/search"
    val childrenRequest: Request[IO] = Request(method = Method.POST).withUri(uri).withEntity(searchResults.asJson.toString)

    val response: Response[IO] = extractResponse(childrenRequest)

    assertResult(Ok)(response.status)
    val entityText = response.as[String].unsafeRunSync()

    val termWithUpperCase = "A1c in Blood by HPLC"
    val termWithLowerCase = "A1c in Blood by calculation"

    assert(entityText.indexOf(termWithUpperCase) > entityText.indexOf(termWithLowerCase))
  }

  @Test
  def testSearchUsingBasecode(): Unit = {

    val searchResults = SearchQuery(searchString = "E61.4") //Chromium deficiency

    val uri = uri"/search"
    val childrenRequest: Request[IO] = Request(method = Method.POST).withUri(uri).withEntity(searchResults.asJson.toString)

    val expectedJson = readJsonFile("/basecodeSearchResults.json")

    val response: Response[IO] = extractResponse(childrenRequest)

    assertResult(Ok)(response.status)
    val entityText = response.as[String].unsafeRunSync()

    assertResult(expectedJson)(entityText)
  }

  @Test
  def testGetTermByPath(): Unit = {
    val termPath1 = """\\ACT_DX_ICD10_2018\ACT\Diagnosis\ICD10\V2_2018AA\A20098492\"""
    val displayName1 = "ACT Diagnoses ICD-10"

    val termPath2 = """\\ACT_PX_HCPCS_2018\ACT\Procedures\HCPCS\V2_2018AA\A13475665\"""
    val displayName2 = "ACT Procedures HCPCS"

    val termPath3 = """\\ACT_PX_ICD10_2018\ACT\Procedures\ICD10\V2_2018AA\A16077350\"""
    val displayName3 = "ACT Procedures ICD-10-PCS"

    val termPath4 = """\\ACT_PX_ICD9_2018\ACT\Procedures\ICD9\V2_2018AA\A18090800\A8352133\"""
    val displayName4 = "ACT Procedures   ICD-9-Proc"

    val ontologyTerm1 = LuceneSearcher.getSingleTermByPathAndDisplayName(termPath1, displayName1)
      .unsafeRunSync().get

    val ontologyTerm2 = LuceneSearcher.getSingleTermByPathAndDisplayName(termPath2, displayName2)
      .unsafeRunSync().get

    val ontologyTerm3 = LuceneSearcher.getSingleTermByPathAndDisplayName(termPath3, displayName3)
      .unsafeRunSync().get

    val ontologyTerm4 = LuceneSearcher.getSingleTermByPathAndDisplayName(termPath4, displayName4)
      .unsafeRunSync().get

    assertResult(displayName1)(ontologyTerm1.displayName)
    assertResult(displayName2)(ontologyTerm2.displayName)
    assertResult(displayName3)(ontologyTerm3.displayName)
    assertResult(displayName4)(ontologyTerm4.displayName)
    assertResult(termPath1)(ontologyTerm1.path)
    assertResult(termPath2)(ontologyTerm2.path)
    assertResult(termPath3)(ontologyTerm3.path)
    assertResult(termPath4)(ontologyTerm4.path)
  }
}

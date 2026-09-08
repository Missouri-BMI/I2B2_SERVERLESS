package net.shrine.ontology.indexer

import java.io.{BufferedReader, File, FileReader, IOException}

import net.shrine.log.Log
import net.shrine.ontology.LabDetail
import net.shrine.ontology.indexer.LuceneIndexer.{pathPrefixMap, pathToOntPathMap, pathToTermMap}
import net.shrine.ontology.indexer.parser.{CloseableCsvToBean, OntologyFileParser, OntologyRow, RootInfo, TermInfo}
import org.apache.lucene.document.{Document, Field, SortedDocValuesField, StoredField, StringField, TextField}
import org.apache.lucene.index.IndexWriter
import org.apache.lucene.util.BytesRef

import scala.io.Source

class SearchIndexer(searchIndexWriter: IndexWriter, codeCategoryFilename: String, ontDirectory: String, fileDelimiter: Char, categoryFileDelimiter: Char, includeTooltips: Boolean) {

  val codeCategories: Map[String, CodeAndConceptCategory] = CodeCategories.extractCodeCategoriesMappings(codeCategoryFilename, categoryFileDelimiter)

  var mappedCodeCategoriesCount = 0
  var mappedConceptCategoriesCount = 0
  var mappedCodeSetCount = 0

  createPathMap()

  def createPathMap(): Unit = {
    Log.debug("Creating maps")

    val ontIt: Seq[CloseableCsvToBean] = OntologyFileParser.ontIterator(ontDirectory, fileDelimiter)
    ontIt.foreach(closeableCsvToBean => {
      try {
        closeableCsvToBean.csvToBean.iterator().forEachRemaining(ontologyRow => {
          val isRoot = ontologyRow match {
            case rootInfo: RootInfo => {
              val tableCd = rootInfo.tableCd
              pathPrefixMap += (rootInfo.path -> tableCd)
              true
            }
            case _: TermInfo => false
          }

          val hasLabDataString = ontologyRow.metadataXmlOption.map(metadataXml => {
            if (metadataXml == "" || metadataXml == "\\N") "FALSE" else "TRUE"
          }).getOrElse("FALSE")

          //Guard against duplicate root paths with different display names
          val result = pathToTermMap.get(ontologyRow.path)
          result.fold(pathToTermMap += (ontologyRow.path -> List(ontologyRow.name, ontologyRow.visualAttributes, hasLabDataString))){
            _ => {
              if(isRoot){
                pathToTermMap += (ontologyRow.path -> List(ontologyRow.name, ontologyRow.visualAttributes, hasLabDataString))
              }
            }
          }
        })
      }finally {
        closeableCsvToBean.close()
      }
    })

    Log.debug("Creating path to natural ordering")
    pathToTermMap.keys.foreach(fullPath => {

      val pathItems = fullPath.split("\\\\")
      var newpath = ""
      var visAttrPath = ""
      var isLabPath = ""
      var rootFound = false

      var currentPath = ""
      pathItems.foreach(item => {
        currentPath = s"$currentPath\\$item"

        if(pathPrefixMap.get(currentPath.drop(1) + "\\").nonEmpty) {
          rootFound = true
        }

        val m = pathToTermMap.get(s"${currentPath.drop(1)}\\")
        if(m.nonEmpty)
        {
          if(rootFound)
          {
            newpath = s"$newpath\\${m.get.head}"
            visAttrPath = s"$visAttrPath\\${m.get(1)}"
            isLabPath = s"$isLabPath\\${m.get(2)}"
          }
        }
      })

      if(rootFound)
      {
        newpath = s"$newpath\\"
        visAttrPath = s"$visAttrPath\\"
        isLabPath = s"$isLabPath\\"
        pathToOntPathMap += (fullPath -> List(newpath, visAttrPath, isLabPath))
      }
    })

    pathToTermMap = Map.empty
    Log.debug("Completed path to natural ordering creation")
  }

  def createAndAddDocument(ontologyRow: OntologyRow): Either[String, Unit] =    {

    //skip any rows with root paths that are not labeled as root -- root paths and their display names are overridden in the table_access table
    if(pathPrefixMap.exists(_._1 == ontologyRow.path && !ontologyRow.isRoot))
    {
      Log.info(s"Filtering out duplicate root path ${ontologyRow.path} with name ${ontologyRow.name}")
      return Right(())
    }

    val displayField = new TextField("displayName", ontologyRow.name, Field.Store.YES)
    val displaySortField  = new SortedDocValuesField("displayName", new BytesRef(ontologyRow.name))
    val isRootField = new StringField("isRoot", ontologyRow.isRoot.toString, Field.Store.YES)
    val visualAttrField = new StoredField("visualAttributes", ontologyRow.visualAttributes)

    val searchDoc = new Document
    searchDoc.add(displayField)
    searchDoc.add(displaySortField)
    searchDoc.add(isRootField)
    searchDoc.add(visualAttrField)

    if(includeTooltips) {
      ontologyRow.tooltipOption.foreach(t => searchDoc.add(new StoredField("tooltip", t)))
    }

    ontologyRow.basecodeOption.foreach(basecode => {
      val basecodeField = new StringField("basecode", basecode, Field.Store.YES)
      searchDoc.add(basecodeField)
    })

    ontologyRow.metadataXmlOption.foreach(metadataXml => {
      import io.circe.generic.auto.exportEncoder
      import io.circe.syntax.EncoderOps

      val labDetailOption = LabDetail(metadataXml, ontologyRow.path)
      labDetailOption.foreach{ labDetail =>
        val labDetailJson  = labDetail.asJson.toString()
        val labField = new StoredField("labDetail", labDetailJson)
        searchDoc.add(labField)
      }
    })

    var fullNameWithTableNamePrefix = ""

    //add the table prefix to the path name so that the path names match
    // what is in the legacy SHRINE adapter mappings file
    pathPrefixMap.find(k => ontologyRow.path.startsWith(k._1)).foreach(prefixEntry => {
      val tablePrefix = prefixEntry._2
      fullNameWithTableNamePrefix = s"\\\\$tablePrefix${ontologyRow.path}"

      val pathField = new StringField("path", fullNameWithTableNamePrefix, Field.Store.YES)
      searchDoc.add(pathField)

      if(!ontologyRow.isRoot){
        val parentPath = fullNameWithTableNamePrefix.split("\\\\").dropRight(1).mkString("\\")
        val parentField = new StringField("parentPath", parentPath, Field.Store.NO)
        searchDoc.add(parentField)
      }
    })

    val errorOrFine = codeCategories.find(c => fullNameWithTableNamePrefix.contains(c._1)).fold[Either[String,Unit]] {
      Log.error(s"No category found for path=${ontologyRow.path} and name=${ontologyRow.name}")
      Left(ontologyRow.path)
    }{ c =>

      mappedConceptCategoriesCount = mappedConceptCategoriesCount + 1
      searchDoc.add(new StoredField("conceptCategory", c._2.conceptCategory))
      mappedCodeCategoriesCount = mappedCodeCategoriesCount + 1
      searchDoc.add(new StringField("codeCategory", c._2.codeCategory, Field.Store.YES))
      if (c._2.codeSet.isDefined) {
        mappedCodeSetCount = mappedCodeSetCount + 1
        searchDoc.add(new StringField("codeSet", c._2.codeSet.get, Field.Store.YES))
      }
      val naturalPathAndVisAttrAndLabList = pathToOntPathMap.getOrElse(ontologyRow.path,List())
      val naturalPath = naturalPathAndVisAttrAndLabList.head
      val visPath = naturalPathAndVisAttrAndLabList(1)
      val isLabPath = naturalPathAndVisAttrAndLabList(2)
      val naturalPathWithCategory = s"\\${c._2.codeCategory}$naturalPath"
      val naturalPathField  = new StringField("nPath", naturalPathWithCategory, Field.Store.YES)
      searchDoc.add(naturalPathField)
      val naturalPathSortedField  = new SortedDocValuesField("nPath", new BytesRef(naturalPathWithCategory))
      searchDoc.add(naturalPathSortedField)
      val visPathField  = new StoredField("visPath", visPath)
      searchDoc.add(visPathField)
      val isLabPathField  = new StoredField("isLabPath",isLabPath)
      searchDoc.add(isLabPathField)

      addDocument(searchDoc)
      Right(())
    }

    errorOrFine
  }

  private def addDocument(searchDoc: Document):Long = {
    searchIndexWriter.addDocument(searchDoc)
  }

  def commit(): Unit = {
    searchIndexWriter.commit
  }
}

object CodeCategories {
  import com.opencsv.CSVParserBuilder
  import com.opencsv.CSVReaderBuilder

  def extractCodeCategoriesMappings(filename: String, categoryFileDelimiter: Char): Map[String, CodeAndConceptCategory] = {

    val parser = new CSVParserBuilder()
      .withSeparator(categoryFileDelimiter)
      .withEscapeChar('\u0000')
      .build
    val codeCategories = scala.collection.mutable.Map[String, CodeAndConceptCategory]()

    Log.info(s"Parsing category definition file $filename")
    withBufferedFileReader(new File(filename)) { br =>
      val csvReader = new CSVReaderBuilder(br).withCSVParser(parser).build
      try {
        csvReader.iterator().forEachRemaining(line => {
          if (line.length == 3) {
            codeCategories += (line(0).strip().stripLineEnd.toString -> CodeAndConceptCategory(line(1), line(2)))
          } else if (line.length == 4) {
            codeCategories += (line(0).strip().stripLineEnd.toString -> CodeAndConceptCategory(line(1), line(2), Some(line(3))))
          } else {
            Log.error(s"Error parsing line: ${line.mkString(categoryFileDelimiter.toString)}")
          }
        })
        Log.info(s"Found ${codeCategories.size} code category listings")

        codeCategories.toMap
      }
      finally {
        csvReader.close()
        br.close()
      }
    }
  }

  private def withBufferedFileReader(file: File)(op: BufferedReader =>  Map[String, CodeAndConceptCategory] ):  Map[String, CodeAndConceptCategory]  = {
    try {
      val reader = new BufferedReader(new FileReader(file))
      op(reader)
    } catch {
      case e: IOException => Log.error(s"An error occurred trying to read file: $file", e)
        throw e
    }
  }
}


package net.shrine.ontology.indexer.parser

import java.io.{BufferedReader, File, FileReader, IOException, Reader}

import com.opencsv.bean.{BeanVerifier, CsvToBean, CsvToBeanBuilder, CsvToBeanFilter}
import net.shrine.log.Log

import scala.util.control.NonFatal
import org.apache.commons.text.StringEscapeUtils

object OntologyFileParser {

  def ontIterator(directory: String, delimiter: Char =  '|'): Seq[CloseableCsvToBean] = {
    val file = new File(directory)
    val fileList: Array[File] = file.listFiles.filter(_.isFile).sorted

    fileList.map(fileName => {
      withBufferedFileReader(fileName) { fileReader =>
        try{
          val ms = new OntologyMappingStrategy()
          val cb = new CsvToBeanBuilder(fileReader)
            .withSeparator(delimiter)
            .withIgnoreQuotations(false)
            .withType(classOf[OntologyRow])
            .withMappingStrategy(ms)
            .withEscapeChar('\u0000')
            .withVerifier(new OntologyVerifier)
            .build

          CloseableCsvToBean(cb, fileReader, fileName.getName)
        }catch {
          case NonFatal(e) => {
            Log.debug(s"Error parsing file: $fileName", e)
            throw e
          }
        }
      }
    }).toSeq
  }

  private def withBufferedFileReader(file: File)(op: BufferedReader => CloseableCsvToBean): CloseableCsvToBean = {
    try {
      val reader = new BufferedReader(new FileReader(file))
      op(reader)
    } catch {
      case e: IOException => Log.error(s"An error occurred trying to read file: $file", e)
        throw e
    }
  }

  def main(args: Array[String]): Unit = {

    val dir = args(0)

    val ontIt = OntologyFileParser.ontIterator(dir)
    ontIt.foreach(closeableCsvToBean => {
        closeableCsvToBean.csvToBean.iterator().forEachRemaining(ontologyRow => {
      })
    })
  }
}

object OntologyHeader {
  val FULL_NAME: String = "C_FULLNAME"
  val HLEVEL: String = "C_HLEVEL"
  val NAME: String = 	"C_NAME"
  val SYNONYM_CD: String = "C_SYNONYM_CD"
  val VISUALATTRIBUTES: String = "C_VISUALATTRIBUTES"
  val BASECODE: String = "C_BASECODE"
  val METADATAXML: String	 = "C_METADATAXML"
  val TABLENAME: String = "C_TABLE_NAME"
  val TOOLTIP: String = "C_TOOLTIP"
  val APPLIED_PATH: String = "M_APPLIED_PATH"
  val TABLE_CD: String = "C_TABLE_CD"
}

sealed trait OntologyRow {
  val hlevel: Int
  val path: String
  val name: String
  val synonymCd: String
  val visualAttributes: String
  val basecodeOption: Option[String]
  val metadataXmlOption: Option[String]
  val tooltipOption: Option[String]

  def isHidden: Boolean = {
    visualAttributes.length > 1 && visualAttributes(1) == 'H'
  }

  def isSynonym:Boolean = {
    synonymCd == "Y"
  }

  def isRoot: Boolean
}

case class RootInfo (
                      tableCd: String,
                      tableName: String,
                      hlevel: Int,
                      path: String,
                      name: String,
                      synonymCd: String,
                      visualAttributes: String,
                      basecodeOption: Option[String],
                      metadataXmlOption: Option[String],
                      tooltipOption: Option[String]
) extends OntologyRow{
  def isRoot: Boolean = true
}


case class TermInfo(
                     hlevel: Int,
                     path: String,
                     name: String,
                     synonymCd: String,
                     visualAttributes: String,
                     basecodeOption: Option[String],
                     metadataXmlOption: Option[String],
                     tooltipOption: Option[String],
                     mAppliedPathOption: Option[String],
                       ) extends OntologyRow {
  def isRoot: Boolean = false
  def isModifier:Boolean = {
    !mAppliedPathOption.contains("@")
  }
}

import com.opencsv.bean.HeaderNameBaseMappingStrategy


class OntologyMappingStrategy() extends HeaderNameBaseMappingStrategy[OntologyRow]{
  this.setType(classOf[OntologyRow])


  override def populateNewBean(line: Array[String]): OntologyRow = {

    // NOTE: not all fields may need to be unescaped; however we have not studied the ontology in depth for the
    // presence of unicode characters in every field. So to be on the safe side we unescape every field with a call to
    // cleanupForIndexing

    val header: Map[String,Int] = this.headerIndex.getHeaderIndex.zipWithIndex.map{ case (v,i) => (v.toUpperCase, i) }.toMap
    val metadataXmlOption = if(header.contains(OntologyHeader.METADATAXML)){
      Option(ParserUtil.cleanupForIndexing(line(header(OntologyHeader.METADATAXML))).trim).filter(_.nonEmpty)
    }else{
      None
    }

    val tooltipOption = if(header.contains(OntologyHeader.TOOLTIP)){
      Option(ParserUtil.cleanupForIndexing(line(header(OntologyHeader.TOOLTIP))).trim).filter(_.nonEmpty)
    }else{
      None
    }

    val basecodeOption = if(header.contains(OntologyHeader.BASECODE)){
      //remove the prefix i.e. CPT4:XXXX
      Option(ParserUtil.cleanupForIndexing(line(header(OntologyHeader.BASECODE))).trim.replaceFirst("(.*:)", "")).filter(_.nonEmpty)
    }else{
      None
    }

    if(header.contains(OntologyHeader.TABLE_CD)) {
      val pathNoTrim = s"${ParserUtil.cleanupForIndexing(line(header(OntologyHeader.FULL_NAME))).replaceAll("\\\\\\\\", "\\\\")}"
      val path = s"${ParserUtil.cleanupForIndexing(line(header(OntologyHeader.FULL_NAME))).trim.replaceAll("\\\\\\\\", "\\\\")}"

      val rootInfo = RootInfo(
        tableCd = line(header(OntologyHeader.TABLE_CD)),
        tableName = line(header(OntologyHeader.TABLENAME)),
        hlevel = ParserUtil.cleanupForIndexing(line(header(OntologyHeader.HLEVEL))).toInt,
        path = path,
        name = ParserUtil.cleanupForIndexing(line(header(OntologyHeader.NAME))),
        synonymCd = ParserUtil.cleanupForIndexing(line(header(OntologyHeader.SYNONYM_CD))).trim,
        visualAttributes = ParserUtil.cleanupForIndexing(line(header(OntologyHeader.VISUALATTRIBUTES))).trim,
        basecodeOption = basecodeOption,
        metadataXmlOption = metadataXmlOption,
        tooltipOption = tooltipOption
      )

      if(path != pathNoTrim){
        Log.warn(s"WARNING: Trailing spaces found in $pathNoTrim")
      }

      rootInfo
    }
    else
    {
      val mAppliedPathOption = if(header.contains(OntologyHeader.APPLIED_PATH)){
        Option(line(header(OntologyHeader.APPLIED_PATH)).trim).filter(_.nonEmpty)
      }else{
        None
      }

      val pathNoTrim = s"${ParserUtil.cleanupForIndexing(line(header(OntologyHeader.FULL_NAME))).replaceAll("\\\\\\\\", "\\\\")}"
      val path= s"${ ParserUtil.cleanupForIndexing(line(header(OntologyHeader.FULL_NAME))).trim.replaceAll("\\\\\\\\", "\\\\")}"

      val termInfo = TermInfo(
        hlevel =  ParserUtil.cleanupForIndexing(line(header(OntologyHeader.HLEVEL))).toInt,
        path = path,
        name =  ParserUtil.cleanupForIndexing(line(header(OntologyHeader.NAME))),
        synonymCd =  ParserUtil.cleanupForIndexing(line(header(OntologyHeader.SYNONYM_CD))).trim,
        visualAttributes = ParserUtil.cleanupForIndexing(line(header(OntologyHeader.VISUALATTRIBUTES))).trim,
        basecodeOption = basecodeOption,
        metadataXmlOption = metadataXmlOption,
        tooltipOption = tooltipOption,
        mAppliedPathOption = mAppliedPathOption
      )

      if(path != pathNoTrim){
        Log.warn(s"WARNING: Trailing spaces found in $pathNoTrim")
      }
      
      termInfo
    }

  }
}


class OntologyVerifier extends BeanVerifier[OntologyRow] {

  def verifyBean(ontologyRow: OntologyRow): Boolean = {
    ontologyRow match {
      case termInfo: TermInfo =>

        if(termInfo.isModifier){
          return false
        }

      case _: RootInfo => ;
    }

    ontologyRow.hlevel >= 0 && !ontologyRow.isHidden && !ontologyRow.isSynonym
  }
}

case class CloseableCsvToBean(csvToBean: CsvToBean[OntologyRow], reader: Reader, fileName: String){
  def close(): Unit = {
    reader.close()
  }
}

object ParserUtil{
  def cleanupForIndexing(inStr: String): String = {
    // Add two leading 0's to unicodes with only 2 hexadecimal characters, such as &#xab; --> &#x00ab;
    // Some of the ontology has | characters, encoded as &#x7C;, and which need to be padded with the two 0s
    // and decoded before being displayed in the "medical concepts" list
    StringEscapeUtils
      .unescapeXml(
        inStr.replaceAll("&#x([0-9a-fA-F][0-9a-fA-F]);*", "&#x00$1;")
      )
  }
}

package net.shrine.ontology.indexer

import java.io.{File, PrintWriter}

import net.shrine.log.Log

import scala.collection.mutable
import scala.io.Source

/*
 * Use this to generate new files for testing
 * Extracts a subset of the ontology data from a larger ontology data file
 *
 * This program will add unicode characters to the lines that contain the string "Demographics"
 * The test code will thus be able to test whether unicode characters are handled correctly by the indexer
 */
object CreateTestFile {

  def createDataSubset(filename: String, codeCategoryFilename: String): Unit = {

    val writer = new PrintWriter(new File("act_ontology_subset.txt"))

   // LuceneIndexer.createPathMap(filename, codeCategoryFilename)
    Log.debug("Creating subset of ontology file")

    val codeCategoriesMap: Map[String, CodeAndConceptCategory] = CodeCategories.extractCodeCategoriesMappings(codeCategoryFilename, '\t')

    val rootPaths: List[String] = codeCategoriesMap.keys.toList.map(rootPath => {
      s"\\\\${rootPath.split("\\\\").drop(3).mkString("\\")}"
    })

    var pathCounts: mutable.HashMap[String, Integer] = new mutable.HashMap[String, Integer]

    rootPaths.foreach( key => {
        pathCounts += (key -> 0)
    })

    var totalLinesPrinted = 0
    val file = new File(filename)

    val bufferedSource = Source.fromFile(file.toPath.toString)
    for (lineFinal <- bufferedSource.getLines())
    {
      val result = lineFinal.split("\t", -1)

      //result.length == 24 will get the root level (ie. hleve = 1) ontology
      if ((result.length == 24 || result.length == 25) && !lineFinal.contains("C_HLEVEL") && !lineFinal.contains("C_TABLE_NAME"))
      {
        var fullName: String = ""
        var hlevel: Int = -1
        if(result.length == 24){
          fullName = result(4).trim.replaceAll("\\\\\\\\", "\\\\")
          hlevel = result(3).toInt
        }
        else if ( result(0).toInt != 1
          || lineFinal.startsWith("1\t\\Diagnoses\\")){

          fullName = result(1).trim.replaceAll("\\\\\\\\", "\\\\")
          hlevel = result(0).toInt
        }

        fullName = s"\\$fullName"
        rootPaths.filter(fullName.startsWith(_)).foreach(rootPath => {
          val currentCount = pathCounts(rootPath)

          if (hlevel < 5 || (fullName.startsWith("\\\\ACT\\Labs\\") && hlevel < 6)) {
            pathCounts(rootPath) = currentCount + 1
            writer.println(lineFinal)
            totalLinesPrinted = totalLinesPrinted + 1
          }
        })

        rootPaths.filter(fullName.equals(_)).foreach(_ => {
          // Add a unicode character so we can test the feature of the indexer where we decode such characters
          // Note: the unicode character that prompted this change is &#x7C;
          // Note also: the category_definition.txt file must have the same modification on Diagnoses,
          // i.e. \\Diagnoses|\	Diagnoses	diagnosis with the "|" character just after the first occurrence of "Diagnoses"
          writer.println(lineFinal.replaceAll("Demographics", "Demographics&#x7C;"))
          totalLinesPrinted = totalLinesPrinted + 1
        })
      }
    }

    Log.debug(s"Printed $totalLinesPrinted")
    bufferedSource.close()
    writer.close()
  }

  def main(args: Array[String]): Unit = {

    // Typical arguments: ACT_ontology_covid19.txt, category_definition.txt
    // ACT_ontology_covid19.txt is the complete ontology. This program will generate a sample set of it,
    // and to that sample set it will add unicode characters so we can reliably test how unicode are handled.
    // category_definition.txt must be the version without any unicode characters added
    if (args.length != 2) throw WrongNumberOfArguments("CreateTestFile requires a tab delimited text input file and a file defining the code categories")

    createDataSubset(args(0), args(1))
  }
}

package net.shrine.qep.metrics

import java.io.{File, FileWriter, PrintWriter}
import com.opencsv.CSVWriter
import net.shrine.log.Loggable

object CommonMaps extends Loggable {

  def writeCsv(file: File, writeToCsv:CSVWriter => _):Unit = {
    val csvWriter = new CSVWriter(new PrintWriter(new FileWriter(file), true)) //autoFlush = true seems to have no effect. Setting it anyway.
    try {
      writeToCsv(csvWriter)
    } finally {
      csvWriter.close()
    }
  }

}

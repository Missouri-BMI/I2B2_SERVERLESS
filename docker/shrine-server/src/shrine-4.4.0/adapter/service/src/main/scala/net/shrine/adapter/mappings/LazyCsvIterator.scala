package net.shrine.adapter.mappings

import java.io.Reader

import com.opencsv.{CSVParserBuilder, CSVReader, CSVReaderBuilder}

import scala.collection.AbstractIterator
import scala.util.control.NonFatal

class LazyCsvIterator(reader: Reader) extends AbstractIterator[(String, String)] {
  val csvReader: CSVReader = new CSVReaderBuilder(reader).withCSVParser(
    new CSVParserBuilder()
      .withSeparator(',')
      .withQuoteChar('"')
      .withEscapeChar('`')
      .build()
  ).build()

  private[this] var lastLine = csvReader.readNext()
  private[this] var lineNumber = 1
  override def hasNext: Boolean = lastLine != null

  override def next(): (String, String) = closeOnException {
    val result = parse(lastLine)
    advance()
    result
  }
  private[this] def advance(): Unit = {
    lastLine = csvReader.readNext()
    lineNumber += 1
    if (!hasNext) { csvReader.close() }
  }
  private[this] def closeOnException[T](f: => T): T = {
    try { f }
    catch { case NonFatal(e) => csvReader.close(); throw e }
  }
  private[this] def parse(line: Array[String]): (String, String) = {
    require(line.length == 2, s"Line $lineNumber: Expected two 'columns' in csv line, but got $line")
    val Array(shrine, i2b2) = line
    shrine -> i2b2
  }
}
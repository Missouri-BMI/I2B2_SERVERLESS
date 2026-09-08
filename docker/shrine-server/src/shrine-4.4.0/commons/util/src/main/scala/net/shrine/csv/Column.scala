package net.shrine.csv

/**
 * Generic Column for a CSV file
 */
case class Column[Row](name:String,cell:Row => String)

object Column {
  def rowForColumns[Row](columns:Array[Column[Row]])(nodeRow: Row): Array[String] = {
    columns.map(c => c.cell(nodeRow))
  }

  def header[Row](columns:Array[Column[Row]]): Array[String] = {
    columns.map(_.name)
  }
}

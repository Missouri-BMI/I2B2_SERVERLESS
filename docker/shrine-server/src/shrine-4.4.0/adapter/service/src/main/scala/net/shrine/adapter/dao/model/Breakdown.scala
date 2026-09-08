package net.shrine.adapter.dao.model

import net.shrine.protocol.i2b2.ResultOutputType

/**
 * @author clint
 * @date Oct 16, 2012
 */
final case class Breakdown(
  resultId: Int,
  localId: Long,
  resultType: ResultOutputType,
  data: Map[String, Long]) extends HasResultId

object Breakdown {
  def fromRows(resultType: ResultOutputType, localId: Long, rows: Seq[BreakdownResultRow]): Option[Breakdown] = {
    require(resultType.isBreakdown)

    rows match {
      case Nil => None
      case head +: _ => {
        val entries = rows.map(r => (r.dataKey -> r.obfuscatedValue))

        Some(Breakdown(head.resultId, localId, resultType, entries.toMap))
      }
    }
  }
}
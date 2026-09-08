package net.shrine.adapter.dao.model

/**
 * @author clint
 * @date Oct 16, 2012
 * 
 * NB: Can't be final, since Squeryl runs this class through cglib to make a synthetic subclass :(
 */
final case class BreakdownResultRow(
  id: Int,
  resultId: Int,
  dataKey: String,
  obfuscatedValue: Long) extends ResultRow(id, resultId)
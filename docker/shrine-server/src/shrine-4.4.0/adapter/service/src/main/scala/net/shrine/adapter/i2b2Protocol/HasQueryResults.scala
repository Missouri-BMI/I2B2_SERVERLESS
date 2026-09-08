package net.shrine.adapter.i2b2Protocol

import net.shrine.protocol.i2b2.QueryResult

/**
 * @author clint
 * @since Nov 30, 2012
 */
trait HasQueryResults extends ShrineResponse {
  def results: Seq[QueryResult]
}

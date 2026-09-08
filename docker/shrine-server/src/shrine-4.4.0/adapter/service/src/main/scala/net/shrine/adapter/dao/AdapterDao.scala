package net.shrine.adapter.dao

import java.sql.Timestamp
import java.util.Date
import net.shrine.protocol.i2b2.AuthenticationInfo
import net.shrine.adapter.dao.model.ShrineQueryResult
import net.shrine.protocol.i2b2.QueryResult
import net.shrine.protocol.i2b2.I2b2Result
import net.shrine.protocol.i2b2.ResultOutputType
import net.shrine.adapter.dao.model.ShrineQuery
import net.shrine.protocol.i2b2.query.I2b2QueryDefinition
import net.shrine.protocol.version.v2.Researcher

import scala.concurrent.duration.Duration
import scala.xml.NodeSeq

/**
 * @author clint
 * @since Oct 15, 2012
 */
trait AdapterDao {
  /**
   * @return the id column of the inserted row
   */
  def insertQuery(masterId: String,
                  networkId: Long,
                  authn: AuthenticationInfo,
                  queryDefinition: I2b2QueryDefinition,
                  hasBeenRun: Boolean): Int

  //Returns a Map of output types to Seqs of inserted ids, since the ERROR output type can be used for multiple query_result rows,
  //Say for a run query operation that results in multiple error responses from the CRC.
  def insertQueryResults(parentQueryId: Int, results: Seq[QueryResult]): Map[ResultOutputType, Seq[Int]]
  
  def insertCountResult(resultId: Int, originalCount: Long, obfuscatedCount: Long): Unit
  
  def insertBreakdownResults(parentResultIds: Map[ResultOutputType, Seq[Int]], originalBreakdowns: Map[ResultOutputType, I2b2Result], obfuscatedBreakdowns: Map[ResultOutputType, I2b2Result]): Unit

  def insertErrorResult(parentResultId: Int, errorMessage: String, codec:String, stampText:String, summary:String, digestDescription:String,detailsXml:NodeSeq): Unit
  
  def findQueriesByUserAndDomain(domain: String, username: String, howMany: Int): Seq[ShrineQuery]

  def findQueryByQueryId(networkQueryId: Long): Option[ShrineQuery]
  
  def findResultsFor(networkQueryId: Long): Option[ShrineQueryResult]

  def findQueuedQueryIds: Seq[QueryResultStatus]

  /**
    * @throws BotDetectedException if it detects a bot attack
    */
  def checkIfBot(authn:AuthenticationInfo, countTimeThresholds:Seq[(Long,Duration)]): Unit

  def renameQuery(networkQueryId: Long, newName: String): Unit
  
  def deleteQuery(networkQueryId: Long): Unit

  def findRecentQueries(howMany: Int): Seq[ShrineQuery]
  
  def storeResults(authn: AuthenticationInfo,
                   masterId: String,
                   networkQueryId: Long,
                   queryDefinition: I2b2QueryDefinition,
                   rawQueryResults: Seq[QueryResult],
                   obfuscatedQueryResults: Seq[QueryResult],
                   failedBreakdownTypes: Seq[ResultOutputType],
                   mergedBreakdowns: Map[ResultOutputType, I2b2Result],
                   obfuscatedBreakdowns: Map[ResultOutputType, I2b2Result]): Unit

  def inTransaction[T](f: => T): T = f
  
}

case class QueryResultStatus(
                              networkQueryId: Long,
                              timestamp: Timestamp,
                              username:String,
                              domain:String,
                              status:String
                            )

case class BotDetectedException(domain:String,
                                username:String,
                                detectedCount:Long,
                                sinceMs:Long,
                                limit:Long) extends Exception() {

  override def getMessage = s"$domain:$username has already run $detectedCount queries since ${new Date(sinceMs)}. The limit is $limit"
}

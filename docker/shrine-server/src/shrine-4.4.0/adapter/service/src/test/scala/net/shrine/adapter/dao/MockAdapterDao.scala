package net.shrine.adapter.dao

import net.shrine.adapter.dao.model.ShrineQuery
import net.shrine.adapter.dao.model.ShrineQueryResult
import net.shrine.protocol.i2b2.AuthenticationInfo
import net.shrine.protocol.i2b2.I2b2Result
import net.shrine.protocol.i2b2.QueryResult
import net.shrine.protocol.i2b2.ResultOutputType
import net.shrine.protocol.i2b2.query.I2b2QueryDefinition
import net.shrine.protocol.version.v2.Researcher

import scala.concurrent.duration.Duration
import scala.xml.NodeSeq

/**
 * @author clint
 * @since Oct 19, 2012
 */
object MockAdapterDao extends MockAdapterDao

trait MockAdapterDao extends AdapterDao {

  override def insertQuery(localMasterId: String, networkId: Long, authn: AuthenticationInfo, query: I2b2QueryDefinition, hasBeenRun: Boolean): Int = 0

  override def insertQueryResults(parentQueryId: Int, results: Seq[QueryResult]): Map[ResultOutputType, Seq[Int]] = Map.empty

  override def insertCountResult(resultId: Int, originalCount: Long, obfuscatedCount: Long): Unit = ()

  override def insertBreakdownResults(parentResultIds: Map[ResultOutputType, Seq[Int]], originalBreakdowns: Map[ResultOutputType, I2b2Result], obfuscatedBreakdowns: Map[ResultOutputType, I2b2Result]): Unit = ()

  override def insertErrorResult(parentResultId: Int, errorMessage: String, codec:String, stampText:String, summary:String, digestDescription:String,detailsXml:NodeSeq): Unit = ()

  override def findQueryByQueryId(networkQueryId: Long): Option[ShrineQuery] = None

  override def findQueriesByUserAndDomain(domain: String, username: String, howMany: Int): Seq[ShrineQuery] = Nil


  override def findResultsFor(networkQueryId: Long): Option[ShrineQueryResult] = None

  override def checkIfBot(authn:AuthenticationInfo, botTimeThresholds:Seq[(Long,Duration)]): Unit = {}


  override def renameQuery(networkQueryId: Long, newName: String): Unit = ()

  override def deleteQuery(networkQueryId: Long): Unit = ()

  override def findRecentQueries(howMany: Int): Seq[ShrineQuery] = Nil

  override def findQueuedQueryIds:Seq[QueryResultStatus] = Nil

  override def storeResults(authn: AuthenticationInfo,
                            masterId: String,
                            networkQueryId: Long,
                            queryDefinition: I2b2QueryDefinition,
                            rawQueryResults: Seq[QueryResult],
                            obfuscatedQueryResults: Seq[QueryResult],
                            failedBreakdownTypes: Seq[ResultOutputType],
                            mergedBreakdowns: Map[ResultOutputType, I2b2Result],
                            obfuscatedBreakdowns: Map[ResultOutputType, I2b2Result]): Unit = ()
}
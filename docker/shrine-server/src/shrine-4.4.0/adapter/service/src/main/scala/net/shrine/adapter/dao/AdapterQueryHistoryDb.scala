package net.shrine.adapter.dao

import java.sql.{SQLException, Timestamp}
import cats.effect.IO
import ch.qos.logback.classic.Level
import com.typesafe.config.Config

import javax.sql.DataSource
import javax.xml.datatype.XMLGregorianCalendar
import net.shrine.adapter.dao.model.{BreakdownResultRow, CountRow, ObfuscatedPair, QueryResultRow, ShrineError, ShrineQuery, ShrineQueryResult}
import net.shrine.adapter.i2b2Protocol
import net.shrine.protocol.i2b2.query.I2b2Expression
import net.shrine.adapter.i2b2Protocol.{RunQueryRequest, RunQueryResponse}
import net.shrine.audit.{LongQueryId, QueryName, Time, UserName}
import net.shrine.dao.DateHelpers
import net.shrine.http4s.catsio.ExecutionContexts
import net.shrine.protocol.i2b2.QueryResult.StatusType
import net.shrine.protocol.version.DateStamp
import net.shrine.protocol.i2b2.{AuthenticationInfo, I2b2Result, QueryResult, ResultOutputType}
import net.shrine.slick.TestableDataSourceCreator
import net.shrine.config.ConfigSource
import net.shrine.log.Loggable
import net.shrine.problem.{AbstractProblem, ProblemSources, XmlProblemDigest}
import net.shrine.protocol.i2b2.query.{I2b2Expression, I2b2QueryDefinition}
import net.shrine.util.Tries
import net.shrine.xml.{StringEnrichments, XmlDateHelper, XmlUtil}
import slick.jdbc.JdbcProfile

import scala.concurrent.{Await, ExecutionContextExecutorService}
import scala.concurrent.duration.{Duration, DurationInt}
import scala.language.postfixOps
import scala.util.Try
import scala.util.control.NonFatal
import scala.xml.NodeSeq


case class AdapterQueryHistoryDb(schemaDef:QueryHistorySchema, dataSource: DataSource) {
  import schemaDef._
  import jdbcProfile.api._
  import schemaDef.jdbcProfile.backend.DatabaseDef

  implicit val executionContext: ExecutionContextExecutorService = ExecutionContexts.databaseExecutionContext

  val database:DatabaseDef = Database.forDataSource(dataSource, None)

  def createTables(): Unit = schemaDef.createTables(database)

  def dropTables(): Unit = schemaDef.dropTables(database)

  private def runIO[R](dbio: DBIOAction[R, NoStream, _]): IO[R] = {
    IO.fromFuture(IO.blocking(database.run(dbio)))
  }

  private def runTransactionIO[R](dbio: DBIOAction[R, NoStream, _]): IO[R] = {
    IO.fromFuture(IO.blocking(database.run(dbio.transactionally)))
  }

  def insertQueryHistoryIO(localMasterId: String,
                           networkId: Long,
                           authenticationInfo: AuthenticationInfo,
                           queryDefinition: I2b2QueryDefinition,
                           hasBeenRun: Boolean): IO[Int] = {
    debug(s"insertQueryHistory $networkId")

    // Allow update of existing query history record - SHRINE2020-620
    val rowsChanged = runIO((allQueryHistory returning allQueryHistory.map(_.id)) += QueryHistory(
      localId = localMasterId,
      networkId = networkId,
      userName = authenticationInfo.username,
      userDomain = authenticationInfo.domain,
      queryName = queryDefinition.name,
      expression = queryDefinition.expr.map(_.toXmlString),
      dateCreated = DateStamp.now.underlying,
      hasBeenRun = hasBeenRun,
      queryXml = Option(queryDefinition.toXmlString)))
    rowsChanged
  }

  def insertQueryResultsIO(parentQueryId: Int, results: Seq[QueryResult]): IO[Map[ResultOutputType, Seq[Int]]] = {
    debug(s"insertQueryResults $parentQueryId")

    def execTime(result: QueryResult): Option[Long] = {
      //Uses the locale of the operating system
      def toMillis(xmlGc: XMLGregorianCalendar): Long = xmlGc.toGregorianCalendar.getTimeInMillis

      for {
        start <- result.startDate
        end <- result.endDate
      } yield toMillis(end) - toMillis(start)
    }

    def convertToQueryResultRowSlick(result: QueryResult): QueryResultRowSlick = {
      val resultType = result.resultType.getOrElse(ResultOutputType.ERROR)
      val elapsed = execTime(result)

      QueryResultRowSlick(
        result.resultId,
        parentQueryId,
        resultType,
        result.statusType,
        elapsed = elapsed,
        lastUpdated = XmlDateHelper.now
      )
    }

    for {
      ids <- runIO((allQueryResults returning allQueryResults.map(_.id)) ++= results.map(convertToQueryResultRowSlick))
    } yield {
      val typeToIdTuples = results.map(_.resultType.getOrElse(ResultOutputType.ERROR)).zip(ids)
      typeToIdTuples.groupBy { case (resultType, _) => resultType }.view.mapValues(_.map { case (_, count) => count }).toMap
    }
  }

  def insertCountResultIO(parentResultId: Int, obfuscatedCount: Long): IO[Unit] = {

    runIO(allCountResults += CountResult(resultId = parentResultId, obfuscatedValue = obfuscatedCount, dateCreated = XmlDateHelper.now.toGregorianCalendar().getTimeInMillis))
      .flatMap(_ => IO.unit)
  }

  def insertErrorResultIO(parentResultId: Int, errorMessage: String, codec:String, stampText:String, summary:String, digestDescription:String, detailsXml:NodeSeq): IO[Unit] = {

    runIO(allErrorResults += ErrorResultSlick(parentResultId, errorMessage, codec, stampText, summary, digestDescription, detailsXml.toString()))
      .flatMap(_ => IO.unit)
  }

  def insertBreakdownResultsIO(
                                parentResultIds: Map[ResultOutputType, Seq[Int]],
                                originalBreakdowns: Map[ResultOutputType, I2b2Result],
                                obfuscatedBreakdowns: Map[ResultOutputType, I2b2Result]): IO[Unit] = {
    def merge(original: I2b2Result, obfuscated: I2b2Result): Map[String, ObfuscatedPair] = {
      Map.empty ++ (for {
        (key, originalValue) <- original.data
        obfuscatedValue <- obfuscated.data.get(key)
      } yield (key, ObfuscatedPair(originalValue, obfuscatedValue)))
    }
    val breakdownResults: Iterable[BreakdownResult] = for {
      (resultType, Seq(resultId)) <- parentResultIds
      //filter out any breakdowns that aren't complete yet. No way to store that info
      if resultType.isBreakdown && originalBreakdowns.contains(resultType)
        originalBreakdown: I2b2Result = originalBreakdowns(resultType)
        obfuscatedBreakdown: I2b2Result = obfuscatedBreakdowns(resultType)
      (key, ObfuscatedPair(_, obfuscated)) <- merge(originalBreakdown, obfuscatedBreakdown)

    }  yield BreakdownResult(resultId, key, obfuscated)

    runIO(allBreakdownResults ++= breakdownResults).flatMap(_ => IO.unit)
  }

  def selectQueryHistoryByQueryIdIO(networkId: LongQueryId):IO[Option[QueryHistory]] = {
    runIO(
      for (
        queryHistory <- allQueryHistory.filter(_.networkId === networkId).result
      ) yield queryHistory
    ).map{
      case Seq(queryHist) => Some(queryHist)
      case Seq() => None
      case x => throw QueryHistoryDatabaseAssertException(s"Expected zero or one query history for network id $networkId, selected ${x.size} $x")
    }
  }

  def storeQueryIO (authenticationInfoToUse: AuthenticationInfo, request: RunQueryRequest): IO[RunQueryResponse] = {
    //Use dummy ids for what we would have received from the CRC
    val masterId: Long = -1L
    val queryInstanceId: Long = -1L
    val resultId: Long = -1L

    //TODO: is this right?? Or maybe it's project id?
    val groupId = authenticationInfoToUse.domain

    val invalidSetSize = -1L
    val now = XmlDateHelper.now
    val queryResult = QueryResult(
      resultId = resultId,
      instanceId = queryInstanceId,
      resultType = Some(ResultOutputType.PATIENT_COUNT_XML),
      setSize = invalidSetSize,
      startDate = Some(now),
      endDate = Some(now),
      description = Some("Query enqueued for later processing"),
      statusType = QueryResult.StatusType.Held,
      statusMessage = Some("Query enqueued for later processing")
    )

     for{
      insertedQueryId <- insertQueryHistoryIO(masterId.toString, request.networkQueryId, authenticationInfoToUse, request.queryDefinition, hasBeenRun = false)
      insertedQueryResultIds <- insertQueryResultsIO(insertedQueryId, Seq(queryResult))
      //NB: We need to insert dummy QueryResult and Count records so that calls to StoredQueries.retrieve() in
      //AbstractReadQueryResultAdapter, called when retrieving results for previously-queued-or-incomplete
      //queries, will work.
      countQueryResultId = insertedQueryResultIds(ResultOutputType.PATIENT_COUNT_XML).head
      _ <- insertCountResultIO(countQueryResultId, -1L)
    } yield i2b2Protocol.RunQueryResponse(masterId, XmlDateHelper.now, authenticationInfoToUse.username, groupId, request.queryDefinition, queryInstanceId, queryResult)
  }

  def storeResultsIO(
                      authenticationInfo: AuthenticationInfo,
                      masterId: String,
                      networkQueryId: Long,
                      queryDefinition: I2b2QueryDefinition,
                      rawQueryResults: Seq[QueryResult],
                      obfuscatedQueryResults: Seq[QueryResult],
                      failedBreakdownTypes: Seq[ResultOutputType],
                      mergedBreakdowns: Map[ResultOutputType, I2b2Result],
                      obfuscatedBreakdowns: Map[ResultOutputType, I2b2Result]): IO[Unit] = {
    debug(s"storeResultsIO $networkQueryId")
    for {
      _ <- deleteQueryHistoryIO(networkQueryId)
      insertedQueryId <- insertQueryHistoryIO(masterId, networkQueryId, authenticationInfo, queryDefinition, hasBeenRun = true)
      insertedResultTypeToQueryResultIds <- insertQueryResultsIO(insertedQueryId, rawQueryResults)
      _ <- storeCountResults(rawQueryResults, obfuscatedQueryResults, insertedResultTypeToQueryResultIds)
      _ <- storeErrorResults(rawQueryResults, insertedResultTypeToQueryResultIds)
      _ <- storeBreakdownFailures(failedBreakdownTypes.toSet, insertedResultTypeToQueryResultIds)
      _ <- insertBreakdownResultsIO(insertedResultTypeToQueryResultIds, mergedBreakdowns, obfuscatedBreakdowns)
    } yield debug(s"Finished storeResultsIO $networkQueryId")
  }

  private[adapter] def storeCountResults(raw: Seq[QueryResult], obfuscated: Seq[QueryResult], insertedIds: Map[ResultOutputType, Seq[Int]]): IO[Unit] = {

    val notErrors = raw.filter(!_.isError)

    val obfuscatedNotErrors = obfuscated.filter(!_.isError)

    if(notErrors.size > 1) {
      warn(s"Got ${notErrors.size} raw (hopefully-)count results; more than 1 is unusual.")
    }

    if(obfuscatedNotErrors.size > 1) {
      warn(s"Got ${obfuscatedNotErrors.size} obfuscated (hopefully-)count results; more than 1 is unusual.")
    }

    if(notErrors.size != obfuscatedNotErrors.size) {
      warn(s"Got ${notErrors.size} raw and ${obfuscatedNotErrors.size} obfuscated (hopefully-)count results; that these numbers are different is unusual.")
    }

    import ResultOutputType.PATIENT_COUNT_XML

    def isCount(qr: QueryResult): Boolean = qr.resultType.contains(PATIENT_COUNT_XML)

    //NB: Take the count/setSize from the FIRST PATIENT_COUNT_XML QueryResult,
    //though the same count should be there for all of them, if there are more than one
    val result: Option[IO[Unit]] = for {
      Seq(insertedCountQueryResultId) <- insertedIds.get(PATIENT_COUNT_XML)
      notError <- notErrors.find(isCount) //NB: Find a count result, just to be sure
      obfuscatedNotError <- obfuscatedNotErrors.find(isCount) //NB: Find a count result, just to be sure
    } yield insertCountResultIO(insertedCountQueryResultId, obfuscatedNotError.setSize)

    result.getOrElse(IO.unit)
  }

  private[adapter] def storeErrorResults(results: Seq[QueryResult], insertedIds: Map[ResultOutputType, Seq[Int]]): IO[Unit] = {

    import cats.implicits._

    val errors = results.filter(_.isError)

    val insertedErrorResultIds = insertedIds.getOrElse(ResultOutputType.ERROR,Nil)

    val insertedIdsToErrors = insertedErrorResultIds zip errors

    val result: Seq[cats.effect.IO[Unit]] = for {
      (insertedErrorResultId, errorQueryResult) <- insertedIdsToErrors
    } yield {
      val pd = errorQueryResult.problem.get //it's an error so it will have a problem digest

      insertErrorResultIO(
        insertedErrorResultId,
        errorQueryResult.statusMessage.getOrElse("Unknown failure"),
        pd.codec,
        pd.stampText,
        pd.summary,
        pd.description,
        pd.detailsXml
      )
    }

    result.toList.sequence.flatMap(_ => IO.unit)
  }

  private[adapter] def storeBreakdownFailures(failedBreakdownTypes: Set[ResultOutputType], insertedIds: Map[ResultOutputType, Seq[Int]]): IO[Unit] = {
    import cats.implicits._

    val insertedIdsForFailedBreakdownTypes = insertedIds.view.filterKeys(failedBreakdownTypes.contains)

    val result = for {
      (failedBreakdownType, Seq(resultId)) <- insertedIdsForFailedBreakdownTypes
    } yield {
      //todo propagate backwards to the breakdown failure to create the correct problem
      object BreakdownFailure extends AbstractProblem(ProblemSources.Adapter) {
        override val summary: String = "Couldn't retrieve result breakdown"
        override val description:String = s"Couldn't retrieve result breakdown of type '$failedBreakdownType'"
        override def logLevel: Level = Level.WARN
      }

      val pd = XmlProblemDigest.create(BreakdownFailure)

      insertErrorResultIO(
        resultId,
        s"Couldn't retrieve breakdown of type '$failedBreakdownType'",
        pd.codec,
        pd.stampText,
        pd.summary,
        pd.description,
        pd.detailsXml
      )
    }

    result.toList.sequence.flatMap(_ => IO.unit)
  }

  private def countQueriesForUserSince(domain:String, username:UserName, sinceMs:Long): IO[Long] = {
    runIO(
      for (
        queryHistory <- allQueryHistory.filter(r => r.userDomain === domain && r.userName === username && r.dateCreated >= new Timestamp(sinceMs)).length.result
      ) yield queryHistory
    )
  }

  def checkIfBotIO(authenticationInfo:AuthenticationInfo, botTimeThresholds:Seq[(Long,Duration)]): IO[Unit] = {
    import cats.implicits._
    val now = System.currentTimeMillis()

    botTimeThresholds.map{countDuration => {
      val sinceMs: Long = now - countDuration._2.toMillis
      val queriesSinceIO: IO[Long] = countQueriesForUserSince(authenticationInfo.domain, authenticationInfo.username, sinceMs)
      queriesSinceIO.map(queriesSince => {
        if (queriesSince >= countDuration._1) throw BotDetectedException(domain = authenticationInfo.domain,
          username = authenticationInfo.username,
          detectedCount = queriesSince,
          sinceMs = sinceMs,
          limit = countDuration._1)
      }
      )
    }}.toList.sequence.flatMap(_ => IO.unit)
  }

  def selectAllQueryResultsByQueryId(queryId: Int):IO[Seq[QueryResultRowSlick]] = {
    runIO(
      for (
        queryResults <- safeQueryResults.filter(_.queryId === queryId).result
      ) yield queryResults
    )
  }

  private lazy val askTheCrcStates = StatusType.values.filter(_.crcPromisedToFinishAfterReply)
  def selectQueuedQueries(): IO[Seq[QueryResultStatus]] = {

    runIO {
      for (
        queryResults <- allQueryHistory.join(safeQueryResults).on(_.id === _.queryId).filter(_._2.status.inSet(askTheCrcStates))
          .map { case (queryRow, resultRow) =>
            (queryRow.networkId, resultRow.lastUpdated, queryRow.userName, queryRow.userDomain, resultRow.status)
          }.result
      ) yield queryResults.map(row => QueryResultStatus(row._1, DateHelpers.toTimestamp(row._2.getTime), row._3, row._4, row._5.name))
    }
  }

  //todo networkId is never used. That seems bad. This could be building a really big join table
  private def selectBreakdownResultsByType(networkId: Long): IO[Map[ResultOutputType, Seq[BreakdownResultRow]]] = {
    runIO {
      for {
        bk <- safeQueryResults.join(allBreakdownResults).on(_.id === _.resultId).result
      } yield {
        bk.groupBy { case (outputType, _) => outputType.toQueryResultRow.resultType }.view.mapValues(_.map { case (_, row) => row.toBreakdownResultRow }).toMap
      }
    }
  }

  private def selectQueryResultsByQueryId(networkId: Long):IO[Seq[QueryResultRowSlick]] = {
    runIO(
      for {
        queryHistoryIdOption <- allQueryHistory.filter(_.networkId === networkId).map(_.id).take(1).result.headOption
        queryResults <- queryHistoryIdOption.map(queryHistoryId => safeQueryResults.filter(_.queryId === queryHistoryId).result).get
      } yield queryResults
    )
  }

  def selectCountResultsByQueryIdIO(networkId: Long):IO[Option[CountResult]] = {
    runIO(
      for {
        queryHistoryIdOption <- allQueryHistory.filter(_.networkId === networkId).map(_.id).take(1).result.headOption
        queryResultIdOption <- queryHistoryIdOption.map(queryHistoryId => safeQueryResults.filter(_.queryId === queryHistoryId).map(_.id).take(1).result.headOption).getOrElse( DBIO.successful(None))
        countResults <- queryResultIdOption.map(queryResultId => allCountResults.filter(_.resultId === queryResultId).take(1).result.headOption).getOrElse( DBIO.successful(None))
      } yield countResults
    )
  }

  def selectErrorResultsByQueryIdIO(networkId: Long):IO[Seq[ErrorResultSlick]] = {
    runIO(
      for {
        queryHistoryIdOption <- allQueryHistory.filter(_.networkId === networkId).map(_.id).take(1).result.headOption
        queryResultIds <- queryHistoryIdOption.map(queryHistoryId => safeQueryResults.filter(_.queryId === queryHistoryId).map(_.id).result).get
        errorResults <- allErrorResults.filter(_.resultId.inSet(queryResultIds)).result
      } yield errorResults
    )
  }

  def selectBreakdownResultsByQueryIdIO(networkId: Long):IO[Seq[BreakdownResult]] = {
    runIO {
      for {
        queryHistoryIds <- allQueryHistory.filter(_.networkId === networkId).map(_.id).result
        queryResultIds <- safeQueryResults.filter(_.queryId.inSet(queryHistoryIds)).map(_.id).result
        breakdownResults <- allBreakdownResults.filter(_.resultId.inSet(queryResultIds)).result
      } yield breakdownResults

    }
  }

  def findResultsForIO(networkQueryId: Long): IO[Option[ShrineQueryResult]] = {
    for {
      breakdownRowsByType <- selectBreakdownResultsByType(networkQueryId)
      queryRowOption <- selectQueryHistoryByQueryIdIO(networkQueryId).map(p => p.map(_.toShrineQuery))
      countRowOption <- selectCountResultsByQueryIdIO(networkQueryId).map(p => p.map(_.toCountRow))
      queryResultRows <- selectQueryResultsByQueryId(networkQueryId).map(p => p.map(_.toQueryResultRow))
      errorResultRows <- selectErrorResultsByQueryIdIO(networkQueryId).map(p => p.map(_.toShrineError))
    } yield {
      ShrineQueryResult.fromRows(queryRowOption.get, queryResultRows, countRowOption, breakdownRowsByType, errorResultRows)
    }
  }

  def renameQueryIO(networkId: Long, newQueryName: String): IO[Unit] = {
    runIO (allQueryHistory.filter(_.networkId === networkId).map(b => b.queryName).update(newQueryName)).flatMap(_ => IO.unit)
  }

  private def deleteQueryHistoryIO(networkQueryId: Long): IO[Int] = {
    runIO(allQueryHistory.filter(_.networkId === networkQueryId).delete)
  }
}

object AdapterQueryHistoryDb {

  val dataSource:DataSource = TestableDataSourceCreator.dataSource(QueryHistorySchema.config)

  val db: AdapterQueryHistoryDb = AdapterQueryHistoryDb(QueryHistorySchema.schema,dataSource)

  val createTablesOnStart: Boolean = QueryHistorySchema.config.getBoolean("createTablesOnStart")
  if(createTablesOnStart) AdapterQueryHistoryDb.db.createTables()
}

/**
  * Separate class to support schema generation without actually connecting to the database.
  *
  * @param jdbcProfile Database profile to use for the schema
  */
case class QueryHistorySchema(jdbcProfile: JdbcProfile) extends Loggable {
  import jdbcProfile.api._

  def ddlForAllTables: jdbcProfile.DDL = {
    allQueryHistory.schema ++ allQueryResults.schema ++ allCountResults.schema ++ allErrorResults.schema ++ allBreakdownResults.schema
  }

  //TODO: Fix so that the schema can be obtained using to using the REPL
  //println(QueryHistorySchema.schema.ddlForAllTables.createIfNotExistsStatements.mkString(";\n"))

  def createTables(database:Database): Unit = {
    try {
      val future = database.run(ddlForAllTables.createIfNotExists)
      Await.result(future,10 seconds)
    } catch {
      //I'd prefer to check and create schema only if absent. No way to do that with Oracle.
      case x:SQLException => info("Caught exception while creating tables. Recover by assuming the tables already exist.",x)
    }
  }

  def dropTables(database:Database): Unit = {
    val future = database.run(ddlForAllTables.drop)
    //Really wait forever for the cleanup
    Await.result(future,Duration.Inf)
  }

  class QueryHistories(tag:Tag) extends Table[QueryHistory](tag,"SHRINE_QUERY") {
    def id = column[Int]("ID", O.AutoInc)
    def localId = column[String]("LOCAL_ID")
    def networkId = column[LongQueryId]("NETWORK_ID", O.PrimaryKey)
    def userName = column[UserName]("USERNAME")
    def userDomain = column[String]("DOMAIN")
    def queryName = column[QueryName]("QUERY_NAME")
    def expression = column[Option[String]]("QUERY_EXPRESSION")
    def dateCreated = column[Timestamp]("DATE_CREATED")
    def hasBeenRun = column[Boolean]("HAS_BEEN_RUN")
    private def queryXml = column[Option[String]]("QUERY_XML")

    def uId = index("unique_id", id, unique = true)

    override def * = (id, localId, networkId, userName, userDomain, queryName, expression, dateCreated, hasBeenRun, queryXml) <>(create, extract)

    def create(t: (Int, String, LongQueryId, UserName, String, QueryName, Option[String], Timestamp, Boolean,Option[String])): QueryHistory = QueryHistory(t._1, t._2, t._3, t._4, t._5, t._6, t._7, t._8.getTime, t._9, t._10)

    def extract(qh: QueryHistory): Option[(Int, String, LongQueryId, UserName, String, QueryName, Option[String], Timestamp, Boolean, Option[String])] =
      Some((qh.id, qh.localId, qh.networkId,qh.userName, qh.userDomain, qh.queryName, qh.expression, new Timestamp(qh.dateCreated), qh.hasBeenRun, qh.queryXml))
  }

  val allQueryHistory = TableQuery[QueryHistories]

  implicit val resultOutputType: BaseColumnType[ResultOutputType] = MappedColumnType.base[ResultOutputType, String](
    a => a.name,
    d => ResultOutputType.valueOf(d).
                          getOrElse(ResultOutputType.tryValueOf(ResultOutputType.breakdownTypes)(d).get)
  )

  implicit val queryResultStatusType: BaseColumnType[QueryResult.StatusType] = MappedColumnType.base[QueryResult.StatusType, String](
    a => a.name,
    d => QueryResult.StatusType.valueOf(d).getOrElse(throw new IllegalStateException(s"Unknown i2b2 status type: '$d'"))
  )

  class QueryResults(tag:Tag) extends Table[QueryResultRowSlick](tag,"ADAPTER_QUERY_RESULT") {
    def id = column[Int]("ID",  O.PrimaryKey, O.AutoInc)
    def localId = column[Long]("LOCAL_ID")
    def queryId = column[Int]("QUERY_ID")
    def resultType = column[ResultOutputType]("TYPE")
    def status = column[QueryResult.StatusType]("STATUS")
    def timeElapsed = column[Option[Long]]("TIME_ELAPSED")
    def lastUpdated = column[Timestamp]("LAST_UPDATED")

    def queryIdFk = foreignKey("FK_QUERY_RESULT_QUERY_ID", queryId, allQueryHistory)(_.id, onDelete=ForeignKeyAction.Cascade)

    override def * = (id, localId, queryId, resultType, status, timeElapsed, lastUpdated) <>(create, extract)

    def create(t: (Int, Long, Int, ResultOutputType, QueryResult.StatusType, Option[Long], Timestamp)): QueryResultRowSlick = QueryResultRowSlick(t._1, t._2, t._3, t._4, t._5, t._6, t._7.getTime)

    def extract(q: QueryResultRowSlick): Option[(Int, Long, Int, ResultOutputType, QueryResult.StatusType, Option[Long], Timestamp)] =
      Some((q.id, q.localId, q.queryId,q.resultType, q.status, q.timeElapsed, new Timestamp(q.lastUpdated)))
  }

  val allQueryResults = TableQuery[QueryResults]

  /**
   * Exclude query results that may have bad values in the type column. If someone changes shrine's breakdown
   * configuration so that existing cell values are no longer supported then those old types remain; this database
   * code will not be able to find the right ResultOutputType. Further, mysql's enums do not error on write. Instead a
   * mysql enum will write an empty string. See SHRINE2020-840 for details.
   */
  val safeQueryResults = allQueryResults.filter(_.resultType.inSet(ResultOutputType.allValidTypes))

  class CountResults(tag:Tag) extends Table[CountResult](tag,"COUNT_RESULT") {
    def id = column[Int]("ID",  O.PrimaryKey, O.AutoInc)
    def resultId = column[Int]("RESULT_ID")
    def obfuscatedCount = column[Long]("OBFUSCATED_COUNT")
    def dateCreated = column[Timestamp]("DATE_CREATED")

    def queryIdFk = foreignKey("FK_COUNT_RESULT_QUERY_RESULT_ID", resultId, allQueryResults)(_.id, onDelete=ForeignKeyAction.Cascade)

    override def * = (id, resultId, obfuscatedCount, dateCreated) <>(create, extract)

    def create(t: (Int, Int, Long, Timestamp)): CountResult = CountResult(t._1, t._2, t._3, t._4.getTime)

    def extract(c: CountResult): Option[(Int, Int, Long, Timestamp)] =
      Some((c.id, c.resultId, c.obfuscatedValue ,  new Timestamp(c.dateCreated)))
  }

  val allCountResults = TableQuery[CountResults]

  class ErrorResults(tag:Tag) extends Table[ErrorResultSlick](tag,"ERROR_RESULT") {
    def id = column[Int]("ID",  O.PrimaryKey, O.AutoInc)
    def resultId = column[Int]("RESULT_ID")
    def message = column[String]("MESSAGE")
    def codec = column[String]("CODEC")
    def stamp = column[String]("STAMP")
    def summary = column[String]("SUMMARY")
    private def problemDescription = column[String]("PROBLEM_DESCRIPTION")
    private def details = column[String]("DETAILS")

    def queryIdFk = foreignKey("FK_ERROR_RESULT_QUERY_RESULT_ID", resultId, allQueryResults)(_.id, onDelete=ForeignKeyAction.Cascade)

    def * = (id, resultId, message, codec, stamp, summary, problemDescription, details) <> (ErrorResultSlick.tupled, ErrorResultSlick.unapply)
  }

  val allErrorResults = TableQuery[ErrorResults]

  class BreakdownResults(tag:Tag) extends Table[BreakdownResult](tag,"BREAKDOWN_RESULT") {
    def id = column[Int]("ID",  O.PrimaryKey, O.AutoInc)
    def resultId = column[Int]("RESULT_ID")
    def dataKey = column[String]("DATA_KEY")
    def obfuscatedValue = column[Long]("OBFUSCATED_VALUE")

    def idFk = foreignKey("FK_BREAKDOWN_RESULT_QUERY_RESULT_ID", resultId, allQueryResults)(_.id, onDelete=ForeignKeyAction.Cascade)

    def * = (id, resultId, dataKey, obfuscatedValue) <> (BreakdownResult.tupled, BreakdownResult.unapply)
  }

  val allBreakdownResults = TableQuery[BreakdownResults]
}

object QueryHistorySchema {

  val config:Config = ConfigSource.config.getConfig("shrine.adapter.query.database")

  val slickProfile:JdbcProfile = TestableDataSourceCreator.slickDriver(config)

  val moreBreakdowns: Set[ResultOutputType] = ResultOutputType.breakdownTypes

  val schema: QueryHistorySchema = QueryHistorySchema(slickProfile)
}

case class QueryHistory(id: Int = 0,
                        localId: String,
                        networkId: LongQueryId,
                        userName: UserName,
                        userDomain: String,
                        queryName: QueryName,
                        expression: Option[String],
                        dateCreated: Long,
                        hasBeenRun: Boolean,
                        queryXml: Option[String]) extends Loggable {
  final def toShrineQuery: ShrineQuery = {

    val queryDefinition: I2b2QueryDefinition = {
      import StringEnrichments._

      val queryXmlAttempt: Try[NodeSeq] = Tries.toTry(queryXml.map(_.tryToXml)) {
        new Exception(s"Couldn't parse '$queryXml' as XML")
      }.flatten

      queryXmlAttempt.flatMap(I2b2QueryDefinition.fromXml).recover {
        case NonFatal(e) =>
          debug(s"queryXml of '$queryXml' unusable, attempting to use old queryExpr field instead.", e)

          expression match {
            case Some(exprXml) => I2b2QueryDefinition(queryName, I2b2Expression.fromXml(exprXml).get)
            case None => throw new Exception(s"No query expression xml to parse",e)
          }
      }.get.copy(unTrimmedName = queryName)
    }

    ShrineQuery(id, localId, networkId, queryDefinition.name, userName, userDomain, DateHelpers.toXmlGc(dateCreated), queryDefinition)
  }
}

case class QueryResultRowSlick(id: Int = 0,
                       localId: Long,
                       queryId: Int,
                       resultType: ResultOutputType,
                       status: QueryResult.StatusType,
                       timeElapsed: Option[Long],
                       lastUpdated: Time){
  def toQueryResultRow: QueryResultRow =
    QueryResultRow(id, localId, queryId, resultType, status, timeElapsed, DateHelpers.toXmlGc(lastUpdated))
}

object QueryResultRowSlick extends ((Int,Long,Int,ResultOutputType, QueryResult.StatusType, Option[Long], Time) => QueryResultRowSlick){

  def apply(localId: Long,
            queryId: Int,
            resultType: ResultOutputType,
            status: QueryResult.StatusType,
            elapsed: Option[Long],
            lastUpdated: XMLGregorianCalendar): QueryResultRowSlick =
  {
    QueryResultRowSlick(
      localId = localId,
      queryId = queryId,
      resultType = resultType,
      status = status,
      timeElapsed = elapsed,
      lastUpdated = DateHelpers.toTimestamp(lastUpdated).getTime
    )
  }
}


case class CountResult(id: Int = 0,
                       resultId: Int,
                       obfuscatedValue: Long,
                       dateCreated: Time)
{
  def toCountRow: CountRow = CountRow(id, resultId, obfuscatedValue, DateHelpers.toXmlGc(dateCreated))
}

object CountResult extends ((Int,Int,Long,Time) => CountResult){
  def apply(resultId: Int,
            obfuscatedValue: Long,
            createdDate: Time): CountResult = {
    CountResult(
      resultId = resultId,
      obfuscatedValue = obfuscatedValue,
      dateCreated = createdDate
    )
  }
}

case class ErrorResultSlick(id: Int = 0,
                       resultId: Int,
                       message:String,
                       codec:String,
                       stamp :String,
                       summary:String,
                       problemDescription :String,
                       details:String){
  def toShrineError: ShrineError = {
    val detailsXml = if(""!=details) XmlUtil.loadString(details)
    else <details/>

    ShrineError(id, resultId, message, codec, stamp, summary, problemDescription, detailsXml)
  }
}

object ErrorResultSlick extends ((Int,Int,String,String,String,String,String,String) => ErrorResultSlick) {
  def apply(queryResultId: Int,
            message: String,
            codec: String,
            stamp: String,
            summary: String,
            problemDescription: String,
            details: String): ErrorResultSlick = {
    ErrorResultSlick(
      resultId = queryResultId,
      message = message,
      codec = codec,
      stamp = stamp,
      summary = summary,
      problemDescription = problemDescription,
      details = details
    )
  }
}

case class QueryHistoryDatabaseAssertException(message:String) extends Exception(message)


case class BreakdownResult(id: Int = 0,
                            resultId: Int,
                            dataKey: String,
                            obfuscatedCount: Long){

  def toBreakdownResultRow: BreakdownResultRow = BreakdownResultRow(id, resultId, dataKey, obfuscatedCount)
}

object BreakdownResult extends ((Int,Int,String,Long) => BreakdownResult) {
  def apply(
             queryResultId: Int,
            dataKey: String,
            obfuscatedCount: Long): BreakdownResult = {
    BreakdownResult(
      resultId = queryResultId,
      dataKey = dataKey,
      obfuscatedCount = obfuscatedCount
    )
  }
}
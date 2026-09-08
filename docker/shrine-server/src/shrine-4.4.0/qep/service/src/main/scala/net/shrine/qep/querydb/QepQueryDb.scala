package net.shrine.qep.querydb

import java.sql.SQLException
import java.util.concurrent.TimeoutException
import cats.effect.IO
import cats.effect.unsafe.implicits.global
import com.typesafe.config.Config

import javax.sql.DataSource
import net.shrine.audit.{AdapterName, Checksum, LongQueryId, QueryName, Time, UserName}
import net.shrine.authentication.pm.User
import net.shrine.config.{ConfigExtensions, ConfigSource}
import net.shrine.http4s.catsio.{ExecutionContexts, SimpleAsyncExecutor}
import net.shrine.log.Log
import net.shrine.problem.XmlProblemDigest
import net.shrine.protocol.i2b2.ResultOutputType.I2b2Options
import net.shrine.protocol.version.v2.{Breakdowns, CountResult, ErrorResult, ObfuscatingParameters, QueryError, QueryStatus, Researcher, Result, ResultMetadata, ResultProgress, ResultStatus, UpdateQueryAtQep, UpdateQueryAtQepWithError, UpdateQueryReadyForAdapters, Query => V2Query}
import net.shrine.protocol.version.{DateStamp, JsonText}
import net.shrine.protocol.i2b2.{DefaultBreakdownResultOutputTypes, QueryResult, ResultOutputType}
import net.shrine.slick.{CouldNotRunDbIoActionException, TestableDataSourceCreator, TimeoutInDbIoActionException}
import net.shrine.util.Sort
import net.shrine.xml.XmlUtil
import slick.ast.BaseTypedType
import slick.jdbc.{JdbcProfile, JdbcType}

import scala.collection.immutable.ListMap
import scala.concurrent.duration.{Duration, DurationInt}
import scala.concurrent.{Await, ExecutionContextExecutorService, Future}
import scala.language.postfixOps
import scala.util.control.NonFatal

/**
  * DB code for the QEP's query instances and query results.
  *
  * @author david
  * @since 1/19/16
  */
case class QepQueryDb(schemaDef:QepQuerySchema,dataSource: DataSource,timeout:Duration) {
  import schemaDef._
  import jdbcProfile.api._
  import schemaDef.jdbcProfile.backend.DatabaseDef

  implicit val executionContext: ExecutionContextExecutorService = ExecutionContexts.databaseExecutionContext

  val database:DatabaseDef = Database.forDataSource(
    ds = dataSource,
    maxConnections = None,
    executor = SimpleAsyncExecutor(executionContext)
  )

  def createTables(): Unit = schemaDef.createTables(database)

  def dropTables(): Unit = schemaDef.dropTables(database)

  private def run[R](dbio: DBIOAction[R, NoStream, _]): Future[R] = {
    database.run(dbio)
  }

  private def runBlocking[R](dbio: DBIOAction[R, NoStream, _], timeout: Duration = timeout): R = {
    try {
      Await.result(this.run(dbio), timeout)
    } catch {
      case tx:TimeoutException => throw TimeoutInDbIoActionException(dataSource, timeout, tx)
      case NonFatal(x) => throw CouldNotRunDbIoActionException(dataSource, x)
    }
  }

  private def runIO[R](dbio: DBIOAction[R, NoStream, _]): IO[R] = {
    IO.fromFuture(IO.blocking(run(dbio)))
  }

  private def runTransactionIO[R](dbio: DBIOAction[R, NoStream, _]): IO[R] = {
    IO.fromFuture(IO.blocking(database.run(dbio.transactionally)))
  }

  //used by AbstractQepService's doRunQuery, which is what the "i2b2" h4s service wraps around to run queries
  def insertQueryIO(query:V2Query, researcher:Researcher):IO[Int] = {
    Log.debug(s"insertQepQuery $query")

    for {
      rowsChanged <- runIO(allQepQueryQuery += QepQuery(query,researcher))
      _ <- QepQueryDbChangeNotifier.triggerChangesFor(query.id.underlying)
    } yield rowsChanged
  }


  //used by Receiver
  def updateQepQueryIO(sqsaq: UpdateQueryAtQep): IO[Unit] = {
    Log.info(s"updateQepQuery $sqsaq")

    val problems = sqsaq match {
      case errorUpdate: UpdateQueryAtQepWithError => Seq(QueryProblemDigestRow(errorUpdate))
      case _ => Seq.empty
    }

    val resultRows: Seq[QueryResultRow] = sqsaq match {
      case hasResultProgress: UpdateQueryReadyForAdapters => hasResultProgress.resultProgresses.map(QueryResultRow(_))
      case _ => Seq.empty
    }

    runTransactionIO {
      for{
        maybeOldQepQuery <- mostRecentVisibleQepQueries.filter(_.networkId === sqsaq.queryId.underlying).take(1).result
        _ <- allQepQueryQuery += {
          maybeOldQepQuery match {
            case Seq(oldQepQuery) => oldQepQuery.updateWith(sqsaq)
            case Seq() => throw NoQueryForQueryIdException(sqsaq.queryId.underlying)
            case _ => throw new IllegalStateException(s"${maybeOldQepQuery.size} queries found for ${sqsaq.queryId.underlying}")
            }
          }
        _ <- allQueryProblemDigestRows ++= problems
        _ <- allQueryResultRows ++= resultRows
      } yield ()
    }.flatMap(_ => QepQueryDbChangeNotifier.triggerChangesFor(sqsaq.queryId.underlying)).handleErrorWith{
      case nqfix:NoQueryForQueryIdException => IO(s"${Log.info(nqfix.getMessage)} . Assuming this is fine for a network health query.")
    }
  }

  //todo only used in test code - replace in SHRINE2020-1097
  def insertQepQuery(qepQuery: QepQuery):Unit = {
    runBlocking(allQepQueryQuery += qepQuery)
    QepQueryDbChangeNotifier.triggerChangesFor(qepQuery.networkId).unsafeRunSync()
  }

  val allQepQueryQuery = TableQuery[QepQueries]

  //todo only used in tests
  def selectAllQepQueries:Seq[QepQuery] = {
    runBlocking(mostRecentVisibleQepQueries.result)
  }

  private def latestQueryResultStatusRows(networkQueryId:LongQueryId): DBIOAction[Seq[QueryResultRow], NoStream, Effect.Read] = {
    allQueryResultRows.filter(_.networkQueryId === networkQueryId).result.map { rows: Seq[QueryResultRow] =>
      rows.groupBy(_.resultId).filter(_._2.nonEmpty).map(grouped => grouped._2.maxBy(k => (ResultStatus.namesToStatuses(k.status).stage, k.changeDate))).toSeq
    }
  }

  //todo find uses and see if there's a way to use the V2 query directly for the query parts
  //todo started
  def selectResultsRowIO(queryId: LongQueryId, user:User, sortSiteBy: String): IO[QueryAndResults] = {

    runTransactionIO(
      for {
        query: QepQuery <- mostRecentVisibleQepQueries.filter(_.networkId === queryId).take(1).result.head
        maybeQueryProblem: Option[QueryProblemDigestRow] <- mostRecentQueryProblemDigestRows.filter(_.networkId === queryId).take(1).result.headOption
        queryResults:Seq[QueryResultRow] <- latestQueryResultStatusRows(queryId)
        breakdowns: Seq[QepQueryBreakdownResultsRow] <- mostRecentBreakdownResultsRows.filter(_.networkQueryId === queryId).result
        problems: Seq[QepProblemDigestRow] <- mostRecentProblemDigestRows.filter(_.networkQueryId === queryId).result
      } yield {

        if (user.sameUserAs(query.userName, query.userDomain)) {
          val breakdownTypeToResults: Map[ResultOutputType, Seq[QepQueryBreakdownResultsRow]] = breakdowns.distinct.groupBy(_.resultType)

          val sortQepQueryBreakdownResultsRowAlphaNumerically =
            Sort.orderingFor[QepQueryBreakdownResultsRow, String](_.dataKey, Sort.compareAlphaNumerically)

          val sortResultOutputTypeTupleAlphaNumerically =
            Sort.orderingFor[(ResultOutputType, _), String](_._1.name, Sort.compareAlphaNumerically)

          val sortedBreakdowns: Map[ResultOutputType, Seq[QepQueryBreakdownResultsRow]] = ListMap(
            breakdownTypeToResults
              .toList
              .map(tup => (tup._1, tup._2.sorted(sortQepQueryBreakdownResultsRowAlphaNumerically)))  // sort each value
              .sorted(sortResultOutputTypeTupleAlphaNumerically) : _*                                // sort map by keys
          )

          val sortedQueryResults: Seq[QueryResultRow] = QueryResultRow.sortResults(queryResults.distinct, sortSiteBy)

          def seqOfOneProblemRowToProblemDigest(problemSeq:Seq[QepProblemDigestRow]):XmlProblemDigest = {
            if(problemSeq.size == 1) problemSeq.head.toProblemDigest
            else throw new IllegalStateException(s"problemSeq size was not 1. $problemSeq")
          }

          val adapterNodesToProblemDigests: Map[String, XmlProblemDigest] = problems.
            groupBy(_.adapterNode).
            map(nodeToProblem => nodeToProblem._1 -> seqOfOneProblemRowToProblemDigest(nodeToProblem._2.distinct) )

          val fullResult: Seq[FullQueryResult] = sortedQueryResults.map(r => FullQueryResult(
            r,
            sortedBreakdowns,
            adapterNodesToProblemDigests.get(r.adapterNode)
          ))

          val queryAndProblems = FullQuery(maybeQueryProblem, query)

          val queryAndResults = QueryAndResults(queryAndProblems, fullResult)
          queryAndResults
        }
        else {
          throw RequestingUsernameDoesNotMatchQueryException(query.networkId, query.userName, user)
        }
      }
    )
  }

  def selectAdapterResultsIO(queryId: LongQueryId, user:User): IO[Seq[AdapterResult]] = {

    runTransactionIO(
      for {
        query: QepQuery <- mostRecentVisibleQepQueries.filter(_.networkId === queryId).take(1).result.head
        queryResults: Seq[QueryResultRow] <- latestQueryResultStatusRows(queryId)
      } yield {
        if (user.sameUserAs(query.userName, query.userDomain)) {

          val sortSiteAlphaNumerically = Sort.orderingFor[QueryResultRow, String](_.adapterNode, Sort.compareAlphaNumerically)

          val sortedQueryResults: Seq[QueryResultRow] = queryResults.distinct.sorted(sortSiteAlphaNumerically)

          sortedQueryResults.map(r => {
            val countOrStatus: Either[Option[ResultStatus], Long] = if(QueryWithResultStatus.isComplete(List(r)) && !QueryWithResultStatus.areAllResultsError(List(r)))
              Right(r.size) else Left(ResultStatus.namesToStatuses.get(r.status))

            AdapterResult(r.adapterNode, countOrStatus, r.resultMetadata.obfuscatingParameters)
          })
        }
        else {
          throw RequestingUsernameDoesNotMatchQueryException(query.networkId, query.userName, user)
        }
      }
    )
  }

  def selectDemographicsDataIO(queryId: LongQueryId, user:User): IO[Seq[DemographicsData]] = {
    runTransactionIO(
      for {
        query: QepQuery <- mostRecentVisibleQepQueries.filter(_.networkId === queryId).take(1).result.head
        queryResults: Seq[QueryResultRow] <- latestQueryResultStatusRows(queryId)
        breakdowns: Seq[QepQueryBreakdownResultsRow] <- mostRecentBreakdownResultsRows.filter(_.networkQueryId === queryId).result
      } yield {

        if (user.sameUserAs(query.userName, query.userDomain)) {
          val adapterToBreakdownType: Map[AdapterName, Seq[QepQueryBreakdownResultsRow]] = breakdowns.distinct.groupBy(_.adapterNode)
          val adapterToQueryResult: Map[AdapterName, Seq[QueryResultRow]] = queryResults.distinct.groupBy(_.adapterNode)

          val sortQepQueryBreakdownResultsRowAlphaNumerically =
            Sort.orderingFor[QepQueryBreakdownResultsRow, String](_.dataKey, Sort.compareAlphaNumerically)

          val sortResultAdapterNameAlphaNumerically =
            Sort.orderingFor[(String, _), String](_._1, Sort.compareAlphaNumerically)

          val sortedBreakdowns: Map[String, Seq[QepQueryBreakdownResultsRow]] = ListMap(
            adapterToBreakdownType
              .toList
              .map(tup => (tup._1, tup._2.sorted(sortQepQueryBreakdownResultsRowAlphaNumerically)))  // sort each value
              .sorted(sortResultAdapterNameAlphaNumerically) : _*                                // sort map by keys
          )

          val sortedBreakdown: Map[String, Seq[DataKeyAndValue]] = sortedBreakdowns.map(tup => (tup._1,
              tup._2.map(d => DataKeyAndValue(s"${d.resultType.i2b2Options.description} ${d.dataKey}", Right(d.value)))))
          adapterToQueryResult.map(tup => {
            val adapterNode: String = tup._1
            val queryResultRow = tup._2.head
            val countValue: Either[Option[ResultStatus], Long] = if(QueryWithResultStatus.isComplete(tup._2) && !QueryWithResultStatus.areAllResultsError(tup._2))
              Right(queryResultRow.size)
            else Left(ResultStatus.namesToStatuses.get(queryResultRow.status))
            val breakdownDataKeyAndValueSeq = Seq(DataKeyAndValue("All Patients", countValue)) ++ sortedBreakdown.getOrElse(adapterNode, Seq.empty)

            DemographicsData(tup._1, queryResultRow.resultMetadata.obfuscatingParameters, breakdownDataKeyAndValueSeq)
          }).toSeq
        }
        else {
          throw RequestingUsernameDoesNotMatchQueryException(query.networkId, query.userName, user)
        }
      }
    )
  }

  //todo this looks like its only used in tests - delete it and the tests?
//  @deprecated
  def selectFullQuery(queryId: LongQueryId): IO[Option[FullQuery]] = {

    runTransactionIO(
      for {
        query: Seq[QepQuery] <- mostRecentVisibleQepQueries.filter(_.networkId === queryId).result
        problems: Seq[QueryProblemDigestRow] <- mostRecentQueryProblemDigestRows.filter(_.networkId === queryId).result
      } yield {
        query.map(FullQuery(problems.lastOption, _)).lastOption
      }
    )
  }

  def countPreviousQueriesByUserAndDomain(userName: UserName, domain: String):Int = {
    val q = mostRecentVisibleQepQueries.filter(r => r.userName === userName && r.userDomain === domain)

    runBlocking(q.size.result)
  }
//todo only used in tests. Clean out for SHRINE2020-1097
  def selectQueryById(queryId: LongQueryId): Option[QepQuery] =
    runBlocking(mostRecentVisibleQepQueries.filter(_.networkId === queryId).result).lastOption

  def selectQueryByIdIO(queryId: LongQueryId): IO[Option[QepQuery]] = runIO{
    mostRecentVisibleQepQueries.filter(_.networkId === queryId).result.headOption
  }

  def updateQueryNameAndNotesIO(
                     queryId:LongQueryId,
                     name:QueryName,
                     notes:Option[String],
                     changeDate:Long = System.currentTimeMillis()
                   ): IO[Unit] = {
    for {
      queryResults <- runIO(mostRecentVisibleQepQueries.filter(_.networkId === queryId).result)
      _ <- runIO(allQepQueryQuery ++= queryResults.map(_.copy(queryName = name, queryNotes = notes, changeDate = changeDate)))
      _ <- QepQueryDbChangeNotifier.triggerChangesFor(queryId)
    } yield ()

  }

  // Watch-out! if called twice with the exact same arguments, this will
  // generate a copy of all the queries with the same changeDate parameter
  def updateQueryFavedIO(queryId:LongQueryId,
                         faved:Boolean,
                         changeDate: Long = System.currentTimeMillis()
                        ): IO[Unit] = {

    for {
      queryResults <- runIO(mostRecentVisibleQepQueries.filter(_.networkId === queryId).result)
      _ <- runIO(allQepQueryQuery ++= queryResults.map(_.copy(queryFaved = faved, changeDate = changeDate)))
      _ <- QepQueryDbChangeNotifier.triggerChangesFor(queryId)
    } yield ()
  }

  //only used in tests
  def insertQepResultRow(qepQueryRow:QueryResultRow): Int = {
    val rowsChanged = runBlocking(allQueryResultRows += qepQueryRow)
    QepQueryDbChangeNotifier.triggerChangesFor(qepQueryRow.networkQueryId).unsafeRunSync()
    rowsChanged
  }

  def insertQueryResultIO(result:Result): IO[Unit] = {

    val queryResultRow = QueryResultRow(result)
    val problems: Seq[QepProblemDigestRow] = result match {
      case errorResult: ErrorResult => Seq(QepProblemDigestRow(errorResult))
      case _ => Seq.empty
    }
    val breakdowns: Seq[QepQueryBreakdownResultsRow] = result match {
      case crcResult: CountResult => QepQueryBreakdownResultsRow.breakdownRowsFor(crcResult)
      case _ => Seq.empty
    }
    runTransactionIO(
      for {
        _ <- allQueryResultRows += queryResultRow
        _ <- allProblemDigestRows ++= problems
        _ <- allBreakdownResultsRows ++= breakdowns
      } yield ()
    ).flatMap{_ => QepQueryDbChangeNotifier.triggerChangesFor(result.queryId.underlying)}
  }

  //todo only used in tests
  private[querydb] def selectMostRecentQepResultRowsForIO(networkId:LongQueryId): IO[Seq[QueryResultRow]] = {
    runTransactionIO(latestQueryResultStatusRows(networkId))
  }

  //todo only used in tests
  private[querydb] def insertQueryBreakdown(breakdownResultsRow:QepQueryBreakdownResultsRow): Int = {
    runBlocking(allBreakdownResultsRows += breakdownResultsRow)
  }

  def selectAllBreakdownResultsRows: Seq[QepQueryBreakdownResultsRow] = {
    runBlocking(allBreakdownResultsRows.result)
  }

  def selectDistinctAdaptersWithResults:Seq[String] = {
   runBlocking(allQueryResultRows.map(_.adapterNode).distinct.result).sorted
  }

  def selectPreviousQueriesHistoryIO(start:DateStamp,end:DateStamp,researcherName:Option[String]): IO[Seq[QepQuery]] = {
    runTransactionIO{
      val dateBound = allQepQueryQuery.filter(_.dateCreated >= start.underlying).filter(_.dateCreated < end.underlying)
      val researcherFilter = researcherName.fold(dateBound)(name => dateBound.filter(_.userName === name ))
      researcherFilter.result
    }
  }

  //todo switch the tests to use this method instead
  def selectLatestResultsForQueryIO(networkId:LongQueryId): IO[Seq[FullQueryResult]] = {
    runTransactionIO(
      for {
        queryResults: Seq[QueryResultRow] <- latestQueryResultStatusRows(networkId)
        breakdowns: Seq[QepQueryBreakdownResultsRow] <- mostRecentBreakdownResultsRows.filter(_.networkQueryId === networkId).result
        problems: Seq[QepProblemDigestRow] <- mostRecentProblemDigestRows.filter(_.networkQueryId === networkId).result
      } yield {
        val breakdownTypeToResults: Map[ResultOutputType, Seq[QepQueryBreakdownResultsRow]] = breakdowns.distinct.groupBy(_.resultType)

        def seqOfOneProblemRowToProblemDigest(problemSeq: Seq[QepProblemDigestRow]): XmlProblemDigest = {
          if (problemSeq.size == 1) problemSeq.head.toProblemDigest
          else throw new IllegalStateException(s"problemSeq size was not 1. $problemSeq")
        }

        val adapterNodesToProblemDigests: Map[String, XmlProblemDigest] = problems.distinct.groupBy(_.adapterNode).map(nodeToProblem => nodeToProblem._1 -> seqOfOneProblemRowToProblemDigest(nodeToProblem._2))

        queryResults.distinct.map(r => FullQueryResult(
          r,
          breakdownTypeToResults,
          adapterNodesToProblemDigests.get(r.adapterNode)
        ))
      }
    )
  }

  def selectResultHistoryForQueryIO(networkId: LongQueryId): IO[Map[AdapterName, Seq[FullQueryResult]]] = {
    runTransactionIO(
      for {
        queryResults: Seq[QueryResultRow] <- allQueryResultRows.filter(_.networkQueryId === networkId).result
        breakdowns: Seq[QepQueryBreakdownResultsRow] <- mostRecentBreakdownResultsRows.filter(_.networkQueryId === networkId).result
        problems: Seq[QepProblemDigestRow] <- mostRecentProblemDigestRows.filter(_.networkQueryId === networkId).result
      } yield {
        val breakdownTypeToResults: Map[ResultOutputType, Seq[QepQueryBreakdownResultsRow]] = breakdowns.distinct.groupBy(_.resultType)

        def seqOfOneProblemRowToProblemDigest(problemSeq: Seq[QepProblemDigestRow]): XmlProblemDigest = {
          if (problemSeq.size == 1) problemSeq.head.toProblemDigest
          else throw new IllegalStateException(s"problemSeq size was not 1. $problemSeq")
        }

        val adapterNodesToProblemDigests: Map[String, XmlProblemDigest] = problems.distinct.groupBy(_.adapterNode).map(nodeToProblem => nodeToProblem._1 -> seqOfOneProblemRowToProblemDigest(nodeToProblem._2))

        val resultHistory: Seq[FullQueryResult] = queryResults.distinct.map { r: QueryResultRow =>
          val breakdowns: Map[ResultOutputType, Seq[QepQueryBreakdownResultsRow]] = if (r.status == ResultStatus.ResultFromCRC.statusName)
                              breakdownTypeToResults
                            else Map.empty
          val maybeProblem = if (r.status == ResultStatus.ErrorFromCrc.statusName || r.status == ResultStatus.ErrorInShrine.statusName)
                          adapterNodesToProblemDigests.get(r.adapterNode)
                        else None
          FullQueryResult(
            r,
            breakdowns,
            maybeProblem
          )
        }
        resultHistory.groupBy(_.adapterNode).map{ adapterNodeToResultHistory =>
          adapterNodeToResultHistory._1 -> adapterNodeToResultHistory._2.sortBy(_.changeDate)
        }
      }
    )
  }

  def selectPreviousQueriesWithResultStatusIO(
                                               userName: UserName,
                                               domain: String,
                                               skip:Option[Int] = None,
                                               limit:Option[Int] = None,
                                               sortBy: Option[String] = None,
                                               selectedQueryId: Option[LongQueryId] = None,
                                               previousQueryIds: Option[List[LongQueryId]] = None
                                             ): IO[(Int, Seq[QueryWithResultStatus])] = {

    def selectDistinctNoClobQueries(): Query[QepNoClobQueries, QepNoClobQuery, Seq] = {
      val q = mostRecentVisibleQepNoClobQueries.filter(r => r.userName === userName && r.userDomain === domain).distinct

      val qWithSortSkipLimit = applySortLimitAndSkipToSqlQuery(q, sortBy, skip, limit)
      val qWithIdFilter = previousQueryIds.fold(qWithSortSkipLimit)(qIdList => qWithSortSkipLimit.filter(_.networkId inSet qIdList))

      qWithIdFilter
    }

    def selectClobQueries(networkIds: Seq[LongQueryId]): Query[QepQueries, QepQuery, Seq] = {
      mostRecentVisibleQepQueries.filter(r => r.userName === userName && r.userDomain === domain).filter(_.networkId inSet networkIds)
    }

    def createQueryStateObservedChecksumSet(queries: Seq[QepQuery], allQueryResults: Seq[QueryResultRow]): Seq[Checksum] = {

      val queryStatesObserved: Seq[Long] = queries.map(query => {
        val queryResults = allQueryResults.filter(_.networkQueryId == query.networkId)
        QueryStateObserved(query, queryResults).checksum
      })

      queryStatesObserved
    }

    /** Specialized fast version that takes advantage of the relatively small fixed number of queries */
    def mostRecentRelevantQueryResultRows(queries: Seq[QepQuery]): DBIOAction[Seq[QueryResultRow], NoStream, Effect.Read] = {
      allQueryResultRows.filter(_.networkQueryId inSet queries.map(q => q.networkId)).result.map { rows: Seq[QueryResultRow] =>
        rows.groupBy(_.resultId).map(grouped => grouped._2.maxBy(k => ResultStatus.namesToStatuses(k.status).stage)).toSeq
      }
    }

    runTransactionIO {
    for {
        queriesWithoutClob: Seq[QepNoClobQuery] <- selectDistinctNoClobQueries().result
        //todo why not just use qWithClob ?
        queries: Seq[QepQuery] <- selectClobQueries(queriesWithoutClob.map(_.networkId)).result.map{ qWithClob => queriesWithoutClob.map(p => QepQuery(p, qWithClob.find(q => q.networkId == p.networkId).get))}
        results: Seq[QueryResultRow] <- mostRecentRelevantQueryResultRows(queries)
        queryStatesObserved: Seq[QueryStateObserved] <- allQueryStatesObserved.filter(_.checksum inSet createQueryStateObservedChecksumSet(queries, results)).result
        queryCount: Int <- allQepNoClobQuery.filter(_.userName === userName).filter(_.userDomain === domain).filter(_.deleted === false).map(_.networkId).distinct.size.result
      }
      yield {
          val queryWithResult: Seq[QueryWithResultStatus] = queries.flatMap(query => {
            val queryResults: Seq[QueryResultRow] = results.filter(_.networkQueryId == query.networkId).distinct
            val checksum: Checksum = QueryStateObserved(query, queryResults).checksum
            val observed: Boolean = selectedQueryId.fold(queryStatesObserved.exists(_.checksum == checksum)) { queryId =>
              if (query.networkId == queryId)
                true
              else {
                queryStatesObserved.exists(_.checksum == checksum)
              }
            }

            Seq(QueryWithResultStatus(query, queryResults, observed))
          })
          (queryCount, queryWithResult)
        }
    }
  }

  /**
   * Given a QepQuery, apply a sort order, skip a specified number of queries, and limit the number of returned queries.
   */
  private def applySortLimitAndSkipToSqlQuery(
                                               query: Query[QepNoClobQueries, QepNoClobQuery, Seq],
                                               sortBy: Option[String],
                                               skip: Option[Int],
                                               limit: Option[Int]
                                             ): Query[QepNoClobQueries, QepNoClobQuery, Seq] = {

    val qWithSort: Query[QepNoClobQueries, QepNoClobQuery, Seq] = sortBy.getOrElse("dateCreated.desc") match {
      case "dateCreated.asc" => query.sortBy(_.dateCreated.asc)
      case "dateCreated.desc" => query.sortBy(_.dateCreated.desc)
      case "queryName.asc" => query.sortBy(_.queryName.asc)
      case "queryName.desc" => query.sortBy(_.queryName.desc)
      case "queryFaved.asc" => query.sortBy(_.queryFaved.asc)
      case "queryFaved.desc" => query.sortBy(_.queryFaved.desc)
      case s => throw new Exception(s"Unknown sortBy option: $s")
    }

    val qWithSkip = skip.fold(qWithSort)(qWithSort.drop)
    val qWithLimit = limit.fold(qWithSkip)(qWithSkip.take)

    qWithLimit
  }

  def insertQueryStateObservedIO(queryStateObserved: QueryStateObserved): IO[Unit] = {
    runIO(allQueryStatesObserved += queryStateObserved).flatMap(_ => IO.unit)
  }

  def selectQueryStateObservedByChecksum(queryStateObserved: QueryStateObserved):IO[Option[QueryStateObserved]] = {
    runIO(allQueryStatesObserved.filter(_.checksum === queryStateObserved.checksum).take(1).result).map{
      case Seq(queryRow) => Some(queryRow)
      case Seq() => None
      case x => throw QepAuditDatabaseAssertException(s"Expected zero or one query for id ${queryStateObserved.networkQueryId}, selected ${x.size} $x")
    }
  }
}

case class QepAuditDatabaseAssertException(message:String) extends Exception(message)


object QepQueryDb {

  val dataSource:DataSource = TestableDataSourceCreator.dataSource(QepQuerySchema.config)
  Log.info("QEP's database config:")
  Log.info(QepQuerySchema.config.toString)

  val timeout: Duration = QepQuerySchema.config.get("timeout",Duration.apply)

  val db: QepQueryDb = QepQueryDb(QepQuerySchema.schema,dataSource,timeout)

  val createTablesOnStart: Boolean = QepQuerySchema.config.getBoolean("createTablesOnStart")
  if(createTablesOnStart) QepQueryDb.db.createTables()

}

/**
  * Separate class to support schema generation without actually connecting to the database.
  *
  * @param jdbcProfile Database profile to use for the schema
  */
case class QepQuerySchema(jdbcProfile: JdbcProfile) {
  import jdbcProfile.api._

  def ddlForAllTables: jdbcProfile.DDL = {
    allQepQueryQuery.schema ++ allQueryResultRows.schema ++ allBreakdownResultsRows.schema ++ allProblemDigestRows.schema ++ allQueryStatesObserved.schema ++ allQueryProblemDigestRows.schema
  }

  //to get the schema, use the REPL
  //println(QepQuerySchema.schema.ddlForAllTables.createStatements.mkString(";\n"))

  def createTables(database:Database): Unit = {
    try {
      val future = database.run(ddlForAllTables.create)
      Await.result(future,10 seconds)
    } catch {
      //I'd prefer to check and create schema only if absent. No way to do that with Oracle.
      case x:SQLException => Log.info("Caught exception while creating tables. Recover by assuming the tables already exist.",x)
    }
  }

  def dropTables(database:Database): Unit = {
    val future = database.run(ddlForAllTables.drop)
    //Really wait forever for the cleanup
    Await.result(future,Duration.Inf)
  }

  class QepQueries(tag:Tag) extends Table[QepQuery](tag,"PREVIOUS_QUERY") {
    def networkId = column[LongQueryId]("NETWORK_ID")
    def userName = column[UserName]("USER_NAME")
    def userDomain = column[String]("DOMAIN")
    def queryName = column[String]("QUERY_NAME")
    def queryNotes = column[Option[String]]("QUERY_NOTES")
    def queryFaved = column[Boolean]("QUERY_FAVED")
    def dateCreated = column[Time]("DATE_CREATED")
    def deleted = column[Boolean]("DELETED")
    def JSON = column[String]("QUERY_JSON")
    def changeDate = column[Long]("CHANGE_DATE")
    def status = column[String]("STATUS")

    def * = (networkId,userName,userDomain,queryName,queryNotes,queryFaved,dateCreated,deleted,JSON,changeDate,status) <> (QepQuery.tupled,QepQuery.unapply)

  }

  /* This table definition leaves out the CLOB columns in the PREVIOUS_QUERY table. This is needed to retrieve distinct rows in the
   * PREVIOUS_QUERY table because "select distinct" queries are not allowed using CLOB columns in Oracle
   */
  class QepNoClobQueries(tag:Tag) extends Table[QepNoClobQuery](tag,"PREVIOUS_QUERY") {
    def networkId = column[LongQueryId]("NETWORK_ID")
    def userName = column[UserName]("USER_NAME")
    def userDomain = column[String]("DOMAIN")
    def queryName = column[QueryName]("QUERY_NAME")
    def queryNotes = column[Option[String]]("QUERY_NOTES")
    def queryFaved = column[Boolean]("QUERY_FAVED")
    def dateCreated = column[Time]("DATE_CREATED")
    def deleted = column[Boolean]("DELETED")
    def changeDate = column[Long]("CHANGE_DATE")
    def status = column[String]("STATUS")
//    def JSON = column[String]("QUERY_JSON")

    def * = (networkId,userName,userDomain,queryName,queryNotes,queryFaved,dateCreated,deleted,changeDate,status) <> (QepNoClobQuery.tupled,QepNoClobQuery.unapply)
  }

  val allQepQueryQuery = TableQuery[QepQueries]
  private val mostRecentQepQueryQuery: Query[QepQueries, QepQuery, Seq] = for(
    queries <- allQepQueryQuery if !allQepQueryQuery.filter(_.networkId === queries.networkId).filter(_.changeDate > queries.changeDate).exists
  ) yield queries
  val mostRecentVisibleQepQueries = mostRecentQepQueryQuery.filter(_.deleted === false)

  val allQepNoClobQuery = TableQuery[QepNoClobQueries]
  private val mostRecentQepNoClobQuery: Query[QepNoClobQueries, QepNoClobQuery, Seq] = for(
    queries <- allQepNoClobQuery if !allQepNoClobQuery.filter(_.networkId === queries.networkId).filter(_.changeDate > queries.changeDate).exists
  ) yield queries
  val mostRecentVisibleQepNoClobQueries = mostRecentQepNoClobQuery.filter(_.deleted === false)

  implicit val qepQueryResultTypesColumnType: JdbcType[ResultOutputType] with BaseTypedType[ResultOutputType] = MappedColumnType.base[ResultOutputType,String] ({
    resultType: ResultOutputType => QepQueryBreakdownResultsRow.queryResultTypesToString(resultType)
  },{
    string: String => QepQueryBreakdownResultsRow.stringsToQueryResultTypes(string)
  })

  class QepQueryResults(tag:Tag) extends Table[QueryResultRow](tag,"QEP_QUERY_RESULT") {
    import io.circe.parser.decode
    import io.circe.{Decoder, Encoder}
    import io.circe.syntax.EncoderOps

   implicit val encodeObfuscatingParameters: Encoder[ObfuscatingParameters] =
     Encoder.forProduct4("binSize","stdDev","noiseClamp","lowLimit")( ObfuscatingParameters.unapply(_).get)

    implicit val encodeResultMetadata: Encoder[ResultMetadata] =
      Encoder.forProduct2("obfuscatingParameters", "custom")(ResultMetadata.unapply(_).get)

    implicit val decodeObfuscatingParams: Decoder[ObfuscatingParameters] =
      Decoder.forProduct4("binSize","stdDev","noiseClamp","lowLimit")( ObfuscatingParameters.apply)

    implicit val decodeResultMetadata: Decoder[ResultMetadata] =
      Decoder.forProduct2("obfuscatingParameters", "custom")(ResultMetadata.apply)

    implicit def resultMetadataMapper: BaseColumnType[ResultMetadata] =
      MappedColumnType.base[ResultMetadata, String](
        resultMetadata => resultMetadata.asJson.toString(),
        jsonText => {
          val resultMetadataJson = decode[ResultMetadata](jsonText)

          resultMetadataJson match {
            case Right(result) => result
            case Left(_) => ResultMetadata(obfuscatingParameters = None)
          }
        }
      )

    def resultId = column[Long]("RESULT_ID")
    def networkQueryId = column[LongQueryId]("NETWORK_QUERY_ID")
    def instanceId = column[Long]("INSTANCE_ID")
    def adapterNode = column[String]("ADAPTER_NODE")
    def size = column[Long]("SIZE")
    def startDate = column[Option[Long]]("START_DATE")
    def endDate = column[Option[Long]]("END_DATE")
    def status = column[String]("STATUS")
    def statusMessage = column[Option[String]]("STATUS_MESSAGE")
    def changeDate = column[Long]("CHANGE_DATE")
    def metadataJson = column[ResultMetadata]("METADATA_JSON")

    def * = (resultId,networkQueryId,instanceId,adapterNode,size,startDate,endDate,status,statusMessage,metadataJson,changeDate) <> (QueryResultRow.tupled,QueryResultRow.unapply)
  }

  val allQueryResultRows = TableQuery[QepQueryResults]

  class QepQueryBreakdownResults(tag:Tag) extends Table[QepQueryBreakdownResultsRow](tag,"QUERY_BREAKDOWN_RESULT") {
    def networkQueryId = column[LongQueryId]("NETWORK_QUERY_ID")
    def adapterNode = column[String]("ADAPTER_NODE")
    def resultId = column[Long]("RESULT_ID")
    def resultType = column[ResultOutputType]("RESULT_TYPE")
    def dataKey = column[String]("DATA_KEY")
    def value = column[Long]("VALUE")
    def changeDate = column[Long]("CHANGE_DATE")

    def * = (networkQueryId,adapterNode,resultId,resultType,dataKey,value,changeDate) <> (QepQueryBreakdownResultsRow.tupled,QepQueryBreakdownResultsRow.unapply)
  }

  val allBreakdownResultsRows = TableQuery[QepQueryBreakdownResults]
  //Most recent query result rows for each queryId from each adapter
  val mostRecentBreakdownResultsRows: Query[QepQueryBreakdownResults, QepQueryBreakdownResultsRow, Seq] = for(
    breakdownResultsRows <- allBreakdownResultsRows if !allBreakdownResultsRows.
      filter(_.networkQueryId === breakdownResultsRows.networkQueryId).
      filter(_.adapterNode === breakdownResultsRows.adapterNode).
      filter(_.resultId === breakdownResultsRows.resultId).
      filter(_.resultType === breakdownResultsRows.resultType).
      filter(_.dataKey === breakdownResultsRows.dataKey).
      filter(_.changeDate > breakdownResultsRows.changeDate).exists
  ) yield breakdownResultsRows

  /*
case class ProblemDigest(codec: String, stampText: String, summary: String, description: String, detailsXml: NodeSeq) extends XmlMarshaller {
    */
  class QepResultProblemDigests(tag:Tag) extends Table [QepProblemDigestRow](tag,"QUERY_RESULT_PROBLEM_DIGEST") {
    def networkQueryId = column[LongQueryId]("NETWORK_QUERY_ID")
    def adapterNode = column[String]("ADAPTER_NODE")
    def codec = column[String]("CODEC")
    private def stamp = column[String]("STAMP")
    def summary = column[String]("SUMMARY")
    def description = column[String]("DESCRIPTION")
    private def details = column[String]("DETAILS")
    def changeDate = column[Long]("CHANGE_DATE")

    def * = (networkQueryId,adapterNode,codec,stamp,summary,description,details,changeDate) <> (QepProblemDigestRow.tupled,QepProblemDigestRow.unapply)
  }

  val allProblemDigestRows = TableQuery[QepResultProblemDigests]
  val mostRecentProblemDigestRows: Query[QepResultProblemDigests, QepProblemDigestRow, Seq] = for(
    problemDigests <- allProblemDigestRows if !allProblemDigestRows.filter(_.networkQueryId === problemDigests.networkQueryId).filter(_.adapterNode === problemDigests.adapterNode).filter(_.changeDate > problemDigests.changeDate).exists
  ) yield problemDigests


  class QueryStatesObserved(tag:Tag) extends Table[QueryStateObserved](tag,"RESULTS_OBSERVED") {
    def networkId = column[LongQueryId]("NETWORK_QUERY_ID")
    def checksum = column[Checksum]("CHECKSUM")
    private def observedTime = column[Long]("OBSERVED_TIME")

    def * = (networkId,checksum,observedTime) <> (QueryStateObserved.tupled,QueryStateObserved.unapply)
  }

  val allQueryStatesObserved = TableQuery[QueryStatesObserved]

  class QueryProblemDigests(tag:Tag) extends Table[QueryProblemDigestRow](tag,"QUERY_PROBLEM_DIGEST") {
    def networkId = column[LongQueryId]("NETWORK_QUERY_ID")
    def codec = column[String]("CODEC")
    private def stamp = column[String]("STAMP")
    def summary = column[String]("SUMMARY")
    def description = column[String]("DESCRIPTION")
    private def details = column[String]("DETAILS")
    def changeDate = column[Long]("CHANGE_DATE")

    def * = (networkId,codec,stamp,summary,description,details,changeDate) <> (QueryProblemDigestRow.tupled,QueryProblemDigestRow.unapply)
  }

  val allQueryProblemDigestRows = TableQuery[QueryProblemDigests]
  val mostRecentQueryProblemDigestRows: Query[QueryProblemDigests, QueryProblemDigestRow, Seq] = for(
    problemDigests <- allQueryProblemDigestRows if !allQueryProblemDigestRows.filter(_.networkId === problemDigests.networkId).filter(_.changeDate > problemDigests.changeDate).exists
  ) yield problemDigests

}

object QepQuerySchema {

  val configProp = "shrine.qep.database"
  val config:Config = ConfigSource.config.getConfig(configProp)

  Log.info(s"QEP's config property = $configProp")
  Log.info("QEP's database config:")
  Log.info(s"$config")

  val slickProfile:JdbcProfile = TestableDataSourceCreator.slickDriver(config)

  val moreBreakdowns: Set[ResultOutputType] = ResultOutputType.breakdownTypes

  val schema: QepQuerySchema = QepQuerySchema(slickProfile)
}

case class QepQuery(
                     networkId:LongQueryId, //todo rename SHRINE2020-467
                     userName: UserName,
                     userDomain: String,
                     queryName: QueryName,
                     queryNotes: Option[String],
                     queryFaved: Boolean,
                     dateCreated: Time,
                     deleted: Boolean,
                     JSON:String,
                     changeDate: Time,
                     status: String
                   ){

  def updateWith(update:UpdateQueryAtQep):QepQuery = {
    val query = V2Query.tryRead(new JsonText(JSON)).get
    val newJSON:String = update.updatedQuery(query).asJsonText.underlying

    this.copy(status = update.queryStatus.statusName,changeDate = update.changeDate.underlying,JSON = newJSON)
  }

  def v2Query:V2Query = {
    V2Query.tryRead(new JsonText(JSON)).get //todo backwards-compatible error handling goes here
  }
}

object QepQuery extends ((LongQueryId, UserName, String, QueryName, Option[String], Boolean, Time, Boolean, String, Time, String) => QepQuery) {

  def apply(query: V2Query,researcher:Researcher):QepQuery = {
    if(query.researcherId != researcher.id) throw new IllegalArgumentException(s"researcher id ${researcher.id} does not equal query researcher id ${query.researcherId} for $researcher and $query")

    new QepQuery(
      networkId = query.id.underlying,
      userName = researcher.userName.underlying,
      userDomain = researcher.userDomainName.underlying,
      queryName = query.queryName,
      queryNotes = query.queryNotes,
      queryFaved = query.queryFaved,
      dateCreated = query.versionInfo.createDate.underlying,
      deleted = false,
      JSON = query.asJsonText.underlying,
      changeDate = query.versionInfo.changeDate.underlying,
      status = query.status.statusName
    )
  }

  //todo looks like use of this apply can be avoided at its one use site. "NoClob" seems very suspect
  def apply(qepNoClobQuery: QepNoClobQuery, qepWithClobQuery: QepQuery):QepQuery = {
    new QepQuery(
      networkId = qepNoClobQuery.networkId,
      userName = qepNoClobQuery.userName,
      userDomain = qepNoClobQuery.userDomain,
      queryName = qepNoClobQuery.queryName,
      queryNotes = qepWithClobQuery.queryNotes,
      queryFaved = qepNoClobQuery.queryFaved,
      dateCreated = qepNoClobQuery.dateCreated,
      deleted = qepNoClobQuery.deleted,
      JSON = qepWithClobQuery.JSON,
      changeDate = qepNoClobQuery.changeDate,
      status = qepNoClobQuery.status
    )
  }
}

private[querydb] case class QepNoClobQuery(
                                            networkId:LongQueryId,
                                            userName: UserName,
                                            userDomain: String,
                                            queryName: QueryName,
                                            queryNotes: Option[String],
                                            queryFaved: Boolean,
                                            dateCreated: Time,
                                            deleted: Boolean,
                                            changeDate: Time,
                                            status: String
                         )

case class QepClobQuery(
                     networkId:LongQueryId,
                     userName: UserName,
                     userDomain: String,
                     expression: Option[String],
                     queryXml: String,
                     deleted: Boolean,
                     changeDate: Time,
                   )

case class FullQueryResult(
                            resultId:Long,
                            queryId:LongQueryId,
                            instanceId:Long,
                            adapterNode:String,
                            count:Long,
                            startDate:Option[Long],
                            endDate:Option[Long],
                            status:String,
                            statusMessage:Option[String],
                            changeDate:Long,
                            breakdownTypeToResults:Map[ResultOutputType,Seq[QepQueryBreakdownResultsRow]],
                            resultMetadata: ResultMetadata,
                            problemDigest:Option[XmlProblemDigest]
                          )

object FullQueryResult {
  def apply(row:QueryResultRow,
            breakdownTypeToResults:Map[ResultOutputType,Seq[QepQueryBreakdownResultsRow]],
            problemDigest:Option[XmlProblemDigest] = None):FullQueryResult = {
    FullQueryResult(resultId = row.resultId,
      queryId = row.networkQueryId,
      instanceId = row.instanceId,
      adapterNode = row.adapterNode,
      count = row.size,
      startDate = row.startDate,
      endDate = row.endDate,
      status = row.status,
      statusMessage = row.statusMessage,
      changeDate = row.changeDate,
      breakdownTypeToResults = breakdownTypeToResults,
      resultMetadata = row.resultMetadata,
      problemDigest = problemDigest
    )
  }
}

case class QueryResultRow(
                           resultId:Long,
                           networkQueryId:LongQueryId,
                           instanceId:Long,
                           adapterNode:String,
                           size:Long,
                           startDate:Option[Long],
                           endDate:Option[Long],
                           status:String,
                           statusMessage:Option[String],
                           resultMetadata: ResultMetadata,
                           changeDate:Long,
                         ) {
}

object QueryResultRow extends ((Long,LongQueryId,Long,String,Long,Option[Long],Option[Long],String,Option[String],ResultMetadata,Long) => QueryResultRow)
{

  def apply(result:Result):QueryResultRow = {
    result match {
      case resultProgress:ResultProgress => QueryResultRow(
        resultId = resultProgress.id.underlying,
        networkQueryId = resultProgress.queryId.underlying,
        instanceId = resultProgress.crcQueryInstanceId.getOrElse(-1L), //todo change instanceId to an optional field
        adapterNode = resultProgress.adapterNodeName.underlying,
        size = -1L, //todo change size to an optional field
        startDate = Some(resultProgress.versionInfo.createDate.underlying),
        endDate = None,
        status = resultProgress.status.statusName,
        statusMessage = resultProgress.statusMessage,
        resultMetadata = resultProgress.resultMetadata,
        changeDate = resultProgress.versionInfo.changeDate.underlying
      )
      case errorResult:ErrorResult => QueryResultRow(
        resultId = errorResult.id.underlying,
        networkQueryId = errorResult.queryId.underlying,
        instanceId = errorResult.crcQueryInstanceId.getOrElse(-1L), //todo change instanceId to an optional field
        adapterNode = errorResult.adapterNodeName.underlying,
        size = -1L, //todo change size to an optional field
        startDate = Some(errorResult.versionInfo.createDate.underlying),
        endDate = Some(errorResult.versionInfo.changeDate.underlying),
        status = errorResult.status.statusName,
        statusMessage = errorResult.statusMessage,
        resultMetadata = errorResult.resultMetadata,
        changeDate = errorResult.versionInfo.changeDate.underlying
      )
      case crcResult:CountResult => QueryResultRow(
        resultId = crcResult.id.underlying,
        networkQueryId = crcResult.queryId.underlying,
        instanceId = crcResult.crcQueryInstanceId.getOrElse(-1L), //todo change instanceId to an optional field
        adapterNode = crcResult.adapterNodeName.underlying,
        size = crcResult.count, //todo change size to an optional field
        startDate = Some(crcResult.versionInfo.createDate.underlying),
        endDate = Some(crcResult.versionInfo.changeDate.underlying),
        status = crcResult.status.statusName,
        statusMessage = crcResult.statusMessage,
        resultMetadata = crcResult.resultMetadata,
        changeDate = crcResult.versionInfo.changeDate.underlying
      )
    }
  }


  def apply(networkQueryId:LongQueryId, result:FullQueryResult):QueryResultRow = {
    new QueryResultRow(
      resultId = result.resultId,
      networkQueryId = networkQueryId,
      instanceId = result.instanceId,
      adapterNode = result.adapterNode,
      size = result.count,
      startDate = result.startDate,
      endDate = result.endDate,
      status = result.status,
      statusMessage = result.statusMessage,
      resultMetadata = result.resultMetadata,
      changeDate = result.changeDate
    )
  }

  def sortResults(results: Seq[QueryResultRow], sortResultsBy: String): Seq[QueryResultRow] = {
    def sortByStatus(results: Seq[QueryResultRow], asc: Boolean): Seq[QueryResultRow] = {
      sealed trait ResultsGroup {
        def statesToValues:Map[String,Int]
        lazy val states: Set[String] = statesToValues.keySet
        def sort(results:Seq[QueryResultRow]): Seq[QueryResultRow] =
          results.sortBy{ r => (statesToValues(r.status),r.adapterNode)}
      }
      case object HasCounts extends ResultsGroup {
        val statesToValues:Map[String,Int] = Set(ResultStatus.ResultFromCRC.statusName, QueryResult.StatusType.Finished.name)
          .map(s => s -> ResultStatus.ResultFromCRC.stage).toMap
        override def sort(results:Seq[QueryResultRow]): Seq[QueryResultRow] =
          results.sortBy{ r => (r.size,r.adapterNode)}
    }
      case object InProgress extends ResultsGroup {
        //In-progress statuses go in order of most complete to least complete
        val statesToValues: Map[String, Int] = ResultStatus.statuses.filterNot(_.isFinal)
          .map(s => s.statusName -> s.stage ).toMap
      }
      case object Error extends ResultsGroup {
        //All statuses from shrine 1.25.4 and earlier are error statuses that should go last. No answer is coming back in shrine 2.0 or later
        val oldStatusesToValues: Map[String, Int] = QueryResult.StatusType.values.filterNot(_ == QueryResult.StatusType.Finished)
          .map(_.name -> - ResultStatus.ResultFromCRC.stage).toMap
        //Error statuses sort second-last
        val errorStatusesToValues: Map[String, Int] = ResultStatus.statuses.filter(_.isError)
          .map(s => s.statusName -> (s.stage - ResultStatus.ResultFromCRC.stage)).toMap
        val statesToValues: Map[String, Int] = (oldStatusesToValues ++ errorStatusesToValues)
          .withDefaultValue(- ResultStatus.ResultFromCRC.stage -1) //push any unknown states to the bottom
      }

      def groupForStatus(result:QueryResultRow): ResultsGroup = {
        val statusName = result.status
        if(HasCounts.states.contains(statusName)) HasCounts
        else if (InProgress.states.contains(statusName)) InProgress
        else if (Error.states.contains(statusName)) Error
        else {
          Log.error(
            s"""Encountered unknown status string $statusName for query ${results.head.networkQueryId}
               |result ${result.resultId} from ${result.adapterNode}""".stripMargin)
          Error
        }
      }

      val sortedGroupedResults: Map[ResultsGroup, Seq[QueryResultRow]] = results.groupBy(qr => groupForStatus(qr)).map{ groupAndResults =>
        groupAndResults._1 -> groupAndResults._1.sort(groupAndResults._2)
      }.withDefaultValue(Seq.empty)

      if (asc) {
        sortedGroupedResults(HasCounts) ++ sortedGroupedResults(InProgress) ++ sortedGroupedResults(Error)
       } else {
        sortedGroupedResults(HasCounts).reverse ++ sortedGroupedResults(InProgress).reverse ++ sortedGroupedResults(Error).reverse
      }
    }

    sortResultsBy match {
      case "site.asc" => results.sortBy(_.adapterNode)
      case "site.desc" => results.sortBy(_.adapterNode)(Ordering[String].reverse)
      case "status.asc" => sortByStatus(results, asc = true)
      case "status.desc" => sortByStatus(results, asc = false)
      case x => throw new IllegalArgumentException(s"Illegal site sort by value: $x")
    }
  }
}

case class QepQueryBreakdownResultsRow(
                                        networkQueryId: LongQueryId,
                                        adapterNode:String,
                                        resultId:Long,
                                        resultType: ResultOutputType,
                                        dataKey:String,
                                        value:Long,
                                        changeDate:Long
                                      )

object QepQueryBreakdownResultsRow extends ((LongQueryId,String,Long,ResultOutputType,String,Long,Long) => QepQueryBreakdownResultsRow){

  def breakdownRowsFor(crcResult: CountResult): Seq[QepQueryBreakdownResultsRow] = {
    crcResult.breakdowns.map{ bd: Breakdowns =>
      bd.counts.flatMap { axis =>
        axis._2.map { nameCount =>
          QepQueryBreakdownResultsRow(
            networkQueryId = crcResult.queryId.underlying,
            adapterNode = crcResult.adapterNodeName.underlying,
            resultId = crcResult.crcQueryInstanceId.get,
            resultType = stringsToQueryResultTypes(axis._1),
            dataKey = nameCount._1,
            value = nameCount._2,
            changeDate = crcResult.versionInfo.changeDate.underlying
          )
        }
      }
    }.getOrElse(Seq.empty)
  }

  private val qepQueryResultTypes: Set[ResultOutputType] = QepQuerySchema.moreBreakdowns ++ DefaultBreakdownResultOutputTypes.toSet ++ ResultOutputType.values
  val stringsToQueryResultTypes: Map[String, ResultOutputType] = qepQueryResultTypes.map(x => (x.name,x)).toMap.withDefault(
    //to support deleted breakdowns . See SHRINE2020-1586
    n =>ResultOutputType(n,isBreakdown = true,I2b2Options(n),None)
  )
  val queryResultTypesToString: Map[ResultOutputType, String] = stringsToQueryResultTypes.map(_.swap)

}

case class QepProblemDigestRow(
                                networkQueryId: LongQueryId,
                                adapterNode: String,
                                codec: String,
                                stampText: String,
                                summary: String,
                                description: String,
                                details: String,
                                changeDate:Long
                              ){
  def toProblemDigest: XmlProblemDigest = {
    XmlProblemDigest(
      codec,
      stampText,
      summary,
      description,
      if(details.nonEmpty) XmlUtil.loadString(details)
      else <details/>,
      //TODO: FIGURE OUT HOW TO GET AN ACTUAL EPOCH INTO HERE
      0
    )
  }
}

object QepProblemDigestRow extends ((LongQueryId,String,String,String,String,String,String,Long) => QepProblemDigestRow) {
  def apply(errorResult:ErrorResult):QepProblemDigestRow = new QepProblemDigestRow(
    networkQueryId = errorResult.queryId.underlying,
    adapterNode = errorResult.adapterNodeName.underlying,
    codec = errorResult.problemDigest.codec,
    stampText = errorResult.problemDigest.stampText,
    summary = errorResult.problemDigest.summary,
    description = errorResult.problemDigest.description,
    details = errorResult.problemDigest.detailsXml.toString(),
    changeDate = errorResult.versionInfo.changeDate.underlying
  )
}

case class QueryWithResultStatus(
                                  networkId:LongQueryId,
                                  userName: UserName,
                                  userDomain: String,
                                  queryName: QueryName,
                                  queryNotes: Option[String],
                                  queryFaved: Boolean,
                                  dateCreated: Time,
                                  deleted: Boolean,
                                  JSON:String,
                                  changeDate: Time,
                                  status: String,
                                  isComplete: Boolean,
                                  isQueryError: Boolean,
                                  isResultsError: Boolean,
                                  observed: Boolean
                                )

object QueryWithResultStatus {
  def apply(query: QepQuery,
            results: Seq[QueryResultRow],
            observed: Boolean
            ): QueryWithResultStatus = {

    new QueryWithResultStatus(
      networkId = query.networkId,
      userName = query.userName,
      userDomain = query.userDomain,
      queryName = query.queryName,
      queryNotes = query.queryNotes,
      queryFaved = query.queryFaved,
      dateCreated = query.dateCreated,
      deleted = query.deleted,
      JSON = query.JSON,
      changeDate = query.changeDate,
      status = query.status,
      isComplete = isComplete(results),
      isQueryError = isQueryError(query),
      isResultsError = areAllResultsError(results),
      observed = observed
    )

  }

  def isComplete(results: Seq[QueryResultRow]): Boolean = {
    if (results.isEmpty) false
    else results.forall{ p =>
      ResultStatus.namesToStatuses.get(p.status).map(_.isFinal).getOrElse {
      QueryResult.StatusType.valueOf(p.status).get.isDone}
    }
  }

  def isQueryError(query:  QepQuery): Boolean = {
    val isQueryError: Boolean = QueryStatus.namesToStatuses.get(query.status).exists(_.isError)

    isQueryError
  }

  def areAllResultsError(results: Seq[QueryResultRow]): Boolean = {

    if(results.isEmpty) false
    else results.forall { p =>
      ResultStatus.namesToStatuses.get(p.status).map(_.isError).getOrElse {
        QueryResult.StatusType.valueOf(p.status).get.isError
      }
    }
  }
}

case class QueryStateObserved(
                               networkQueryId:LongQueryId,
                               checksum: Checksum,
                               observedTime: Time
                             )

object QueryStateObserved extends ((LongQueryId,Checksum,Time) => QueryStateObserved) {

  private def create(networkQueryId: LongQueryId, checksum: Checksum): QueryStateObserved = {
    new QueryStateObserved(
      networkQueryId = networkQueryId,
      checksum = checksum,
      observedTime = System.currentTimeMillis()
    )
  }

  def apply(queryAndResults: QueryAndResults): QueryStateObserved = {

    val query: QepQuery = queryAndResults.fullQuery.query
    val queryResults: Seq[ObservedResult] = queryAndResults.fullResults.map(QueryResultRow(query.networkId, _)).sortBy(_.adapterNode).map(ObservedResult(_))

    val queriesAndResult = QueriesAndResult(ObservedQuery(query), queryResults)
    val checksum = queriesAndResult.hashCode()
    val networkQueryId: LongQueryId = query.networkId

    create(networkQueryId = networkQueryId, checksum = checksum)
  }

  def apply(query: QepQuery, queryResults: Seq[QueryResultRow]): QueryStateObserved = {

    val queriesAndResult = QueriesAndResult(ObservedQuery(query), queryResults.sortBy(_.adapterNode).map(ObservedResult(_)))
    val checksum = queriesAndResult.hashCode()
    val networkQueryId: LongQueryId = query.networkId

    create(networkQueryId = networkQueryId, checksum = checksum)
  }

  case class ObservedQuery(
                            networkId:LongQueryId,
                            status: String
                          )
  private object ObservedQuery {
    def apply(query: QepQuery): ObservedQuery = {
      ObservedQuery (
        networkId = query.networkId,
        status = query.status
      )
    }
  }

  case class ObservedResult(resultId:Long,
                            networkQueryId:LongQueryId,
                            adapterNode:String,
                            status:String
                          )
  private object ObservedResult {
    def apply(result: QueryResultRow): ObservedResult = {
      ObservedResult(
        resultId = result.resultId,
        networkQueryId = result.networkQueryId,
        adapterNode = result.adapterNode,
        status = result.status
      )
    }
  }

  private case class QueriesAndResult(query: ObservedQuery, results: Seq[ObservedResult])
}


case class QueryProblemDigestRow(
                                  networkQueryId: LongQueryId,
                                  codec: String,
                                  stampText: String,
                                  summary: String,
                                  description: String,
                                  details: String,
                                  changeDate:Long
                              ){
  def toProblemDigest: XmlProblemDigest = {
    XmlProblemDigest(
      codec,
      stampText,
      summary,
      description,
      if(details.nonEmpty) XmlUtil.loadString(details)
      else <details/>,
      changeDate
    )
  }
}

object QueryProblemDigestRow extends ((LongQueryId,String,String,String,String,String,Long) => QueryProblemDigestRow) {
  def apply(queryError:QueryError):QueryProblemDigestRow = new QueryProblemDigestRow(
    networkQueryId = queryError.id.underlying,
    codec = queryError.problemDigest.codec,
    stampText = queryError.problemDigest.stampText,
    summary = queryError.problemDigest.summary,
    description = queryError.problemDigest.description,
    details = queryError.problemDigest.detailsXml.toString(),
    changeDate = queryError.problemDigest.epoch
  )

  def apply(update:UpdateQueryAtQepWithError):QueryProblemDigestRow = new QueryProblemDigestRow(
    networkQueryId = update.queryId.underlying,
    codec = update.problemDigest.codec,
    stampText = update.problemDigest.stampText,
    summary = update.problemDigest.summary,
    description = update.problemDigest.description,
    details = update.problemDigest.detailsXml.toString(),
    changeDate = update.problemDigest.epoch
  )
}

case class FullQuery(query: QepQuery,
                     problemDigest: Option[XmlProblemDigest]
                     )

object FullQuery{
  def apply(problemDigest: Option[QueryProblemDigestRow]= None, query: QepQuery) = {
    val xmlProblemDigest: Option[XmlProblemDigest] = problemDigest.map(_.toProblemDigest)
    new FullQuery(query, xmlProblemDigest)
  }
}

case class QueryAndResults(
                            fullQuery:FullQuery,
                            fullResults: Seq[FullQueryResult]
                     )


case class BreakdownResultsForType(resultType:ResultOutputType,results:Seq[BreakdownResult])

object BreakdownResultsForType {
  def apply(adapterName: String, breakdownType: ResultOutputType, breakdowns: Seq[QepQueryBreakdownResultsRow]): BreakdownResultsForType = {
    val breakdownResults = breakdowns.filter(_.adapterNode == adapterName).map(row => BreakdownResult(row.dataKey,row.value,row.changeDate))

    BreakdownResultsForType(breakdownType,breakdownResults)
  }
}

case class BreakdownResult(dataKey:String,value:Long,changeDate:Long)

case class RequestingUsernameDoesNotMatchQueryException(queryId: Long, requestingUser: String, actualUser: User)
  extends Exception(s"Query $queryId belongs to user ${actualUser.username} but requested by user $requestingUser")

case class NoQueryForQueryIdException(queryId: Long)
  extends Exception(s"No query found for $queryId")


case class DataKeyAndValue(dataKey: String, value: Either[ Option[ResultStatus], Long])


case class AdapterResult(adapterNode: String, countOrStatus: Either[Option[ResultStatus], Long], obfuscatingParameters: Option[ObfuscatingParameters])

case class DemographicsData(adapterName: AdapterName, obfuscatingParameters: Option[ObfuscatingParameters], dataKeyAndValues: Seq[DataKeyAndValue])
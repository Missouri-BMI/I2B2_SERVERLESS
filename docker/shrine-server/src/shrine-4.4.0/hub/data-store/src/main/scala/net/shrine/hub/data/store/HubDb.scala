package net.shrine.hub.data.store

import java.sql.SQLException
import cats.effect.IO
import com.typesafe.config.Config

import javax.sql.DataSource
import net.shrine.config.ConfigSource
import net.shrine.http4s.catsio.{ExecutionContexts, SimpleAsyncExecutor}
import net.shrine.log.{Log, Loggable}
import net.shrine.protocol.version.v2.{Network, Node, NodeSystemSpec, Researcher, Result, ResultProgress, ResultStatus, UpdateCrcQueuedResult, UpdateNodeSystemSpec, UpdateQueryAtHub, UpdateQueryAtQep, UpdateQueryReadyForAdapters, UpdateResult, VersionInfo, Versioned, Query => ShrineQuery}
import net.shrine.protocol.version.{DateStamp, Id, ItemVersion, JsonText, NetworkId, NodeId, NodeKey, ProtocolVersion, QueryId, ResearcherId, ResultId}
import net.shrine.slick.TestableDataSourceCreator
import slick.ast.BaseTypedType
import slick.jdbc.{JdbcProfile, JdbcType}

import scala.annotation.unused
import scala.collection.immutable.Iterable
import scala.concurrent.duration.{Duration, DurationInt}
import scala.concurrent.{Await, ExecutionContextExecutorService}
import scala.language.postfixOps
import scala.util.Try
import scala.reflect.ClassTag

/**
  * Slick DB code for storing common currency data for shrine.
  *
  * @author david
  * @since 1.26
  */
case class HubDb(schemaDef:HubSchema, dataSource: DataSource) extends Loggable {
  import schemaDef._
  import schemaDef.jdbcProfile.api._
  import schemaDef.jdbcProfile.backend.DatabaseDef

  implicit val executionContext: ExecutionContextExecutorService = ExecutionContexts.databaseExecutionContext

  val database:DatabaseDef = Database.forDataSource(
    ds = dataSource,
    maxConnections = None,
    executor = SimpleAsyncExecutor(executionContext)
  )
  def createTables(): Unit = schemaDef.createTables(database)

  def dropTables(): Unit = schemaDef.dropTables(database)

  private def runIO[R](dbio: DBIOAction[R, NoStream, _]): IO[R] = {
      IO.fromFuture(IO.blocking(database.run(dbio)))
  }

  private def runTransactionIO[R](dbio: DBIOAction[R, NoStream, _]): IO[R] = {
      IO.fromFuture(IO.blocking(database.run(dbio.transactionally)))
  }

  /**
    * Only for testing
    */
  private[store] def insertResultRowIO(resultRow:ResultRow): IO[Int] = {
    runIO(Results.allRows += resultRow)
  }

  def upsertResultIO(item: Result): IO[Option[Result]] = runTransactionIO(upsertItem(item,Results))

  def upsertQueryIO(item: ShrineQuery): IO[Option[ShrineQuery]] = runTransactionIO(upsertItem(item,Queries))

  def upsertNodeIO(item: Node): IO[Option[Node]] = runTransactionIO(upsertItem(item,Nodes))

  def upsertNodeSystemSpecIO(item: NodeSystemSpec): IO[Option[NodeSystemSpec]] = runTransactionIO(upsertItem(item,NodeSystemSpecs))

  def upsertNetworkIO(item: Network): IO[Option[Network]] = runTransactionIO(upsertItem(item,Networks))

  def upsertResearcherIO(item: Researcher): IO[Option[Researcher]] = runTransactionIO(upsertItem(item,Researchers))

  def upsertQueryUpdateAndResearcher(query:ShrineQuery, researcher: Researcher): IO[Option[ShrineQuery]] = {
    //the researcher is coming from the QEP, doesn't know about versions, so logic here prevents a version clash
    //when the QEP uses this collection of data directly (and is fully fused with the hub) then only the query will need to be inserted.

    //check and update the researcher, then upsert the query
    val maybeUpdateResearcher: IO[Option[Researcher]] = selectResearcherByIdIO(researcher.id).flatMap{ maybeFoundResearcher: Option[Researcher] =>
      maybeFoundResearcher.fold {
        runTransactionIO{upsertItem(researcher, Researchers)}
      } { foundResearcher =>
        if(foundResearcher.id == researcher.id && !foundResearcher.equalsIgnoreVersion(researcher)) {
          val updateResearcher = foundResearcher.withMemberValuesOf( researcher)
          runTransactionIO(upsertItem(updateResearcher, Researchers) )
        } else {
          IO(None)
        }
      }
    }

    maybeUpdateResearcher.flatMap{_ =>
      runTransactionIO(upsertItem(query,Queries))
    }
  }

  private type UpsertItemType[V] = DBIOAction[Option[V], NoStream, Effect.Read with Effect.Write with Effect.Read with Effect.Write]

  /**
    * @return Some(item) if the new row is inserted
    *         None if not, and any the existing rows with this id and version are the same
    *
    * @throws ItemVersionRaceLostException if the row to insert already exists
    *
    * @throws HubDatabaseAssertException if inserting one row somehow claims to have inserted more than one
    */
  private def upsertItem[
    I <: Id: ClassTag, //Needs the ClassTag to let I survive erasure
    R <: Row[I],
    V <: Versioned[I],
    Items <: ItemTable[I,R]
  ](
     item: V,
     tableCompanion: ItemTableCompanion[I,R,V,Items]
   ): UpsertItemType[V] = {

    val row = tableCompanion.itemToRow(item)
    implicit val IdColumnType: JdbcType[I] with BaseTypedType[I] = MappedColumnType.base[I, Long](
      id => id.underlying,
      long => tableCompanion.longToId(long)
    )

    //increment the table version and isolate this transaction - todo does this work? SHRINE2020-1376
    incrementTableVersion(tableCompanion.tableName).flatMap { _ =>
      // Find all the rows with the same id and version number
      tableCompanion.allRows.filter(r => r.id === row.id).filter(r => r.itemVersion === row.itemVersion).result.flatMap {
        case Seq() => { // a row with this id and version does not exist yet
          //insert the row only if it is the only row with this id and version
          tableCompanion.allRows += row
        }.map {
          //expect insertCount to be 1
          case ic if ic == 1 => Some(item)
          case insertCount: Int => throw HubDatabaseAssertException(s"""insert into ${tableCompanion.tableName} returned $insertCount, not 1 as expected""")
        }
        case alreadyExists => // at least one row with this id and version does exist already
          // don't insert the row
          // if all the existing rows with this id and version are the same then this isn't a problem
          if (alreadyExists.forall(_ == row)) DBIO.successful(None)
          // However, this is a problem if the new row is different from the others
          // Due to the transaction this should only happen if some other row was written outside this method
          else DBIO.failed(ItemVersionRaceLostException[I, V, R](item, row, alreadyExists))
      }
    }
  }

  private[store] def selectAllResultsHistoryIO:IO[Seq[Try[Result]]] = {
    runIO(Results.allRows.sortBy(x => x.itemVersion.desc).result)
      .map{_.map(_.toResult)}
  }

  /**
    * Only for testing
    */
  private[store] def selectAllResultRowsIO: IO[Seq[ResultRow]] = {
    runIO(Results.allRows.sortBy(x => x.itemVersion.desc).result)
  }

  def selectResultHistoryIO(queryId: QueryId):IO[Seq[Result]] = {
    runIO(
      Results.allRows.filter(_.queryId === queryId)
      .sortBy(_.createDate.asc)
      .sortBy(x => x.itemVersion.desc)
      .result
    ).map{rs: Seq[ResultRow] => rs.map{ r => r.toResult.get}}
  }

  def selectMostRecentResultsForQueryIO(queryId: QueryId):IO[Seq[Result]] = {
    runIO(Results.allRows.filter(r => r.queryId === queryId).result).map{x => x.groupBy(_.id)
      .map{ idAndResults: (ResultId, Seq[ResultRow]) =>
      idAndResults._2.maxBy(k => ResultStatus.namesToStatuses(k.status).stage).toResult
    }.map(_.get).toSeq}
  }

  private def selectMostRecentResult(resultId:ResultId):IO[Option[Result]] = {
    runIO(Results.allRows.filter(r => r.id === resultId).sortBy(_.itemVersion.desc).take(1).result.headOption).
      map(_.map(_.toResult.get))
  }

  /**
   * @param lastUpdateBefore Skip IDs more recent than this one
   * @param batchNumber Batch n, starting with 0. Skips batchNumber * batchSize
   * @param batchSize Number of IDs to consider at a time
   * @return (batchSize or fewer Results that are overdue,true if this is the last batch)
   */
  def selectOverdueBatchIO(lastUpdateBefore:DateStamp, after:DateStamp, batchNumber:Int, batchSize:Int) = {
    val idsIO: IO[Seq[ResultId]] = runIO(
      Results.allRows
        .filter(_.changeDate < lastUpdateBefore)
        .filter(_.changeDate > after)
        .drop(batchNumber*batchSize)
        .take(batchSize)
        .map(_.id)
        .result
    ).map(ids => ids.distinct) //distinct ids in this batch - not in the whole table. See SHRINE2020-1711

    idsIO.flatMap{ ids:Seq[ResultId] =>
      import cats.implicits._
      ids.map{ id => //one result at a time
        runIO(Results.allRows.filter(_.id === id).sortBy(_.itemVersion.desc).take(1).result).map(_.head)
      }.toList.sequence
    }.map{ rows => (
      rows.filter(r => r.changeDate.underlying < lastUpdateBefore.underlying).map(_.toResult.get).filterNot(_.status.isFinal),
      rows.isEmpty //true if this is the last batch - because no rows were found
    )
    }
  }

  /**
   * Only for testing
   */
  private[store] def selectAllQueriesIO: IO[Seq[Try[ShrineQuery]]] = {
    runIO(Queries.allRows.result).map{
      _.groupBy(_.id).map { idAndQueries: (QueryId, Seq[QueryRow]) =>
      idAndQueries._2.maxBy(_.itemVersion.underlying)
    }.toSeq.map(_.toQuery)}
  }

  def selectQueryIO(id: QueryId):IO[Option[ShrineQuery]] = {
    runIO(Queries.allRows.filter(_.id === id).sortBy(_.itemVersion.desc).take(1).result).map{
      case Seq(queryRow) => Some(queryRow.toQuery.get)
      case Seq() => None
      case x => throw HubDatabaseAssertException(s"Expected zero or one query for id $id, selected ${x.size} $x")
    }
  }

  def selectQueryIdsIO(start:DateStamp,end:DateStamp):IO[Seq[QueryId]] = {
    runIO(Queries.allRows
        .filter(_.createDate >= start).filter(_.createDate < end)
        .sortBy(_.createDate.asc)
        .map(_.id)
        .result
    ).map(_.distinct)
  }

  def selectQueryIdsForResearcherIdsIO(start: DateStamp, end: DateStamp, researcherIds: Seq[ResearcherId]): IO[Seq[QueryId]] = {
    runIO(
      Queries.allRows
      .filter(_.createDate >= start).filter(_.createDate < end).filter(_.researcherId.inSet(researcherIds))
      .sortBy(_.createDate.asc)
      .map(_.id)
      .result
    ).map(_.distinct)
  }

  def selectQueriesWithIdsIO(ids:Iterable[QueryId]):IO[Seq[ShrineQuery]] = {
    runIO(
      Queries.mostRecentRows
        .filter(_.id.inSet(ids))
        .result
    ).map{qrs => qrs.map(qr => qr.toQuery.get)}
  }

  def selectResultsWithQueryIdsIO(ids: Iterable[QueryId]): IO[Seq[Result]] = {
    runIO(
      Results.mostRecentRows
        .filter(_.queryId.inSet(ids))
        .result
    ).map { rrs => rrs.map(rr => rr.toResult.get) }
  }

  def selectResearcherQueryIdsAndStartTimesIO(start: DateStamp, end: DateStamp): IO[Seq[(ResearcherId,QueryId,DateStamp)]] = {
    runIO(Queries.allRows
      .filter(_.createDate >= start).filter(_.createDate < end)
      .sortBy(_.createDate.asc)
      .map(q => (q.researcherId,q.id,q.createDate))
      .result
    ).map(_.distinct)
  }

  def selectQueryHistoryIO(queryId: QueryId):IO[Seq[ShrineQuery]] = {
    runIO(Queries.allRows
      .filter(_.id === queryId)
      .sortBy(_.createDate.asc)
      .sortBy(x => x.itemVersion.desc).result)
      .map{qs: Seq[QueryRow] => qs.map{ q => q.toQuery.get}}
  }

  //todo deal with the try here so you can have IO[Iterable[Node]]
  def selectLatestNodesIO: IO[Iterable[Try[Node]]] = {
    runIO(Nodes.allRows.result).map{x => x.groupBy(_.id).map{idAndNodes: (NodeId,Seq[NodeRow]) =>
      idAndNodes._2.maxBy(_.itemVersion.underlying).toNode
    }}
  }

  def selectNodeByKeyIO(nodeKey:NodeKey):IO[Option[Node]] = {
    runIO(Nodes.allRows.filter(_.key === nodeKey).sortBy(_.itemVersion.desc).take(1).result).map{
      case Seq() => None
      case Seq(nodeRow) => Some(nodeRow.toNode.get)
      case x => throw HubDatabaseAssertException(s"Expected exactly one node for key $nodeKey, selected ${x.size} $x")
    }
  }

  def selectNodeIO(nodeId:NodeId): IO[Option[Node]] = {
    runIO(Nodes.allRows.filter(_.id === nodeId).sortBy(_.itemVersion.desc).take(1).result).map{
      case Seq() => None
      case Seq(nodeRow) => Some(nodeRow.toNode.get)
      case x => throw HubDatabaseAssertException(s"Expected exactly one node for id $nodeId, selected ${x.size} $x")
    }
  }


  def selectNodesByIdIO(nodeIds: Seq[NodeId]): IO[Iterable[Try[Node]]] = {
    runIO(Nodes.allRows.filter(_.id inSet nodeIds).result.map { rows: Iterable[NodeRow] =>
      rows.groupBy(_.id).map(grouped => grouped._2.maxBy( q => q.itemVersion.underlying).toNode)
    })
  }

  def selectLatestNodeSystemSpecsIO: IO[Iterable[Try[NodeSystemSpec]]] = {
    runIO(NodeSystemSpecs.allRows.result).map { x =>
      x.groupBy(_.id).map { idAndNodes: (NodeId, Seq[NodeSystemSpecRow]) =>
        idAndNodes._2.maxBy(_.itemVersion.underlying).toNodeSystemSpec
      }
    }
  }

  def selectTheNetworkIO:IO[Network] = {
    runIO(Networks.allRows.sortBy(_.itemVersion.desc).take(1).result).map{
      case Seq() => throw HubDatabaseNetworkNotFoundException(s"Expected exactly one network, selected none")
      case Seq(network) => network.toNetwork.get
      case x => throw HubDatabaseAssertException(s"Expected exactly one network, selected ${x.size} $x")
    }
  }

  def selectResearcherByIdIO(researcherId: ResearcherId): IO[Option[Researcher]] = {
    runIO(Researchers.allRows.filter(_.id === researcherId).sortBy(_.itemVersion.desc).take(1).result.headOption).
      map( _.map(_.toResearcher.get))
  }

  def selectAllResearchersIO:IO[Seq[Researcher]] = {
    runIO(Researchers.allRows.result).map {
      _.groupBy(_.id).map { idAndResearchers: (ResearcherId, Seq[ResearcherRow]) =>
        idAndResearchers._2.maxBy(_.itemVersion.underlying)
      }.toSeq.map(_.toResearcher.get)
    }
  }

  /**
   * @return
   */
  def updateResultIO(updateResult:UpdateResult):IO[Option[Result]] = {
    //This should work but could detect two results trying to be the same version - a real race condition
    runTransactionIO(upsertItem(updateResult.result, Results).asTry). //This makes an IO[Try[Option[Result]]]
      flatMap { triedUpsert: Try[Option[Result]] => //Use the Try
        val retryThing: Try[IO[Option[Result]]] = triedUpsert.transform ({ x =>
          Try(IO(x)) //It worked, but needs a way back to IO
        },{ //Maybe recover
          case ivrlx: ItemVersionRaceLostException[_, _, _] => //See if shrine should try to fix the race condition
            val retryIO: IO[Option[Result]] = selectMostRecentResult(updateResult.result.id).flatMap { maybeFoundResult =>
              val foundResult = maybeFoundResult.getOrElse(throw ivrlx)
              //Use .stage to prefer CRC results, then errors, last the most progressed result progress
              if (updateResult.result.status.stage > foundResult.status.stage) {
                val tweakedUpdate: UpdateResult = updateResult.withRaceConditionResult(foundResult)
                info(s"Recover from ${ivrlx.getClass.getSimpleName} ${ivrlx.getMessage} with race condition Result $tweakedUpdate")
                updateResultIO(tweakedUpdate) //recurse to try to insert with the new itemVersion
              } else {
                info(s"Recover from ${ivrlx.getClass.getSimpleName} ${ivrlx.getMessage} by ignoring update")
                IO(None) //The result that already has this version number is at a later stage
              }
            }
            Try(retryIO)
        })
        retryThing.get
      }
  }

//todo delete with SHRINE2020-1365
  def updateCrcQueuedResultIO(updateResult:UpdateCrcQueuedResult): IO[Option[Result]] = runTransactionIO{
    Nodes.allRows.filter(_.key === updateResult.adapterNodeKey).sortBy(_.itemVersion.desc).take(1).result.head.
      flatMap{adapterNode => Results.allRows.filter(_.queryId === updateResult.queryId).filter(_.adapterNodeId === adapterNode.id).sortBy(_.itemVersion.desc).take(1).result.headOption}.
      flatMap{ maybeOldResult =>
        maybeOldResult.map{ oldResult =>
          oldResult.toResult.get match {
            case oldResultProgress:ResultProgress =>
              if(!oldResultProgress.status.before(updateResult.status))
                upsertItem(updateResult.updatedResult(oldResultProgress),Results)
              else {
                warn(s"$updateResult arrived after $oldResultProgress. Ignored.")
                DBIO.successful(None)
              }
            case finalResult:Result if finalResult.status.isFinal =>
              warn(s"$updateResult arrived after $finalResult. Ignored.")
              DBIO.successful(None)
            case illegalResult:Result =>
              val illegalStateException = new IllegalStateException(s"result is a ${illegalResult.getClass}, which should either be a ResultProgress or a final result. $illegalResult")
              illegalStateException.fillInStackTrace()
              error(s"$updateResult arrived after $illegalResult. Ignored.",illegalStateException)
              DBIO.successful(None)
          }
        }.getOrElse{
          warn(s"$updateResult for an unknown result. Ignored.")
          DBIO.successful(None)
        }
      }
  }

  // adding a type annotation - : IO[Option[ShrineQuery]] - fails compiling! todo Check after move to Scala 3
  def upsertQueryForUpdateIO(updateQuery:UpdateQueryAtHub) = runTransactionIO{

    Queries.allRows.
      filter(_.id === updateQuery.queryId).
      sortBy(_.itemVersion.desc).take(1).result.flatMap {
      case Seq(queryRow: QueryRow) =>
        val updatedQuery = updateQuery.updatedQuery(queryRow.toQuery.get)
        upsertItem(updatedQuery, Queries)
      case Seq() => DBIO.successful(None) //no query was found to update. The hub doesn't know anything about this query
    }
  }

  //todo maybe combine upsertQueryForUpdateIO and upsertQueryForUpdateIO
  // adding a type annotation - : IO[Option[ShrineQuery]] - fails compiling! todo Check after move to Scala 3
  def upsertQueryForUpdateIO(updateQuery:UpdateQueryAtQep) = runTransactionIO{

    Queries.allRows.
      filter(_.id === updateQuery.queryId).
      sortBy(_.itemVersion.desc).take(1).result.flatMap {
      case Seq(queryRow: QueryRow) =>
        val updatedQuery = updateQuery.updatedQuery(queryRow.toQuery.get)
        upsertItem(updatedQuery, Queries)
      case Seq() => DBIO.successful(None) //no query was found to update. The hub doesn't know anything about this query
    }
  }

  def upsertQueryReadyForAdaptersIO(updateQuery:UpdateQueryReadyForAdapters): IO[Seq[Option[Result]]] = runTransactionIO{

    Queries.allRows.
      filter(_.id === updateQuery.queryId).
      sortBy(_.itemVersion.desc).take(1).result.flatMap {
      case Seq(queryRow: QueryRow) =>
        val updatedQuery = updateQuery.updatedQuery(queryRow.toQuery.get)
        upsertItem(updatedQuery, Queries)
      case Seq() => DBIO.successful(None) //no query was found to update. The hub doesn't know anything about this query
    }.flatMap{ _ =>
      DBIO.sequence(updateQuery.resultProgresses.map(upsertItem(_,Results)))
    }
  }

  def updateNodeSystemSpecIO(update:UpdateNodeSystemSpec): IO[Option[NodeSystemSpec]] = {
    //get the latest
    val maybePreviousNodeSystemSpecIO: IO[Option[NodeSystemSpec]] = runIO(NodeSystemSpecs.allRows.filter(ns => ns.id === update.nodeSystemSpec.id).sortBy(_.itemVersion.desc).take(1).result.headOption).
      map(_.map(_.toNodeSystemSpec.get))

    //copy the createDate to the update
    maybePreviousNodeSystemSpecIO.flatMap{maybePreviousNodeSystemSpec: Option[NodeSystemSpec] =>
      val updateVersionInfo: VersionInfo = update.nodeSystemSpec.versionInfo
      val correctVersionInfo: VersionInfo = maybePreviousNodeSystemSpec.map{ previous =>
        val (itemVersion,changeDate) = if(previous.equalsExceptMetadata(update.nodeSystemSpec))
          (previous.versionInfo.itemVersion,previous.versionInfo.changeDate)
        else
          (ItemVersion.next(previous.versionInfo.itemVersion),updateVersionInfo.changeDate)

        updateVersionInfo.copy(
          createDate = previous.versionInfo.createDate,
          itemVersion = itemVersion,
          changeDate = changeDate,
        )}.getOrElse(updateVersionInfo)
      val correctNodeSystemSpec = update.nodeSystemSpec.copy(versionInfo = correctVersionInfo)

      runTransactionIO{upsertItem(correctNodeSystemSpec,NodeSystemSpecs)}
    }
  }

  //Reusable query parts
  private def incrementTableVersion(name:String) = {
    for {
      tvs: Seq[TableVersion] <- allTableVersions.filter(_.name === name).result
      updateOut: Int <- if(tvs.isEmpty){
                          allTableVersions += TableVersion(name,0)
                        } else {
                          allTableVersions.insertOrUpdate(tvs.head.next) }
    } yield {
      if(tvs.size > 1) throw IncrementTableVersionException(s"""Expected zero or one TableVersion named "$name", but found ${tvs.size} : ${tvs.mkString(", ")}""")
      if(updateOut != 1) throw IncrementTableVersionException(s"""Expected exactly one row of TableVersions to be updated, not $updateOut""")
      updateOut
    }
  }
}



object HubDb {

  val dataSource:DataSource = TestableDataSourceCreator.dataSource(HubSchema.config)

  val db: HubDb = HubDb(HubSchema.schema,dataSource)

  val createTablesOnStart: Boolean = HubSchema.config.getBoolean("createTablesOnStart")
  if(createTablesOnStart) db.createTables()

}

/**
  * Separate class to support schema generation without actually connecting to the database.
  *
  * @param jdbcProfile Database profile to use for the schema
  */
case class HubSchema(jdbcProfile: JdbcProfile) extends Loggable {
  import jdbcProfile.api._

  val allTableVersions = TableQuery[TableVersions]

  def ddlForAllTables: jdbcProfile.DDL = {
    allTableVersions.schema ++
      Results.allRows.schema ++
      Queries.allRows.schema ++
      Nodes.allRows.schema ++
      NodeSystemSpecs.allRows.schema ++
      Networks.allRows.schema ++
      Researchers.allRows.schema
  }

  //to get the schema, use the REPL
  //println(QepQuerySchema.schema.ddlForAllTables.createStatements.mkString(";\n"))

  def createTables(database:Database): Unit = {
    try {
      val future = database.run(ddlForAllTables.create)
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

  implicit val ResultIdColumnType: JdbcType[ResultId] with BaseTypedType[ResultId] = MappedColumnType.base[ResultId, Long](
    resultId => resultId.underlying,
    long => new ResultId(long)
  )

  implicit val NodeIdColumnType: JdbcType[NodeId] with BaseTypedType[NodeId] = MappedColumnType.base[NodeId, Long](
    NodeId => NodeId.underlying,
    long => new NodeId(long)
  )

  implicit val NetworkIdColumnType: JdbcType[NetworkId] with BaseTypedType[NetworkId] = MappedColumnType.base[NetworkId, Long](
    NetworkId => NetworkId.underlying,
    long => new NetworkId(long)
  )

  implicit val ResearcherIdColumnType: JdbcType[ResearcherId] with BaseTypedType[ResearcherId] = MappedColumnType.base[ResearcherId, Long](
    ResearcherId => ResearcherId.underlying,
    long => new ResearcherId(long)
  )
  implicit val QueryIdColumnType: JdbcType[QueryId] with BaseTypedType[QueryId] = MappedColumnType.base[QueryId, Long](
    QueryId => QueryId.underlying,
    long => new QueryId(long)
  )

  implicit val ItemVersionColumnType: JdbcType[ItemVersion] with BaseTypedType[ItemVersion] = MappedColumnType.base[ItemVersion, Int](
    ItemVersion => ItemVersion.underlying,
    int => new ItemVersion(int)
  )

  implicit val ProtocolVersionColumnType: JdbcType[ProtocolVersion] with BaseTypedType[ProtocolVersion] = MappedColumnType.base[ProtocolVersion, Int](
    ProtocolVersion => ProtocolVersion.underlying,
    v => new ProtocolVersion(v)
  )

  implicit val DateStampColumnType: JdbcType[DateStamp] with BaseTypedType[DateStamp] = MappedColumnType.base[DateStamp, Long](
    DateStamp => DateStamp.underlying,
    long => new DateStamp(long)
  )

  implicit val JsonTextColumnType: JdbcType[JsonText] with BaseTypedType[JsonText] = MappedColumnType.base[JsonText, String](
    JsonText => JsonText.underlying,
    text => new JsonText(text)
  )

  implicit val NodeKeyColumnType: JdbcType[NodeKey] with BaseTypedType[NodeKey] = MappedColumnType.base[NodeKey, String](
    NodeKey => NodeKey.underlying,
    text => new NodeKey(text)
  )

  class TableVersions(tag:Tag) extends Table[TableVersion](tag,"TABLE_VERSION") {
    def name = column[String]("NAME", O.PrimaryKey)
    def version = column[Int]("VERSION")

    def * = (name,version) <> ((TableVersion.apply _).tupled,TableVersion.unapply)
  }

  abstract class ItemTable[I <: Id,R <:Row[I]](tag:Tag, tableName:String, @unused toId: Long => I) extends Table[R](tag,tableName) {
    def id:Rep[I]
    def protocolVersion = column[ProtocolVersion]("PROTOCOL_VERSION")
    def itemVersion = column[ItemVersion]("ITEM_VERSION")
    def createDate = column[DateStamp]("CREATE_DATE")
    def changeDate = column[DateStamp]("CHANGE_DATE")
    def jsonText = column[JsonText]("JSON")
  }

  /**
    * Gathers up all the specifics for storing this particular kind of common currency data.
    *
    * @tparam I The Id subtype to use
    * @tparam R The Row subtype to use
    * @tparam V The common currency data type
    * @tparam Items The table to store rows in
    */
  trait ItemTableCompanion[
    I <: Id,
    R <: Row[I],
    V <: Versioned[I],
    Items <: ItemTable[I,R]
  ] {
    def tableName:String
    def allRows:TableQuery[Items]
    def itemToRow: V => R
    def longToId: Long => I
  }

  class Results(tag:Tag) extends ItemTable[ResultId,ResultRow](tag,Results.tableName,new ResultId(_)) {
    def id = column[ResultId]("ID")
    def queryId = column[QueryId]("QUERY_ID")
    def adapterNodeId = column[NodeId]("NODE_ID")
    def status = column[String]("STATUS")

    def * = (id,protocolVersion,itemVersion,createDate,changeDate,jsonText,queryId,adapterNodeId,status) <> ((ResultRow.apply _).tupled,ResultRow.unapply)
  }

  object Results extends ItemTableCompanion[ResultId,ResultRow,Result,Results]{
    override def tableName: String = "HUB_QUERY_RESULT"
    override def allRows:TableQuery[Results] = TableQuery[Results]
    override def itemToRow: Result => ResultRow = ResultRow.fromResult
    override def longToId: Long => ResultId = new ResultId(_)

    //todo if you can figure out how to generalize === then move this to the superclass
    def mostRecentRows: Query[Results, ResultRow, Seq] = for (
      rows <- allRows if !allRows.filter(_.id === rows.id).filter(_.itemVersion > rows.itemVersion).exists
    ) yield rows
  }

  class Queries(tag:Tag) extends ItemTable[QueryId,QueryRow](tag,Queries.tableName,new QueryId(_)) {
    def id = column[QueryId]("ID")
    def researcherId = column[ResearcherId]("RESEARCHER_ID")

    def status = column[String]("STATUS")

    def * = (id,protocolVersion,itemVersion,createDate,changeDate,jsonText,researcherId,status) <> ((QueryRow.apply _).tupled,QueryRow.unapply)
  }

  object Queries extends ItemTableCompanion[QueryId,QueryRow,ShrineQuery,Queries]{
    override def tableName: String = "QUERY"
    override def allRows:TableQuery[Queries] = TableQuery[Queries]
    override def itemToRow: ShrineQuery => QueryRow = QueryRow.fromQuery
    override def longToId: Long => QueryId = new QueryId(_)

    //todo if you can figure out how to generalize === then move this to the superclass
    def mostRecentRows: Query[Queries, QueryRow, Seq] = for (
      rows <- allRows if !allRows.filter(_.id === rows.id).filter(_.itemVersion > rows.itemVersion).exists
    ) yield rows
  }

  class Nodes(tag:Tag) extends ItemTable[NodeId,NodeRow](tag,Nodes.tableName,new NodeId(_)) {
    def id = column[NodeId]("ID")
    def key = column[NodeKey]("KEY")

    def * = (id,protocolVersion,itemVersion,createDate,changeDate,jsonText,key) <> ((NodeRow.apply _).tupled,NodeRow.unapply)
  }

  object Nodes extends ItemTableCompanion[NodeId,NodeRow,Node,Nodes]{
    override def tableName: String = "NODE"
    override def allRows:TableQuery[Nodes] = TableQuery[Nodes]
    override def itemToRow: Node => NodeRow = NodeRow.fromNode
    override def longToId: Long => NodeId = new NodeId(_)
  }


  class NodeSystemSpecs(tag: Tag) extends ItemTable[NodeId, NodeSystemSpecRow](tag, NodeSystemSpecs.tableName, new NodeId(_)) {
    def id = column[NodeId]("ID")

    def key = column[NodeKey]("KEY")

    def * = (id, protocolVersion, itemVersion, createDate, changeDate, jsonText, key) <> ((NodeSystemSpecRow.apply _).tupled, NodeSystemSpecRow.unapply)
  }

  object NodeSystemSpecs extends ItemTableCompanion[NodeId, NodeSystemSpecRow, NodeSystemSpec, NodeSystemSpecs] {
    override def tableName: String = "NODE_SYSTEM_SPEC"

    override def allRows: TableQuery[NodeSystemSpecs] = TableQuery[NodeSystemSpecs]

    override def itemToRow: NodeSystemSpec => NodeSystemSpecRow = NodeSystemSpecRow.fromNodeSystemSpec

    override def longToId: Long => NodeId = new NodeId(_)
  }

  class Networks(tag:Tag) extends ItemTable[NetworkId,NetworkRow](tag,Networks.tableName,new NetworkId(_)) {
    def id = column[NetworkId]("ID")

    def * = (id,protocolVersion,itemVersion,createDate,changeDate,jsonText) <> ((NetworkRow.apply _).tupled,NetworkRow.unapply)
  }

  object Networks extends ItemTableCompanion[NetworkId,NetworkRow,Network,Networks]{
    override def tableName: String = "NETWORK"
    override def allRows:TableQuery[Networks] = TableQuery[Networks]
    override def itemToRow: Network => NetworkRow = NetworkRow.fromNetwork
    override def longToId: Long => NetworkId = new NetworkId(_)
  }

  class Researchers(tag:Tag) extends ItemTable[ResearcherId,ResearcherRow](tag,Researchers.tableName,new ResearcherId(_)) {
    def id = column[ResearcherId]("ID")

    def * = (id,protocolVersion,itemVersion,createDate,changeDate,jsonText) <> ((ResearcherRow.apply _).tupled,ResearcherRow.unapply)
  }

  object Researchers extends ItemTableCompanion[ResearcherId,ResearcherRow,Researcher,Researchers]{
    override def tableName: String = "RESEARCHER"
    override def allRows:TableQuery[Researchers] = TableQuery[Researchers]
    override def itemToRow: Researcher => ResearcherRow = ResearcherRow.fromResearcher
    override def longToId: Long => ResearcherId = new ResearcherId(_)
  }
}

object HubSchema {

  val configProp = "shrine.hub.database"
  val config: Config = ConfigSource.config.getConfig(configProp)

  Log.info(s"HUB's config property = $configProp")
  Log.info("HUB's database config:")
  Log.info(s"$config")


  val slickProfile:JdbcProfile = TestableDataSourceCreator.slickDriver(config)

  val schema: HubSchema = HubSchema(slickProfile)
}

case class TableVersion(
                       name:String,
                       count:Int
                       ) {
  def next: TableVersion = this.copy(count = this.count+1)
}

case class IncrementTableVersionException(message:String) extends Exception(message)

/**
  * The common trait for rows of data in the common currency
  */
trait Row[I <: Id] {
  def id:I
  def itemVersion:ItemVersion
}

case class ResultRow (
                      id:ResultId,
                      protocolVersion:ProtocolVersion,
                      itemVersion:ItemVersion,
                      createDate:DateStamp,
                      changeDate:DateStamp,
                      jsonText:JsonText,
                      queryId: QueryId,
                      adapterNodeId: NodeId,
                      status:String
                    ) extends Row[ResultId] {

  def toResult: Try[Result] = Result.tryRead(jsonText)
}

object ResultRow {
  def fromResult(result: Result): ResultRow = ResultRow (
      id = result.id,
      protocolVersion = result.versionInfo.protocolVersion,
      itemVersion = result.versionInfo.itemVersion,
      createDate = result.versionInfo.createDate,
      changeDate = result.versionInfo.changeDate,
      jsonText = result.asJsonText,
      queryId = result.queryId,
      adapterNodeId = result.adapterNodeId,
      status = result.status.statusName
  )
}

case class QueryRow (
                       id:QueryId,
                       protocolVersion:ProtocolVersion,
                       itemVersion:ItemVersion,
                       createDate:DateStamp,
                       changeDate:DateStamp,
                       jsonText:JsonText,
                       researcherId: ResearcherId,
                       status:String
                     ) extends Row[QueryId] {

  def toQuery: Try[ShrineQuery] = ShrineQuery.tryRead(jsonText)
}

object QueryRow {
  def fromQuery(query: ShrineQuery): QueryRow = QueryRow (
    id = query.id,
    protocolVersion = query.versionInfo.protocolVersion,
    itemVersion = query.versionInfo.itemVersion,
    createDate = query.versionInfo.createDate,
    changeDate = query.versionInfo.changeDate,
    jsonText = query.asJsonText,
    researcherId = query.researcherId,
    status = query.status.statusName
  )
}

case class NodeRow (
                       id:NodeId,
                       protocolVersion:ProtocolVersion,
                       itemVersion:ItemVersion,
                       createDate:DateStamp,
                       changeDate:DateStamp,
                       jsonText:JsonText,
                       key: NodeKey
                     ) extends Row[NodeId] {

  def toNode: Try[Node] = Node.tryRead(jsonText)
}

object NodeRow {
  def fromNode(result: Node): NodeRow = NodeRow (
    id = result.id,
    protocolVersion = result.versionInfo.protocolVersion,
    itemVersion = result.versionInfo.itemVersion,
    createDate = result.versionInfo.createDate,
    changeDate = result.versionInfo.changeDate,
    jsonText = result.asJsonText,
    key = result.key
  )
}

case class NodeSystemSpecRow (
                               id:NodeId,
                               protocolVersion:ProtocolVersion,
                               itemVersion:ItemVersion,
                               createDate:DateStamp,
                               changeDate:DateStamp,
                               jsonText:JsonText,
                               key: NodeKey,
                             ) extends Row[NodeId] {

  def toNodeSystemSpec: Try[NodeSystemSpec] = NodeSystemSpec.tryRead(jsonText)
}

object NodeSystemSpecRow {
  def fromNodeSystemSpec(result: NodeSystemSpec): NodeSystemSpecRow = NodeSystemSpecRow (
    id = result.id,
    protocolVersion = result.versionInfo.protocolVersion,
    itemVersion = result.versionInfo.itemVersion,
    createDate = result.versionInfo.createDate,
    changeDate = result.versionInfo.changeDate,
    jsonText = result.asJsonText,
    key = result.key
  )
}

case class NetworkRow (
                     id:NetworkId,
                     protocolVersion:ProtocolVersion,
                     itemVersion:ItemVersion,
                     createDate:DateStamp,
                     changeDate:DateStamp,
                     jsonText:JsonText
                   ) extends Row[NetworkId] {

  def toNetwork: Try[Network] = Network.tryRead(jsonText)
}

object NetworkRow {
  def fromNetwork(result: Network): NetworkRow = NetworkRow (
    id = result.id,
    protocolVersion = result.versionInfo.protocolVersion,
    itemVersion = result.versionInfo.itemVersion,
    createDate = result.versionInfo.createDate,
    changeDate = result.versionInfo.changeDate,
    jsonText = result.asJsonText
  )
}

case class ResearcherRow (
                     id:ResearcherId,
                     protocolVersion:ProtocolVersion,
                     itemVersion:ItemVersion,
                     createDate:DateStamp,
                     changeDate:DateStamp,
                     jsonText:JsonText
                   ) extends Row[ResearcherId] {

  def toResearcher: Try[Researcher] = Researcher.tryRead(jsonText)
}

object ResearcherRow {
  def fromResearcher(result: Researcher): ResearcherRow = ResearcherRow (
    id = result.id,
    protocolVersion = result.versionInfo.protocolVersion,
    itemVersion = result.versionInfo.itemVersion,
    createDate = result.versionInfo.createDate,
    changeDate = result.versionInfo.changeDate,
    jsonText = result.asJsonText
  )
}

case class ItemVersionRaceLostException[I <:Id,V <: Versioned[I],R <: Row[I]](item:V,row:R,alreadyExists:Seq[R])
  extends Exception(s"""$row was not inserted for $item. ${alreadyExists.size} rows exist with id of ${item.id}, version ${item.versionInfo.itemVersion} :\n ${alreadyExists.mkString("\n")}""")

case class HubDatabaseAssertException(message:String) extends Exception(message)
case class HubDatabaseNetworkNotFoundException(message:String) extends Exception(message)
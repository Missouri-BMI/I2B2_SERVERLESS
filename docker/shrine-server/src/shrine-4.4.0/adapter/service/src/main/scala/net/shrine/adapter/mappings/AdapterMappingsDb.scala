package net.shrine.adapter.mappings

import cats.effect.{Async, IO}

import javax.sql.DataSource
import slick.jdbc.{GetResult, JdbcProfile}
import slick.lifted.ProvenShape
import fs2.Stream
import net.shrine.config.ConfigSource
import net.shrine.http4s.catsio.{ExecutionContexts, SimpleAsyncExecutor}
import net.shrine.log.{Log, Loggable}
import net.shrine.slick.TestableDataSourceCreator

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

object AdapterMappingsDb extends Loggable {

  lazy val db: AdapterMappingsDb = {
    import cats.effect.unsafe.implicits.global

    val configProp = "shrine.adapter.query.database"
    val queryDbConfig = ConfigSource.config.getConfig(configProp)
    // TODO: confirm whether or not logging the full db config is a security risk
    Log.info(s"Adapter's config property = $configProp")
    Log.info("Adapter's database config:")
    Log.info(s"$queryDbConfig")

    val dataSource: DataSource = TestableDataSourceCreator.dataSource(queryDbConfig)
    val profile: JdbcProfile = TestableDataSourceCreator.slickDriver(queryDbConfig)

    val db = AdapterMappingsDb(profile, dataSource)

    val createTablesOnStart: Boolean = queryDbConfig.getBoolean("createTablesOnStart")
    if (createTablesOnStart)
      db.createTables.unsafeRunSync()

    db
  }

  def reloadMappings(iter: Iterator[(String, String)], filename: String, lastModified: Long, checksum: Long): IO[Long] = for {
    _ <- AdapterMappingsDb.db.truncateMappings()
    _ <- IO(info(s"Old adapter mappings have been deleted. Inserting new mappings..."))
    upserted <- IO(logDuration("Reload mappings")(info(_)) {
      import cats.effect.unsafe.implicits.global
      AdapterMappingsDb.db.upsert(iter).unsafeRunSync() //todo clean up with SHRINE2020-1278
    })
    _ <- AdapterMappingsDb.db.updateMetaInformation(filename, lastModified, checksum)
  } yield upserted
}

case class AdapterMappingsDb(jdbcProfile: JdbcProfile, dataSource: DataSource) {
  import jdbcProfile.api._
  import jdbcProfile.backend.DatabaseDef

  val db:DatabaseDef = Database.forDataSource(
    ds = dataSource,
    maxConnections = None,
    executor = SimpleAsyncExecutor(ExecutionContexts.databaseExecutionContext)
  )

  def checksum: IO[Long] = metainformation.map(_.map(_.checksum).getOrElse(-1))
  def fileLastModified: IO[Long] = metainformation.map(_.map(_.fileLastModified).getOrElse(-1))
  def filename: IO[String] = metainformation.map(_.map(_.filename).getOrElse(""))
  def loadedFromFile: IO[Long] = metainformation.map(_.map(_.loadedFromFile).getOrElse(-1))

  private def metainformation: IO[Option[AdapterMappingsMetadataRow]] = runIO {
    adapterMappingsMetadataTableQuery.sortBy(_.loadedFromFile.desc).result
  }.map(_.headOption)

  def createTables: IO[Unit] = runIO {
    DBIO.seq(
      adapterMappingsTableQuery.schema.createIfNotExists,
      adapterMappingsMetadataTableQuery.schema.createIfNotExists,
    )
  }

  def dropTables: IO[Unit] = runIO {
    DBIO.seq(
      adapterMappingsTableQuery.schema.dropIfExists,
      adapterMappingsMetadataTableQuery.schema.dropIfExists,
    )
  }

  def truncateMappings(): IO[Int] = runIO(adapterMappingsTableQuery.delete)

  def countAll:IO[Int] = runIO(adapterMappingsTableQuery.size.result)

  def localTermsFor(shrineKey: String): IO[Set[String]] = getAllByShrineKey(shrineKey)

  def localTermsFor(shrineKeys: Seq[String]): IO[Map[String, Set[String]]] = getAllByShrineKeys(shrineKeys)

  def updateMetaInformation(filename: String, fileLastModified: Long, checksum: Long): IO[Int] = runIO {
    adapterMappingsMetadataTableQuery += AdapterMappingsMetadataRow(filename, fileLastModified, System.currentTimeMillis(), checksum)
  }

  def upsert(mappings: Iterable[(String, String)]): IO[Int] = upsert(mappings.iterator)

  def upsert(mappings: Iterator[(String, String)]): IO[Int] =
    upsertChunked(mappings.map(mapping => AdapterMappingsRow(mapping._1.getBytes, mapping._2.getBytes)), 1000, 15)

  //noinspection SameParameterValue
  private def upsertChunked(rows: Iterator[AdapterMappingsRow], chunkSize: Int, connections: Int): IO[Int] = {
    Stream.fromIterator(rows,chunkSize)(Async[IO])
      .chunkN(chunkSize)
      .parEvalMap(connections)(chunkedRows => IO.fromFuture(upsertRowsJdbc(chunkedRows.iterator)))
      .compile
      .fold(0)(_ + _)
  }

  private def upsertRowsJdbc(rows: Iterator[AdapterMappingsRow]): IO[Future[Int]] = IO {
    val sql = ConfigSource.config.getConfig("shrine.adapter").getString("adapterMappingsInsertStatement")

    val inserts = SimpleDBIO[Int] { session =>
      val statement = session.connection.prepareStatement(sql)
      rows.foreach { row =>
        statement.setBytes(1, row.shrineKey)
        statement.setBytes(2, row.adapterKey)
        statement.addBatch()
      }

      statement.executeBatch().sum
    }

    db.run(inserts.transactionally)
  }

  private def runIO[R](dbio: DBIOAction[R, NoStream, _]): IO[R] =
    IO.fromFuture(IO.blocking(db.run(dbio)))

  private def getAllByShrineKey(shrineKey: String): IO[Set[String]] = runIO(lookupByShrineKey(shrineKey))

  //todo what happens if result sets size doesn't match shrineKeys ?? Or a resultSets is empty ?
  private def getAllByShrineKeys(shrineKeys: Seq[String]): IO[Map[String, Set[String]]] = runIO {
    implicit val ex: ExecutionContext = ExecutionContexts.databaseExecutionContext

    DBIO.sequence(shrineKeys.map(p => lookupByShrineKey(p).asTry.flatMap {
      case Success(v) => DBIO.successful(v)
      case Failure(e) => Log.error(s"Failed getAllByShrineKeys for $p: $e");
        DBIO.failed(e)
    }))
  }.map(resultSets => shrineKeys.zip(resultSets).toMap)

  private def lookupByShrineKey(shrineKey: String):DBIO[Set[String]] = {

    implicit val ex: ExecutionContext = ExecutionContexts.databaseExecutionContext

    implicit val getAdapterKeyResult = GetResult(r => r.nextBytes())

    import slick.jdbc.{SetParameter, PositionedParameters}

    implicit object SetByteArray extends SetParameter[Array[Byte]] {
      def apply(v: Array[Byte], pp: PositionedParameters): Unit = {
        pp.setBytes(v)
      }
    }

    val findAdapterKeySql = sql"""
      SELECT ADAPTER_KEY FROM ADAPTER_MAPPING WHERE SHRINE_KEY=${shrineKey.getBytes}
    """.as[Array[Byte]]

    findAdapterKeySql.map(_.toSet)
      .map((s: Set[Array[Byte]]) => s.map(ba => new String(ba)) )
  }

  private val adapterMappingsTableQuery = TableQuery[AdapterMappingsTable]
  private val adapterMappingsMetadataTableQuery = TableQuery[AdapterMappingsMetadataTable]

  private case class AdapterMappingsTable(tag: Tag) extends Table[AdapterMappingsRow](tag, "ADAPTER_MAPPING") {
    //this table uses byte arrays - not Strings - to avoid a bug in H2's over-handling of escape characters - i2b2's path separators - in query strings
    def shrineKey:Rep[Array[Byte]] = column[Array[Byte]]("SHRINE_KEY")
    def adapterKey:Rep[Array[Byte]] = column[Array[Byte]]("ADAPTER_KEY")

    override def * :ProvenShape[AdapterMappingsRow] = (shrineKey, adapterKey) <> (AdapterMappingsRow.tupled, AdapterMappingsRow.unapply)
  }

  private case class AdapterMappingsMetadataTable(tag: Tag) extends Table[AdapterMappingsMetadataRow](tag, "ADAPTER_MAPPING_META") {
    private def filename:Rep[String] = column[String]("FILENAME")
    private def fileLastModified:Rep[Long] = column[Long]("FILE_LAST_MODIFIED")
    def loadedFromFile:Rep[Long] = column[Long]("LOADED_FROM_FILE")
    private def checksum:Rep[Long] = column[Long]("CHECKSUM")

    override def * :ProvenShape[AdapterMappingsMetadataRow] = (filename, fileLastModified, loadedFromFile, checksum) <> (AdapterMappingsMetadataRow.tupled, AdapterMappingsMetadataRow.unapply)
  }

  private case class AdapterMappingsMetadataRow(filename: String, fileLastModified: Long, loadedFromFile: Long, checksum: Long)

  private case class AdapterMappingsRow(shrineKey: Array[Byte], adapterKey: Array[Byte])
}
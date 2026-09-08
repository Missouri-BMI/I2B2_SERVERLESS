package net.shrine.qep.audit

import java.sql.SQLException
import cats.effect.IO

import javax.sql.DataSource
import com.typesafe.config.Config
import net.shrine.audit.{LongQueryId, QueryName, ShrineNodeId, Time, UserName}
import net.shrine.log.{Log, Loggable}
import net.shrine.slick.TestableDataSourceCreator
import net.shrine.config.ConfigSource
import net.shrine.http4s.catsio.{ExecutionContexts, SimpleAsyncExecutor}
import slick.jdbc.JdbcProfile

import scala.concurrent.duration.{Duration, DurationInt}
import scala.concurrent.{Await, Future}
import scala.language.postfixOps

/**
 * DB code for the QEP audit metrics.
 *
 * @author david
 * @since 8/18/15
 */
case class QepAuditDb(schemaDef:QepAuditSchema,dataSource: DataSource) extends Loggable {
  import schemaDef._
  import jdbcProfile.api._
  import schemaDef.jdbcProfile.backend.DatabaseDef

  val database:DatabaseDef = Database.forDataSource(
    ds = dataSource,
    maxConnections = None,
    executor = SimpleAsyncExecutor(ExecutionContexts.databaseExecutionContext)
  )

  def createTables(): Unit = schemaDef.createTables(database)

  def dropTables(): Unit = schemaDef.dropTables(database)

  private def run[R](dbio: DBIOAction[R, NoStream, _]): Future[R] = {
    database.run(dbio)
  }

  private def runIO[R](dbio: DBIOAction[R, NoStream, _]): IO[R] = {
    IO.fromFuture(IO.blocking(run(dbio)))
  }
  private def runTransactionIO[R](dbio: DBIOAction[R, NoStream, _]): IO[R] = {
    IO.fromFuture(IO.blocking(database.run(dbio.transactionally)))
  }

  def insertQepQueryIO(qepQueryAuditData: QepQueryAuditData):IO[Int] = {
    debug(s"insertQepQuery $qepQueryAuditData")

    runIO(allQepQueryQuery += qepQueryAuditData)
  }

  def selectAllQepQueriesIO: IO[Seq[QepQueryAuditData]] = {
    runIO(allQepQueryQuery.result)
  }

}

object QepAuditDb extends Loggable {

  val dataSource:DataSource = TestableDataSourceCreator.dataSource(QepAuditSchema.config)

  val db: QepAuditDb = QepAuditDb(QepAuditSchema.schema,dataSource)

  val createTablesOnStart: Boolean = QepAuditSchema.config.getBoolean("createTablesOnStart")
  if(createTablesOnStart) QepAuditDb.db.createTables()

}

/**
 * Separate class to support schema generation without actually connecting to the database.
 *
 * @param jdbcProfile Database profile to use for the schema
 */
case class QepAuditSchema(jdbcProfile: JdbcProfile) extends Loggable {
  import jdbcProfile.api._

  def ddlForAllTables: jdbcProfile.DDL = {
    allQepQueryQuery.schema
  }

  //to get the schema, use the REPL
  //println(QepAuditSchema.schema.ddlForAllTables.createStatements.mkString(";\n"))

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

  class QueriesSent(tag:Tag) extends Table[QepQueryAuditData](tag,"QUERY_SENT") {
    def shrineNodeId = column[ShrineNodeId]("SHRINE_NODE_ID")
    def userName = column[UserName]("USER_NAME")
    def queryId = column[LongQueryId]("NETWORK_QUERY_ID")
    def queryName = column[QueryName]("QUERY_NAME")
    def timeQuerySent = column[Time]("TIME_QUERY_SENT")

    def * = (shrineNodeId,userName,queryId,queryName,timeQuerySent) <> (QepQueryAuditData.tupled,QepQueryAuditData.unapply)

  }
  val allQepQueryQuery = TableQuery[QueriesSent]
}

object QepAuditSchema {

  val configProp = "shrine.qep.database"

  Log.info("QEP's config property")
  Log.info(configProp)

  val config:Config = ConfigSource.config.getConfig(configProp)
  Log.info("QEP's database config:")
  Log.info(config.toString)

  val slickProfile:JdbcProfile = TestableDataSourceCreator.slickDriver(config)

  val schema: QepAuditSchema = QepAuditSchema(slickProfile)
}


/**
 * Container for QEP audit data for ACT metrics
 *
 * @author david
 * @since 8/17/15
 */
case class QepQueryAuditData(
                              shrineNodeId:ShrineNodeId,
                              userName:UserName,
                              queryId:LongQueryId,
                              queryName:QueryName,
                              timeQuerySent:Time
                            ) {}

object QepQueryAuditData extends ((
                                    ShrineNodeId,
                                    UserName,
                                    LongQueryId,
                                    QueryName,
                                    Time
                                  ) => QepQueryAuditData) {

  def apply(
             shrineNodeId:String,
             userName:String,
             queryId:Long,
             queryName:String,
             ):QepQueryAuditData = QepQueryAuditData(
    shrineNodeId,
    userName,
    queryId,
    queryName,
    System.currentTimeMillis()
                                                    )
}
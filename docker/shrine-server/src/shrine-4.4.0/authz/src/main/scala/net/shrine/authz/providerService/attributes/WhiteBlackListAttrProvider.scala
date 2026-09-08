package net.shrine.authz.providerService.attributes

import com.typesafe.config.Config
import net.shrine.slick.TestableDataSourceCreator
import slick.jdbc.JdbcProfile

import javax.sql.DataSource
import scala.collection.mutable
import scala.concurrent.duration.Duration

import cats.effect.IO
import net.shrine.http4s.catsio.{ExecutionContexts, SimpleAsyncExecutor}
import net.shrine.log.Loggable
import slick.lifted
import slick.lifted.ProvenShape
import java.sql.SQLException

import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Future, blocking}
import scala.language.postfixOps

/**
 * This class implements AttrProviderTrait so its output can be used
 * by an authorizer. The attribute values are found in a SQL database
 * called 'AUTHZ' and a table called 'AUTHZ_USER';
 * table 'AUTHZ_USER' has three columns:
 * ssoID, BLACK_LISTED, and WHITE_LISTED; the latter 2 are boolean.
 * This class will return the values of both BLACK_LISTED and
 * WHITE_LISTED, without giving precedence to one or the other.
 * That is left for the authorizer to decide.
 */
//SHRINE2020-1324cDon't use mutable maps in WhiteBlackListAttrProvider.scala
//SHRINE2020-1327 Rewrite EndPointAttrProvider, etc. using val's and IO's

class WhiteBlackListAttrProvider extends AttrProviderTrait {

  def populateAttributes(userId: String,
                         headers: List[(String, String)],
                         config:Config
             ):(String, mutable.Map[String, Seq[String]]) = {

    val dbConfig = config.getConfig("database")
    val slickProfile:JdbcProfile = TestableDataSourceCreator.slickDriver(dbConfig)
    val schema: AuthzSchema = AuthzSchema(slickProfile)
    val dataSource: DataSource = TestableDataSourceCreator.dataSource(dbConfig)

    val timeout:Duration = Duration(dbConfig.getString("timeout"))
    val createTablesOnStart = dbConfig.getBoolean("createTablesOnStart")

    val db = BlackWhiteTableDb(schema, dataSource, timeout, createTablesOnStart)

    val isBlack = db.isBlack(userId)
    val isWhite = db.isWhite(userId)

    // hm, why does (only) 'mutable Map' work here but everywhere else it's 'mutable.Map'??
    // "Why is this Map different from all other Maps?", asks the child
    val userInfo: scala.collection.mutable.Map[String, Seq[String]] =
      mutable.Map[String, Seq[String]](
            ("isBlack" -> Seq(isBlack.toString)),
            ("isWhite" -> Seq(isWhite.toString))
        )

    (config.getString("name"), userInfo)
  }
}


/**
 * DB code for AUTHZ
 *
 */
case class BlackWhiteTableDb(schemaDef:AuthzSchema,
                             dataSource: DataSource,
                             timeout:Duration,
                             createTablesOnStart:Boolean) extends Loggable {
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

  def dbRun[R](action: DBIOAction[R, NoStream, Nothing]):R = {
    val future: Future[R] = database.run(action)
    blocking {
      Await.result(future, timeout)
    }
  }

  private def run[R](dbio: DBIOAction[R, NoStream, _]): Future[R] = {
    database.run(dbio)
  }

  private def runIO[R](dbio: DBIOAction[R, NoStream, _]): IO[R] = {
    IO.fromFuture(IO.blocking(run(dbio)))
  }

  // for examples of more possibly handy methods, see HubDb.scala
  // see https://scala-slick.org/doc/3.4.1/queries.html
  def isWhite(ssoId: String): Boolean = {
    val query = allWBdbQueries.filter(_.ssoId === ssoId).filter(_.whiteListed === true)
    isColor(query)
  }

  def isBlack(ssoId: String): Boolean = {
    val query = allWBdbQueries.filter(_.ssoId === ssoId).filter(_.blackListed === true)
    isColor(query)
  }

  /**
   * Takes as input a query which returns a list of either white or black list entries for a given user and list
   * @param query
   * @return
   */
  def isColor(query: Query[BlackWhiteTableDb.this.schemaDef.BlackWhiteUser,
    BlackWhiteTableDb.this.schemaDef.BlackWhiteUser#TableElementType,
    Seq]): Boolean = {
    val action = query.result
    val resultList: Seq[BlackWhiteListData] = dbRun(action)

    resultList.nonEmpty
  }

  if (createTablesOnStart) createTables()
}

case class BlackWhiteListData(
                               ssoId:String,
                               whiteListed:Boolean,
                               blackListed:Boolean
                             ) {}

/**
 * Separate class to support schema generation without actually connecting to the database.
 *
 * @param jdbcProfile Database profile to use for the schema
 */
case class AuthzSchema(jdbcProfile: JdbcProfile) extends Loggable {
  import jdbcProfile.api._

  def ddlForAllTables: jdbcProfile.DDL = {
    allWBdbQueries.schema
  }

  def createTables(database:Database): Unit = {

    try {
      val future = database.run(ddlForAllTables.create)
      Await.result(future,10 seconds)
    } catch {

      case x:SQLException => info("Caught exception while creating tables. Recover by assuming the tables already exist.",x)
    }
  }

  def dropTables(database:Database): Unit = {
    val future = database.run(ddlForAllTables.drop)

    Await.result(future,Duration.Inf)
  }

  class BlackWhiteUser(tag: Tag) extends Table[BlackWhiteListData](tag, "AUTHZ_USER") {
    def ssoId = column[String]("SSO_ID")
    def whiteListed = column[Boolean]("WHITE_LISTED")
    def blackListed = column[Boolean]("BLACK_LISTED")

    def * : ProvenShape[BlackWhiteListData] = (ssoId, whiteListed, blackListed) <>
      (BlackWhiteListData.tupled, BlackWhiteListData.unapply)
  }

  val allWBdbQueries = lifted.TableQuery[BlackWhiteUser]
}

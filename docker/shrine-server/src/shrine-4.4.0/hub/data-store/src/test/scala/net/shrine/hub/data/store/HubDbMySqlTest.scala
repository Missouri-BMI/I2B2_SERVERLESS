
/**
 * A simple scala script to test the hub database with mysql
 *
 * To run the test, first set up an ssh tunnel from your lap top to some mysql database
 *
 * > ssh -i ~/.ssh/dwalend-shrine-id-rsa -L 3306:shrine-dev1.catalyst:3306 dwalend@shrine-dev1.catalyst
 *
 * Start the REPL
 *
 * > mvn scala:console
 *
 * Maybe load this file (or just cut and paste
 *
 * :load /path/to/file
 *
 */

//todo how does maven know not to try to compile MessageQueueWebClientTest.scala?
/*
import net.shrine.hub.data.store.HubDb
import net.shrine.protocol.version.{NodeId, NodeName, QueryId, ResultStatuses}
import net.shrine.protocol.version.v1.{CrcResult, ResultProgress}
import net.shrine.config.ConfigSource
/*
shrine {
  qep {
      database {
        slickProfileClassName = "slick.jdbc.MySQLProfile$"
        createTablesOnStart = false //for testing with H2 in memory, when not running unit tests. Set to false normally

        dataSourceFrom = "dataSourceConfig" //Can be JNDI or dataSourceConfig . Use dataSourceConfig for command line tools and tests, JNDI everywhere else

        dataSourceConfig {
          driverClassName = "com.mysql.jdbc.Driver"
          url = "jdbc:mysql://localhost:3306/QEP" //H2 embedded in-memory for unit tests ;TRACE_LEVEL_SYSTEM_OUT=2 for H2's trace

  //username="shrine" password="demouser"
        }
        timeout = "30 seconds" //time to wait before db gives up, in seconds.
      }
  }
}
*/
val configMap: Map[String, String] = Map(

  "shrine.qep.database.slickProfileClassName" -> "slick.jdbc.MySQLProfile$",
  "shrine.qep.database.createTablesOnStart" -> "false",
  "shrine.qep.database.dataSourceFrom" -> "dataSourceConfig",
  "shrine.qep.database.timeout" -> "30 seconds",
  "shrine.qep.database.dataSourceConfig.driverClassName" -> "com.mysql.jdbc.Driver",
  "shrine.qep.database.dataSourceConfig.url" -> "jdbc:mysql://localhost:3306/QEP",
  "shrine.qep.database.dataSourceConfig.credentials.username" -> "root", //Mwha ha ha ha!
  "shrine.qep.database.dataSourceConfig.credentials.password" -> ""
)

val db = ConfigSource.atomicConfig.configForBlock(configMap, "HubDbMySqlTest") {
  HubDb.db
}

val resultInProgress = ResultProgress(
  queryId = new QueryId(2),
  adapterNodeId = new NodeId(3),
  adapterNodeName = new NodeName("test node"),
  status = ResultStatuses.ResultFromCRC,
  statusMessage = None
)

HubDb.db.upsertResult(resultInProgress)
HubDb.db.selectAllResultsHistory

val crcResult: CrcResult = resultInProgress.toCrcResult(2050)
HubDb.db.upsertResult(crcResult)
HubDb.db.selectAllResultsHistory
*/
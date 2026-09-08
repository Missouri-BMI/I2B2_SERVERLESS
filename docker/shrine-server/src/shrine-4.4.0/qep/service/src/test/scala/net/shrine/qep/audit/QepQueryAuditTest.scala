package net.shrine.qep.audit

import cats.effect.unsafe.implicits.global
import net.shrine.config.ConfigSource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.{AfterEach, BeforeEach, Test}

/**
 * @author david 
 * @since 8/18/15
 */
class QepQueryAuditTest {// with TestWithDatabase {

  val qepAuditData: QepQueryAuditData = QepQueryAuditData("example.com","ben",-1,"ben's query")

  @Test
  def testApply():Unit = {

    ConfigSource.configForBlock("shrine.qep.audit.useQepAudit","true",this.getClass.getSimpleName){

      QepAuditDb.db.insertQepQueryIO(qepAuditData).unsafeRunSync()

      val results = QepAuditDb.db.selectAllQepQueriesIO.unsafeRunSync()
      assertEquals(results,Seq(qepAuditData))
    }
  }

  @BeforeEach
  def beforeEach(): Unit = {
    QepAuditDb.db.createTables()
  }

  @AfterEach
  def afterEach(): Unit = {
    QepAuditDb.db.dropTables()
  }

}

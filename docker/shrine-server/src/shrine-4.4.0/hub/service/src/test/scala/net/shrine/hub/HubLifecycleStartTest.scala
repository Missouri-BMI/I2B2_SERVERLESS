package net.shrine.hub

import net.shrine.hub.data.store.{HubDatabaseNetworkNotFoundException, HubDb}
import org.junit.{After, Before, Test}
import org.scalatestplus.junit.AssertionsForJUnit

class HubLifecycleStartTest extends AssertionsForJUnit {
  import cats.effect.unsafe.implicits.global
  @Test
  def testNoNodeOrNetwork():Unit = {
    assertThrows[HubDatabaseNetworkNotFoundException](HubLifecycle.queuesFromDatabaseIO().unsafeRunSync())
  }
  @Before
  def beforeEach(): Unit = {
    HubDb.db.createTables()
  }

  @After
  def afterEach(): Unit = {
    HubDb.db.dropTables()
  }

}

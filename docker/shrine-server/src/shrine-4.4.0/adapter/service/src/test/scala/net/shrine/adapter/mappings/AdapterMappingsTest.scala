package net.shrine.adapter.mappings

import cats.effect.IO
import org.junit.Assert.{assertEquals, assertNotEquals}
import net.shrine.util.ShouldMatchersForJUnit
import org.junit.Test

class AdapterMappingsTest extends ShouldMatchersForJUnit {
  import cats.effect.unsafe.implicits.global
  val db: AdapterMappingsDb = AdapterMappingsDb.db
  val csv = "simple-mappings.csv"

  @Test
  def testReloadMappings(): Unit = {
    type MappingState = (String, Long, Long, Int)
    def assertMappingStateAllEqual(currentState: MappingState, expectedState: MappingState): Unit =
      assertEquals(expectedState, currentState)

    def readMappingState(): MappingState =
      (db.filename.unsafeRunSync(),
       db.fileLastModified.unsafeRunSync(),
       db.checksum.unsafeRunSync(),
       db.countAll.unsafeRunSync())

    def assertMappingStateNoneEqual(mappingState: MappingState, noneShouldEqual: MappingState): Unit = {
      assertNotEquals(mappingState._1, noneShouldEqual._1)
      assertNotEquals(mappingState._2, noneShouldEqual._2)
      assertNotEquals(mappingState._3, noneShouldEqual._3)
      assertNotEquals(mappingState._4, noneShouldEqual._4)
    }

    val initial: MappingState = readMappingState()
    assertMappingStateAllEqual(initial, ("", -1, -1, 0))

    // Should insert for the first time
    AdapterMappings.compareAndReloadMappings(csv).unsafeRunSync()
    val updated: MappingState = readMappingState()
    assertMappingStateNoneEqual(initial, updated)

    // When any of the three are the only change, make sure the mappings are reloaded
    val (sameFilename, sameLastModified, sameChecksum, _) = updated
    val differentFilename = "differentFilename"
    val differentLastModified = -2L
    val differentChecksum = -2L

    def assertMappingsUpdated(updatefn: IO[_]): Unit = {
      updatefn.unsafeRunSync()
      val insertionTime = db.loadedFromFile.unsafeRunSync()
      AdapterMappings.compareAndReloadMappings(csv).unsafeRunSync()
      assertMappingStateAllEqual(readMappingState(), updated)
      assertNotEquals(db.loadedFromFile.unsafeRunSync(), insertionTime)
    }

    assertMappingsUpdated(db.updateMetaInformation(differentFilename, sameLastModified, sameChecksum))
    assertMappingsUpdated(db.updateMetaInformation(sameFilename, differentLastModified, sameChecksum))
    assertMappingsUpdated(db.updateMetaInformation(sameFilename, sameLastModified, differentChecksum))
    // When nothing changes the mappings should not be reloaded.
    assertThrows[AssertionError]{
      assertMappingsUpdated(db.updateMetaInformation(sameFilename, sameLastModified, sameChecksum))
    }
  }
}

package net.shrine.protocol.i2b2

import net.shrine.util.ShouldMatchersForJUnit
import junit.framework.TestCase
import org.junit.Test

/**
 * @author clint
 * @since Mar 6, 2013
 */
final class StatusTypeTest extends TestCase with ShouldMatchersForJUnit {
  import QueryResult.StatusType._
  
  //NB: QueryResult.StatusType.toI2b2 is tested in QueryResultTest
  
  @Test
  def testName():Unit = {
    Error.name should be("ERROR")
    Finished.name should be("FINISHED")
    Processing.name should be("PROCESSING")
    Queued.name should be("QUEUED")
    Incomplete.name should be("INCOMPLETE")
    Held.name should be("HELD")
    SmallQueue.name should be("SMALL_QUEUE")
    MediumQueue.name should be("MEDIUM_QUEUE")
    LargeQueue.name should be("LARGE_QUEUE")
    NoMoreQueue.name should be("NO_MORE_QUEUE")
  }
  
  @Test
  def testIsDone():Unit = {
    Error.isDone should be(true)
    Finished.isDone should be(true)
    Processing.isDone should be(false)
    Queued.isDone should be(false)
    Incomplete.isDone should be(true)
    Held.isDone should be(false)
    SmallQueue.isDone should be(false)
    MediumQueue.isDone should be(false)
    LargeQueue.isDone should be(false)
    NoMoreQueue.isDone should be(true)
  }
  
  @Test
  def testIsError():Unit = {
    Error.isError should be(true)
    Finished.isError should be(false)
    Processing.isError should be(false)
    Queued.isError should be(false)
    Incomplete.isError should be(true)
    Held.isError should be(false)
    SmallQueue.isError should be(false)
    MediumQueue.isError should be(false)
    LargeQueue.isError should be(false)
    NoMoreQueue.isError should be(true)
  }
  
  @Test
  def testI2b2Id():Unit = {
    Error.i2b2Id should be(None)
    Finished.i2b2Id.get should be(3)
    Processing.i2b2Id.get should be(2)
    Queued.i2b2Id.get should be(2)
    Incomplete.i2b2Id.get should be(5)
    Held.i2b2Id.get should be(-1)
    SmallQueue.i2b2Id.get should be(-1)
    MediumQueue.i2b2Id.get should be(-1)
    LargeQueue.i2b2Id.get should be(-1)
    NoMoreQueue.i2b2Id.get should be(-1)
  }
}
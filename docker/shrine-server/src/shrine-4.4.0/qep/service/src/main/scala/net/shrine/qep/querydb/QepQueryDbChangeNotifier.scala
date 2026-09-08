package net.shrine.qep.querydb

import java.util.concurrent.atomic.AtomicInteger

import cats.effect.IO
import net.shrine.audit.LongQueryId
import net.shrine.log.Log
import org.http4s.Response

import scala.collection.concurrent.{TrieMap, Map => ConcurrentMap}
import scala.concurrent.duration.FiniteDuration
import cats.effect.Deferred

/**
 * Set up triggers to create responses for updates about particular queries.
 *
 * This singleton guarantees that each request gets exactly one response.
 */
object QepQueryDbChangeNotifier {

  private type A = Response[IO]

  //todo switch from untyped NetworkQueryIds to QueryIds when this moves to the hub
  private case class Trigger(requestId: Long, queryId: LongQueryId, deferred: Deferred[IO,String])

  private val requestIdsToTriggers:ConcurrentMap[Long,Trigger] = TrieMap.empty

  private val requestIdSource: AtomicInteger = new AtomicInteger(0)

  /**
   * Set up the trigger and task for a particular query.
   */
  def setTriggerForChange(queryId:LongQueryId, timeout: FiniteDuration, task:IO[A]):IO[A] = {

    val requestId = requestIdSource.getAndIncrement()

    //when a request comes in -
    //1) watch for the change by installing the IO trigger
    val takeAndDoTask:IO[A] = {
      // It's fine if the trigger has already been removed. The Deferred task will only be done once and memoized.
      val triggerIO: IO[Option[Trigger]] = IO(requestIdsToTriggers.remove(requestId))
      triggerIO.flatMap(_ => task)
    }

    val deferredIO: IO[Deferred[IO, String]] = Deferred[IO,String]

    deferredIO.flatMap{deferred: Deferred[IO, String] =>
      val deferredTask: IO[A] = for {
        logSegment <- deferred.get
        _ <- IO(Log.debug(s"Task started $logSegment"))
        a <- takeAndDoTask
      } yield a

      val timeoutTask:IO[A] = IO(Log.debug(s"Task started via timeout")).flatMap(_ => takeAndDoTask)

      val trigger = Trigger(requestId,queryId,deferred)
      requestIdsToTriggers.put(requestId,trigger)
      Log.debug(s"setTriggerForChange requestIdsToTriggers is $requestIdsToTriggers")

      //2) start a timeoutTo to do the task even if it is never triggered
      // if the deferredTask hasn't happened after timeout, do it after waiting long enough
      deferredTask.timeoutTo(timeout,timeoutTask) //todo complete the deferred if you see "tried to do it twice" exceptions
    }
  }

  //find all the triggers to trip for queryId, and trip them
  def triggerChangesFor(queryId:LongQueryId): IO[Unit] = {
    import cats.implicits._

    Log.debug(s"triggerChangesFor $queryId requestIdsToTriggers is $requestIdsToTriggers")
    requestIdsToTriggers.values.filter(_.queryId == queryId).map{ trigger: Trigger =>
      Log.debug(s"About to complete $trigger")
      trigger.deferred.complete(s"- triggered for $queryId").flatMap{s => IO(Log.debug(s"Completed deferred $s"))}
    }.toList.sequence.start.flatMap(_ => IO.unit)
    //because the tasks' first step is to remove themselves from the map there's nothing to clean up.
  }
}
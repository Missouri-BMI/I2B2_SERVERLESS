package net.shrine.http4s.catsio

import java.util.concurrent.{ExecutorService, Executors, ForkJoinPool, ForkJoinWorkerThread, SynchronousQueue, ThreadFactory, ThreadPoolExecutor, TimeUnit}
import java.util.concurrent.atomic.AtomicInteger
import cats.effect.IO
import net.shrine.log.Loggable
import slick.util.AsyncExecutor

import scala.annotation.unused
import scala.concurrent.{BlockContext, CanAwait, ExecutionContext, ExecutionContextExecutorService}

/**
  * Execution contexts for shrine
  *
  * @author dwalend
  * @since 1.26
  */
object ExecutionContexts extends Loggable {

  /**
    * For chatting with the database at 10-500 millisecond-timescales
    */
  //noinspection RedundantDefaultArgument
  lazy val databaseExecutionContext: ExecutionContextExecutorService = cachedThreadPoolExecutionContext(
    prefix = "database-",
    daemonic = false //Want to let these wind down gracefully when tomcat shuts down
  )

  def shutdownIO(): IO[Unit] = IO {
    databaseExecutionContext.shutdown() //shutdown databases last. Give them a chance to finish writes.
  }

  @unused
  private def fixedThreadPoolExecutionContext(prefix:String, size:Int, daemonic:Boolean = false): ExecutionContextExecutorService = {
    val threadFactory = ShrineThreadFactory(prefix,daemonic)
    val executorService: ExecutorService = Executors.newFixedThreadPool(size,threadFactory)

    val executionContext: ExecutionContextExecutorService = ExecutionContext.fromExecutorService(executorService)

    executionContext
  }

  //noinspection SameParameterValue
  @unused
  private def forkJoinThreadPoolExecutionContext(prefix:String, maxSize:Int, daemonic:Boolean = false): ExecutionContextExecutorService = {
    val threadFactory = ShrineThreadFactory(prefix,daemonic)
    val executorService: ExecutorService = forkJoinExecutorService(maxSize,threadFactory)

    val executionContext: ExecutionContextExecutorService = ExecutionContext.fromExecutorService(executorService)

    executionContext
  }

  //noinspection SameParameterValue
  private def cachedThreadPoolExecutionContext(prefix:String, daemonic:Boolean = false): ExecutionContextExecutorService = {
    val threadFactory = ShrineThreadFactory(prefix,daemonic)
    val executorService: ExecutorService = Executors.newCachedThreadPool(threadFactory)

    ExecutionContext.fromExecutorService(executorService)
  }

  @unused
  private def boundedExecutorService(maxPoolSize:Int, prefix:String, daemonic:Boolean = false): ExecutionContextExecutorService = {
    val threadFactory = ShrineThreadFactory(prefix,daemonic)

    val executor = new ThreadPoolExecutor(
      0,
      maxPoolSize,
      60L,
      TimeUnit.SECONDS,
      new SynchronousQueue[Runnable],
      threadFactory
    )

    ExecutionContext.fromExecutorService(executor)
  }

  private def forkJoinExecutorService(maxSize:Int,threadFactory:ShrineThreadFactory):ExecutorService = {
    new ForkJoinPool(maxSize, threadFactory, threadFactory.uncaughtExceptionHandler, true)
  }

}

case class ShrineThreadFactory(
                                threadNamePrefix:String,
                                daemonic:Boolean
                              ) extends ThreadFactory with ForkJoinPool.ForkJoinWorkerThreadFactory with Loggable {

  private val counter = new AtomicInteger(0)

  //noinspection ConvertExpressionToSAM
  val uncaughtExceptionHandler: Thread.UncaughtExceptionHandler = new Thread.UncaughtExceptionHandler {
    def uncaughtException(thread: Thread, cause: Throwable): Unit = error(s"${cause.getClass.getSimpleName} never caught in ${thread.getName}",cause)
  }

  private def wire[T <: Thread](thread: T): T = {
    thread.setDaemon(daemonic)
    thread.setUncaughtExceptionHandler(uncaughtExceptionHandler)
    thread.setName(s"$threadNamePrefix${counter.getAndIncrement()}")
    thread
  }

  def newThread(runnable: Runnable): Thread = wire(new Thread(runnable))

  //mostly cut-paste from scala.concurrent.impl.ExecutionContextImpl.DefaultThreadFactory
  def newThread(fjp: ForkJoinPool): ForkJoinWorkerThread = wire(
    new ForkJoinWorkerThread(fjp) with BlockContext {
      override def blockOn[T](thunk: =>T)(implicit permission: CanAwait): T = {
        var result: T = null.asInstanceOf[T]
        ForkJoinPool.managedBlock(new ForkJoinPool.ManagedBlocker {
          @volatile var isDone: Boolean = false
          override def block(): Boolean = {
            result = try thunk finally { isDone = true }
            true
          }
          override def isReleasable: Boolean = isDone
        })
        result
      }
    }
  )
}

case class SimpleAsyncExecutor(executionContext:ExecutionContextExecutorService) extends AsyncExecutor {
  override def close(): Unit = executionContext.shutdown()
}
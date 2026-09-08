package net.shrine.slick

import java.util.concurrent.TimeoutException
import javax.sql.DataSource

import scala.concurrent.duration.Duration

/**
  * Created by ty on 7/22/16.
  */
abstract class DbIoActionException(dataSource: DataSource, message:String, throwable: Option[Throwable] = None) extends RuntimeException(message,throwable.orNull)

case class CouldNotRunDbIoActionException(dataSource: DataSource, throwable: Throwable) extends DbIoActionException(dataSource,s"Could not use the database defined by $dataSource due to ${throwable.getLocalizedMessage}",Some(throwable))

case class TimeoutInDbIoActionException(dataSource: DataSource, timeout: Duration, tx: TimeoutException) extends DbIoActionException(dataSource,s"Timed out after $timeout while using $dataSource : ${tx.getLocalizedMessage}",Some(tx))
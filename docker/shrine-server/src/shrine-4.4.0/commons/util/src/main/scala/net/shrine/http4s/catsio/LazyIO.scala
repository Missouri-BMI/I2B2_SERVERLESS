package net.shrine.http4s.catsio

import cats.effect.IO
import net.shrine.log.Loggable

/**
 * IO-based lazy cache of a value
 */
object LazyIO extends Loggable {
  def apply[A](logString:String)(blockIO:IO[A]):IO[A] = {
    IO(info(logString)).flatMap(_ => blockIO).memoize.flatten
  }
}

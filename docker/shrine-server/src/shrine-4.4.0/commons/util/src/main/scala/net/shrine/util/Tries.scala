package net.shrine.util

import scala.collection.BuildFrom
import scala.util.Try
import scala.util.Success
import scala.util.Failure

/**
 * Helpers for working with scala.util.Try
 * @author clint
 * @since Oct 18, 2012
 */
object Tries {
  def toTry[T](o: Option[T])(ex: => Throwable): Try[T] = o match {
    case Some(t) => Success(t)
    case None => Failure(ex)
  }

  /**
   * Turns an Option[Try[T]] into a Try[Option[T]], a la Future.sequence.
   */
  def sequence[T](tryOption: Option[Try[T]]): Try[Option[T]] = tryOption match {
    case Some(attempt) => attempt.map(Option(_))
    case None => Try(None)
  }

  /**
   * Turns a Iterable[Try[T]] into a Try[Seq[T]], a la Future.sequence.
   * Uses CanBuildFrom magic to ensure that the subtype of Traversable passed in is the
   * same subtype of Traversable returned, and that this is verifiable at compile-time.
   *
   * NB: If *any* of the input Tries are Failures, then the first Failure is returned;
   * this can drop subsequent Failures if there are more than one.
   */
  def sequence[A, C[+A] <: Iterable[A]](attempts: C[Try[A]]): Try[Seq[A]] = {
    Try (attempts.map { a => a.get }.toSeq)
  }
}
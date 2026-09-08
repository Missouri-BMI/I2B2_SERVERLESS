package net.shrine.authentication

import ch.qos.logback.classic.Level
import net.shrine.authentication.AuthenticationResult.NotAuthenticated
import net.shrine.problem.{AbstractProblem, ProblemSources}

/**
 * @author clint
 * @since Dec 13, 2013
 */
final case class NotAuthenticatedException(domain: String, username: String,message: String, cause: Throwable) extends RuntimeException(message, cause) {

  def problem: NotAuthenticatedProblem = NotAuthenticatedProblem(this)

}

object NotAuthenticatedException {

  def apply(na:NotAuthenticated):NotAuthenticatedException = NotAuthenticatedException(na.domain,na.username,na.message,na.cause.orNull)

}

case class NotAuthenticatedProblem(nax:NotAuthenticatedException) extends AbstractProblem(ProblemSources.Qep){
  override def logLevel: Level = Level.INFO

  override val summary = s"Can not authenticate ${nax.domain}:${nax.username}."

  override val throwable: Option[Throwable] = Some(nax)

  override val description = s"Can not authenticate ${nax.domain}:${nax.username}. ${nax.getLocalizedMessage}"
}
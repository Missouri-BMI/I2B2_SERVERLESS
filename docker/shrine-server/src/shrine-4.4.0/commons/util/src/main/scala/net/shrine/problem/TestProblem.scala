package net.shrine.problem

import java.net.InetAddress

import ch.qos.logback.classic.Level

/**
  * @author david 
  * @since 1.22
  */
case class TestProblem(override val summary: String = "test summary",
                       override val description:String = "test description",
                       override val throwable: Option[Throwable] =  None,
                       override val stamp: Stamp = new TestStamp) extends AbstractProblem(ProblemSources.Unknown) {
  override def logLevel: Level = Level.ERROR
  override def timer = 0
}

class TestStamp extends Stamp(source = ProblemSources.Unknown, time = 0, host = InetAddress.getByName("0.0.0.0"))
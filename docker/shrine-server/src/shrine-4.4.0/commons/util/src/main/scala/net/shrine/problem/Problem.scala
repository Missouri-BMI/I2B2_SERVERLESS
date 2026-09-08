package net.shrine.problem

import java.net.{InetAddress, UnknownHostException}
import net.shrine.log.{Log, Loggable}
import net.shrine.config.{ConfigExtensions, ConfigSource}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}
import scala.xml.{Elem, Node, NodeSeq}
import ch.qos.logback.classic.Level
import net.shrine.protocol.i2b2.serialization.{XmlMarshaller, XmlUnmarshaller}

import java.time.Instant

/**
  * Describes what information we have about a problem at the site in code where we discover it.
  *
  * @author david
  * @since 8/6/15
  */

sealed trait Problem {
  def toXml:Node

  def summary: String

  def codec: String

  def description: String

  def stampText:String

  def detailsXml: NodeSeq
}

trait RawProblem extends Problem {
  /**
   * Use
   *
   * ERROR for unrecoverable problems with shrine - bad config means part of shrine cannot start
   * WARN for external resource problems - cannot communicate with i2b2
   * INFO for recoverable problems within shrine - bad signature on a query
   * DEBUG for non-problems within shrine - could not map a term
   * TRACE for trivia - synthetic problem created for a query when all results are errors
   */
  def logLevel:Level

  def problemName: String = getClass.getName

  def detailsText: Option[String] = None

  def throwable: Option[Throwable] = None

  def stamp: Stamp

  def codec: String = problemName

  override def stampText: String = stamp.pretty

  def detailsXml: NodeSeq = {
    def exceptionXml(exception: Option[Throwable]): Option[Elem] = {
      exception.map { x =>
        <exception>
          <name>{x.getClass.getName}</name>
          <message>{x.getMessage}</message>
          <stacktrace>
            {x.getStackTrace.map(line => <line>{line}</line>)}{exceptionXml(Option(x.getCause)).getOrElse("")}
          </stacktrace>
        </exception>
      }
    }

    val throwableDetail: Option[Elem] = exceptionXml(throwable)

    NodeSeq.fromSeq(<details>{detailsText.map(_ + "\n").getOrElse("")}{throwableDetail.getOrElse("")}</details>)
  }

  /**
    * Temporary replacement for onCreate, which may be released in a future version of Scala (after 2.13)
    */
  def hackToHandleAfterInitialization(handler: ProblemHandler): Future[Unit] = {
    import scala.concurrent.blocking
    Future {
      var continue = true
      while (continue) {
        try {
          blocking(synchronized(handler.handleProblem(this)))
          continue = false
        } catch {
          case _: UninitializedFieldError =>
            blocking{Thread.sleep(5)}
            continue = true
        }
      }
      ()
    }
  }
}

case class ExceptionDigest(
                            name:String,
                            message:Option[String],
                            stackTrace:List[String],
                            cause:Option[ExceptionDigest]
                          )

object ExceptionDigest {
  def apply(t:Throwable): ExceptionDigest = ExceptionDigest(
    t.getClass.getName,
    Option(t.getMessage),
    t.getStackTrace.map(x => x.toString).toList,
    Option(t.getCause).map(ExceptionDigest(_))
  )
}

case class JsonProblemDigest(
                              codec: String,
                              stampText: String,
                              summary: String,
                              description: String,
                              detailsText: Option[String],
                              exceptionDigest: Option[ExceptionDigest],
                              epoch: Long
                            ) extends Problem {

  def toXml: Node = {
    <problem>
      <codec>{codec}</codec>
      <stamp>{stampText}</stamp>
      <summary>{summary}</summary>
      <description>{description}</description>
      <epoch>{epoch}</epoch>
      {detailsXml}
    </problem>
  }

  override def detailsXml: NodeSeq = {
    def exceptionXml(exception: Option[ExceptionDigest]): Option[Elem] = {
      exception.map { x =>
        <exception>
          <name>{x.getClass.getName}</name>
          <message>{x.message}</message>
          <stacktrace>
            {x.stackTrace.map(line => <line>{line}</line>)}{exceptionXml(x.cause).getOrElse("")}
          </stacktrace>
        </exception>
      }
    }
    val throwableDetail: Option[Elem] = exceptionXml(exceptionDigest)

    NodeSeq.fromSeq(<details>{detailsText.map(_ + "\n").getOrElse("")}{throwableDetail.getOrElse("")}</details>)
  }
}

object JsonProblemDigest {
  def apply(problem: RawProblem):JsonProblemDigest = JsonProblemDigest(
    problem.getClass.getName,
    problem.stamp.pretty,
    problem.summary,
    problem.description,
    problem.detailsText,
    problem.throwable.map(ExceptionDigest(_)),
    problem.stamp.time
  )

  def apply(problem: XmlProblemDigest):JsonProblemDigest = JsonProblemDigest(
    problem.codec,
    problem.stampText,
    problem.summary,
    problem.description,
    Some(problem.detailsXml.toString()),
    None,
    problem.epoch
  )

}

case class XmlProblemDigest(codec: String,
                            stampText: String,
                            summary: String,
                            description: String,
                            detailsXml: NodeSeq,
                            epoch: Long)
    extends Problem with XmlMarshaller {

  override def toXml: Node = {
    <problem>
      <codec>{codec}</codec>
      <stamp>{stampText}</stamp>
      <summary>{summary}</summary>
      <description>{description}</description>
      <epoch>{epoch}</epoch>
      {detailsXml}
    </problem>
  }

  /**
    * Ignores detailXml. equals with scala.xml is impossible. See http://www.scala-lang.org/api/2.10.3/index.html#scala.xml.Equality$
    */
  override def equals(other: Any): Boolean =
    other match {

      case that: XmlProblemDigest =>
        (that canEqual this) &&
          codec == that.codec &&
          stampText == that.stampText &&
          summary == that.summary &&
          description == that.description &&
          epoch == that.epoch
      case _ => false
    }

  /**
    * Ignores detailXml
    */
  override def hashCode: Int = {
    val prime = 67
    codec.hashCode + prime * (stampText.hashCode + prime * (summary.hashCode + prime * (description.hashCode + prime * epoch
      .hashCode())))
  }
}

object XmlProblemDigest extends XmlUnmarshaller[XmlProblemDigest] with Loggable {

  //XmlMarshaller spookiness keeps a simple apply() method from working.
  def create(problem:RawProblem): XmlProblemDigest = {
    this(
      problem.problemName,
      problem.stamp.pretty,
      problem.summary,
      problem.description,
      problem.detailsXml,
      problem.stamp.time
    )
  }

  override def fromXml(xml: NodeSeq): XmlProblemDigest = {
    val problemNode = xml \ "problem"
    require(problemNode.nonEmpty, s"No problem tag in $xml")

    def extractText(tagName: String) = (problemNode \ tagName).text

    val codec = extractText("codec")
    val stampText = extractText("stamp")
    val summary = extractText("summary")
    val description = extractText("description")
    val detailsXml: NodeSeq = problemNode \ "details"
    val epoch =
      try { extractText("epoch").toLong } catch {
        case nx: NumberFormatException =>
          error(
            s"While parsing xml representing a ProblemDigest, the epoch could not be parsed into a long",
            nx)
          0
      }

    XmlProblemDigest(codec, stampText, summary, description, detailsXml, epoch)
  }

}

case class Stamp(host: InetAddress,
                 time: Long,
                 source: ProblemSources.ProblemSource) {
  def pretty: String = {
    val hostName = Try(host.getHostName) match {
      case Success(h) => h
      case Failure(exception: UnknownHostException) =>
        Log.warn("No valid host found, please configure the server hosts configuration properly."
          + s"UnknownHostException failed with: `${exception.getLocalizedMessage}`")
        "[COULD NOT FIND HOST]"
      case Failure(exception) => throw exception
    }
    s"${Instant.ofEpochMilli(time)} on $hostName ${source.pretty}"
  }
}

object Stamp {
  //TODO: val dateFormatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS")?
  //TODO: Currently the stamp text is locale specific, which can change depending on the jre/computer running it...
  def apply(source: ProblemSources.ProblemSource,
            timer: => Long,
            host: InetAddress = InetAddress.getLocalHost): Stamp =
    Stamp(host, timer, source)
}

abstract class AbstractProblem(source: ProblemSources.ProblemSource)
    extends RawProblem {
  def timer: Long = System.currentTimeMillis
  override val stamp: Stamp = Stamp(source, timer)
  private val config = ConfigSource.config.getConfig("shrine.problem")
  hackToHandleAfterInitialization(
    config.getObjectByClassname("problemHandler"))

  override def toXml: Node = XmlProblemDigest.create(this).toXml
}

trait ProblemHandler {
  def handleProblem(problem: RawProblem):Unit
}

/**
  * Write problems to the default log
  */
object LoggingProblemHandler extends ProblemHandler with Loggable {
  override def handleProblem(problem: RawProblem): Unit = log(problem)
}

/**
  * Mainly for testing, when you don't want problems to print a bunch
  * to stdout
  */
object NoOpProblemHandler extends ProblemHandler {
  override def handleProblem(problem: RawProblem): Unit = ()
}

object ProblemSources {

  sealed trait ProblemSource {
    def pretty: String = getClass.getSimpleName.dropRight(1)
  }

  case object Adapter extends ProblemSource
  case object Commons extends ProblemSource
  case object Hub extends ProblemSource
  case object Qep extends ProblemSource
  case object Unknown extends ProblemSource

  def problemSources = Set(Adapter, Commons, Hub, Qep, Unknown)
}

case class ProblemNotYetEncoded(dt: String,
                                t: Option[Throwable] = None)
    extends AbstractProblem(ProblemSources.Unknown) {
  override def logLevel: Level = Level.ERROR

  override val summary = "An unanticipated problem encountered."

  override def detailsText: Option[String] = Some(dt)

  override val throwable: Some[Throwable] = {
    val rx = t.getOrElse{
      val x = new IllegalStateException(s"$summary")
      x.fillInStackTrace()
      x
    }
    Some(rx)
  }

  val reportedAtStackTrace = new IllegalStateException(
    "Capture reporting stack trace.")

  override val description =
    "This problem is not yet classified in Shrine source code. Please report the details to the Shrine dev team."
}

object ProblemNotYetEncoded {
  def apply(summary: String, x: Throwable): ProblemNotYetEncoded =
    ProblemNotYetEncoded(summary, Some(x))
}
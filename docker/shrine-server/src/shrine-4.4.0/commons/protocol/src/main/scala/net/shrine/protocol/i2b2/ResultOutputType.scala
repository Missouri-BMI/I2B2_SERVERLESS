package net.shrine.protocol.i2b2

import com.typesafe.config.Config
import net.shrine.protocol.i2b2.ResultOutputType.I2b2Options
import net.shrine.config.{ConfigExtensions, ConfigSource}
import net.shrine.protocol.i2b2.serialization.{I2b2Unmarshaller, XmlUnmarshaller}
import net.shrine.xml.{OptionEnrichments, XmlUtil}

import scala.util.{Failure, Success, Try}
import scala.xml.NodeSeq

/**
 * @author Bill Simons
 * @author clint
 * @since 8/30/11
 * @see http://cbmi.med.harvard.edu
 * @see http://chip.org
 * <p/>
 * NOTICE: This software comes with NO guarantees whatsoever and is
 * licensed as Lgpl Open Source
 * @see http://www.gnu.org/licenses/lgpl.html
 */
//todo used in DefaultBreakdownResultOutputTypes - can be moved when that other file can be moved.
final case class ResultOutputType(
  name: String,
  isBreakdown: Boolean,
  i2b2Options: I2b2Options,
  id: Option[Int]) { //only used as a "priority" in i2b2

  override def toString: String = name

  def deepEquals(that: ResultOutputType): Boolean = {
    this.productIterator.toSeq == that.productIterator.toSeq
  }

  //Preserve old enum-like behavior
  override def equals(other: Any): Boolean = other match {
    case null => false
    case that: ResultOutputType => this.name == that.name
    case _ => false
  }
  
  //Preserve old enum-like behavior
  override def hashCode: Int = name.hashCode
  
  def isError: Boolean = this.name == ResultOutputType.ERROR.name

  def withId(i: Int): ResultOutputType = copy(id = Some(i))
  
  import OptionEnrichments._
  
  def toI2b2: NodeSeq = XmlUtil.stripWhitespace {
    <query_result_type>
			{ id.toXml(<result_type_id/>) }
      <name>{ name }</name>
      <display_type>{ i2b2Options.displayType }</display_type>
      <visual_attribute_type>LA</visual_attribute_type>
      <description>{ i2b2Options.description }</description>
    </query_result_type>
  }
  
  def toI2b2NameOnly(nameToUse: String = name): NodeSeq = XmlUtil.stripWhitespace {
    <query_result_type>
      <name>{ nameToUse }</name>
    </query_result_type>
  }
  
  def toXml: NodeSeq = XmlUtil.stripWhitespace {
    <resultType>
      { id.toXml(<id/>) }
			<name>{ name }</name>
			<isBreakdown>{ isBreakdown }</isBreakdown>
			<description>{ i2b2Options.description }</description>
			<displayType>{ i2b2Options.displayType }</displayType>
		</resultType>
  }
}

object ResultOutputType extends I2b2Unmarshaller[Try[ResultOutputType]] with XmlUnmarshaller[Try[ResultOutputType]] {
  val defaultDisplayType = "CATNUM"

  final case class I2b2Options(description: String, displayType: String = defaultDisplayType)

  val PATIENT_COUNT_XML: ResultOutputType = ResultOutputType("PATIENT_COUNT_XML", isBreakdown = false, I2b2Options("Number of patients"), Some(4))

  val ERROR: ResultOutputType = ResultOutputType("ERROR", isBreakdown = false, I2b2Options("Error"), None)

  lazy val values: Seq[ResultOutputType] = Seq(
    PATIENT_COUNT_XML,
    ERROR)

  private[this] def toMap(resultTypes: Iterable[ResultOutputType]): Map[String, ResultOutputType] = Map.empty ++ resultTypes.map(rt => toKey(rt.name) -> rt)

  private[this] lazy val byName: Map[String, ResultOutputType] = toMap(values)

  private[this] def toKey(s: String): String = s.toUpperCase

  def valueOf(name: String): Option[ResultOutputType] = valueOf(Set.empty[ResultOutputType])(name)

  def valueOf(knownTypes: Set[ResultOutputType])(name: String): Option[ResultOutputType] = tryValueOf(knownTypes)(name).toOption

  implicit object ResultOutputTypeOrdering extends Ordering[ResultOutputType] {
    override def compare(x: ResultOutputType, y: ResultOutputType): Int = x.name.compareTo(y.name)
  }

  def tryValueOf(knownTypes: Set[ResultOutputType])(name: String): Try[ResultOutputType] = {
    val allTypes: Map[String, ResultOutputType] = byName ++ toMap(knownTypes)

    def failure(s: String): Failure[ResultOutputType] = Failure(new IllegalArgumentException(s))

    name match {
      case null => failure(s"Null result output type name")
      case _ => allTypes.get(toKey(name)) match {
        case None => failure(s"Unknown result output type '$name'; known types are ${allTypes.values.toSeq.sorted}")
        case Some(rt) => Success(rt)
      }
    }
  }
  
  def fromI2b2(xml: NodeSeq): Try[ResultOutputType] = unmarshalXml(xml)("name", "display_type", "description", "result_type_id")
  
  def fromXml(xml: NodeSeq): Try[ResultOutputType] = unmarshalXml(xml)("name", "displayType", "description", "id")
  
  private def unmarshalXml(xml: NodeSeq)(nameTag: String, displayTypeTag: String, descriptionTag: String, idTag: String): Try[ResultOutputType] = {
    import net.shrine.xml.NodeSeqEnrichments.Strictness._
    
    val trim: NodeSeq => String = _.text.trim
    
    def lookup(name: String) = tryValueOf(Set.empty)(name)

    //If name refers to one of the ResultOutputTypes defined in this object, return that 
    //value.  Otherwise, construct a new ResultOutputType from the values in the XML.
    //This code assumes all non-breakdown ROTs are defined in this file.
    for {
      name <- xml.withChild(nameTag).map(trim)
      if name.nonEmpty
      displayType = xml.withChild(displayTypeTag).map(trim).getOrElse(defaultDisplayType)
      description = xml.withChild(descriptionTag).map(trim).getOrElse(name)
      id = xml.withChild(idTag).map(trim).map(_.toInt).toOption
      result <- lookup(name).orElse(Success(ResultOutputType(name, isBreakdown = true, I2b2Options(description, displayType), id)))
    } yield {
      result
    }
  } 

  def nonBreakdownTypes: Seq[ResultOutputType] = values

  lazy val nonErrorTypes: Seq[ResultOutputType] = values.filterNot(_.isError)

  def fromConfig(config: Config): Set[ResultOutputType] = {

    def parseResultOutputType(name: String, config: Config): ResultOutputType = {
      val description = config.getString("description")
      val displayType = config.getOption("displayType",_.getString).getOrElse(ResultOutputType.defaultDisplayType)

      ResultOutputType(name, isBreakdown = true, ResultOutputType.I2b2Options(description, displayType), None)
    }

    import scala.jdk.CollectionConverters.SetHasAsScala
    config.root.keySet.asScala.toSet.map { name: String =>
      parseResultOutputType(name, config.getConfig(name))
    }
  }

  lazy val breakdownTypes: Set[ResultOutputType] = {
    val config: Config = ConfigSource.config
    val shrineConfig: Config = config.getConfig("shrine")

    shrineConfig.getOptionConfigured("breakdownResultOutputTypes", fromConfig).getOrElse(Set.empty)
  }

  lazy val allValidTypes: Seq[ResultOutputType] = nonBreakdownTypes ++ breakdownTypes

  private lazy val breakdownNamesToTypes: Map[String, ResultOutputType] = breakdownTypes.map(b => b.name -> b).toMap

  private lazy val breakdownNamesToTypesWithFallback = breakdownNamesToTypes.withDefault(
    //to support deleted breakdowns . See SHRINE2020-1586
    n => ResultOutputType(n, isBreakdown = true, I2b2Options(n), None)
  )

  def breakdownOrFallbackForName(name:String): ResultOutputType = breakdownNamesToTypesWithFallback(name)

  def fromBreakdownNames(breakdownNames:Seq[String]): Set[ResultOutputType] = {
    breakdownNames.map(
      name => breakdownNamesToTypes.getOrElse(name,throw UnknownBreakdownNameException(name,breakdownNamesToTypes.keys.toSeq))
    ).appended(PATIENT_COUNT_XML).toSet
  }
}

case class UnknownBreakdownNameException(unkownName:String,possibleNames:Seq[String])
  extends Exception(s"Unknown breakdown name: $unkownName . Known names are: ${possibleNames.mkString(", ")}")

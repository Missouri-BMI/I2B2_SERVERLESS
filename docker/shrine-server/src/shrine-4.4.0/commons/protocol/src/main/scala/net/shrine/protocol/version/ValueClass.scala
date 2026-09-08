package net.shrine.protocol.version

import java.text.SimpleDateFormat
import java.util.{Date, TimeZone}
import net.shrine.config.{ConfigExtensions, ConfigSource}
import net.shrine.util.Versions

/**
  * Marker trait for ValueTypes
  */
trait ValueClass[U] extends Any {
  def underlying:U

  override def toString: String = s"${this.getClass.getSimpleName}($underlying)"
}

//VersionInfo parts

class ProtocolVersion(val underlying:Int) extends AnyVal with ValueClass[Int] 

object ProtocolVersion {
  val current:ProtocolVersion = v2.versionId
}

class ShrineVersion(val underlying:String) extends AnyVal with ValueClass[String]

object ShrineVersion {
  val current:ShrineVersion = new ShrineVersion(Versions.version)
}

class ItemVersion(val underlying:Int) extends AnyVal with ValueClass[Int]

object ItemVersion {
  val one:ItemVersion = new ItemVersion(1)
  def next(itemVersion: ItemVersion) = new ItemVersion(itemVersion.underlying+1)
}

class DateStamp(val underlying:Long) extends AnyVal with ValueClass[Long] {
  def toDateString:String = DateStamp.format.format(new Date(underlying))
}

object DateStamp {
  def now = new DateStamp(System.currentTimeMillis())

  val repeatableFakeDateStamp:DateStamp = new DateStamp(0L)

  val format: SimpleDateFormat = {
    val f = new SimpleDateFormat("yyyy-MM-dd.HH:mm:ss.SSS")
    f.setTimeZone(TimeZone.getTimeZone("UTC"))
    f
  }

  def parse(string:String):DateStamp = new DateStamp(format.parse(string).getTime)
}

class JsonText(val underlying:String) extends AnyVal with ValueClass[String]

//Other shared parts of different Shrine data
class NodeName(val underlying:String) extends AnyVal with ValueClass[String]

class NodeKey(val underlying:String) extends AnyVal with ValueClass[String] 

object NodeKey {
  val localNodeKey:NodeKey = ConfigSource.atomicConfig.config.get("shrine.nodeKey",new NodeKey(_))
}

class MomQueueName(val underlying:String) extends AnyVal with ValueClass[String]

object MomQueueName {

  def apply(rawName:String):MomQueueName = new MomQueueName(urlQueueName(rawName))

  /**
   * @param rawName - unmodified name for a queue - not squashed into a resource path element
   * @return a name that can easily be used in the resource path of a URL
   */
  def urlQueueName(rawName:String):String = {
    // filter all (Unicode) characters that are not letters
    // filter neither letters nor (decimal) digits, replaceAll("[^\\p{L}]+", "")
    val urlName = rawName.filterNot((c:Char) => c.isWhitespace).
      replaceAll("[^\\p{L}\\p{Nd}]+", "").
      filterNot(_ == '.') //And your little dot, too
    if(urlName.nonEmpty) urlName
    else throw new IllegalArgumentException("ERROR: A valid Queue name must contain at least one letter!")
  }
}

class UserDomainName(val underlying:String) extends AnyVal with ValueClass[String]

class UserName(val underlying:String) extends AnyVal with ValueClass[String]
package net.shrine.protocol.version

import net.shrine.crypto.SecureRandomSource

/**
  * Type-checked ids for various kinds of data in shrine
  *
  * @author david
  * @since 1.26
  */
trait Id extends Any with ValueClass[Long] {
  /**
   * @return the id as a string wrapped in single-quotes to prevent Microsoft Excel from chopping off the last digits
   *         when it converts the (double-quoted) number to 32-bit floating point.
   *         See https://learn.microsoft.com/en-us/office/troubleshoot/excel/last-digits-changed-to-zeros
   */
  def excelSafeString:String = s"'$underlying'"
}

object Id {
  private[version] def create[I <: Id](longToI: Long => I):I = longToI(nextId())

  def nextId(): Long = SecureRandomSource.nextId()
}

class ResultId(val underlying:Long) extends AnyVal with Id

object ResultId {
  private[version] def create():ResultId = Id.create(new ResultId(_))
}

class QueryId(val underlying:Long) extends AnyVal with Id

object QueryId {
  def create():QueryId = Id.create(new QueryId(_)) //todo make private[version] when the QEP doesn't have to make these
}

class ResearcherId(val underlying:Long) extends AnyVal with Id

object ResearcherId {
  private[version] def create():ResearcherId = Id.create(new ResearcherId(_))
}
class NodeId(val underlying:Long) extends AnyVal with Id

object NodeId {
  private[version] def create():NodeId = Id.create(new NodeId(_))
}

class NetworkId(val underlying:Long) extends AnyVal with Id

object NetworkId {
  /**
    * The NetworkId is special because there's exactly one of them in a shrine network.
    */
  val oneNetwork:NetworkId = new NetworkId(1L)
}


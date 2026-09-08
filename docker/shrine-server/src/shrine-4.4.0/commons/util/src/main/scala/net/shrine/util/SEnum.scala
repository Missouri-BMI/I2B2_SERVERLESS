package net.shrine.util

import scala.collection.mutable.Buffer
import scala.collection.mutable.ListBuffer
import scala.math.Ordering
import scala.util.Try

/**
 * @author clint
 * @since Mar 11, 2011
 *
 * Adapted from http://stackoverflow.com/questions/1898932/case-classes-vs-enumerations-in-scala/4958905#4958905
 *
 * Enum objects containing enum constants mix in SEnum, with T being the enum constant class
 */
//
trait SEnum[T] {
  private type ValueType = T with Value

  //Enum constants extend Value
  trait Value extends Ordered[Value] { self: T =>
    register(this)

    //name must be supplied somehow
    def name: String

    //ordinal field, like Java (is this valuable?) 
    val ordinal: Int = nextOrdinal()

    override def toString: String = name

    //Enums can be ordered by their ordinal field
    final override def compare(other: Value): Int = this.ordinal - other.asInstanceOf[Value].ordinal
    
    override def hashCode: Int = ordinal.hashCode

    // TODO change Entire SEnum to scala enum in scala 3 : SHRINE-2808
    /**
     * Note that this was never type-safe. The only equality check is on the ordinal.
     *
     * i.e
     *
     * object SEnumTest1 {
     *   final class TestEnum1 private (val name: String) extends TestEnum1.Value
     *
     *   object TestEnum1 extends SEnum[TestEnum1] {
     *     val Foo = new TestEnum1("Foo1")
     *     val Bar = new TestEnum1("Bar1")
     *     val Baz = new TestEnum1("Baz1")
     *   }
     * }
     *
     * object SEnumTest2 {
     *   final class TestEnum2 private (val name: String) extends TestEnum2.Value
     *
     *   object TestEnum2 extends SEnum[TestEnum2] {
     *     val Foo = new TestEnum2("Foo2")
     *     val Bar = new TestEnum2("Bar2")
     *     val Baz = new TestEnum2("Baz2")
     *   }
     * }
     *
     * SEnumTest1.TestEnum1.Foo1.equals(SEnumTest2.TestEnum2.Foo2) // true but should be false
     */
    override def equals(other: Any): Boolean = {
      //NB: This can't be a pattern-match, like case that: ValueType, because that leads to
      //ClassCastExceptions under Scala 2.11.5 and (very indirectly) blows up Squeryl. :( :( :(
      other != null && other.isInstanceOf[Value] && other.asInstanceOf[ValueType].ordinal == this.ordinal
    }
  }
  
  implicit object ValueTypeOrdering extends Ordering[ValueType] {
    final override def compare(x: ValueType, y: ValueType): Int = x.compare(y)
  }

  final def values: Seq[T] = constants.toSeq

  private def asKey(name: String): String = name.toLowerCase.filter(_ != '-')
  
  import scala.util.{Failure, Success}
  
  final def tryValueOf(name: String): Try[T] = name match {
    case null => Failure(new Exception("Null name passed in"))
    case _ => constantsByName.get(asKey(name)) match {
      case Some(value) => Success(value)
      case None => Failure(new Exception(s"Unknown name '$name' passed in; known values are: ${values.map(v => s"'$v'").mkString(",")}"))
    }
  }
  
  final def valueOf(name: String): Option[T] = tryValueOf(name).toOption
  
  final def valueOfOrElse(name: String)(default: T): T = valueOf(name) match {
    case Some(v) => v
    case None => default
  }
  
  private var ordinalCounter = 0

  private def nextOrdinal() = {
    val current = ordinalCounter

    ordinalCounter += 1

    current
  }

  private def register(v: ValueType): Unit = {
    constants += v
    constantsByName += (asKey(v.name) -> v)
  }
  
  private val constants: Buffer[ValueType] = new ListBuffer

  private var constantsByName: Map[String, ValueType] = Map.empty
}

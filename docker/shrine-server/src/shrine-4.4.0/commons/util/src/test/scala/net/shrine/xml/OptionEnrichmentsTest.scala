package net.shrine.xml

import org.junit.jupiter.api.Assertions.{assertEquals, assertNull}
import org.junit.jupiter.api.Test

import scala.xml.{Elem, NodeSeq}

/**
 * @author clint
 * @since Sep 30, 2014
 */
final class OptionEnrichmentsTest {
  import net.shrine.xml.OptionEnrichments._

  private val none: Option[Int] = None
  private val nullElem: Elem = null
  private val nullString: String = null
  private def nullFn[T]: T => NodeSeq = null

  private final class Nuh(x: Int) {
    override def toString = s"xyz: $x"
    
    def toXml: NodeSeq = <baz>{ x }</baz>
  }

  @Test
  def testToXmlElem(): Unit = {
    assertNull(none.toXml(nullElem))
    assertNull(none.toXml(<foo/>))

    assertEquals(Some(123).toXml(<foo/>), <foo>123</foo>)

    assertEquals(Some(new Nuh(123)).toXml(<bar></bar>), <bar>xyz: 123</bar>)
  }
  
  @Test
  def testToXmlFn(): Unit = {
    assertNull(none.toXml(nullFn))
    assertNull(none.asInstanceOf[Option[Nuh]].toXml(_.toXml))

    assertEquals(Some(new Nuh(123)).toXml(_.toXml).toString, <baz>123</baz>.toString)
  }
  
  @Test
  def testToXmlElemAndFn(): Unit = {
    assertNull(none.toXml(<foo/>, nullFn))
    assertNull(none.asInstanceOf[Option[Nuh]].toXml(<foo/>, _.toXml))

    assertEquals(Some(new Nuh(123)).toXml(<foo/>, _.toXml).toString, <foo><baz>123</baz></foo>.toString)
  }
}
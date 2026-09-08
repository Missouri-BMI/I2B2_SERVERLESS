package net.shrine.util

import org.junit.jupiter.api.Assertions.{assertEquals, assertNotEquals}
import org.junit.jupiter.api.Test

/**
 * @author clint
 * @since Aug 24, 2012
 */
object SEnumTest {
  final class TestEnum private (val name: String) extends TestEnum.Value
  
  object TestEnum extends SEnum[TestEnum] {
    val Foo = new TestEnum("Foo")
    val Bar = new TestEnum("Bar")
    val Baz = new TestEnum("Baz")
  }
}

final class SEnumTest {
  import SEnumTest._
 
  @Test
  def testValues():Unit = {
    import TestEnum._

    assertEquals(Seq(Foo, Bar, Baz),TestEnum.values)
    assertNotEquals(Foo,Bar)
    assertNotEquals(Foo,Baz)
    assertNotEquals(Bar,Foo)
    assertNotEquals(Bar,Baz)
    assertNotEquals(Baz,Foo)
    assertNotEquals(Baz,Bar)
  }
  
  @Test
  def testValueOf():Unit = {
    import TestEnum._
    
    assertEquals(valueOf("ajklshdkalshjals"),None)

    assertEquals(valueOf("Foo").get,Foo)
    assertEquals(valueOf("fOO").get,Foo)
  }
  
  @Test
  def testNameAndToString():Unit = {
    import TestEnum._

    assertEquals("Foo",Foo.name)
    assertEquals(Foo.toString,"Foo")
  }
  
  @Test
  def testOrdinal():Unit = {
    import TestEnum._

    assertEquals(Foo.ordinal,0)
    assertEquals(Bar.ordinal,1)
    assertEquals(Baz.ordinal,2)
  }
  
  @Test
  def testCompareAndOrdering():Unit = {
    import TestEnum.{Foo, Bar, Baz}

    assertEquals(Seq(Bar, Baz, Foo).sorted,Seq(Foo, Bar, Baz))
  }
}
package net.shrine.adapter.mappings

import cats.effect.IO
import net.shrine.http4s.catsio.ExecutionContexts
import net.shrine.util.ShouldMatchersForJUnit
import org.junit.Assert.{assertEquals, assertNotEquals}
import org.junit.{After, Before, Ignore, Test}


class AdapterMappingsDbTest extends ShouldMatchersForJUnit {
  import cats.effect.unsafe.implicits.global
  val db = AdapterMappingsDb.db

  @Test
  def testNoMappings(): Unit = {
    assertEquals(Set.empty, db.localTermsFor("hello").unsafeRunSync())
  }

  @Test
  def testOneMapping(): Unit = {
    db.upsert(Seq("foo" -> "bar")).unsafeRunSync()
    assertEquals(Set("bar"), db.localTermsFor("foo").unsafeRunSync())
  }

  @Test
  def testTwoMappings(): Unit = {
    db.upsert(Seq(
      "foo" -> "bar",
      "foo" -> "more")).unsafeRunSync()

    assertEquals(Set("bar", "more"), db.localTermsFor("foo").unsafeRunSync())
  }

  @Test
  def testTwoMappingsRedundant(): Unit = {
    db.upsert(Seq(
      "foo" -> "bar",
      "foo" -> "bar",
      "foo" -> "more")).unsafeRunSync()

    assertEquals(Set("bar", "more"), db.localTermsFor("foo").unsafeRunSync())
  }

  @Test
  def testTwoMappingsIgnored(): Unit = {
    db.upsert(Seq(
      "foo" -> "bar",
      "baz" -> "egg",
      "foo" -> "more")).unsafeRunSync()

    assertEquals(Set("bar", "more"), db.localTermsFor("foo").unsafeRunSync())
  }

  @Test
  def testMultipleInsert(): Unit = {
    db.upsert(Seq(
      "foo" -> "bar",
      "baz" -> "egg",
      "foo" -> "more")).unsafeRunSync()

    assertEquals(Set("bar", "more"), db.localTermsFor("foo").unsafeRunSync())

    db.upsert(Seq(
      "foo" -> "even more",
      "spam" -> "eggs",
      "foo" -> "more")).unsafeRunSync()

    assertEquals(Set("bar", "more", "even more"), db.localTermsFor("foo").unsafeRunSync())
  }

  @Test
  def testMany(): Unit = {
    db.upsert(Seq(
      "foo" -> "1",
      "foo" -> "2",
      "foo" -> "3",
      "foo" -> "4",
      "foo" -> "5",
      "foo" -> "6",
      "foo" -> "7",
      "foo" -> "8",
      "foo" -> "9",
      "foo" -> "10",
      "baz" -> "11",
      "foo" -> "12")).unsafeRunSync()

    assertEquals(Set("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "12"), db.localTermsFor("foo").unsafeRunSync())
  }

  @Test
  def testSelectMultipleTerms(): Unit = {
    db.upsert(Seq(
      "foo" -> "bar",
      "baz" -> "egg",
      "foo" -> "more")).unsafeRunSync()

    // results are in the same order as initial
    assertEquals(Map("foo" -> Set("bar", "more"), "baz" -> Set("egg")), db.localTermsFor(Seq("foo", "baz")).unsafeRunSync())
  }

//  @Ignore // H2 database driver does not behave like mysql, even in mysql compatibility mode. See SHRINE-3606.
  @Test
  def testWithBackslash(): Unit = {
    db.upsert(Seq(
      """\\SHRINE\SHRINE\DIAGNOSIS\10\(I00-I99) Dise~3w8h\(I80-I89) Dise~cgvv\(I87) I am the same\""" -> """\\PCORI_DIAG\PCORI\DIAGNOSIS\10\(I00-I99) Dise~3w8h\(I80-I89) Dise~cgvv\(I87) I am the same\""",
      """\\SHRINE\SHRINE\DIAGNOSIS\10\(I00-I99) Dise~3w8h\(I80-I89) Dise~cgvv\(I87) I am the same\""" -> """\\PCORI_DIAG\PCORI\DIAGNOSIS\10\(I00-I99) Dise~3w8h\(I80-I89) Dise~cgvv\(I87) I am the same\""",
      """\\SHRINE\SHRINE\DIAGNOSIS\10\(I00-I99) Dise~3w8h\(I80-I89) Dise~cgvv\(I87) I am the same\""" -> """\\PCORI_DIAG\PCORI\DIAGNOSIS\10\(I00-I99) Dise~3w8h\(I80-I89) Dise~cgvv\(I87) I'm actually different\""",
      """\\SHRINE\SHRINE\DIAGNOSIS\10\(I00-I99) Dise~3w8h\(I80-I89) Dise~cgvv\(I87) I am really different\""" -> """\\PCORI_DIAG\PCORI\DIAGNOSIS\10\(I00-I99) Dise~3w8h\(I80-I89) Dise~cgvv\(I87) I am really different\""",
    )).unsafeRunSync()

    assertEquals(Set(
      """\\PCORI_DIAG\PCORI\DIAGNOSIS\10\(I00-I99) Dise~3w8h\(I80-I89) Dise~cgvv\(I87) I am the same\""",
      """\\PCORI_DIAG\PCORI\DIAGNOSIS\10\(I00-I99) Dise~3w8h\(I80-I89) Dise~cgvv\(I87) I'm actually different\""",
    ), db.localTermsFor("""\\SHRINE\SHRINE\DIAGNOSIS\10\(I00-I99) Dise~3w8h\(I80-I89) Dise~cgvv\(I87) I am the same\""").unsafeRunSync())
  }

//  @Ignore // H2 database driver does not behave like mysql, even in mysql compatibility mode. See SHRINE-3606.
  @Test
  def testBackslashSimple():Unit = {
    db.upsert(Seq(
      """foo""" -> """bar""",
      """\\foo""" -> """two-slash-bar""",
    )).unsafeRunSync()
    assertEquals(Set(
      """bar"""
    ),db.localTermsFor("""foo""").unsafeRunSync())
    assertEquals(Set(
      """two-slash-bar"""
    ),db.localTermsFor("""\\foo""").unsafeRunSync()) //this works - but the insert vs query strings have different rules ! db.localTermsFor(raw"""\\foo""").unsafeRunSync())
  }

  @Test
  def testMetaInformation(): Unit = {
    def assertMetadataMatches(filename: String, fileLastModified: Long, checksum: Long): Unit = {
      assertEquals(db.fileLastModified.unsafeRunSync(), fileLastModified)
      assertEquals(db.checksum.unsafeRunSync(), checksum)
      assertEquals(db.filename.unsafeRunSync(), filename)
    }
    assertMetadataMatches("", -1, -1)
    assertEquals(db.loadedFromFile.unsafeRunSync(), -1)

    db.updateMetaInformation("a",1L, 2L).unsafeRunSync()
    val loaded = db.loadedFromFile.unsafeRunSync()

    assertMetadataMatches("a", 1L, 2L)
    assertNotEquals(-1, loaded)

    db.updateMetaInformation("b",3L, 4L).unsafeRunSync()
    assertMetadataMatches("b", 3L, 4L)
    assertNotEquals(loaded, db.loadedFromFile.unsafeRunSync())
    assertNotEquals(-1, db.loadedFromFile.unsafeRunSync())
  }

  @Test
  def testTruncate(): Unit = {
    db.upsert(Seq("foo" -> "bar")).unsafeRunSync()
    assertNotEquals(0, db.countAll.unsafeRunSync())
    db.truncateMappings().unsafeRunSync()
    assertEquals(0, db.countAll.unsafeRunSync())
  }

  @Before
  def beforeEach(): Unit = {
  }

  @After
  def afterAll(): Unit = {
    db.dropTables.unsafeRunSync()
    db.createTables.unsafeRunSync()
  }
}

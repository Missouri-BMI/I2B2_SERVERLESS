package net.shrine.adapter.mappings

import java.io.InputStreamReader
import java.net.URL
import cats.effect.IO
import net.shrine.log.Loggable

import scala.annotation.unused

/**
  * AdapterMappings associate "global/core" shrine terms with local terms.
  * A single global term can have MANY local term mappings.
  */
case class AdapterMappings(source: String, lastModified: Long, version: String) extends Loggable {
  import cats.effect.unsafe.implicits.global

  def localTermsFor(networkTerms: Seq[String]): Map[String, Set[String]] = AdapterMappingsDb.db.localTermsFor(networkTerms).unsafeRunSync() //todo clean up with SHRINE2020-1278
}

object AdapterMappings extends Loggable {
  final val Unknown = "Unknown"
  final val Unmodified = -1L

  def apply(source: String): AdapterMappings = {
    val url = sourceToUrl(source)
    AdapterMappings(
      source = source,
      lastModified = url.fold(Unmodified)(lastModified),
      version = url.fold(Unknown)(mappingsVersion)
    )
  }

  def compareAndReloadMappings(source: String): IO[Unit] = {
    val url = sourceToUrl(source)

    // We must blow up if adapter mapping file name is misconfigured.
    url.fold(throw new IllegalArgumentException(s"Cannot find adapter mapping file $source on class path.")) { url =>

      // Reload if _any_ of these do not match the stored values, but don't calculate if not needed.
      lazy val fsFilename = url.getFile
      lazy val fsModified = lastModified(url)
      lazy val fsChecksum = checksum(url)

      def reloadOnPropChange[P](propFromDB: IO[P], fsProp: P, propName: String)(ifNotChanged: IO[Unit]): IO[Unit] = {
        propFromDB.flatMap{ dbProp =>
          if (dbProp != fsProp) {
            info(s"Adapter mappings file $source $propName changed from $dbProp to $fsProp. Reloading mappings")
            for {
              upserted <- AdapterMappingsDb.reloadMappings(new LazyCsvIterator(new InputStreamReader(url.openStream())), fsFilename, fsModified, fsChecksum)
              _ <- IO(info(s"$upserted rows from ${url.getFile} were loaded into the database."))
            } yield ()
          } else {
            debug(s"Adapter mappings file $source $propName ($fsProp) unchanged.")
            ifNotChanged
          }
        }
      }

      reloadOnPropChange(AdapterMappingsDb.db.filename, fsFilename, "filename") {
        reloadOnPropChange(AdapterMappingsDb.db.fileLastModified, fsModified, "modification date") {
          reloadOnPropChange(AdapterMappingsDb.db.checksum, fsChecksum, "checksum") {
            IO(debug(s"Adapter mappings file $source is unchanged. Mappings were not reloaded."))
          }
        }
      }
    }
  }

  private def checksum(url: URL): Long = {
    import java.io.FileInputStream
    import java.nio.channels.FileChannel
    import java.util.zip.CRC32
    val inputStream = new FileInputStream(url.getFile)
    val fileChannel = inputStream.getChannel
    val len = fileChannel.size
    val buffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, 0, len)
    val crc = new CRC32
    var current = 0
    while (current < len) {
      val i = buffer.get(current)
      crc.update(i)
      current += 1; current - 1
    }
    crc.getValue
  }

  private def sourceToUrl(source: String): Option[URL] =
    Option(getClass.getClassLoader.getResource(source))

  private def lastModified(url: URL): Long = {
    val conn = url.openConnection()
    val modified = conn.getLastModified
    conn.getInputStream.close() // release the resources
    modified
  }

  private def mappingsVersion(@unused url: URL): String = {
    Unknown // TODO get some version information from the ontology and extract it here. SHRINE-2046
  }
}

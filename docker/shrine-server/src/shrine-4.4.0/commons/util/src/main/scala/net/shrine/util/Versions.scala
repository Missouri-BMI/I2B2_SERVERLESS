package net.shrine.util

import java.util.Properties

/**
 * @author Clint Gilbert
 * @date Jul 31, 2011
 * @link http://cbmi.med.harvard.edu
 * @link http://chip.org
 */
object Versions {
  def versionString(appName: String): String = {
    s"$appName version $version built on $buildDate from scm branch $scmBranch revision $scmRevision"
  }
  
  /**
   * The SCM revision this class was built from
   */
  lazy val scmRevision: String = getFromProperties("SCM-Revision")

  /**
   * The SCM branch this class was built from
   */
  lazy val scmBranch: String = getFromProperties("SCM-Branch")
  
  /**
   * The date this class was built
   */
  lazy val buildDate: String = getFromProperties("buildDate")
  
  /**
   * The maven version of the project this class belongs to
   */
  lazy val version: String = getFromProperties("version")

  private val Unknown = "Unknown"
    
  private val propsFileName = "shrine-versions.properties"
  
  private def getFromProperties(property: String): String = {

    import java.io.{ InputStream, IOException }

    def withStream(stream: InputStream)(f: InputStream => Any): Unit = {
      if (stream != null) {
        try { f(stream) } catch { case e: java.io.IOException => () }
      }
    }

    val manifestProps = new Properties
    
    val manifestStream = Option(getClass.getClassLoader.getResourceAsStream(propsFileName))
    
    manifestStream.foreach(withStream(_)(manifestProps.load))
    
    manifestProps.getProperty(property, Unknown)
  }
}
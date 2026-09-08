package net.shrine.xml

import javax.xml.XMLConstants
import javax.xml.parsers.SAXParserFactory
import net.shrine.log.Loggable

import scala.xml.Utility.isSpace
import scala.xml.factory.XMLLoader
import scala.xml.parsing.ConstructingParser
import scala.xml.{Elem, Node, NodeSeq, PrettyPrinter, SAXParser, Text, TopScope, XML}

/**
 * @author Justin Quan
 * @author clint
 * @since 8/24/11
 * @see http://chip.org
 *       <p/>
 *       NOTICE: This software comes with NO guarantees whatsoever and is
 *       licensed as Lgpl Open Source
 * @see http://www.gnu.org/licenses/lgpl.html
 */
object XmlUtil extends Loggable {

  def loadString(xmlString:String): Elem = safeXmlLoader.loadString(xmlString)

  def safeXmlLoader: XMLLoader[Elem] = {
    val parser: SAXParser = {
      val factory = SAXParserFactory.newInstance()
      factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING,true)

      factory.setNamespaceAware(false)
      factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
      factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false)
      factory.setFeature("http://xml.org/sax/features/external-general-entities", false)
      factory.setXIncludeAware(false)

      factory.newSAXParser()
    }

    XML.withSAXParser(parser)
  }

  def trim(xml: NodeSeq): String = xml.text.trim

  def toInt(xml: NodeSeq): Int = trim(xml).toInt

  def toLong(xml: NodeSeq): Long = trim(xml).toLong

  def loadStringIgnoringRemoteResources(xml: String): Option[Node] = {
    import scala.io.Source

    ConstructingParser.fromSource(Source.fromString(xml), true).document() match {
      case null => {
        warn(s"Failed to parse XML from '$xml'")

        None
      }
      case doc => Option(doc.docElem)
    }
  }

  def stripNamespace(s: String): String = {
    stripNamespaces(XmlUtil.loadString(s)).toString
  }

  def stripNamespaces(node: Node): Node = {
    node match {
      case e: Elem => e.copy(prefix = null, scope = TopScope, child = e.child.map(stripNamespaces))
      case _ => node
    }
  }

  //NB: As of Scala 2.10, now collapses elements like <foo></foo> to <foo/> :\
  def stripWhitespace(node: Node): Node = {
    def removeWhitespaceNodes(node: Node): NodeSeq = {
      node match {
        case text @ Text(t) => if(isSpace(t)) NodeSeq.Empty else text
        case e: Elem => e.copy(child = e.child.map(removeWhitespaceNodes).flatten)
        case _ => node
      }
    }

    removeWhitespaceNodes(node).headOption.getOrElse(node)
  }

   def condense(node: Node): Node = {
     def extractOnlyNonWhitespaceChild(node: Node): Option[Text] = {
       val hasOnlyTextChildren: Boolean = node.child.forall(_.isInstanceOf[Text])

       if(!hasOnlyTextChildren) { None }
       else {
         val nonWhitespaceNodes = node.child.collect { case t: Text => t }.filterNot { case Text(t) => isSpace(t) }

         if(nonWhitespaceNodes.size > 1) { None }
         else { nonWhitespaceNodes.headOption }
       }
     }

    def doCondensing(n: Node): Node = n match {
      case elem: Elem => {
        extractOnlyNonWhitespaceChild(n) match {
          case Some(Text(t)) => elem.copy(child = Text(t.trim))
          case None => elem.copy(child = elem.child.map(doCondensing))
          case _ => throw new IllegalStateException(s"Did not expect a non-Text element: $n")
        }
      }
      case _ => n
    }

    doCondensing(node).headOption.getOrElse(node)
  }

  def renameRootTag(newRootTagName: String)(xml: Node): Node = xml match {
    case elem: Elem => elem.copy(label = newRootTagName)
    case _ => xml
  }

  def prettyPrint(xml: Node): String = {
    (new PrettyPrinter(Int.MaxValue, 2)).format(condense(xml))
  }

  def surroundWith(outerTag: Elem)(xml: NodeSeq): Node = surroundWith(outerTag.label)(xml)

  def surroundWith(outerTagName: String)(xml: NodeSeq): Node = {
    renameRootTag(outerTagName) {
      <placeHolder>
				{ xml }
			</placeHolder>
    }
  }
}

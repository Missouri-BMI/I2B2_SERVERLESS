package net.shrine.xml

import scala.util.{Failure, Success, Try}
import scala.xml.NodeSeq

/**
 * @author clint
 * @since Feb 3, 2014
 */
object NodeSeqEnrichments {
  object Helpers {
    final implicit class HasHelperNodeSeqEnrichments(val xml: NodeSeq) extends AnyVal {
      def children: NodeSeq = xml.flatMap(_.child)
    }
  }
  
  object Strictness {
    final implicit class HasStrictNodeSeqEnrichments(val xml: NodeSeq) extends AnyVal {
      def withChild(name: String): Try[NodeSeq] = Try(xml).withChild(name) 
      
      def attribute(name: String): Try[String] = Try(xml).attribute(name)
    }

    final implicit class HasStrictNodeSeqEnrichmentsForAttempts(val xmlAttempt: Try[NodeSeq]) extends AnyVal {
      def withChild(name: String): Try[NodeSeq] = xmlAttempt.flatMap { xml =>
        import Helpers._
        
        if (xml.children.exists(_.label == name)) { Success(xml \ name) }
        else Failure( MissingChildNodeException(name,xml))
      }
      
      def attribute(name: String): Try[String] = xmlAttempt.flatMap { xml =>
        val attrOption = for {
          headNode <- xml.headOption
          attributeValue <- headNode.attribute(name).map(_.text)
        } yield attributeValue
        
        attrOption match {
          case Some(a) => Success(a)
          case None => Failure(new Exception(s"Attribute named '$name' not found in XML '$xml'"))
        }
      }
    }
  }
}

case class MissingChildNodeException(nodeName:String,xml:NodeSeq,message:String) extends Exception(message)

object MissingChildNodeException extends ((String,NodeSeq,String) => MissingChildNodeException) {
  def apply(nodeName:String,xml:NodeSeq) = new MissingChildNodeException(nodeName,xml,s"Child node with label '$nodeName' not found in XML '$xml'")
}
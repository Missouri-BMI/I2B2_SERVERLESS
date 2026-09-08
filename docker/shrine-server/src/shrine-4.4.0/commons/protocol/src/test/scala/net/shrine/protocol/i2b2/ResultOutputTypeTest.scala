package net.shrine.protocol.i2b2

import org.junit.Test
import net.shrine.util.ShouldMatchersForJUnit

import scala.xml.NodeSeq
import scala.util.Success
import net.shrine.xml.XmlUtil

/**
 * @author clint
 * @since Aug 28, 2012
 */
final class ResultOutputTypeTest extends ShouldMatchersForJUnit {
  import ResultOutputType._

  @Test
  def testToXml(): Unit = {
    val breakdownTypes = DefaultBreakdownResultOutputTypes.toSet

    def expectedXml(rt: ResultOutputType): String = {
      val idXml = rt.id.map(i => <id>{ i }</id>.toString).getOrElse("")
      
      s"<resultType>$idXml<name>${rt.name}</name><isBreakdown>${rt.isBreakdown}</isBreakdown><description>${rt.i2b2Options.description}</description><displayType>${rt.i2b2Options.displayType}</displayType></resultType>"
    }
    
    for {
      rt <- ResultOutputType.values ++ breakdownTypes
    } {
      rt.toXml.toString should equal(expectedXml(rt))
    }
  }
  
  @Test
  def testFromI2b2(): Unit = {
    val breakdownTypes = DefaultBreakdownResultOutputTypes.toSet
    
    fromI2b2(null).isFailure should be(true)
    fromI2b2(<foo/>).isFailure should be(true)
    
    fromI2b2("<foo/>").isFailure should be(true)
    
    for {
      rt <- ResultOutputType.values
    } {
      fromI2b2(rt.toI2b2).get should equal(rt)
      (fromI2b2(rt.toI2b2).get eq rt) should be(true)
    }
    
    for {
      rt <- breakdownTypes
    } {
      fromI2b2(rt.toI2b2).get should equal(rt)
    }
  }
  
  @Test
  def testFromXml(): Unit = {
    val breakdownTypes = DefaultBreakdownResultOutputTypes.toSet
    
    fromXml(null).isFailure should be(true)
    fromXml(<foo/>).isFailure should be(true)
    
    fromXml("<foo/>").isFailure should be(true)
    
    for {
      rt <- ResultOutputType.values
    } {
      fromXml(rt.toXml).get should equal(rt)
      (fromXml(rt.toXml).get eq rt) should be(true)
    }
    
    for {
      rt <- breakdownTypes
    } {
      fromXml(rt.toXml).get should equal(rt)
    }
  }
  
  @Test
  def testToString(): Unit = {
    for {
      rt <- values
    } {
      rt.toString should equal(rt.name)
    }
  }
  
  @Test
  def testValues(): Unit = {
    values should equal(Seq(
      PATIENT_COUNT_XML,
      ERROR))
  }
  
  @Test
  def testValueOf(): Unit = {
    valueOf(null: String) should be(None)
    valueOf("") should be(None)
    valueOf("askldjlasdj") should be(None)
    
    valueOf("ERROR") should equal(Some(ERROR)) 
    valueOf("error") should equal(Some(ERROR))
    valueOf("ErRoR") should equal(Some(ERROR))
    
    for {
      rt <- values
    } {
      valueOf(rt.name) should equal(Some(rt))
      valueOf(rt.name.toLowerCase) should equal(Some(rt))
    }
  }
  
  @Test
  def testValueOfKnownValues(): Unit = {
    val known = DefaultBreakdownResultOutputTypes.values.toSet
    
    valueOf(known)(null) should be(None)
    valueOf(known)("") should be(None)
    valueOf(known)("askldjlasdj") should be(None)
    
    valueOf(known)("ERROR") should equal(Some(ERROR)) 
    valueOf(known)("error") should equal(Some(ERROR))
    valueOf(known)("ErRoR") should equal(Some(ERROR))
    
    for {
      rt <- known
    } {
      valueOf(known)(rt.name) should equal(Some(rt))
      valueOf(known)(rt.name.toLowerCase) should equal(Some(rt))
    }
  }
  
  @Test
  def testTryValueOfKnownValues(): Unit = {
    val known = DefaultBreakdownResultOutputTypes.values.toSet
    
    tryValueOf(known)(null).isFailure should be(true)
    tryValueOf(known)("").isFailure should be(true)
    tryValueOf(known)("askldjlasdj").isFailure should be(true)
    
    tryValueOf(known)("ERROR") should equal(Success(ERROR)) 
    tryValueOf(known)("error") should equal(Success(ERROR))
    tryValueOf(known)("ErRoR") should equal(Success(ERROR))
    
    for {
      rt <- known
    } {
      tryValueOf(known)(rt.name) should equal(Success(rt))
      tryValueOf(known)(rt.name.toLowerCase) should equal(Success(rt))
    }
  }

  @Test
  def testBreakdownFlag(): Unit =  {
    PATIENT_COUNT_XML.isBreakdown should be(false)
    ERROR.isBreakdown should be(false)
  }

  @Test
  def testIsError(): Unit =  {
    ERROR.isError should be(true)
    PATIENT_COUNT_XML.isError should be(false)
  }

  @Test
  def testNonBreakdownTypes(): Unit =  {
    nonBreakdownTypes.toSet should equal(Set(
      PATIENT_COUNT_XML,
      ERROR))
  }

  @Test
  def testNonErrorTypes(): Unit =  {
    nonErrorTypes.toSet should equal(Set(
      PATIENT_COUNT_XML))
  }
  
  @Test
  def testToI2b2():Unit = {
    import XmlUtil.{ stripWhitespace => compact }

    val expected: Map[ResultOutputType, NodeSeq] = Map(
      PATIENT_COUNT_XML -> compact(<query_result_type>
                                     <result_type_id>4</result_type_id>
                                     <name>PATIENT_COUNT_XML</name>
                                     <display_type>CATNUM</display_type>
                                     <visual_attribute_type>LA</visual_attribute_type>
                                     <description>Number of patients</description>
                                   </query_result_type>))
    for {
      outputType <- ResultOutputType.nonErrorTypes
      xml = outputType.toI2b2
    } {
      xml.toString should equal(expected(outputType).toString)
    }
  }
}
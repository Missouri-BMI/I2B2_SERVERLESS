package net.shrine.ontology

import net.shrine.xml.XmlUtil

import scala.util.{Failure, Success, Try}
import scala.xml.NodeSeq

case class LabDetail(flagValues: Option[Seq[String]] = None, units: Option[Seq[String]] = None, enumValues: Option[List[String]] = None)

object LabDetail {
  def apply(labDetailXml: String, path: String): Option[LabDetail] = {

    val xmlTry = Try(XmlUtil.loadString(labDetailXml))

    xmlTry match {

      case Success(xml) =>
        val versionNode = xml \ "Version"
        require(versionNode.nonEmpty, s"No version node in $labDetailXml")

        val flagsToUse = (xml \ "Flagstouse").text

        val flagsToUseListOption: Option[Seq[String]] = if(flagsToUse.trim.equalsIgnoreCase("HL")){
            Some(Seq("Normal", "High", "Low"))
        } else if(flagsToUse.equalsIgnoreCase("A")){
          Some(Seq("Normal", "Abnormal"))
        } else {
          None
        }

        val unitValues: NodeSeq = xml \ "UnitValues"

        val convertingUnits = unitValues \ "ConvertingUnits"

        val normalUnitsSeq: Seq[String] =for( unit <- unitValues \ "NormalUnits") yield unit.text
        val equalUnitsSeq: Seq[String] =for( unit <- unitValues \ "EqualUnits") yield unit.text
        val convertingUnitsSeq: Seq[String] =for( unit <- convertingUnits \ "Units") yield unit.text


        val unitsListOption: Option[Seq[String]] = Option((normalUnitsSeq ++ equalUnitsSeq ++ convertingUnitsSeq).distinct.filterNot(_ == "")).filter(_.nonEmpty)

        val enumValues: NodeSeq = xml \ "EnumValues"
        val enumValuesSeq = for( enum <- enumValues \ "Val") yield enum.text
        //        val enumValuesListOption: Option[List[String]] = Option(List.empty[String] ++ enumValuesSeq).filter(_.nonEmpty)
        val enumValuesListOption: Option[List[String]] = Option(enumValuesSeq.toList).filter(_.nonEmpty)

        Some(LabDetail(flagsToUseListOption, unitsListOption, enumValues = enumValuesListOption))

      case Failure(_) => None
    }
  }
}

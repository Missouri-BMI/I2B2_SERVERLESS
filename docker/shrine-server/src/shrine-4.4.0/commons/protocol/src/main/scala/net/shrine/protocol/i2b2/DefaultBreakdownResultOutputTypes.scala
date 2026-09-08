package net.shrine.protocol.i2b2

/**
 * @author clint
 * @date Oct 24, 2014
 */
object DefaultBreakdownResultOutputTypes {
  import ResultOutputType.I2b2Options
  
  //TODO: What should the ids be here?
  val PATIENT_AGE_COUNT_XML = ResultOutputType("PATIENT_AGE_COUNT_XML", true, I2b2Options("Demographic Distribution by Age"), None)
  val PATIENT_RACE_COUNT_XML = ResultOutputType("PATIENT_RACE_COUNT_XML", true, I2b2Options("Demographic Distribution by Race"), None)
  val PATIENT_VITALSTATUS_COUNT_XML = ResultOutputType("PATIENT_VITALSTATUS_COUNT_XML", true, I2b2Options("Demographic Distribution by Vital Status"), None)
  val PATIENT_GENDER_COUNT_XML = ResultOutputType("PATIENT_GENDER_COUNT_XML", true, I2b2Options("Demographic Distribution by Sex"), None)
  
  lazy val values: Seq[ResultOutputType] = Seq(
      PATIENT_AGE_COUNT_XML,
      PATIENT_RACE_COUNT_XML,
      PATIENT_VITALSTATUS_COUNT_XML,
      PATIENT_GENDER_COUNT_XML)
      
  lazy val toSet: Set[ResultOutputType] = values.toSet
}
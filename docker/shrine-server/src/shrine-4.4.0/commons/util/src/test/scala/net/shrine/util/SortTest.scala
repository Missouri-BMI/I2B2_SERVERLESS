package net.shrine.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

import scala.util.Random.shuffle

class SortTest {
  val stackOverflowExamplesSorted = List(
    "2",
    "12",
    "200000",
    "1000000",
    "a",
    "a12",
    "b2",
    "text2",
    "text2a",
    "text2a2",
    "text2a12",
    "text2b",
    "text12",
    "text12a"
  )

  // A truly alphanumeric example
  val qepAgeResultsSorted = List(
    "0-9 years old",
    "10-17 years old",
    "18-34 years old",
    "35-44 years old",
    "45-54 years old",
    "55-64 years old",
    "65-74 years old",
    "75-84 years old",
    ">= 65 years old",
    ">= 85 years old",
  )

  // Just alphabetic
  val qepRaceResultsSorted = List(
    "Aleutian",
    "American Indian",
    "Asian",
    "Asian Pacific Islander",
    "Black",
    "Eskimo",
    "Hispanic",
    "Indian",
    "Middle Eastern",
    "Multiracial",
    "Native American",
    "Navajo",
    "Not recorded",
    "Oriental",
    "Other",
    "White",
  )

  // Caps before lowecase
  val mixedCaseSorted = List(
    "A",
    "AA",
    "Aa",
    "B",
    "Zebra",
    "z",
    "z1",
    "za"
  )

  val allExamplesSorted = List(stackOverflowExamplesSorted, qepAgeResultsSorted, qepRaceResultsSorted, mixedCaseSorted)

  @Test
  def testAlphaNumericSort(): Unit = {
    for (example <- allExamplesSorted) {
      assertEquals(example, Sort.sortAlphaNumerically(shuffle(example)))
    }
  }


  @Test
  def testAlphaNumericLargeNumSort(): Unit = {
    // A truly alphanumeric example
    val medications = List(
      "Valsartan 160 Mg Oral Tablet [Diovan]",
      "NDC 00378162001",
      "NDC 16571020110",
      "XYZ 354657390232329232g82308088230823028308232h",
      "NDC 65862014930",
      "Losartan Potassium 100 Mg Oral Tablet [Cozaar]",
      "XYZ 82374639434034739270192023745378257822939399237239",
      "24 Hr Metoprolol Succinate 100 Mg Extended Release Oral Tablet",
      "Carvedilol 12.5 Mg Oral Tablet [Coreg]",
      "Hydrochlorothiazide 25 Mg / Valsartan 160 Mg Oral Tablet [Diovan Hct]",
      "XYZ 3546573902323292321823080882308230283082320",
      "NDC 31722071390",
      "NDC 2570"
    )
    val medicationsSorted = List(
      "24 Hr Metoprolol Succinate 100 Mg Extended Release Oral Tablet",
      "Carvedilol 12.5 Mg Oral Tablet [Coreg]",
      "Hydrochlorothiazide 25 Mg / Valsartan 160 Mg Oral Tablet [Diovan Hct]",
      "Losartan Potassium 100 Mg Oral Tablet [Cozaar]",
      "NDC 2570",
      "NDC 00378162001",
      "NDC 16571020110",
      "NDC 31722071390",
      "NDC 65862014930",
      "Valsartan 160 Mg Oral Tablet [Diovan]",
      "XYZ 354657390232329232g82308088230823028308232h",
      "XYZ 3546573902323292321823080882308230283082320",
      "XYZ 82374639434034739270192023745378257822939399237239",
    )
    for (example <- medications) {
      assertEquals(medicationsSorted, Sort.sortAlphaNumerically(medications))
    }
  }
}

package net.shrine.config

import org.junit.jupiter.api.{Disabled, Test}
import org.junit.jupiter.api.Assertions.{assertEquals, assertFalse, assertThrows}

final class ConfigSourceTest {

  @Test
  def testFromReference(): Unit = {
    assertEquals("from reference.conf",ConfigSource.config.getString("referenceTestTarget"))
  }

  @Test
  def testFromPassword():Unit = {
    assertEquals("from password.conf",ConfigSource.config.getString("passwordTestTarget"))
  }

  @Test
  def testFromShrine():Unit = {
    ConfigSource.config

    assertEquals("from shrine.conf",ConfigSource.config.getString("shrineTestTarget"))
  }

  @Test
  def testFromQa():Unit = {
    assertEquals("from override.conf",ConfigSource.config.getString("qaTestTarget"))
  }

  @Test
  def testOverridePasswordConf():Unit = {
    assertEquals("from password.conf",ConfigSource.config.getString("passwordShrineVariablesTestTarget"))
  }

  @Test
  def testOverrideNetworkConf():Unit = {
    assertEquals("from shrine.conf",ConfigSource.config.getString("overrideNetworkTestTarget"))
  }

  @Test
  @Disabled //todo SHRINE2020-1438  this can't be done because slick's GlobalConfig calls ConfigFactory.load()
  def testSupplyVariableToReferenceConf():Unit = {
    assertEquals("from shrine.conf",ConfigSource.config.getString("requiredByReferenceConfTestTarget"))
  }

  @Test
  @Disabled //todo SHRINE2020-1438 this can't be done because slick's GlobalConfig calls ConfigFactory.load()
  def testOverrideReferenceVariableUsedInShrineConf():Unit = {
    assertEquals("from override.conf",ConfigSource.config.getString("overrideVariablesTestTarget"))
  }

  @Test
  def testOverrideVariableUsedInShrineConf():Unit = {
    assertEquals("from override.conf",ConfigSource.config.getString("overrideShrineVariablesTestTarget"))
  }

  @Test
  def testNullAShrineValueInShrineConf():Unit = {
    assertFalse(ConfigSource.config.hasPath("nullAShrineValue"))
  }

  @Test
  def testPasswordHiveCredentials(): Unit = {
    assertEquals("examplePassword",ConfigSource.config.getString("shrine.hiveCredentials.password"))
  }

  @Test
  def testDuplicatedKeyInShrineConf():Unit = {
    assertEquals("last value in the file",ConfigSource.config.getString("duplicatedKey"))
  }
}
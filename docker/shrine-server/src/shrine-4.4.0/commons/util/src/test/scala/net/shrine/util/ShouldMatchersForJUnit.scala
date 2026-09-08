package net.shrine.util

import org.scalatest.Matchers
import org.scalatestplus.junit.AssertionsForJUnit

/**
 * @author clint
 * @since Jul 10, 2014
 *
 * Aggreation trait to easily work around lots of deprecation warnings regarding
 * org.scalatest.junit.ShouldMatchersForJUnit.  Follows that class's advice and
 * extends org.scalatest.Matchers and org.scalatest.junit.AssertionsForJUnit.
 */
//todo delete this with the last of SHRINE2020-895
trait ShouldMatchersForJUnit extends Matchers with AssertionsForJUnit

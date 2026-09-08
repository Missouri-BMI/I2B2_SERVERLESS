package net.shrine.adapter.translators

import net.shrine.adapter.mappings.AdapterMappings
import net.shrine.protocol.i2b2.query.I2b2Expression

/**
 * @author Clint Gilbert
 * @since Feb 29, 2012
 *
 * A class to translate query Expressions from Shrine terms to local terms
 * given an AdapterMappings instance.
 */
final case class ExpressionTranslator(termMapCreator: Seq[String] => Map[String, Set[String]]) {

  def translate(expr: I2b2Expression): I2b2Expression = {
    I2b2Expression.translate(expr, createTermMap).normalize
  }

  def createTermMap(strings:Seq[String]):Map[String, Set[String]] = termMapCreator(strings)
}

object ExpressionTranslator {
  def apply(mappings: Map[String, Set[String]]): ExpressionTranslator =
    ExpressionTranslator((vs: Seq[String]) => vs.map(v => v -> mappings.getOrElse(v, Set())).toMap)

  def apply(adapterMappings: AdapterMappings): ExpressionTranslator =
    ExpressionTranslator((vs: Seq[String]) => adapterMappings.localTermsFor(vs))
}
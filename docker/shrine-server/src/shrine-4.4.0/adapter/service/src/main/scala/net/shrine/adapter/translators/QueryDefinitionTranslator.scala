package net.shrine.adapter.translators

import net.shrine.protocol.i2b2.query.I2b2QueryDefinition

/**
 * @author Clint Gilbert
 * @since Feb 29, 2012
 * 
 * Convenience class to translate QueryDefinitions' wrapped Expressions 
 * from Shrine terms to local terms given an AdapterMappings instance
 */
//todo combine this indirection class with ExpressionTranslator
final class QueryDefinitionTranslator(private[translators] val expressionTranslator: ExpressionTranslator) {
  
  def translate(queryDef: I2b2QueryDefinition):I2b2QueryDefinition = queryDef.transform(expressionTranslator.translate)
}
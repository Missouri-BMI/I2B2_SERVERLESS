package net.shrine.protocol.i2b2.query

/**
 * @author clint
 * @since Dec 10, 2012
 */
sealed abstract class I2b2Conjunction(val combine: Seq[I2b2Expression] => I2b2Expression) {
  //def combine(exprs: Expression*) = combinator(exprs: _*)
}

object I2b2Conjunction {
  case object And extends I2b2Conjunction(net.shrine.protocol.i2b2.query.And.apply)
  case object Or extends I2b2Conjunction(net.shrine.protocol.i2b2.query.Or.apply)
}
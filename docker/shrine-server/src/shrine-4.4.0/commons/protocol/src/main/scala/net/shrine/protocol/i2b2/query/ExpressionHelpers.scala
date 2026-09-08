package net.shrine.protocol.i2b2.query

/**
 * @author clint
 * @since Dec 11, 2012
 */
object ExpressionHelpers {
  private[query] def is[E: Manifest](x: AnyRef) = manifest[E].runtimeClass.isAssignableFrom(x.getClass)
}

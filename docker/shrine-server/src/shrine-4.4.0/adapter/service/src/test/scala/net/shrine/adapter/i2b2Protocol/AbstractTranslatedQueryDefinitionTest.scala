package net.shrine.adapter.i2b2Protocol

import net.shrine.protocol.i2b2.query.{I2b2QueryDefinition, Or, Term}

trait AbstractTranslatedQueryDefinitionTest {
  protected val t1 = "foo"
  protected val n1 = "fooName"
  protected val t2 = "bar"
  protected val n2 = "barName"

  protected val expr: Or = Or(Term(t1, n1), Term(t2, n2))

  protected val name = "blarg"

  protected val queryDef = I2b2QueryDefinition(name, expr)
}

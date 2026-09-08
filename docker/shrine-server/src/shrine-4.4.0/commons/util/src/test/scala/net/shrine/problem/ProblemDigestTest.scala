package net.shrine.problem

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 *
 * @author dwalend
 * @since 1.20
 */
final class ProblemDigestTest {

  @Test
  def testRoundTrip():Unit = {
    val problemDigest = XmlProblemDigest(getClass.getName, "stampText", "Test problem", "A problem for testing", <details>"We use this problem for testing. Don't worry about it"</details>, 0)

    val xml = <embed>{problemDigest.toXml}</embed>
    val fromXml = XmlProblemDigest.fromXml(xml)

    assertEquals(problemDigest,fromXml)
  }
}
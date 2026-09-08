package net.shrine.qep.metrics

import org.junit.jupiter.api.Test

class ResearcherMetricsTest {

  @Test
  def testAllQueries(): Unit = {
    ResearcherMetrics.doCommand(Array("queries","--out","target/queries.csv"))
  }

  @Test
  def testAllResults(): Unit = {
    ResearcherMetrics.doCommand(Array("results", "--out", "target/results.csv"))
  }

  @Test
  def testAllResearchers(): Unit = {
    ResearcherMetrics.doCommand(Array("researchers", "--out", "target/researchers.csv"))
  }

}

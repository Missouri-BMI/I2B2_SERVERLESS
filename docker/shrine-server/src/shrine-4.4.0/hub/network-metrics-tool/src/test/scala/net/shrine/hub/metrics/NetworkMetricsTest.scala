package net.shrine.hub.metrics

import org.junit.jupiter.api.Test

class NetworkMetricsTest {

  @Test
  def testAllQueries(): Unit = {
    NetworkMetrics.doCommand(Array("queries","--out","target/queries.csv"))
  }

  @Test
  def testAllResults(): Unit = {
    NetworkMetrics.doCommand(Array("results", "--out", "target/results.csv"))
  }

  @Test
  def testAllResearchers(): Unit = {
    NetworkMetrics.doCommand(Array("researchers", "--out", "target/researchers.csv"))
  }

  @Test
  def testAllNodes(): Unit = {
    NetworkMetrics.doCommand(Array("nodes", "--out", "target/nodes.csv"))
  }
}

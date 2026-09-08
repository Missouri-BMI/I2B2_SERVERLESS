package net.shrine.hub

import cats.effect.IO
import net.shrine.hub.data.store.HubDb
import net.shrine.protocol.version.v2.querydefinition.QueryDefinition
import net.shrine.protocol.version.v2.{ErrorResult, Network, Node, ObfuscatingParameters, Query, Result, ResultMetadata, ResultProgress, ResultStatus}
import net.shrine.protocol.version.{DateStamp, MomQueueName, ResearcherId}
import org.junit.{After, Before, Test}
import org.scalatestplus.junit.AssertionsForJUnit

class OverdueResultsPollerTest extends AssertionsForJUnit {
  import cats.effect.unsafe.implicits.global
  val day: Long = 24L * 60 * 60 * 1000
  val sixDays: Long = 6L * day
  val thirtyDays: Long = 30L * day
  @Test
  def testGiveUpOnSomeResults():Unit = {
    val nodes = (1 to 3).map(i =>
      Node.create(
        name = s"node$i",
        key = s"node$i",
        userDomainName = s"node$i",
        adminEmail = s"admin@node$i.co",
        momId = s"node$i"
    ))

    val query = Query.create(
      queryDefinition = QueryDefinition.standIn,
      breakdownNames = Seq.empty,
      queryName = "expiring query",
      queryNotes = Some("expiring query, notes"),
      queryFaved = false,
      nodeOfOriginId = nodes.head.id,
      researcherId = new ResearcherId(-1),
    )

    val results = nodes.map{ node =>
      Result.create(query,node, ResultMetadata(Option(ObfuscatingParameters(5, 6.5, 10, 10))))
    }.map{r =>
      r.copy(versionInfo = r.versionInfo.copy(
        createDate = new DateStamp(r.versionInfo.createDate.underlying - sixDays),
        changeDate = new DateStamp(r.versionInfo.changeDate.underlying - sixDays)
      ))
    }

    import cats.implicits._
    val setUpDbIo: IO[List[Option[Result]]] = nodes.map(node => HubDb.db.upsertNodeIO(node)).toList.sequence.
      flatMap{_ => results.map(result => HubDb.db.upsertResultIO(result)).toList.sequence}

    setUpDbIo.unsafeRunSync()

    val now: DateStamp = DateStamp.now
    val tooLongAgo = new DateStamp(now.underlying - thirtyDays)
    assertResult(3)(HubDb.db.selectOverdueBatchIO(now,tooLongAgo,0,100).unsafeRunSync()._1.size)
    OverdueResultsPoller.checkForOverdueResults().unsafeRunSync()

    val actual = HubDb.db.selectMostRecentResultsForQueryIO(query.id).unsafeRunSync()
    assertResult(results.size)(actual.size)

    actual.foreach{ r =>
      assertResult(ResultStatus.ErrorFromCrc)(r.status)
      r match {
        case errorResult:ErrorResult =>
          assertResult(
            QueryAttemptTimeToLiveExceeded.getClass.getName.dropRight(1)
          )(errorResult.problemDigest.codec)
        case _ => fail(s"Unexpected type ${r.getClass} for $r")
      }
    }
    assert(HubDb.db.selectOverdueBatchIO(now,tooLongAgo,0,100).unsafeRunSync()._1.isEmpty)
  }

  @Test
  def testBatchResults():Unit = {
    val nodes = (1 to 3).map(i =>
      Node.create(
        name = s"node$i",
        key = s"node$i",
        userDomainName = s"node$i",
        adminEmail = s"admin@node$i.com",
        momId = s"momForNode$i"
      ))

    val query1 = Query.create(
      queryName = "expiring query",
      nodeOfOriginId = nodes.head.id,
      researcherId = new ResearcherId(-1),
      queryDefinition = QueryDefinition.standIn,
      breakdownNames = Seq.empty
    )

    val query2 = Query.create(
      queryName = "too old to care query",
      nodeOfOriginId = nodes.head.id,
      researcherId = new ResearcherId(-1),
      queryDefinition = QueryDefinition.standIn,
      breakdownNames = Seq.empty
    )

    val query3 = Query.create(
      queryName = "Pending",
      nodeOfOriginId = nodes.head.id,
      researcherId = new ResearcherId(-1),
      queryDefinition = QueryDefinition.standIn,
      breakdownNames = Seq.empty
    )

    val pendingResults = nodes.map{ node =>
      Result.create(query3,node, ResultMetadata(Option(ObfuscatingParameters(5, 6.5, 10, 10))))
    }

    val overdueResults = nodes.map{ node =>
      Result.create(query1,node, ResultMetadata(Option(ObfuscatingParameters(5, 6.5, 10, 10))))
    }.map{r =>

      r.copy(versionInfo = r.versionInfo.copy(
              createDate = new DateStamp(r.versionInfo.createDate.underlying - sixDays),
              changeDate = new DateStamp(r.versionInfo.changeDate.underlying - sixDays)
            ))
    }

    val wayOverdueResults = nodes.map{ node =>
      Result.create(query2,node, ResultMetadata(Option(ObfuscatingParameters(5, 6.5, 10, 10))))
    }.map{r =>

      r.copy(versionInfo = r.versionInfo.copy(
              createDate = new DateStamp(r.versionInfo.createDate.underlying - (sixDays+thirtyDays)),
              changeDate = new DateStamp(r.versionInfo.changeDate.underlying - (sixDays+thirtyDays))
            ))
    }

    import cats.implicits._
    val setUpDbIo: IO[List[Option[Result]]] = nodes.map(node => HubDb.db.upsertNodeIO(node)).toList.sequence.
      flatMap{_ => overdueResults.map(result => HubDb.db.upsertResultIO(result)).toList.sequence}.
      flatMap{_ => wayOverdueResults.map(result => HubDb.db.upsertResultIO(result)).toList.sequence}.
      flatMap{_ => pendingResults.map(result => HubDb.db.upsertResultIO(result)).toList.sequence}

    setUpDbIo.unsafeRunSync()

    val now: DateStamp = DateStamp.now
    val anHourAgo: DateStamp = new DateStamp(now.underlying - 60 * 60 * 1000)
    val tooLongAgo = new DateStamp(now.underlying - thirtyDays)
    assertResult(2)(HubDb.db.selectOverdueBatchIO(anHourAgo,tooLongAgo,0,2).unsafeRunSync()._1.size)
    assertResult(1)(HubDb.db.selectOverdueBatchIO(anHourAgo,tooLongAgo,1,2).unsafeRunSync()._1.size)
    assertResult(0)(HubDb.db.selectOverdueBatchIO(anHourAgo,tooLongAgo,3,2).unsafeRunSync()._1.size)

    OverdueResultsPoller.checkForOverdueResults().unsafeRunSync()

    val actualResults1 = HubDb.db.selectMostRecentResultsForQueryIO(query1.id).unsafeRunSync()
    assertResult(overdueResults.size)(actualResults1.size)

    actualResults1.foreach{ r =>
      assertResult(ResultStatus.ErrorFromCrc)(r.status)
      r match {
        case errorResult:ErrorResult =>
          assertResult(
            QueryAttemptTimeToLiveExceeded.getClass.getName.dropRight(1)
          )(errorResult.problemDigest.codec)
        case _ => fail(s"Unexpected type ${r.getClass} for $r")
      }
    }

    val actualResults2 = HubDb.db.selectMostRecentResultsForQueryIO(query2.id).unsafeRunSync()
    assertResult(wayOverdueResults.size)(actualResults2.size)
    actualResults2.foreach{ r =>
      assertResult(ResultStatus.IdAssigned)(r.status)
      r match {
        case _:ResultProgress => //this is fine

        case _ => fail(s"Unexpected type ${r.getClass} for $r")
      }
    }

    assert(HubDb.db.selectOverdueBatchIO(anHourAgo,tooLongAgo,0,100).unsafeRunSync()._1.isEmpty)
  }

  @Before
  def beforeEach(): Unit = {
    HubDb.db.createTables()

    val expectedNetwork = Network(
      networkName = "testNetwork",
      hubQueueName = MomQueueName("testHub"),
      adminEmail = "yourname@example.com",
      momId = "testNetwork",
      awsSqsConfig = None,
      kafkaConfig = None
    )
    HubDb.db.upsertNetworkIO(expectedNetwork).unsafeRunSync()
  }

  @After
  def afterEach(): Unit = {
    HubDb.db.dropTables()
  }

}

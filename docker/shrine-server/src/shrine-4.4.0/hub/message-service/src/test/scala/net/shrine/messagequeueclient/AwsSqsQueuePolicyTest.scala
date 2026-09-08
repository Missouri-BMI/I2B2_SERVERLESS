package net.shrine.messagequeueclient

import io.circe.Json
import net.shrine.config.ConfigSource
import net.shrine.protocol.version.MomQueueName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals

class AwsSqsQueuePolicyTest {

  val queueArn = "arn:aws:sqs:us-east-1:714168927121:daveTest-firstQueue.fifo"
  val queueName:MomQueueName = MomQueueName.apply("firstQueue")

  val cleanNewQueuePolicyString:String =
    """{
      |  "Version": "2012-10-17",
      |  "Id": "SHRINE-firstQueue-policy"
      |}
      |""".stripMargin
  val cleanNewQueuePolicy:Json = AwsSqsQueuePolicy.parseJson(cleanNewQueuePolicyString)

  val oneReceiverPolicyString:String =
    """{
      |  "Version" : "2012-10-17",
      |  "Id" : "SHRINE-firstQueue-policy",
      |  "Statement" : [
      |    {
      |      "Effect" : "Allow",
      |      "Principal" : {
      |        "AWS" : [
      |          "arn:aws:iam::714168927121:user/shrine-sqs-hub"
      |        ]
      |      },
      |      "Action" : [
      |        "SQS:ChangeMessageVisibility",
      |        "SQS:DeleteMessage",
      |        "SQS:ReceiveMessage",
      |        "SQS:GetQueueUrl"
      |      ],
      |      "Resource" : "arn:aws:sqs:us-east-1:714168927121:daveTest-firstQueue.fifo",
      |      "Sid" : "May-Receive-Shrine1"
      |    }
      |  ]
      |}""".stripMargin
  val oneReceiverPolicy:Json = AwsSqsQueuePolicy.parseJson(oneReceiverPolicyString)

  @Test
  def testAddReceiverToNewPolicy():Unit = {
    val result:Json = AwsSqsQueuePolicy.addReceiver(
      "arn:aws:iam::714168927121:user/shrine-sqs-hub",
      queueName,
      queueArn,
      cleanNewQueuePolicy
    )
    assertEquals(oneReceiverPolicy,result)
  }


  @Test
  def testAddReceiverToExistingPolicy():Unit = {
    val twoReceiverPolicyString:String =
      """{
        |  "Version" : "2012-10-17",
        |  "Id" : "SHRINE-firstQueue-policy",
        |  "Statement" : [
        |    {
        |      "Effect" : "Allow",
        |      "Principal" : {
        |        "AWS" : [
        |          "arn:aws:iam::714168927121:user/shrine-sqs-hub",
        |          "arn:aws:iam::714168927121:user/shrine-sqs-weirdOtherHub"
        |        ]
        |      },
        |      "Action" : [
        |        "SQS:ChangeMessageVisibility",
        |        "SQS:DeleteMessage",
        |        "SQS:ReceiveMessage",
        |        "SQS:GetQueueUrl"
        |      ],
        |      "Resource" : "arn:aws:sqs:us-east-1:714168927121:daveTest-firstQueue.fifo",
        |      "Sid" : "May-Receive-Shrine1"
        |    }
        |  ]
        |}""".stripMargin
    val twoReceiverPolicy:Json = AwsSqsQueuePolicy.parseJson(twoReceiverPolicyString)

    val result:Json = AwsSqsQueuePolicy.addReceiver(
      "arn:aws:iam::714168927121:user/shrine-sqs-weirdOtherHub",
      queueName,
      queueArn,
      oneReceiverPolicy
    )
    assertEquals(twoReceiverPolicy,result)
  }

  val oneSenderPolicyString:String =
    """{
      |  "Version" : "2012-10-17",
      |  "Id" : "SHRINE-firstQueue-policy",
      |  "Statement" : [
      |    {
      |      "Effect" : "Allow",
      |      "Principal" : {
      |        "AWS" : [
      |          "arn:aws:iam::714168927121:user/shrine-sqs-node01"
      |        ]
      |      },
      |      "Action" : [
      |        "SQS:SendMessage",
      |        "SQS:GetQueueUrl"
      |      ],
      |      "Resource" : "arn:aws:sqs:us-east-1:714168927121:daveTest-firstQueue.fifo",
      |      "Sid" : "May-Send-Shrine1"
      |    }
      |  ]
      |}""".stripMargin
  val oneSenderPolicy:Json = AwsSqsQueuePolicy.parseJson(oneSenderPolicyString)

  @Test
  def testAddSenderToNewPolicy():Unit = {
    val result:Json = AwsSqsQueuePolicy.addSender(
      "arn:aws:iam::714168927121:user/shrine-sqs-node01",
      queueName,
      queueArn,
      cleanNewQueuePolicy
    )
    assertEquals(oneSenderPolicy,result)
  }

  val twoSenderPolicyString:String =
    """{
      |  "Version" : "2012-10-17",
      |  "Id" : "SHRINE-firstQueue-policy",
      |  "Statement" : [
      |    {
      |      "Effect" : "Allow",
      |      "Principal" : {
      |        "AWS" : [
      |          "arn:aws:iam::714168927121:user/shrine-sqs-node01",
      |          "arn:aws:iam::686598906819:user/shrine-sqs-node02"
      |        ]
      |      },
      |      "Action" : [
      |        "SQS:SendMessage",
      |        "SQS:GetQueueUrl"
      |      ],
      |      "Resource" : "arn:aws:sqs:us-east-1:714168927121:daveTest-firstQueue.fifo",
      |      "Sid" : "May-Send-Shrine1"
      |    }
      |  ]
      |}""".stripMargin
  val twoSenderPolicy:Json = AwsSqsQueuePolicy.parseJson(twoSenderPolicyString)

  @Test
  def testAddSecondSenderToPolicy():Unit = {
    val result:Json = AwsSqsQueuePolicy.addSender(
      "arn:aws:iam::686598906819:user/shrine-sqs-node02",
      queueName,
      queueArn,
      oneSenderPolicy
    )
    assertEquals(twoSenderPolicy,result)
  }

  val twoSenderOneReceiverPolicyString: String =
    """
      |{
      |  "Version" : "2012-10-17",
      |  "Id" : "SHRINE-firstQueue-policy",
      |  "Statement" : [
      |    {
      |      "Effect" : "Allow",
      |      "Principal" : {
      |        "AWS" : [
      |          "arn:aws:iam::714168927121:user/shrine-sqs-node01",
      |          "arn:aws:iam::686598906819:user/shrine-sqs-node02"
      |        ]
      |      },
      |      "Action" : [
      |        "SQS:SendMessage",
      |        "SQS:GetQueueUrl"
      |      ],
      |      "Resource" : "arn:aws:sqs:us-east-1:714168927121:daveTest-firstQueue.fifo",
      |      "Sid" : "May-Send-Shrine1"
      |    },
      |    {
      |      "Effect" : "Allow",
      |      "Principal" : {
      |        "AWS" : [
      |          "arn:aws:iam::714168927121:user/shrine-sqs-hub"
      |        ]
      |      },
      |      "Action" : [
      |        "SQS:ChangeMessageVisibility",
      |        "SQS:DeleteMessage",
      |        "SQS:ReceiveMessage",
      |        "SQS:GetQueueUrl"
      |      ],
      |      "Resource" : "arn:aws:sqs:us-east-1:714168927121:daveTest-firstQueue.fifo",
      |      "Sid" : "May-Receive-Shrine1"
      |    }
      |  ]
      |}
      |""".stripMargin
  @Test
  def testAddReceiverToSenderPolicy():Unit = {
    val twoSenderOneReceiverPolicy:Json = AwsSqsQueuePolicy.parseJson(twoSenderOneReceiverPolicyString)

    val result:Json = AwsSqsQueuePolicy.addReceiver(
      "arn:aws:iam::714168927121:user/shrine-sqs-hub",
      queueName,
      queueArn,
      twoSenderPolicy
    )
    assertEquals(twoSenderOneReceiverPolicy,result)
  }

  val threeSenderPrincipalLimitPolicyString: String =
    """
      |{
      |  "Version" : "2012-10-17",
      |  "Id" : "SHRINE-firstQueue-policy",
      |  "Statement" : [
      |    {
      |      "Effect" : "Allow",
      |      "Principal" : {
      |        "AWS" : [
      |          "arn:aws:iam::714168927121:user/shrine-sqs-node01",
      |          "arn:aws:iam::686598906819:user/shrine-sqs-node02"
      |        ]
      |      },
      |      "Action" : [
      |        "SQS:SendMessage",
      |        "SQS:GetQueueUrl"
      |      ],
      |      "Resource" : "arn:aws:sqs:us-east-1:714168927121:daveTest-firstQueue.fifo",
      |      "Sid" : "May-Send-Shrine1"
      |    },
      |    {
      |      "Effect" : "Allow",
      |      "Principal" : {
      |        "AWS" : [
      |          "arn:aws:iam::686598906819:user/shrine-sqs-node03"
      |        ]
      |      },
      |      "Action" : [
      |        "SQS:SendMessage",
      |        "SQS:GetQueueUrl"
      |      ],
      |      "Resource" : "arn:aws:sqs:us-east-1:714168927121:daveTest-firstQueue.fifo",
      |      "Sid" : "May-Send-Shrine2"
      |    }
      |  ]
      |}
      |""".stripMargin
  val threeSenderPrincipalLimitPolicy: Json = AwsSqsQueuePolicy.parseJson(threeSenderPrincipalLimitPolicyString)

  @Test
  def testAddThirdSenderToPolicyWithAwsPrincipalLimit(): Unit = {
    ConfigSource.configForBlock("shrine.aws.maxPrincipalsPerSqsPolicyStatement","2",getClass.getSimpleName) {
      val result: Json = AwsSqsQueuePolicy.addSender(
        "arn:aws:iam::686598906819:user/shrine-sqs-node03",
        queueName,
        queueArn,
        twoSenderPolicy
      )
      assertEquals(threeSenderPrincipalLimitPolicy, result)
    }
  }

  val fourSenderPrincipalLimitPolicyString: String =
    """
      |{
      |  "Version" : "2012-10-17",
      |  "Id" : "SHRINE-firstQueue-policy",
      |  "Statement" : [
      |    {
      |      "Effect" : "Allow",
      |      "Principal" : {
      |        "AWS" : [
      |          "arn:aws:iam::714168927121:user/shrine-sqs-node01",
      |          "arn:aws:iam::686598906819:user/shrine-sqs-node02"
      |        ]
      |      },
      |      "Action" : [
      |        "SQS:SendMessage",
      |        "SQS:GetQueueUrl"
      |      ],
      |      "Resource" : "arn:aws:sqs:us-east-1:714168927121:daveTest-firstQueue.fifo",
      |      "Sid" : "May-Send-Shrine1"
      |    },
      |    {
      |      "Effect" : "Allow",
      |      "Principal" : {
      |        "AWS" : [
      |          "arn:aws:iam::686598906819:user/shrine-sqs-node03",
      |          "arn:aws:iam::686598906819:user/shrine-sqs-node04"
      |        ]
      |      },
      |      "Action" : [
      |        "SQS:SendMessage",
      |        "SQS:GetQueueUrl"
      |      ],
      |      "Resource" : "arn:aws:sqs:us-east-1:714168927121:daveTest-firstQueue.fifo",
      |      "Sid" : "May-Send-Shrine2"
      |    }
      |  ]
      |}
      |""".stripMargin
  val fourSenderPrincipalLimitPolicy: Json = AwsSqsQueuePolicy.parseJson(fourSenderPrincipalLimitPolicyString)

  @Test
  def testAddForthSenderToPolicyWithAwsPrincipalLimit(): Unit = {

    ConfigSource.configForBlock("shrine.aws.maxPrincipalsPerSqsPolicyStatement", "2", getClass.getSimpleName) {
      val result: Json = AwsSqsQueuePolicy.addSender(
        "arn:aws:iam::686598906819:user/shrine-sqs-node04",
        queueName,
        queueArn,
        threeSenderPrincipalLimitPolicy
      )
      assertEquals(fourSenderPrincipalLimitPolicy, result)
    }
  }
  @Test
  def testRemoveSenderFromPolicy():Unit = {
    val oneSenderOneReceiverPolicyString =
      """
        |{
        |  "Version" : "2012-10-17",
        |  "Id" : "SHRINE-firstQueue-policy",
        |  "Statement" : [
        |    {
        |      "Effect" : "Allow",
        |      "Principal" : {
        |        "AWS" : [
        |          "arn:aws:iam::714168927121:user/shrine-sqs-node01"
        |        ]
        |      },
        |      "Action" : [
        |        "SQS:SendMessage",
        |        "SQS:GetQueueUrl"
        |      ],
        |      "Resource" : "arn:aws:sqs:us-east-1:714168927121:daveTest-firstQueue.fifo",
        |      "Sid" : "May-Send-Shrine1"
        |    },
        |    {
        |      "Effect" : "Allow",
        |      "Principal" : {
        |        "AWS" : [
        |          "arn:aws:iam::714168927121:user/shrine-sqs-hub"
        |        ]
        |      },
        |      "Action" : [
        |        "SQS:ChangeMessageVisibility",
        |        "SQS:DeleteMessage",
        |        "SQS:ReceiveMessage",
        |        "SQS:GetQueueUrl"
        |      ],
        |      "Resource" : "arn:aws:sqs:us-east-1:714168927121:daveTest-firstQueue.fifo",
        |      "Sid" : "May-Receive-Shrine1"
        |    }
        |  ]
        |}
        |""".stripMargin

    val oneSenderOneReceiverPolicy:Json = AwsSqsQueuePolicy.parseJson(oneSenderOneReceiverPolicyString)

    val twoSenderOneReceiverPolicy:Json = AwsSqsQueuePolicy.parseJson(twoSenderOneReceiverPolicyString)

    val result:Json = AwsSqsQueuePolicy.removePrincipal(
      "arn:aws:iam::686598906819:user/shrine-sqs-node02",
      twoSenderOneReceiverPolicy
    )
    assertEquals(oneSenderOneReceiverPolicy,result)
  }

  @Test
  def testRemoveSenderFromPolicyShrineDev():Unit = {
    val foundPolicyString =
      """{
        |  "Version" : "2012-10-17",
        |  "Id" : "SHRINE-hub-policy",
        |  "Statement" : [
        |    {
        |      "Sid" : "May-Receive-Shrine",
        |      "Effect" : "Allow",
        |      "Principal" : {
        |        "AWS" : "arn:aws:iam::714168927121:user/shrine-dev-hub"
        |      },
        |      "Action" : [
        |        "SQS:ChangeMessageVisibility",
        |        "SQS:DeleteMessage",
        |        "SQS:ReceiveMessage",
        |        "SQS:GetQueueUrl"
        |      ],
        |      "Resource" : "arn:aws:sqs:us-east-1:714168927121:shrine-dev-hub.fifo"
        |    },
        |    {
        |      "Sid" : "May-Send-Shrine",
        |      "Effect" : "Allow",
        |      "Principal" : {
        |        "AWS" : [
        |          "arn:aws:iam::714168927121:user/shrine-dev-node03",
        |          "arn:aws:iam::714168927121:user/shrine-dev-node02",
        |          "arn:aws:iam::714168927121:user/shrine-dev-hub",
        |          "arn:aws:iam::714168927121:user/shrine-dev-node01"
        |        ]
        |      },
        |      "Action" : [
        |        "SQS:SendMessage",
        |        "SQS:GetQueueUrl"
        |      ],
        |      "Resource" : "arn:aws:sqs:us-east-1:714168927121:shrine-dev-hub.fifo"
        |    }
        |  ]
        |}
        |""".stripMargin

    val expectedPolicyString =
      """{
        |  "Version" : "2012-10-17",
        |  "Id" : "SHRINE-hub-policy",
        |  "Statement" : [
        |    {
        |      "Sid" : "May-Receive-Shrine",
        |      "Effect" : "Allow",
        |      "Principal" : {
        |        "AWS" : [
        |          "arn:aws:iam::714168927121:user/shrine-dev-hub"
        |        ]
        |      },
        |      "Action" : [
        |        "SQS:ChangeMessageVisibility",
        |        "SQS:DeleteMessage",
        |        "SQS:ReceiveMessage",
        |        "SQS:GetQueueUrl"
        |      ],
        |      "Resource" : "arn:aws:sqs:us-east-1:714168927121:shrine-dev-hub.fifo"
        |    },
        |    {
        |      "Sid" : "May-Send-Shrine",
        |      "Effect" : "Allow",
        |      "Principal" : {
        |        "AWS" : [
        |          "arn:aws:iam::714168927121:user/shrine-dev-node02",
        |          "arn:aws:iam::714168927121:user/shrine-dev-hub",
        |          "arn:aws:iam::714168927121:user/shrine-dev-node01"
        |        ]
        |      },
        |      "Action" : [
        |        "SQS:SendMessage",
        |        "SQS:GetQueueUrl"
        |      ],
        |      "Resource" : "arn:aws:sqs:us-east-1:714168927121:shrine-dev-hub.fifo"
        |    }
        |  ]
        |}
        |""".stripMargin


    val foundPolicy:Json = AwsSqsQueuePolicy.parseJson(foundPolicyString)
    val expectedPolicy:Json = AwsSqsQueuePolicy.parseJson(expectedPolicyString)

    val result:Json = AwsSqsQueuePolicy.removePrincipal(
      "arn:aws:iam::714168927121:user/shrine-dev-node03",
      foundPolicy
    )
    assertEquals(expectedPolicy,result)
  }

}

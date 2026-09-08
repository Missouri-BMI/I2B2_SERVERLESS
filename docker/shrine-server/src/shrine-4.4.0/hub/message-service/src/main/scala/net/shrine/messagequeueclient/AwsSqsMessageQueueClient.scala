package net.shrine.messagequeueclient

import cats.effect.IO
import fs2.Stream
import io.circe.{Json, JsonObject}
import io.laserdisc.pure.sqs.tagless.{Interpreter, SqsAsyncClientOp}
import net.shrine.config.ConfigSource
import net.shrine.http4s.catsio.ExecutionContexts
import net.shrine.log.Loggable
import net.shrine.messagequeueservice.{DeliveryAttemptId, Message, MessageQueueService, MomQueue}
import net.shrine.protocol.version.MomQueueName
import net.shrine.protocol.version.v2.AwsSqsConfig
import software.amazon.awssdk.auth.credentials.{AwsBasicCredentials, StaticCredentialsProvider}
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.iam.IamClient
import software.amazon.awssdk.services.iam.model.PutUserPolicyRequest
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import software.amazon.awssdk.services.sqs.model.{CreateQueueRequest, CreateQueueResponse, DeleteMessageRequest, DeleteMessageResponse, DeleteQueueRequest, DeleteQueueResponse, GetQueueAttributesRequest, GetQueueAttributesResponse, GetQueueUrlRequest, GetQueueUrlResponse, ListQueuesRequest, ListQueuesResponse, QueueAttributeName, ReceiveMessageRequest, ReceiveMessageResponse, SendMessageRequest, SendMessageResponse, SetQueueAttributesRequest}

import scala.concurrent.duration.Duration
import scala.jdk.javaapi.CollectionConverters

/**
  * A MessageQueueService that adapts for AWS SQS .
  *
  * @author David
  * @since 3.3
  */

case class AwsSqsMessageQueueClient(awsSqsConfig: AwsSqsConfig) extends MessageQueueService with Loggable {

  info(s"Using AwsSqsMessageQueueClient")
  override def createQueueIfAbsentIO(queueName: MomQueueName): IO[SqsQueue] = {

    useAwsSqs{ sqsClient: SqsAsyncClientOp[IO] =>
      val attributes = CollectionConverters.asJava(Map(
        QueueAttributeName.RECEIVE_MESSAGE_WAIT_TIME_SECONDS -> "20"
      ))

      val request: CreateQueueRequest = CreateQueueRequest.builder()
        .queueName(AwsSqsQueueUrl.toAwsQueueName(awsSqsConfig.networkPrefix,queueName))
        .attributes(attributes)
        .build()
      info(s"Will create queue with $request")
      val response: IO[CreateQueueResponse] = sqsClient.createQueue(request)
      response.map{ r =>
        info(s"Created SQS queue with url ${r.queueUrl()}")
        val queue = SqsQueue(queueName,new AwsSqsQueueUrl(r.queueUrl()))
        queue
      }
    }
  }

  private sealed trait Role

  private object Sender extends Role
  private object Receiver extends Role

  /**
   * @param queueName MOM queue name
   * @param awsIam An AWS Iam Arn like "arn:aws:iam::714168927121:user/shrine-sqs-hub"
   */
  override def addReceiverPermissionToQueueIO(queueName: MomQueueName, awsIam: String): IO[Unit] = {
    addPermissionToQueueIO(queueName,awsIam,Receiver)
  }

  /**
   * @param queueName MOM queue name
   * @param awsIam An AWS Iam Arn like "arn:aws:iam::714168927121:user/shrine-sqs-hub"
   */
  override def addSenderPermissionToQueueIO(queueName: MomQueueName, awsIam: String): IO[Unit] = {
    addPermissionToQueueIO(queueName,awsIam,Sender)
  }

  /**
   * @param queueName MOM queue name
   * @param awsIam An AWS Iam Arn like "arn:aws:iam::714168927121:user/shrine-sqs-hub"
   */
  override def removePermissionFromQueueIO(queueName: MomQueueName, awsIam: String): IO[Unit] = {
    useAwsSqs { sqsClient: SqsAsyncClientOp[IO] =>
      getQueueIO(queueName) flatMap { sqsQueue =>
        import scala.jdk.CollectionConverters._

        val getPolicyRequest = GetQueueAttributesRequest.builder()
          .queueUrl(sqsQueue.url.underlying)
          .attributeNames(QueueAttributeName.POLICY,QueueAttributeName.QUEUE_ARN)
          .build()
        debug(s"Will request permissions with $getPolicyRequest")

        val getPolicyResponse: IO[GetQueueAttributesResponse] = sqsClient.getQueueAttributes(getPolicyRequest)
        getPolicyResponse.flatMap { getPolicyResponse =>
          info(s"getPolicyResponse is $getPolicyResponse")

          val policyString: String = Option(getPolicyResponse.attributes().get(QueueAttributeName.POLICY)).getOrElse(starterPolicyString)
          val originalPolicyJson: Json = AwsSqsQueuePolicy.parseJson(policyString)

          val newPolicyJson = AwsSqsQueuePolicy.removePrincipal(awsIam, originalPolicyJson)
          debug(s"newPolicyJson is $newPolicyJson")

          val map = Map(QueueAttributeName.POLICY.toString -> newPolicyJson.toString)
          val setPolicyRequest = SetQueueAttributesRequest.builder()
            .queueUrl(sqsQueue.url.underlying)
            .attributesWithStrings(map.asJava)
            .build()
          info(s"Will set policy with $setPolicyRequest")
          val response = sqsClient.setQueueAttributes(setPolicyRequest)
          response
        }.flatMap(_ => IO.unit)
      }
    }
  }

  private val starterPolicyString ="""{
                                     |  "Version": "2012-10-17",
                                     |  "Id": "SHRINE-starter-policy"
                                     |}
                                     |""".stripMargin

  private def addPermissionToQueueIO(queueName:MomQueueName, awsIam:String, role:Role): IO[Unit] = {
    // software.amazon.awssdk.services.sqs.model.AddPermissionRequest will only take account ids, nothing more refined; that API is a nonstarter - blog fodder
    useAwsSqs { sqsClient: SqsAsyncClientOp[IO] =>
      getQueueIO(queueName) flatMap { sqsQueue: SqsQueue =>
        getQueueAttributes(sqsQueue).flatMap { getPolicyResponse =>
          import scala.jdk.CollectionConverters._

          val policyString: String = Option(getPolicyResponse.attributes().get(QueueAttributeName.POLICY)).getOrElse(starterPolicyString)
          val queueArn: String = getPolicyResponse.attributes().get(QueueAttributeName.QUEUE_ARN)
          val originalPolicyJson = AwsSqsQueuePolicy.parseJson(policyString)
          val newPolicyJson = role match {
            case Sender => AwsSqsQueuePolicy.addSender(awsIam, queueName, queueArn, originalPolicyJson)
            case Receiver => AwsSqsQueuePolicy.addReceiver(awsIam, queueName, queueArn, originalPolicyJson)
          }
          debug(s"newPolicyJson is $newPolicyJson")

          val map = Map(QueueAttributeName.POLICY.toString -> newPolicyJson.toString())
          val setPolicyRequest = SetQueueAttributesRequest.builder()
            .queueUrl(sqsQueue.url.underlying)
            .attributesWithStrings(map.asJava)
            .build()
          info(s"Will set policy with $setPolicyRequest")
          val response = sqsClient.setQueueAttributes(setPolicyRequest)
          response
        }.flatMap(_ => IO.unit)
      }
    }
  }

  def getQueueArn(queueName:MomQueueName):IO[String] = {
    getQueueIO(queueName) flatMap { sqsQueue: SqsQueue =>
      getQueueAttributes(sqsQueue).map { getPolicyResponse =>
        getPolicyResponse.attributes().get(QueueAttributeName.QUEUE_ARN)
      }
    }
  }

  private def getQueueAttributes(sqsQueue: SqsQueue): IO[GetQueueAttributesResponse] = {
    useAwsSqs { sqsClient: SqsAsyncClientOp[IO] =>
      val getPolicyRequest = GetQueueAttributesRequest.builder()
        .queueUrl(sqsQueue.url.underlying)
        .attributeNames(QueueAttributeName.POLICY,QueueAttributeName.QUEUE_ARN)
        .build()
      debug(s"Will request queue attributes with $getPolicyRequest")

      val getPolicyResponse = sqsClient.getQueueAttributes(getPolicyRequest)
      getPolicyResponse
    }
  }

  override def getQueueIO(queueName: MomQueueName): IO[SqsQueue] = {
    info(s"About to get SQS queue named $queueName")

    useAwsSqs{ sqsClient: SqsAsyncClientOp[IO] =>
      val request: GetQueueUrlRequest = GetQueueUrlRequest.builder()
        .queueName(AwsSqsQueueUrl.toAwsQueueName(awsSqsConfig.networkPrefix,queueName))
        .queueOwnerAWSAccountId(awsSqsConfig.queueOwnerAwsAccountId)
        .build()

      info(s"Will get queue with $request")
      val response: IO[GetQueueUrlResponse] = sqsClient.getQueueUrl(request)
      response.map{r => SqsQueue(queueName,new AwsSqsQueueUrl(r.queueUrl()))}
    }
  }

  override def deleteQueueIO(queueName: MomQueueName): IO[Unit] = {
    info(s"About to delete SQS queue named $queueName")

    useAwsSqs { sqsClient: SqsAsyncClientOp[IO] =>
      getQueueIO(queueName).flatMap{ sqsQueue =>
        val request: DeleteQueueRequest = DeleteQueueRequest.builder()
          .queueUrl(sqsQueue.url.underlying)
          .build()
        val responseIO: IO[DeleteQueueResponse] = sqsClient.deleteQueue(request)
        responseIO
      }.flatMap{response =>
        IO(info(response.toString))
      }.flatMap(_ => IO.unit)
    }
  }

  override def queuesIO: IO[Seq[SqsQueue]] = {
    useAwsSqs{ sqsClient: SqsAsyncClientOp[IO] =>
      val request = ListQueuesRequest.builder()
        .queueNamePrefix(awsSqsConfig.networkPrefix)
        .build()
      val response: IO[ListQueuesResponse] = sqsClient.listQueues(request)
      import scala.jdk.CollectionConverters._
      response.map(_.queueUrls().asScala.map{ urlString =>
        val url = new AwsSqsQueueUrl(urlString)
        SqsQueue(url.momQueueName,url)
      }.toSeq)
    }
  }

  def putIamReceiveUserPolicy(userName:String,identifier:String,queueArn:String): IO[Unit] = {
    val receiveFromPolicy = s"""{
                                |    "Version": "2012-10-17",
                                |    "Statement": [
                                |        {
                                |            "Sid": "MayReceiveShrine",
                                |            "Effect": "Allow",
                                |            "Action": [
                                |                "SQS:ChangeMessageVisibility",
                                |                "SQS:DeleteMessage",
                                |                "SQS:ReceiveMessage",
                                |                "SQS:GetQueueUrl"
                                |            ],
                                |            "Resource": "$queueArn"
                                |        }
                                |    ]
                                |}""".stripMargin
    putIamUserPolicy(userName,s"receive-from-$identifier",receiveFromPolicy)
  }

  def putIamSendUserPolicy(userName:String,identifier:String,queueArn:String):IO[Unit] = {
    val sendToPolicy = s"""{
                              |    "Version": "2012-10-17",
                              |    "Statement": [
                              |        {
                              |            "Sid": "MaySendShrine",
                              |            "Effect": "Allow",
                              |            "Action": [
                              |                "sqs:GetQueueUrl",
                              |                "sqs:SendMessage"
                              |            ],
                              |            "Resource": "$queueArn"
                              |        }
                              |    ]
                              |}""".stripMargin
    putIamUserPolicy(userName,s"send-to-$identifier",sendToPolicy)
  }

  private def putIamUserPolicy(userName:String,policyName:String,policy:String): IO[Unit] = {
    val putUserPolicyRequest = PutUserPolicyRequest.builder()
      .userName(userName)
      .policyName(policyName)
      .policyDocument(policy).build()

    useAwsIam{iam =>
      info(s"Adding $policyName for $userName\n$policy")
      iam.putUserPolicy(putUserPolicyRequest)
    }.flatMap{_ => IO.unit}
  }

  private def useAwsSqs[A](
                            block:SqsAsyncClientOp[IO] => IO[A],
                          ): IO[A] = {
    Interpreter[IO].SqsAsyncClientOpResource(
      SqsAsyncClient
        .builder()
        .credentialsProvider(StaticCredentialsProvider.create(awsBasicCredentials))
        .region(region)
    ).use{sqsClient: SqsAsyncClientOp[IO] =>
      block(sqsClient)
    }
  }

  //todo should io.laserdisc support Iam, use that instead of the AWS SDK
  private def useAwsIam[A](
                            block:IamClient => A,
                          ):IO[A] = {
    val iamClient = IamClient
      .builder()
      .credentialsProvider(StaticCredentialsProvider.create(awsBasicCredentials))
      .region(Region.AWS_GLOBAL)
      .build()

    try {
      IO.blocking(block(iamClient))
    } finally {
      iamClient.close()
    }
  }

  val region: Region = Region.of(awsSqsConfig.regionName)

  private val awsBasicCredentials: AwsBasicCredentials = {
    val awsAccessKeyId: String = ConfigSource.config.getString("shrine.aws.accessKeyId")
    val awsSecretAccessKey: String = ConfigSource.config.getString("shrine.aws.secretAccessKey")
    AwsBasicCredentials.create(awsAccessKeyId, awsSecretAccessKey)
  }


  case class SqsQueue(name: MomQueueName,url:AwsSqsQueueUrl) extends MomQueue {

    override def sendIO(contents: String,subject: Long): IO[Unit] = {
      useAwsSqs{ sqsClient: SqsAsyncClientOp[IO] =>
        val request = SendMessageRequest.builder()
          .queueUrl(url.underlying)
          .messageBody(contents)
          .build()
        val response: IO[SendMessageResponse] = sqsClient.sendMessage(request)
        response.flatMap(_ => IO.unit)
      }
    }

    override def receiveIO(timeout: Duration): IO[Option[Message]] = {
      useAwsSqs{ sqsClient: SqsAsyncClientOp[IO] =>
        val request = ReceiveMessageRequest.builder()
          .queueUrl(url.underlying)
          .waitTimeSeconds(20)
          .maxNumberOfMessages(1)
          .build()
        val response: IO[ReceiveMessageResponse] = sqsClient.receiveMessage(request)
        response.map{r => CollectionConverters.asScala(r.messages()).headOption.map(m => SqsMessage(m,url))}
      }
    }

    override def receiveStream(): Stream[IO, Message] = {
      //todo share this streaming bit with fs2-aws
      info(s"start receiveStream from ${name.underlying}")
      val request = ReceiveMessageRequest.builder()
        .queueUrl(url.underlying)
        .waitTimeSeconds(20)
        .maxNumberOfMessages(10)
        .build()

      Stream.repeatEval(useAwsSqs({ sqsClient: SqsAsyncClientOp[IO] =>
        info(s"About to request messages")
        sqsClient.receiveMessage(request)
      })
      ).flatMap{ rmr:ReceiveMessageResponse =>
        info(s"Received ${rmr.messages().size()} messages")
        Stream.emits{
          CollectionConverters.asScala(rmr.messages()).map{ m => SqsMessage(m,url) }
        }
      }
    }
  }

  case class SqsMessage (
                          contents:String,
                          queueUrl:AwsSqsQueueUrl,
                          receiptHandle:String
                        ) extends Message {
    /**
     * Call after the receiver has completed work on this message to prevent it being redelivered
     */
    override def completeIO(): IO[Unit] = {
      useAwsSqs{ sqsClient: SqsAsyncClientOp[IO] =>
        val request = DeleteMessageRequest.builder()
          .queueUrl(queueUrl.underlying)
          .receiptHandle(receiptHandle)
          .build()
        val response: IO[DeleteMessageResponse] = sqsClient.deleteMessage(request)
        response.flatMap(_ => IO.unit)
      }
    }


    override lazy val deliveryAttemptId: DeliveryAttemptId = new DeliveryAttemptId(receiptHandle.hashCode.toLong)

    override lazy val millisecondsToComplete: Long = 30000L //could be read from a property, but 30 seconds is the default
  }

  private object SqsMessage {
    def apply(message: software.amazon.awssdk.services.sqs.model.Message,queueUrl:AwsSqsQueueUrl):SqsMessage = {
      new SqsMessage(
        contents = message.body(),
        queueUrl = queueUrl,
        receiptHandle = message.receiptHandle()
      )
    }
  }
}

class AwsSqsQueueUrl(val underlying:String) extends AnyVal{

  def momQueueName:MomQueueName = {
    // take apart https://sqs.us-east-1.amazonaws.com/714168927121/devNet-shrinedevnode03.fifo to get just queueName
    MomQueueName(underlying.split("/").last.split("""\.""").head.split("-").last)
  }

  def name:String = {
    momQueueName.underlying
  }
}

object AwsSqsQueueUrl {
  def toAwsQueueName(networkPrefix:String,name:MomQueueName):String = {
    s"$networkPrefix-${name.underlying}"
  }
}

object AwsSqsQueuePolicy extends Loggable {

  /**
   * @param awsIam An AWS Iam Arn like "arn:aws:iam::714168927121:user/shrine-sqs-hub"
   * @param awsSqs An AWS Queue Arn like "arn:aws:sqs:us-east-1:714168927121:daveTest-firstQueue.fifo"
   * @param policy Json access policy downloaded from AWS
   * @param baseSid policy statement id
   * @param actions for this policy statement
   * @return A policy that allows the awsIam to use the actions on the aws sqs queue
   */
  private def addPrincipal(awsIam:String,queueName: MomQueueName,awsSqs:String,policy:Json)(baseSid:String, actions:Json):Json = {
    //set policy id to something not ARN-based
    val policyWithId: Option[Json] = policy.asObject.map(_.add("Id",Json.fromString(s"SHRINE-${queueName.underlying}-policy"))).map(Json.fromJsonObject)

    //If there's no Statement list - add a Statement list - as an array
    val jsonWithStatementArray: Option[JsonObject] = for {
      policyWId: Json <- policyWithId
      statementArray: Json <- policyWId.hcursor
        .downField("Statement")
        .focus
        .orElse(Option(Json.arr())) //an empty array
      jsonObject: JsonObject <- policyWId.asObject
    } yield jsonObject.add("Statement",statementArray)

    //if there's an existing statement in the list with fewer than maxPrincipalsPerSqsPolicyStatement principals
    //then add to the first list with space in it
    //if there's no statement or no existing statement in the list with fewer than maxPrincipalsPerSqsPolicyStatement
    //principals then create a new statement
    def sidAndStatementForPrincipal(sidCount:Int):(String,Json) = {
      //if there's an existing statement in the list then maybe reuse that
      val sid = s"$baseSid$sidCount"
      val existingStatement: Option[Json] = {
        policyWithId.get.hcursor
          .downField("Statement")
          .withFocus { (j: Json) =>
            j.asArray.flatMap { (v: Vector[Json]) =>
              v.find(_.asObject.flatMap(_.apply("Sid")).flatMap(_.asString).exists(_ == sid))
            }.getOrElse(Json.Null)
          }.focus
      }.filterNot(_ == Json.Null)

      val sidAndStatementWithRoom: Option[(String, Json)] = existingStatement.map{ es =>
        //if there are fewer than maxPrincipalsPerSqsPolicyStatement statements, OK to use this one
        val okToUse: Boolean = es.hcursor.downField("Principal").downField("AWS").values.forall(_.size < ConfigSource.config.getInt("shrine.aws.maxPrincipalsPerSqsPolicyStatement"))
        if(okToUse) (sid,es)
        else sidAndStatementForPrincipal(sidCount+1) //otherwise use the next one
      }

      val sidAndStatementToUse: (String, Json) = sidAndStatementWithRoom.getOrElse {
        //otherwise make a new statement

        //but If there's no statement create one
        val emptyStatement: Json = parseJson(
          """{
            | "Effect": "Allow",
            | "Principal": {
            |   "AWS": []
            | },
            | "Action": []
            |}""".stripMargin)

        val statementWithResource: Option[JsonObject] = for {
          statementObject <- emptyStatement.asObject
        } yield
          statementObject.add(
            "Resource",
            Json.fromString(awsSqs) //todo is this really needed??
          )

        val statement: Option[Json] = for {
          statementObject: JsonObject <- statementWithResource
        } yield Json.fromJsonObject(statementObject
          .add("Sid", Json.fromString(sid))
          .add("Action", actions))
        (sid,statement.get)
      }
      sidAndStatementToUse
    }

    val sidAndStatement = sidAndStatementForPrincipal(1)

    info(s"sidAndStatementForPrincipal is $sidAndStatement")

    val statementWithNewPrincipal: Option[Json] = for {
      newStatement: Json <- sidAndStatement._2.hcursor
        .downField("Principal")
        .downField("AWS")
        .withFocus { (j: Json) =>
          val principals: Seq[Json] = if(j.isArray) j.asArray.get.appended(Json.fromString(awsIam))
          else Seq(j,Json.fromString(awsIam))
          Json.fromValues(principals.distinct)
        }
        .top
    } yield newStatement

    val newPolicy: Option[Json] = for{
      jsonObject: JsonObject <- jsonWithStatementArray
      json <- Option(Json.fromJsonObject(jsonObject))

      statement:Json <- statementWithNewPrincipal
      newPolicy: Json <- json.hcursor
        .downField("Statement")
        //keep all but the old statement if there is one
        .withFocus { (j: Json) =>
          Json.fromValues(
            j.asArray.get.filter(_.asObject.flatMap(_.apply("Sid")).flatMap(_.asString).exists ( _ != sidAndStatement._1))
          )
        }
        //add the new statement
        .withFocus((j: Json) => Json.fromValues(j.asArray.get.appended(statement)))
        .top

    } yield newPolicy

    newPolicy.get
  }

  /**
   * @param awsIam An AWS Iam Arn like "arn:aws:iam::714168927121:user/shrine-sqs-hub"
   * @param policy original policy
   * @return a new policy where awsIam is no longer a Principal anywhere
   */
  def removePrincipal(awsIam:String,policy:Json):Json = {

    val policyWithoutAwsIam: Option[Json] = policy.hcursor.downField("Statement").withFocus{ statementSection:Json =>
      val ss: Option[Json] = statementSection.asArray.map{ statements: Seq[Json] => //scan through all the statements
        val s: Seq[Json] = statements.flatMap{ json: Json => //a Statement
          info(s"Statement $json \n")
          val principalSection: Option[Json] = json.hcursor.downField("Principal").downField("AWS").withFocus{ idsSection:Json => //json array of awsIam

            val idsWithoutAwsIam: Option[Seq[Json]] = if(idsSection.isArray) {
                idsSection.asArray.map{ ids: Seq[Json] => //seq of awsIam
                ids.filterNot(id => id.asString.contains(awsIam))
              }
            } else {
              if(idsSection.asString.contains(awsIam)) None
              else Option(Seq(idsSection))
            }

            idsWithoutAwsIam.map{ idJsons => Json.fromValues(idJsons.toVector)}.getOrElse(Json.Null)
          }.top
          principalSection
        }
        Json.fromValues(s.toVector)
      }
      ss.getOrElse(Json.Null)
    }.top

    policyWithoutAwsIam.getOrElse(policy) //todo error or do no harm??
  }

  /**
   * @param awsIam An AWS Iam Arn like "arn:aws:iam::714168927121:user/shrine-sqs-hub"
   * @param awsSqs An AWS Queue Arn like "arn:aws:sqs:us-east-1:714168927121:daveTest-firstQueue.fifo"
   * @param policy Json access policy downloaded from AWS
   * @return A policy that allows the awsIam to receive from aws sqs queue
   */
  def addReceiver(awsIam:String,queueName: MomQueueName,awsSqs:String,policy:Json):Json = {
    val receiveSid = "May-Receive-Shrine"
    //todo does this really need ChangeMessageVisibility ? I don't think SHRINE uses that
    val actions: Json = parseJson(
      """[
        | "SQS:ChangeMessageVisibility",
        | "SQS:DeleteMessage",
        | "SQS:ReceiveMessage",
        | "SQS:GetQueueUrl"
        |]""".stripMargin)

    addPrincipal(awsIam, queueName, awsSqs, policy)(receiveSid,actions)
  }

  /**
   * @param awsIam An AWS Iam Arn like "arn:aws:iam::714168927121:user/shrine-sqs-hub"
   * @param awsSqs An AWS Queue Arn like "arn:aws:sqs:us-east-1:714168927121:daveTest-firstQueue.fifo"
   * @param policy Json access policy downloaded from AWS
   * @return A policy that allows the awsIam to send from aws sqs queue
   */
  def addSender(awsIam:String,queueName: MomQueueName,awsSqs:String,policy:Json):Json = {
    val senderSid = "May-Send-Shrine"
    val actions: Json = parseJson(
      """[
        | "SQS:SendMessage",
        | "SQS:GetQueueUrl"
        |]""".stripMargin)

    addPrincipal(awsIam, queueName, awsSqs, policy)(senderSid,actions)
  }

  def parseJson(jsonString:String):Json = {
    import io.circe.parser.parse
    parse(jsonString).toTry.get
  }
}
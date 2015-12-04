package com.micronautics.aws

import com.amazonaws.auth.{DefaultAWSCredentialsProviderChain, AWSCredentials, BasicAWSCredentials}
import com.amazonaws.services.elastictranscoder.AmazonElasticTranscoderClient
import com.amazonaws.services.elastictranscoder.model._
import com.amazonaws.services.elastictranscoder.samples.model.JobStatusNotification
import com.amazonaws.services.sqs.AmazonSQSAsyncClient
import com.amazonaws.services.sqs.model.SendMessageRequest
import com.amazonaws.util.json.JSONObject
import java.util
import org.slf4j.LoggerFactory
import play.api.libs.json.Json
import scala.collection.JavaConverters._

object Settings {
  // Clients are built using the credentials provider chain.  This will look for credentials in the following order:
  //  1. Environment variables (AWS_ACCESS_KEY and AWS_SECRET_KEY).
  //  2. Java system properties (AwsCredentials.properties).
  //  3. Instance profile credentials on EC2 instances.
  lazy implicit val awsCredentials: AWSCredentials = new DefaultAWSCredentialsProviderChain().getCredentials
  lazy implicit val et: ElasticTranscoder = ElasticTranscoder(awsCredentials)
  lazy val etCachedPresets = et.defaultPresets
  lazy implicit val cf: CloudFront = CloudFront(awsCredentials)
  lazy implicit val iam: IAM = IAM(awsCredentials)
  lazy implicit val s3: S3 = S3(awsCredentials)
  lazy implicit val sns: SNS = SNS(awsCredentials)
  lazy implicit val sqsClient = new AmazonSQSAsyncClient()
  lazy implicit val etClient = new AmazonElasticTranscoderClient

  val Logger = LoggerFactory.getLogger("JobStatus")
  val sqsUrl = "https://sqs.us-east-1.amazonaws.com/031372724784/videoTranscodeStatus"
  val pipelineId = "1363029094772-ca6961"
  val inputKey = "1/html/play/assets/videos/lecture_playOverview.mp4"

  val jobId = "1448921251134-86nf5b"

  // All outputs will have this prefix prepended to their output inKey.
  val OUTPUT_KEY_PREFIX = "elastic-transcoder-samples/output/"

  // generate a 480p, 16:9 mp4 output.
  val PRESET_ID = "1351620000001-000020"

  val preset: Preset = etClient.readPreset(new ReadPresetRequest().withId(PRESET_ID)).getPreset
}

/** This sample shows how job status notifications can be used to receive job status updates using an event-driven model.
  * Using notifications allows transcoding jobs status to be tracked in a scalable fashion.
 * This implementation will not scale to multiple machines because the provided JobStatusNotificationHandler is looking for a specific job ID.
 * If there are multiple machines polling a given SQS queue for notifications, there is no guarantee that a particular machine will receive a particular notification.
 * More information about notifications can be found in the Elastic Transcoder documentation:
 * http://docs.aws.amazon.com/elastictranscoder/latest/developerguide/notifications.html */
object JobStatusNotificationsSample extends App {
  import Settings._

  if (args.length != 3) {
    println(
      s"""Test of AWS Elastic Transcoder notification
         |Usage: AWS_ACCESS_KEY=xxxx AWS_SECRET_KEY=xxxx ${getClass.getName} pipelineId sqsQueueUrl videoName
         |Example: ${getClass.getName} 1363029094772-ca6961 https://sqs.us-east-1.amazonaws.com/031372724784/videoTranscodeStatus 1/html/play/assets/videos/lecture_playOverview.mp4
         |""".stripMargin)
    System.exit(1)
  }

  // ID of the Elastic Transcoder pipeline that was created when setting up your AWS environment:
  // http://docs.aws.amazon.com/elastictranscoder/latest/developerguide/sample-code.html#java-pipeline
  val pipelineId = args(0)

  // URL of the SQS queue that was created when setting up your AWS environment.
  // http://docs.aws.amazon.com/elastictranscoder/latest/developerguide/sample-code.html#java-sqs
  val sqsQueueUrl = args(1)

  // input inKey that to transcode.
  val inputKey = args(2)

  val job = et.createJob(pipelineId, List(preset), inputKey, OUTPUT_KEY_PREFIX, List(inputKeyToOutputKey(preset, inputKey)))
  TranscoderJobHandler.awaitJob(job, sqsQueueUrl)

  def inputKeyToOutputKey(preset: Preset, inKey: String): String = {
    val i = inKey.lastIndexOf(".")
    inKey.substring(0, i) + "_" + preset.getName + "." + inKey.substring(i)
  }

  def maybeJobForId(id: String): Option[Job] = {
    val request = new ListJobsByPipelineRequest().withPipelineId(pipelineId)
    etClient.listJobsByPipeline(request).getJobs.asScala.find(_.getId == id)
  }
}

object doOver extends App {
  import Settings._

  val request = new ListJobsByPipelineRequest().withPipelineId(pipelineId)
  etClient
    .listJobsByPipeline(request)
    .getJobs.asScala.find(_.getId == jobId)
    .foreach ( job => TranscoderJobHandler.awaitJob(job, sqsUrl) )
}

object poke extends App {
  import Settings._
  import com.amazonaws.services.elastictranscoder.samples.model.JobStatusNotification._

  val jobInput = new JobInput()
  jobInput.setKey(inputKey)

  val jobOutput = new JobOutput()
  jobOutput.setErrorCode(0)
  jobOutput.setId("asdf")
  jobOutput.setKey("qwer.mp4")
  jobOutput.setPresetId(PRESET_ID)
  jobOutput.setStatus("Complete")
  jobOutput.setStatusDetail("")

  val jobOutputs: util.List[JobOutput] = List(jobOutput).asJava

  val notification = new JobStatusNotification
  notification.setJobId(jobId)
  notification.setState(JobState.COMPLETED)
  notification.setPipelineId(pipelineId)
  notification.setInput(jobInput)
  notification.setOutputKeyPrefix(OUTPUT_KEY_PREFIX)
  notification.setVersion("1")
  notification.setOutputs(jobOutputs)

  val message = new JSONObject(notification).toString // this always results in a null JobStatusNotification message
  val prettyMsg = Json.prettyPrint(Json.parse(message))
  /* prettyMsg looks like this:
  {
    "outputs" : [ {
      "errorCode" : 0,
      "statusDetail" : "",
      "id" : "asdf",
      "presetId" : "1351620000001-000020",
      "key" : "qwer.mp4",
      "status" : "Complete"
    } ],
    "jobId" : "1448921251134-86nf5b",
    "input" : {
      "key" : "1/html/play/assets/videos/lecture_playOverview.mp4"
    },
    "outputKeyPrefix" : "elastic-transcoder-samples/output/",
    "errorCode" : 0,
    "state" : {
      "terminalState" : true
    },
    "version" : "1",
    "pipelineId" : "1363029094772-ca6961"
  } */
  println(prettyMsg)

  val msg2 = s"""{
                |  "state" : "${JobState.COMPLETED}",
                |  "errorCode" : "0",
                |  "messageDetails" : "",
                |  "version" : "1",
                |  "jobId" : "$jobId",
                |  "pipelineId" : "$pipelineId",
                |  "input" : {
                |  },
                |  "outputKeyPrefix" : "$OUTPUT_KEY_PREFIX",
                |  "outputs": [
                |    {
                |      "status" : "Completed"
                |    }
                |  ]
                |}""".stripMargin
  sqsClient.sendMessage(new SendMessageRequest(sqsUrl, msg2)) // msg2 also results in a null JobStatusNotification message
}

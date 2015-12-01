package com.micronautics.aws

import com.amazonaws.services.elastictranscoder.AmazonElasticTranscoderClient
import com.amazonaws.services.elastictranscoder.model._
import com.amazonaws.services.elastictranscoder.samples.model.{JobStatusNotification, JobStatusNotificationHandler}
import com.amazonaws.services.elastictranscoder.samples.utils.{SqsQueueNotificationWorker, TranscoderSampleUtilities}
import com.amazonaws.services.sqs.AmazonSQSClient
import org.slf4j.LoggerFactory
import scala.collection.JavaConverters._

object Settings {
  // Clients are built using the credentials provider chain.  This will look for credentials in the following order:
  //  1. Environment variables (AWS_ACCESS_KEY and AWS_SECRET_KEY).
  //  2. Java system properties (AwsCredentials.properties).
  //  3. Instance profile credentials on EC2 instances.
  implicit val sqsClient = new AmazonSQSClient()
  implicit val etClient = new AmazonElasticTranscoderClient
  val Logger = LoggerFactory.getLogger("JobStatus")
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

  // generate a 480p, 16:9 mp4 output.
  val PRESET_ID = "1351620000001-000020"

  // All outputs will have this prefix prepended to their output key.
  val OUTPUT_KEY_PREFIX = "elastic-transcoder-samples/output/"

  // ID of the Elastic Transcoder pipeline that was created when setting up your AWS environment:
  // http://docs.aws.amazon.com/elastictranscoder/latest/developerguide/sample-code.html#java-pipeline
  val pipelineId = args(0)

  // URL of the SQS queue that was created when setting up your AWS environment.
  // http://docs.aws.amazon.com/elastictranscoder/latest/developerguide/sample-code.html#java-sqs
  val sqsQueueUrl = args(1)

  // input key that to transcode.
  val inputKey = args(2)

  val job = createElasticTranscoderJob(pipelineId, PRESET_ID, inputKey, OUTPUT_KEY_PREFIX)
  TranscoderJobHandler.awaitJob(job, sqsQueueUrl)

  /** Creates a job in Elastic Transcoder using the configured pipeline, input key, preset, and output key prefix.
    * @return Job that was created in Elastic Transcoder. */
  def createElasticTranscoderJob(pipelineId: String, presetId: String, key: String,
                                 outputKeyPrefix: String)
                                        (implicit etClient: AmazonElasticTranscoderClient): Job = {
    val input = new JobInput().withKey(key)
    // Setup the job output using the provided input key to generate an output key.
    val outputs = List(new CreateJobOutput()
      .withKey(TranscoderSampleUtilities.inputKeyToOutputKey(key))
      .withPresetId(presetId))
    // Create a job on the specified pipeline and return the job ID.
    val createJobRequest = new CreateJobRequest()
      .withPipelineId(pipelineId)
      .withOutputKeyPrefix(outputKeyPrefix)
      .withInput(input)
      .withOutputs(outputs.asJava)
    Logger.info("Submitting Elastic Transcoder job")
    etClient.createJob(createJobRequest).getJob
  }

  def maybeJobForId(id: String): Option[Job] = {
    val request = new ListJobsByPipelineRequest().withPipelineId(pipelineId)
    etClient.listJobsByPipeline(request).getJobs.asScala.find(_.getId == id)
  }
}

object doOver extends App {
  import Settings._

  val sqsUrl = "https://sqs.us-east-1.amazonaws.com/031372724784/videoTranscodeStatus"
  val request = new ListJobsByPipelineRequest().withPipelineId("1363029094772-ca6961")
  etClient
    .listJobsByPipeline(request)
    .getJobs.asScala.find(_.getId == "1448921251134-86nf5b")
    .foreach ( job => TranscoderJobHandler.awaitJob(job, sqsUrl) )
}

package com.micronautics.aws

import com.amazonaws.services.elastictranscoder.model.Job
import com.amazonaws.services.elastictranscoder.samples.model.{JobStatusNotification, JobStatusNotificationHandler}
import com.amazonaws.services.elastictranscoder.samples.utils.SqsQueueNotificationWorker
import com.amazonaws.services.sqs.AmazonSQSClient
import concurrent.duration._
import org.slf4j.LoggerFactory
import scala.language.postfixOps

object TranscoderJobHandler {
  val Logger = LoggerFactory.getLogger("JobStatus")

  /** Wait for the specified job to complete by adding a handler to the SQS notification worker that is polling for status updates.
   * Blocks until the specified job completes. */
  def awaitJob(job: Job, sqsQueueUrl: String)(implicit sqsClient: AmazonSQSClient) = {
    val jobId: String = job.getId
    val status = job.getStatus
    if (false && List("Complete", "Canceled", "Error").contains(status)) {
      Logger.info(s"Job $jobId finished with status '$status'")
    } else {
      Logger.info(s"Waiting for job $jobId to finish")
      val doneSignal = concurrent.Promise[String]()
      val handler = new JobStatusNotificationHandler() {
        def handle(jobStatusNotification: JobStatusNotification): Unit = {
          if (null==jobStatusNotification) {
            Logger.info("Incoming JobStatusNotification is null")
          } else {
            Logger.info(s"Incoming JobStatusNotification has jobId ${jobStatusNotification.getJobId}; looking for $jobId")
            if (jobStatusNotification.getJobId==jobId) {
              val state = jobStatusNotification.getState
              if (state.isTerminalState)
                doneSignal.complete(util.Success(jobStatusNotification.getJobId + s" finished with status $state"))
            }
          }
        }
      }
      val sqsQueueNotificationWorker = new SqsQueueNotificationWorker(sqsClient, sqsQueueUrl)
      sqsQueueNotificationWorker.addHandler(handler)
      val notificationThread = new Thread(sqsQueueNotificationWorker)
      notificationThread.start()
      concurrent.Await.ready(doneSignal.future, 1 hour)
      sqsQueueNotificationWorker.shutdown()
    }
  }
}

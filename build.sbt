organization := "com.micronautics"

name := "awsJavaSamples"

version := "0.1.0"

scalaVersion := "2.11.7"

scalacOptions ++= Seq("-deprecation", "-encoding", "UTF-8", "-feature", "-target:jvm-1.7", "-unchecked",
    "-Ywarn-adapted-args", "-Ywarn-value-discard", "-Xlint")

scalacOptions in (Compile, doc) <++= baseDirectory.map {
  (bd: File) => Seq[String](
     "-sourcepath", bd.getAbsolutePath,
     "-doc-source-url", "https://github.com/mslinn/awsSamples/tree/master�{FILE_PATH}.scala"
  )
}

javacOptions ++= Seq("-Xlint:deprecation", "-Xlint:unchecked", "-source", "1.7", "-target", "1.7", "-g:vars")

resolvers ++= Seq(
  "Typesafe Releases"   at "http://repo.typesafe.com/typesafe/releases"
)

libraryDependencies ++= Seq(
  "com.fasterxml.jackson.core" % "jackson-core"        % "2.6.3"   withSources(),
  "com.fasterxml.jackson.core" % "jackson-annotations" % "2.6.3"   withSources(),
  "com.fasterxml.jackson.core" % "jackson-databind"    % "2.6.3"   withSources(),
  "com.amazonaws"              %  "aws-java-sdk-osgi"  % "1.10.35" withSources()
)

logLevel := Level.Warn

// Only show warnings and errors on the screen for compilations.
// This applies to both test:compile and compile and is Info by default
logLevel in compile := Level.Warn

// Level.INFO is needed to see detailed output when running tests
logLevel in test := Level.Info

// define the statements initially evaluated when entering 'console', 'console-quick', but not 'console-project'
initialCommands in console := """
                                |import com.amazonaws.services.elastictranscoder.AmazonElasticTranscoderClient
                                |import com.amazonaws.services.elastictranscoder.model._
                                |import com.amazonaws.services.elastictranscoder.samples.model.{JobStatusNotification, JobStatusNotificationHandler}
                                |import com.amazonaws.services.elastictranscoder.samples.utils.{SqsQueueNotificationWorker, TranscoderSampleUtilities}
                                |import com.amazonaws.services.sqs.AmazonSQSClient
                                |import scala.collection.JavaConverters._
                                |import scala.language.postfixOps
                                |import com.micronautics.aws.JobStatusNotificationsSample._
                                |""".stripMargin

cancelable := true

sublimeTransitive := true

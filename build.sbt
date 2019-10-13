import Dependencies._

organization := "org.sharpsw.spark"

name := "kaggle-london-crime-data-spark"

val appVersion = "1.1.3.0"

val appName = "kaggle-london-crime-data-spark"

version := appVersion

scalaVersion := "2.11.8"

resolvers += Classpaths.typesafeReleases

libraryDependencies ++= Seq(
  "org.apache.spark" % "spark-core_2.11" % "2.2.1",
  "org.apache.spark" % "spark-sql_2.11" % "2.2.1",
  "com.amazonaws" % "aws-java-sdk-s3" % "1.11.354",
  "com.microsoft.azure" % "azure-storage" % "6.1.0",
  "com.google.cloud" % "google-cloud-storage" % "1.96.0",
  //"org.apache.hadoop" % "hadoop-aws" % "2.7.5",
  "org.scalacheck" % "scalacheck_2.11" % "1.14.2",
  scalaTest % Test
)

dependencyOverrides += "com.google.guava" % "guava" % "16.0"
dependencyOverrides += "com.google.auth" % "google-auth-library-oauth2-http" % "0.4.0"
package org.sharpsw.spark.utils

import java.io.{File, FileInputStream}
import java.nio.file.Paths
import java.util.Arrays

import com.amazonaws.{ClientConfiguration, Request}
import com.amazonaws.auth.{AWSStaticCredentialsProvider, SignerFactory}
import com.amazonaws.client.builder.AwsClientBuilder
import com.amazonaws.handlers.RequestHandler2
import com.amazonaws.services.s3.AmazonS3ClientBuilder
import com.amazonaws.services.s3.model.{GetObjectRequest, ObjectMetadata, PutObjectRequest}
import com.google.auth.oauth2.{GoogleCredentials, ServiceAccountCredentials}
import org.apache.log4j.Logger
import org.sharpsw.spark.CmdLineOptions
import org.sharpsw.spark.gcp.GCPSessionCredentials

/*
 * Reason for using S3 APIs for Google cloud storage is,
 * google cloud storage library brings conflicting guava with hadoop libraries.
 * Google cloud storage takes dependency on guava v28.0, while
 * hadoop libraries require guava version to be <= v16.0
 */
case class GCPS3Util(
  private val serviceEndpoint: String,
  private val serviceRegion: String,
  private val pathToServiceAccountKey: String,
  private val projectId: String)
  extends StorageUtil {

  @transient lazy val logger: Logger = Logger.getLogger(getClass.getName)

  private val svcAccCredentials = GoogleCredentials
    .fromStream(new FileInputStream(pathToServiceAccountKey))
    .createScoped(Arrays.asList("https://www.googleapis.com/auth/cloud-platform"))
    .asInstanceOf[ServiceAccountCredentials]

  private val credentials: GCPSessionCredentials = GCPSessionCredentials(svcAccCredentials)

  SignerFactory.registerSigner(
    "org.sharpsw.spark.gcp.CustomGCPSigner",
    classOf[org.sharpsw.spark.gcp.CustomGCPSigner])

  private val clientConfiguration: ClientConfiguration = {
    val clientConfig = new ClientConfiguration()
    clientConfig.setSignerOverride("org.sharpsw.spark.gcp.CustomGCPSigner")
    clientConfig
  }

  private val s3Client = AmazonS3ClientBuilder
    .standard()
    .withClientConfiguration(clientConfiguration)
    .withEndpointConfiguration(
      new AwsClientBuilder.EndpointConfiguration(serviceEndpoint, serviceRegion))
    .withRequestHandlers(new RequestHandler2() {
      override def beforeRequest(request: Request[_]): Unit =
        request.addHeader("x-goog-project-id", projectId)

    })
    .withCredentials(new AWSStaticCredentialsProvider(credentials))
    .build()

  override def getCloudStorageType(): String = CmdLineOptions.GCloudFileInputSource

  override def downloadObject(
    bucket: String,
    pathToInputFile: String,
    local: String = "."): String = {

    val outputFileName = if (pathToInputFile.contains("/")) {
      pathToInputFile.split("/").last
    } else {
      pathToInputFile
    }

    val path = Paths.get(local).toAbsolutePath
    val outputFileAbsolutePath = path + File.separator + outputFileName

    s3Client
      .getObject(new GetObjectRequest(bucket, pathToInputFile), new File(outputFileAbsolutePath))

    // return
    outputFileAbsolutePath
  }

  override def uploadFiles(
    bucket: String,
    prefix: String,
    localBasePath: String,
    files: List[String]): Unit = {
    val outputAbsolutePath = Paths.get(localBasePath).toAbsolutePath.toString
    files.foreach(
      item =>
        uploadSingleFile(
          bucket,
          prefix + item.substring(outputAbsolutePath.length).replaceAll("\\\\", "/"),
          item))
  }

  private def uploadSingleFile(bucket: String, key: String, uploadFileName: String): Unit = {
    try {
      logger.info(s"Uploading file $uploadFileName to S3")
      val file = new File(uploadFileName)
      val is = new FileInputStream(file)
      val metadata = new ObjectMetadata()
      metadata.setContentLength(file.length())
      s3Client.putObject(new PutObjectRequest(bucket, key, is, metadata))
      is.close()
    } catch {
      case exception: Exception => exception.printStackTrace()
    }
  }
}

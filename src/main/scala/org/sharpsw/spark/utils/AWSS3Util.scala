package org.sharpsw.spark.utils

import java.io.{File, FileInputStream}
import java.nio.file.Paths

import com.amazonaws.ClientConfiguration
import com.amazonaws.auth.{AWSCredentials, AWSStaticCredentialsProvider, BasicAWSCredentials}
import com.amazonaws.client.builder.AwsClientBuilder
import com.amazonaws.services.s3.AmazonS3ClientBuilder
import com.amazonaws.services.s3.model.{GetObjectRequest, ObjectMetadata, PutObjectRequest}
import org.apache.log4j.Logger
import org.sharpsw.spark.CmdLineOptions

case class AWSS3Util(
  private val serviceEndpoint: String,
  private val serviceRegion: String,
  private val accessKey: String,
  private val secretKey: String)
  extends StorageUtil {

  @transient lazy val logger: Logger = Logger.getLogger(getClass.getName)

  private val credentials: AWSCredentials =
    new BasicAWSCredentials(accessKey, secretKey)

  private val clientConfiguration: ClientConfiguration = {
    val clientConfig = new ClientConfiguration()
    clientConfig.setSignerOverride("AWSS3V4SignerType")
    clientConfig
  }

  private val s3Client = AmazonS3ClientBuilder
    .standard()
    .withEndpointConfiguration(
      new AwsClientBuilder.EndpointConfiguration(serviceEndpoint, serviceRegion))
    .withPathStyleAccessEnabled(true)
    .withClientConfiguration(clientConfiguration)
    .withCredentials(new AWSStaticCredentialsProvider(credentials))
    .build()

  override def getCloudStorageType(): String = CmdLineOptions.S3FileInputSource

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

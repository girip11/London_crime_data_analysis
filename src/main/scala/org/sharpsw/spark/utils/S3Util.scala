package org.sharpsw.spark.utils

import java.io.File.separator
import java.io.{File, FileInputStream, FileOutputStream}
import java.nio.file.Paths

import com.amazonaws.ClientConfiguration
import com.amazonaws.auth.{AWSCredentials, AWSStaticCredentialsProvider, BasicAWSCredentials}
import com.amazonaws.client.builder.AwsClientBuilder
import com.amazonaws.services.s3.AmazonS3ClientBuilder
import com.amazonaws.services.s3.model.{ObjectMetadata, PutObjectRequest}
import org.apache.log4j.Logger
import org.sharpsw.spark.CmdLineOptions

case class S3Util(
  private val serviceEndpoint: String,
  private val serviceRegion: String,
  private val accessKey: String,
  private val secretKey: String)
  extends StorageUtil {

  @transient lazy val logger: Logger = Logger.getLogger(getClass.getName)

  private val credentials: AWSCredentials =
    new BasicAWSCredentials(accessKey, secretKey);

  private val clientConfiguration: ClientConfiguration = new ClientConfiguration();
  clientConfiguration.setSignerOverride("AWSS3V4SignerType");

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
    val o = s3Client.getObject(bucket, pathToInputFile)
    val s3is = o.getObjectContent
    val outputFileName = if (pathToInputFile.contains("/")) {
      pathToInputFile.split("/").last
    } else {
      pathToInputFile
    }

    val path = Paths.get(local).toAbsolutePath()
    val outputFileAbsolutePath = path + separator + outputFileName
    val fos = new FileOutputStream(new File(outputFileAbsolutePath))
    val read_buf = new Array[Byte](1024)
    var len = s3is.read(read_buf)
    while (len > 0) {
      fos.write(read_buf, 0, len)
      len = s3is.read(read_buf)
    }
    s3is.close()
    fos.close()

    // return
    outputFileAbsolutePath
  }

  override def uploadFiles(
    bucket: String,
    prefix: String,
    localBasePath: String,
    files: List[String]): Unit =
    files.foreach(
      item =>
        uploadSingleFile(
          bucket,
          prefix + item.substring(localBasePath.length).replaceAll("\\\\", "/"),
          item))

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

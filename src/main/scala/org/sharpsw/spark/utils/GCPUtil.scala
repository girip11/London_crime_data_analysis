package org.sharpsw.spark.utils

import java.io.{File, FileInputStream, FileOutputStream, PrintStream}
import java.io.File.separator
// import java.nio.ByteBuffer
// import java.nio.channels.{Channels, WritableByteChannel}
import java.nio.charset.Charset
import java.nio.file.{Files, Paths}

import com.google.auth.oauth2.{GoogleCredentials, ServiceAccountCredentials}
// import com.google.cloud.ReadChannel
import com.google.cloud.storage.{Blob, BlobId, BlobInfo, Storage, StorageOptions}
import org.apache.log4j.Logger
import org.sharpsw.spark.CmdLineOptions

import scala.io.{BufferedSource, Source}

/* References:
 * Create service key: https://cloud.google.com/storage/docs/reference/libraries
 * MVN Repo: https://mvnrepository.com/artifact/com.google.cloud/google-cloud-storage/1.96.0
 * Java google cloud storage examples:
 * https://github.com/googleapis/google-cloud-java/tree/master/google-cloud-examples
 *
 * NOTE: GCP Storage Client having issues due to conflicting
 * dependency versions of Guava library between java google cloud storage library and hadoop-common library
 *
 * THIS FILE IS KEPT FOR REFERENCE PURPOSE ONLY.
 * FOR RUNNING LOCALLY USING SPARK USE GCPS3UTIL
 *
 */
case class GCPUtil(private val serviceAccountFilePath: String) extends StorageUtil {

  @transient private lazy val logger: Logger = Logger.getLogger(getClass.getName)

  val credentialsPath = new File(serviceAccountFilePath)
  var credentials: GoogleCredentials =
    GoogleCredentials.fromStream(new FileInputStream(credentialsPath))

  private val storage: Storage =
    StorageOptions.newBuilder().setCredentials(credentials).build().getService

  override def downloadObject(
    bucketName: String,
    pathToBlob: String,
    localPathToDownload: String = "."): String = {

    val outputFileName = if (pathToBlob.contains("/")) {
      pathToBlob.split("/").last
    } else {
      pathToBlob
    }

    val outputFileAbsolutePath = Paths
      .get(localPathToDownload)
      .toAbsolutePath + separator + outputFileName

    val blob: Blob = storage.get(BlobId.of(bucketName, pathToBlob))
    blob.downloadTo(new FileOutputStream(outputFileAbsolutePath))
//    val reader: ReadChannel = blob.reader()
//
//    val writeTo = new PrintStream(new FileOutputStream(outputFileAbsolutePath))
//    val channel: WritableByteChannel = Channels.newChannel(writeTo)
//    val bytes: ByteBuffer = ByteBuffer.allocate(64 * 1024)
//    while (reader.read(bytes) > 0) {
//      bytes.flip()
//      channel.write(bytes)
//      bytes.clear()
//    }
//
//    writeTo.close()
    outputFileAbsolutePath
  }

  override def uploadFiles(
    bucketName: String,
    pathPrefixWithinBucket: String,
    localBasePath: String,
    files: List[String]): Unit = {
    val localOutputAbsPath: String = Paths.get(localBasePath).toAbsolutePath.toString

    files.foreach(
      item =>
        uploadSingleFile(
          bucketName,
          pathPrefixWithinBucket + item.substring(localOutputAbsPath.length),
          item))
  }

  private def uploadSingleFile(
    bucketName: String,
    pathToBlob: String,
    uploadFileName: String): Unit = {
    try {
      logger.info(s"Uploading file $uploadFileName to Google cloud storage account")
      val contentType = Files.probeContentType(Paths.get(uploadFileName))
      val blobId = BlobId.of(bucketName, pathToBlob)
      val blobInfo = BlobInfo.newBuilder(blobId).setContentType(contentType).build()

      if (uploadFileName.endsWith(".csv")) {
        val file: BufferedSource = Source.fromFile(uploadFileName, "UTF-8")
        val content: String = file.getLines().mkString("\n")
        file.close()

        storage.create(blobInfo, content.getBytes(Charset.forName("UTF-8")))
      } else if (uploadFileName.endsWith(".parquet")) {
        // Since the parquet files are very small using this method of uploading
        storage.create(blobInfo, Files.readAllBytes(Paths.get(uploadFileName)))
      }
    } catch {
      case exception: Exception => exception.printStackTrace()
    }
  }

  override def getCloudStorageType(): String = CmdLineOptions.GCloudFileInputSource

}

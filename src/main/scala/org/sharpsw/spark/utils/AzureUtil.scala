package org.sharpsw.spark.utils

import java.io.File.separator
import java.io.{File, FileInputStream, FileOutputStream}
import java.nio.file.Paths

import com.microsoft.azure.storage.CloudStorageAccount
import com.microsoft.azure.storage.blob.{CloudBlobContainer, CloudBlockBlob}
import org.apache.log4j.Logger
import org.sharpsw.spark.CmdLineOptions

import scala.io.{BufferedSource, Source}

case class AzureUtil(private val connectionString: String) extends StorageUtil {
  @transient lazy val logger: Logger = Logger.getLogger(getClass.getName)

  private val blobClient = {
    val storageAccount = CloudStorageAccount.parse(connectionString)
    storageAccount.createCloudBlobClient()
  }

  override def getCloudStorageType(): String = CmdLineOptions.AzureFileInputSource

  override def downloadObject(
    containerName: String,
    pathToBlob: String,
    local: String = "."): String = {

    val outputFileName = if (pathToBlob.contains("/")) {
      pathToBlob.split("/").last
    } else {
      pathToBlob
    }

    val path = Paths.get(local).toAbsolutePath
    val outputFileAbsolutePath = path + separator + outputFileName
    val outputFile = new File(outputFileAbsolutePath)

    val container = this.blobClient.getContainerReference(containerName)
    val blob: CloudBlockBlob = container.getBlockBlobReference(pathToBlob)
    blob.download(new FileOutputStream(outputFile))

    outputFileAbsolutePath
  }

  override def uploadFiles(
    containerName: String,
    prefix: String,
    localBasePath: String,
    files: List[String]): Unit = {
    val container = this.blobClient.getContainerReference(containerName)
    val localOutputAbsPath = new File(localBasePath).getAbsolutePath
    files.foreach(item =>
      uploadSingleFile(container, prefix + item.substring(localOutputAbsPath.length), item))
  }

  private def uploadSingleFile(
    container: CloudBlobContainer,
    pathToBlob: String,
    uploadFileName: String): Unit = {
    try {
      logger.info(s"Uploading file $uploadFileName to Azure storage account")
      val blob: CloudBlockBlob = container.getBlockBlobReference(pathToBlob)

      if (uploadFileName.endsWith(".csv")) {
        val file: BufferedSource = Source.fromFile(uploadFileName, "UTF-8")
        val content: String = file.getLines().mkString("\n")
        file.close()

        // Use the other signature to accommodate access condition, retry policies
        blob.uploadText(content)
      } else if (uploadFileName.endsWith(".parquet")) {
        blob.upload(new FileInputStream(uploadFileName), -1)
      }
    } catch {
      case exception: Exception => exception.printStackTrace()
    }
  }
}

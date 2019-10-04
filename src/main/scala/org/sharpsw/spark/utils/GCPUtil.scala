package org.sharpsw.spark.utils

import org.sharpsw.spark.CmdLineOptions

case class GCPUtil() extends StorageUtil {

  override def getCloudStorageType(): String = CmdLineOptions.GCloudFileInputSource

  override def downloadObject(
    bucketName: String,
    pathToBlob: String,
    localPathToDownload: String): String =
    throw new NotImplementedError()

  override def uploadFiles(
    bucketName: String,
    pathPrefixWithinBucket: String,
    localBasePath: String,
    files: List[String]): Unit = throw new NotImplementedError()
}

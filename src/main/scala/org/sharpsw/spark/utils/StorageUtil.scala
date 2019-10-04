package org.sharpsw.spark.utils

trait StorageUtil {
  def getCloudStorageType(): String

  def downloadObject(
    bucketName: String,
    pathToBlob: String,
    localPathToDownload: String = "."): String

  def uploadFiles(
    bucketName: String,
    pathPrefixWithinBucket: String,
    localBasePath: String,
    files: List[String]): Unit
}

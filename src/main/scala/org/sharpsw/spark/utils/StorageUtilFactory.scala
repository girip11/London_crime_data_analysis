package org.sharpsw.spark.utils

import com.amazonaws.services.s3.model.Region

/*
 * TODO: Read configuration from a config file
 */
object StorageUtilFactory {

  def getStorageUtil(cloudStorageProvider: String): StorageUtil = {
    cloudStorageProvider.toLowerCase().trim() match {
      case "--aws-s3" =>
        new S3Util(
          "http://127.0.0.1:9000",
          Region.AP_Mumbai.toString(),
          "7EFHH3SM4KDNCHM4I3H0",
          "zNI+QYy9MMMjMEQFoZ0c+qPI0+7VFT+9514LRA9d")
      case "--azure" =>
        new AzureUtil("UseDevelopmentStorage=true")
      case "--gcloud" =>
        new GCPUtil()
      case _ => throw new IllegalArgumentException(s"$cloudStorageProvider is not supported")
    }
  }
}

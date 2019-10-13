package org.sharpsw.spark.gcp

import com.amazonaws.SignableRequest
import com.amazonaws.auth.{AWS4Signer, AWSCredentials}

import scala.collection.mutable.{Map => MutableMap}
import scala.collection.JavaConverters._

object CustomGCPSigner {

  private val awsToGcpHeaderMap: Map[String, String] = {
    val headerMap = MutableMap[String, String]()
    headerMap.+=("x-amz-storage-class" -> "x-goog-storage-class")
    headerMap.+=("x-amz-date" -> "x-goog-date")
    headerMap.+=("x-amz-copy-source" -> "x-goog-copy-source")
    headerMap.+=("x-amz-metadata-directive" -> "x-goog-metadata-directive")
    headerMap.+=("x-amz-copy-source-if-match" -> "x-goog-copy-source-if-none-match")
    headerMap.+=(
      "x-amz-copy-source-if-unmodified-since" -> "x-goog-copy-source-if-unmodified-since")
    headerMap.+=("x-amz-copy-source-if-modified-since" -> "x-goog-copy-source-if-modified-since")

    headerMap.toMap
  }

  def getAWSGCPHeaderMap: scala.collection.Map[String, String] = awsToGcpHeaderMap
}
class CustomGCPSigner extends AWS4Signer {

  override def sign(request: SignableRequest[_], credentials: AWSCredentials): Unit = {
    request.addHeader("Authorization", "Bearer " + credentials.getAWSAccessKeyId)

    request.getHeaders.asScala.toMap.foreach(entry => {
      val currentHeader: String = entry._1.toLowerCase
      if (CustomGCPSigner.awsToGcpHeaderMap.contains(currentHeader)) {
        request.addHeader(CustomGCPSigner.awsToGcpHeaderMap.getOrElse(entry._1, ""), entry._2)
      }

      if (currentHeader.startsWith("x-amz-meta-")) {
        request.addHeader(currentHeader.replace("x-amz-meta-", "x-goog-meta-"), entry._2)
      }
    })

  }

}

package org.sharpsw.spark.gcp

import com.amazonaws.auth.AWSSessionCredentials
import com.google.auth.oauth2.GoogleCredentials
import scala.util.control.Exception

case class GCPSessionCredentials(private val credentials: GoogleCredentials)
  extends AWSSessionCredentials {

  private def getGCPToken: String = {
    val result: Either[Throwable, Unit] =
      Exception.allCatch.either(this.credentials.refresh())

    if (result.isLeft) {
      ""
    } else {
      this.credentials.getAccessToken.getTokenValue
    }
  }

  override def getAWSAccessKeyId: String = getGCPToken

  override def getAWSSecretKey: String = ""

  override def getSessionToken: String = ""
}

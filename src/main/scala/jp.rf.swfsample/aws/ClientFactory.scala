package jp.rf.swfsample.aws

import com.amazonaws.AmazonWebServiceClient
import com.amazonaws.auth.{AWSCredentialsProvider, BasicAWSCredentials}
import com.amazonaws.regions.{Region, Regions}

object ClientFactory {
  def create[T <: AmazonWebServiceClient](accessKey: String, secretKey: String, regionName: String, clientClass: Class[T]): T = {
    val region = Region.getRegion(Regions.fromName(regionName))
    region.createClient(clientClass, createCredentialsProvider(accessKey, secretKey), null)
  }
  private def createCredentialsProvider(accessKey: String, secretKey: String) = new AWSCredentialsProvider() {
    def getCredentials() = new BasicAWSCredentials(accessKey, secretKey)
    def refresh() {}
  }
}

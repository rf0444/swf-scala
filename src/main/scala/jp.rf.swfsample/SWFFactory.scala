package jp.rf.swfsample

import com.amazonaws.auth.AWSCredentialsProvider
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.regions.Region
import com.amazonaws.regions.Regions
import com.amazonaws.services.simpleworkflow.AmazonSimpleWorkflowClient

object SWFFactory {
  def create(accessKey: String, secretKey: String, regionName: String) = {
    val region = Region.getRegion(Regions.fromName(regionName))
    region.createClient(
      classOf[AmazonSimpleWorkflowClient],
      createCredentialsProvider(accessKey, secretKey),
      null
    )
  }
  private def createCredentialsProvider(accessKey: String, secretKey: String) = new AWSCredentialsProvider() {
    def getCredentials() = new BasicAWSCredentials(accessKey, secretKey)
    def refresh() {}
  }
}

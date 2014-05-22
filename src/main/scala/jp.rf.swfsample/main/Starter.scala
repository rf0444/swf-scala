package jp.rf.swfsample.main

import com.amazonaws.services.simpleworkflow.AmazonSimpleWorkflowClient
import com.amazonaws.services.simpleworkflow.model._
import com.typesafe.config.ConfigFactory

import jp.rf.swfsample.aws.ClientFactory

object Starter {
  def main(args: Array[String]) {
    if (args.length < 1) {
      println("usage: {program} input")
      return
    }
    val conf = ConfigFactory.load
    val swf = ClientFactory.create(
      clientClass = classOf[AmazonSimpleWorkflowClient],
      accessKey = conf.getString("aws.accessKey"),
      secretKey = conf.getString("aws.secretKey"),
      regionName = conf.getString("aws.swf.regionName")
    )
    val workflowType = new WorkflowType()
      .withName(conf.getString("aws.swf.workflowType.name"))
      .withVersion(conf.getString("aws.swf.workflowType.version"))
    val timestamp = System.currentTimeMillis.toString
    swf.startWorkflowExecution(new StartWorkflowExecutionRequest()
      .withDomain(conf.getString("aws.swf.domainName"))
      .withWorkflowType(workflowType)
      .withWorkflowId(timestamp)
      .withInput(args(0))
    )
  }
}


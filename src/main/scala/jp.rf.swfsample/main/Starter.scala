package jp.rf.swfsample.main

import com.amazonaws.services.simpleworkflow._
import com.amazonaws.services.simpleworkflow.model._

import jp.rf.swfsample.{Config, SWFFactory}

object Starter {
  def main(args: Array[String]) {
    if (args.length < 1) {
      println("usage: {program} input")
      return
    }
    val swf = SWFFactory.create(Config.accessKey, Config.secretKey, Config.regionName)
    val timestamp = System.currentTimeMillis.toString
    swf.startWorkflowExecution(new StartWorkflowExecutionRequest()
      .withDomain(Config.domainName)
      .withWorkflowType(new WorkflowType()
        .withName(Config.workflowType.name)
        .withVersion(Config.workflowType.version)
      )
      .withWorkflowId(timestamp)
      .withInput(args(0))
    )
  }
}


package jp.rf.swfsample.main.setup

import com.amazonaws.services.simpleworkflow.AmazonSimpleWorkflowClient
import com.amazonaws.services.simpleworkflow.model._
import com.typesafe.config.{Config, ConfigFactory}

import jp.rf.swfsample.aws.ClientFactory

class Registerer(val conf: Config) {
  val swf = ClientFactory.create(
    clientClass = classOf[AmazonSimpleWorkflowClient],
    accessKey = conf.getString("aws.accessKey"),
    secretKey = conf.getString("aws.secretKey"),
    regionName = conf.getString("aws.swf.region")
  )
  val domain = conf.getString("aws.swf.domain")
  val version = conf.getString("aws.swf.version")
  val workflowTaskList = conf.getString("aws.swf.taskList.decision")
  val shortActivityTaskList = conf.getString("aws.swf.taskList.activity.short")
  val longActivityTaskList = conf.getString("aws.swf.taskList.activity.long")
  
  def registerDomain() {
    swf.registerDomain(new RegisterDomainRequest()
      .withName(domain)
      .withWorkflowExecutionRetentionPeriodInDays("7")
    )
  }
  def registerWorkflow(name: String) {
    swf.registerWorkflowType(new RegisterWorkflowTypeRequest()
      .withDomain(domain)
      .withName(name)
      .withVersion(version)
      .withDefaultTaskList(new TaskList().withName(workflowTaskList))
      .withDefaultChildPolicy(ChildPolicy.TERMINATE)
      .withDefaultExecutionStartToCloseTimeout("600")
      .withDefaultTaskStartToCloseTimeout("300")
    )
  }
  def registerShortActivity(name: String) {
    swf.registerActivityType(new RegisterActivityTypeRequest()
      .withDomain(domain)
      .withName(name)
      .withVersion(version)
      .withDefaultTaskList(new TaskList().withName(shortActivityTaskList))
      .withDefaultTaskScheduleToStartTimeout("600")
      .withDefaultTaskStartToCloseTimeout("300")
      .withDefaultTaskScheduleToCloseTimeout("NONE")
      .withDefaultTaskHeartbeatTimeout("NONE")
    )
  }
  def registerLongActivity(name: String) {
    swf.registerActivityType(new RegisterActivityTypeRequest()
      .withDomain(domain)
      .withName(name)
      .withVersion(version)
      .withDefaultTaskList(new TaskList().withName(shortActivityTaskList))
      .withDefaultTaskScheduleToStartTimeout("10800")
      .withDefaultTaskStartToCloseTimeout("43200")
      .withDefaultTaskScheduleToCloseTimeout("NONE")
      .withDefaultTaskHeartbeatTimeout("NONE")
    )
  }
}

object Main {
  def main(args: Array[String]) {
    val conf = ConfigFactory.load
    val reg = new Registerer(conf)
    
    reg.registerDomain()
    
    reg.registerWorkflow("start-ec2-instance")
    reg.registerShortActivity("start-ec2-instance-request")
    reg.registerLongActivity("start-ec2-instance-check")
    
    reg.registerWorkflow("stop-ec2-instance")
    reg.registerShortActivity("stop-ec2-instance-request")
    reg.registerLongActivity("stop-ec2-instance-check")
  }
}

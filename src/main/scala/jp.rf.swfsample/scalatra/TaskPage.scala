package jp.rf.swfsample.scalatra

import com.amazonaws.services.simpleworkflow.AmazonSimpleWorkflowClient
import com.amazonaws.services.simpleworkflow.model._
import org.json4s.{DefaultFormats, Formats}
import org.json4s.jackson.Serialization
import org.scalatra.ScalatraServlet
import org.scalatra.json.JacksonJsonSupport

class TaskPage(
  swf: AmazonSimpleWorkflowClient,
  domainName: String,
  version: String
) extends ScalatraServlet with JacksonJsonSupport {
  protected implicit val jsonFormats: Formats = DefaultFormats
  before() {
    contentType = formats("json")
  }
  post("/ec2/start") {
    val workflowType = new WorkflowType()
      .withName("start-ec2-instance")
      .withVersion(version)
    val input = parsedBody.extract[StartInstance]
    Starter.start(swf, domainName, workflowType, input)
  }
  post("/ec2/stop") {
    val workflowType = new WorkflowType()
      .withName("stop-ec2-instance")
      .withVersion(version)
    val input = parsedBody.extract[StopInstance]
    Starter.start(swf, domainName, workflowType, input)
  }
}

case class StartInstance(
  instanceId: String
)
case class StopInstance(
  instanceId: String
)

case class StartedTask(
  domain: String,
  workflowTypeName: String,
  workflowTypeVersion: String,
  workflowId: String,
  runId: String,
  input: String
)

object Starter {
  def start[T <: AnyRef](
    swf: AmazonSimpleWorkflowClient,
    domainName: String,
    workflowType: WorkflowType,
    input: T
  )(implicit format: Formats) = {
    val workflowId = java.util.UUID.randomUUID.toString
    val inputStr = Serialization.write(input)
    val run = swf.startWorkflowExecution(new StartWorkflowExecutionRequest()
      .withDomain(domainName)
      .withWorkflowType(workflowType)
      .withWorkflowId(workflowId)
      .withInput(inputStr)
    )
    StartedTask(
      domainName,
      workflowType.getName,
      workflowType.getVersion,
      workflowId,
      run.getRunId,
      inputStr
    )
  }
}

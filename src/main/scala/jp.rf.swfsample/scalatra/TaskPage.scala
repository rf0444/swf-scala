package jp.rf.swfsample.scalatra

import com.amazonaws.services.simpleworkflow.AmazonSimpleWorkflowClient
import com.amazonaws.services.simpleworkflow.model._
import org.json4s.{DefaultFormats, Formats}
import org.scalatra.ScalatraServlet
import org.scalatra.json.JacksonJsonSupport

class TaskPage(
  swf: AmazonSimpleWorkflowClient,
  domainName: String,
  workflowType: WorkflowType
) extends ScalatraServlet with JacksonJsonSupport {
  protected implicit val jsonFormats: Formats = DefaultFormats
  before() {
    contentType = formats("json")
  }
  post("/") {
    val workflowId = System.currentTimeMillis.toString
    Starter.start(swf, domainName, workflowType, workflowId, request.body)
  }
}

case class StartedTask(
  domain: String,
  workflowTypeName: String,
  workflowTypeVersion: String,
  workflowId: String,
  runId: String,
  input: String
)

object Starter {
  def start(
    swf: AmazonSimpleWorkflowClient,
    domainName: String,
    workflowType: WorkflowType,
    workflowId: String,
    input: String
  ) = {
    val run = swf.startWorkflowExecution(new StartWorkflowExecutionRequest()
      .withDomain(domainName)
      .withWorkflowType(workflowType)
      .withWorkflowId(workflowId)
      .withInput(input)
    )
    StartedTask(
      domainName,
      workflowType.getName,
      workflowType.getVersion,
      workflowId,
      run.getRunId,
      input
    )
  }
}

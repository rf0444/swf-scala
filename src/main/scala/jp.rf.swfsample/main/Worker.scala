package jp.rf.swfsample.main

import akka.actor.ActorDSL._
import akka.actor.ActorRef
import akka.actor.ActorSystem

import com.amazonaws.services.simpleworkflow._
import com.amazonaws.services.simpleworkflow.model._

import jp.rf.swfsample.Config
import jp.rf.swfsample.SWFFactory

object Worker {
  def main(args: Array[String]) {
    implicit val system = ActorSystem()
    val actor = createActor
    actor ! 'execute
    println("worker started. press enter for stop.")
    val str = scala.io.StdIn.readLine()
    println("stop another activity task pollings. please wait a minutes for current polling.")
    system.shutdown
  }
  def createActor(implicit system: ActorSystem): ActorRef = {
    val swf = SWFFactory.create(Config.accessKey, Config.secretKey, Config.regionName)
    val workflow = swf.describeWorkflowType(new DescribeWorkflowTypeRequest()
      .withDomain(Config.domainName)
      .withWorkflowType(new WorkflowType()
        .withName(Config.workflowType.name)
        .withVersion(Config.workflowType.version)
      )
    )
    val taskListName = workflow.getConfiguration.getDefaultTaskList.getName
    actor(new Act {
      become {
        case 'execute => {
          val result = execute(swf, taskListName)
          println(result)
          self ! 'execute
        }
      }
    })
  }
  def execute(swf: AmazonSimpleWorkflow, taskListName: String): (ActivityTask, Option[String]) = {
    val task = swf.pollForActivityTask(new PollForActivityTaskRequest()
      .withDomain(Config.domainName)
      .withTaskList(new TaskList()
        .withName(taskListName)
      )
    )
    if (task.getTaskToken == null) {
      return (task, None)
    }
    val result = action(task.getInput)
    swf.respondActivityTaskCompleted(new RespondActivityTaskCompletedRequest()
      .withTaskToken(task.getTaskToken)
      .withResult(result)
    )
    (task, Some(result))
  }
  def action(input: String): String = {
    println(input)
    val result = "printed: " + input
    result
  }
}

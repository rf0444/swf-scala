package jp.rf.swfsample.main

import akka.actor.ActorDSL._
import akka.actor.ActorRef
import akka.actor.ActorSystem

import com.amazonaws.services.simpleworkflow._
import com.amazonaws.services.simpleworkflow.model._

import jp.rf.swfsample.Config
import jp.rf.swfsample.SWFFactory

object Decider {
  def main(args: Array[String]) {
    implicit val system = ActorSystem()
    val actor = createActor
    actor ! 'execute
    println("decider started. press enter for stop.")
    val str = scala.io.StdIn.readLine()
    println("stop another decision task pollings. please wait a minutes for current polling.")
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
  def execute(swf: AmazonSimpleWorkflow, taskListName: String): (DecisionTask, Option[Decision]) = {
    val task = swf.pollForDecisionTask(new PollForDecisionTaskRequest()
      .withDomain(Config.domainName)
      .withTaskList(new TaskList()
        .withName(taskListName)
      )
    )
    if (task.getTaskToken == null) {
      return (task, None)
    }
    val decision = decide(task)
    swf.respondDecisionTaskCompleted(new RespondDecisionTaskCompletedRequest()
      .withTaskToken(task.getTaskToken)
      .withDecisions(decision)
    )
    (task, Some(decision))
  }
  def decide(decisionTask: DecisionTask): Decision = {
    import scala.collection.JavaConverters._
    
    val events = decisionTask.getEvents.asScala
    if (events.exists(_.getEventType == "ActivityTaskCompleted")) {
      return new Decision()
        .withDecisionType(DecisionType.CompleteWorkflowExecution)
    }
    val input = events
      .filter(_.getEventType == "WorkflowExecutionStarted").take(1)
      .map(_.getWorkflowExecutionStartedEventAttributes.getInput)
      .mkString("\n")
    val timestamp = System.currentTimeMillis.toString
    new Decision()
      .withDecisionType(DecisionType.ScheduleActivityTask)
      .withScheduleActivityTaskDecisionAttributes(new ScheduleActivityTaskDecisionAttributes()
        .withActivityId(timestamp)
        .withActivityType(new ActivityType()
          .withName(Config.activityType.name)
          .withVersion(Config.activityType.version)
        )
        .withInput(input)
      )
  }
}

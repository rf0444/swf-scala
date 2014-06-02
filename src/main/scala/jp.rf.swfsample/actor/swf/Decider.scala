package jp.rf.swfsample.actor.swf

import akka.actor.{ActorRef, ActorRefFactory}
import com.amazonaws.services.simpleworkflow._
import com.amazonaws.services.simpleworkflow.model._

object DeciderFactory {
  def create(
    swf: AmazonSimpleWorkflowClient,
    domainName: String,
    workflowType: WorkflowType,
    activityType: ActivityType
  ): DeciderFactory = {
    val workflow = swf.describeWorkflowType(new DescribeWorkflowTypeRequest()
      .withDomain(domainName)
      .withWorkflowType(workflowType)
    )
    val workflowTaskListName = workflow.getConfiguration.getDefaultTaskList.getName
    new DeciderFactory(swf, domainName, workflowTaskListName, activityType)
  } 
}
class DeciderFactory(
  val swf: AmazonSimpleWorkflowClient,
  val domainName: String,
  val taskListName: String,
  val activityType: ActivityType
) {
  def create(implicit factory: ActorRefFactory): ActorRef = {
    Decider.create(swf, domainName, taskListName, activityType)
  }
}

object Decider {
  def create(
    swf: AmazonSimpleWorkflowClient,
    domainName: String,
    taskListName: String,
    activityType: ActivityType
  )(implicit factory: ActorRefFactory): ActorRef = {
    SwfActor.create(new SwfActorConf[DecisionTask] {
      override def poll = {
        val task = swf.pollForDecisionTask(new PollForDecisionTaskRequest()
          .withDomain(domainName)
          .withTaskList(new TaskList()
            .withName(taskListName)
          )
        )
        if (task.getTaskToken == null) None else Some(task)
      }
      override def execute(task: DecisionTask) = {
        val decision = decide(task, activityType)
        swf.respondDecisionTaskCompleted(new RespondDecisionTaskCompletedRequest()
          .withTaskToken(task.getTaskToken)
          .withDecisions(decision)
        )
      }
    })
  }
  
  def decide(decisionTask: DecisionTask, activityType: ActivityType): Decision = {
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
        .withActivityType(activityType)
        .withInput(input)
      )
  }
}

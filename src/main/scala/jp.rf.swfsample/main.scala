package jp.rf.swfsample.main

import com.amazonaws.services.simpleworkflow.model._

import jp.rf.swfsample.Config
import jp.rf.swfsample.SWFFactory

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

object Decider {
  def main(args: Array[String]) {
    val swf = SWFFactory.create(Config.accessKey, Config.secretKey, Config.regionName)
    val workflow = swf.describeWorkflowType(new DescribeWorkflowTypeRequest()
      .withDomain(Config.domainName)
      .withWorkflowType(new WorkflowType()
        .withName(Config.workflowType.name)
        .withVersion(Config.workflowType.version)
      )
    )
    val taskListName = workflow.getConfiguration.getDefaultTaskList.getName
    while (true) {
      val decisionTask = swf.pollForDecisionTask(new PollForDecisionTaskRequest()
        .withDomain(Config.domainName)
        .withTaskList(new TaskList()
          .withName(taskListName)
        )
      )
      if (decisionTask.getTaskToken == null) {
        println("no tasks")
        return
      }
      val decision = decide(decisionTask)
      swf.respondDecisionTaskCompleted(new RespondDecisionTaskCompletedRequest()
        .withTaskToken(decisionTask.getTaskToken)
        .withDecisions(decision)
      )
    }
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

object Worker {
  def main(args: Array[String]) {
    val swf = SWFFactory.create(Config.accessKey, Config.secretKey, Config.regionName)
    val workflow = swf.describeWorkflowType(new DescribeWorkflowTypeRequest()
      .withDomain(Config.domainName)
      .withWorkflowType(new WorkflowType()
        .withName(Config.workflowType.name)
        .withVersion(Config.workflowType.version)
      )
    )
    val taskListName = workflow.getConfiguration.getDefaultTaskList.getName
    while (true) {
      val activityTask = swf.pollForActivityTask(new PollForActivityTaskRequest()
        .withDomain(Config.domainName)
        .withTaskList(new TaskList()
          .withName(taskListName)
        )
      )
      if (activityTask.getTaskToken == null) {
        println("no tasks")
        return
      }
      
      println(activityTask.getInput)
      
      swf.respondActivityTaskCompleted(new RespondActivityTaskCompletedRequest()
        .withTaskToken(activityTask.getTaskToken)
        .withResult("printed: " + activityTask.getInput)
      )
    }
  }
}

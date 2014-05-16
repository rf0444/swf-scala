package jp.rf.swfsample.actor.swf

import akka.actor.{ActorRef, ActorRefFactory}
import com.amazonaws.services.simpleworkflow._
import com.amazonaws.services.simpleworkflow.model._

object WorkerActorFactory {
  def create(
    swf: AmazonSimpleWorkflowClient,
    domainName: String,
    activityType: ActivityType
  ): WorkerActorFactory = {
    val activity = swf.describeActivityType(new DescribeActivityTypeRequest()
      .withDomain(domainName)
      .withActivityType(activityType)
    )
    val activityTaskListName = activity.getConfiguration.getDefaultTaskList.getName
    new WorkerActorFactory(swf, domainName, activityTaskListName)
  } 
}
class WorkerActorFactory(
  val swf: AmazonSimpleWorkflowClient,
  val domainName: String,
  val taskListName: String
) {
  def create(implicit factory: ActorRefFactory): ActorRef = {
    WorkerActor.create(swf, domainName, taskListName)
  }
}

object WorkerActor {
  def create(
    swf: AmazonSimpleWorkflowClient,
    domainName: String,
    taskListName: String
  )(implicit factory: ActorRefFactory): ActorRef = {
    ActivityActor.create(new ActivityActorConf[ActivityTask] {
      override def poll = {
        val task = swf.pollForActivityTask(new PollForActivityTaskRequest()
          .withDomain(domainName)
          .withTaskList(new TaskList()
            .withName(taskListName)
          )
        )
        if (task.getTaskToken == null) None else Some(task)
      }
      override def execute(task: ActivityTask) = {
        val result = action(task.getInput)
        swf.respondActivityTaskCompleted(new RespondActivityTaskCompletedRequest()
          .withTaskToken(task.getTaskToken)
          .withResult(result)
        )
      }
    })
  }
  
  def action(input: String): String = {
    println(input)
    val result = "printed: " + input
    result
  }
}

package jp.rf.swfsample.actor.swf

import akka.actor.{ActorRef, ActorRefFactory}
import com.amazonaws.services.simpleworkflow._
import com.amazonaws.services.simpleworkflow.model._

object WorkerActor {
  def create(
    swf: AmazonSimpleWorkflowClient,
    domainName: String,
    activityType: ActivityType
  )(implicit factory: ActorRefFactory): ActorRef = {
    val activity = swf.describeActivityType(new DescribeActivityTypeRequest()
      .withDomain(domainName)
      .withActivityType(activityType)
    )
    val activityTaskListName = activity.getConfiguration.getDefaultTaskList.getName
    
    ActivityActor.create(new ActivityActorConf[ActivityTask] {
      override def poll = {
        val task = swf.pollForActivityTask(new PollForActivityTaskRequest()
          .withDomain(domainName)
          .withTaskList(new TaskList()
            .withName(activityTaskListName)
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

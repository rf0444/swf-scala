package jp.rf.swfsample.actor.swf

import akka.actor.{ActorRef, ActorRefFactory}
import com.amazonaws.services.simpleworkflow._
import com.amazonaws.services.simpleworkflow.model._

sealed trait ActivityResult
case class ActivityCanceled(details: String) extends ActivityResult
case class ActivityCompleted(result: String) extends ActivityResult
case class ActivityFailed(details: String, reason: String) extends ActivityResult

object Worker {
  def create(
    swf: AmazonSimpleWorkflowClient,
    domainName: String,
    taskListName: String,
    action: String => ActivityResult
  )(implicit factory: ActorRefFactory): ActorRef = {
    SwfActor.create(new SwfActorConf[ActivityTask] {
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
        action(task.getInput) match {
          case ActivityCanceled(details) => {
            swf.respondActivityTaskCanceled(new RespondActivityTaskCanceledRequest()
              .withTaskToken(task.getTaskToken)
              .withDetails(details)
            )
          }
          case ActivityCompleted(result) => {
            swf.respondActivityTaskCompleted(new RespondActivityTaskCompletedRequest()
              .withTaskToken(task.getTaskToken)
              .withResult(result)
            )
          }
          case ActivityFailed(details, reason) => {
            swf.respondActivityTaskFailed(new RespondActivityTaskFailedRequest()
              .withTaskToken(task.getTaskToken)
              .withDetails(details)
              .withReason(reason)
            )
          }
        }
      }
    })
  }
}

package jp.rf.swfsample2.main.sample1.worker

import akka.actor.{Actor, ActorRef, ActorRefFactory, Props}
import com.amazonaws.services.simpleworkflow.AmazonSimpleWorkflow
import com.amazonaws.services.simpleworkflow.model._

import jp.rf.swfsample.util.uuid

sealed trait ActivityResult
case class ActivityCanceled(details: String) extends ActivityResult
case class ActivityCompleted(result: String) extends ActivityResult
case class ActivityFailed(details: String, reason: String) extends ActivityResult

object Activities {
}
class Activities {
}

object Poller {
  def createActor(
    swf: AmazonSimpleWorkflow,
    domainName: String,
    taskListName: String
  )(implicit factory: ActorRefFactory): ActorRef = {
    factory.actorOf(Props(new Poller(swf, domainName, taskListName)), name = uuid)
  }
}
class Poller(
  swf: AmazonSimpleWorkflow,
  defaultDomainName: String,
  defaultTaskListName: String
) extends Actor {
  def receive = {
    case () => {
      sender ! poll(defaultDomainName, defaultTaskListName)
    }
    case taskListName: String => {
      sender ! poll(defaultDomainName, taskListName)
    }
    case (domainName: String, taskListName: String) => {
      sender ! poll(domainName, taskListName)
    }
    case others => sender ! ('invalid, others)
  }
  def poll(domainName: String, taskListName: String): Option[ActivityTask] = {
    val task = swf.pollForActivityTask(new PollForActivityTaskRequest()
      .withDomain(domainName)
      .withTaskList(new TaskList()
        .withName(taskListName)
      )
    )
    if (task.getTaskToken == null) None else Some(task)
  }
}

object Executor {
  def createActor(
    swf: AmazonSimpleWorkflow,
    action: ActivityTask => ActivityResult
  )(implicit factory: ActorRefFactory): ActorRef = {
    factory.actorOf(Props(new Executor(swf, action)), name = uuid)
  }
}
class Executor(
  swf: AmazonSimpleWorkflow,
  action: ActivityTask => ActivityResult
) extends Actor {
  def receive = {
    case task: ActivityTask => {
      sender ! execute(task)
      context.stop(self)
    }
    case others => {
      sender ! ('invalid, others)
      context.stop(self)
    }
  }
  def execute(task: ActivityTask): ActivityResult = {
    val result = action(task)
    result match {
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
    result
  }
}

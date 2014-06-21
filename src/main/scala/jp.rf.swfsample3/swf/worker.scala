package jp.rf.swfsample3.swf.worker

import akka.actor.{Actor, ActorRef, ActorRefFactory, Props}
import com.amazonaws.services.simpleworkflow.AmazonSimpleWorkflow
import com.amazonaws.services.simpleworkflow.model._

import jp.rf.swfsample.actor.swf.{ActivityCompleted, ActivityCanceled, ActivityFailed, ActivityResult}
import jp.rf.swfsample3.Activities

object Worker {
  def createActor(
    name: String,
    swf: AmazonSimpleWorkflow,
    action: ActivityTask => ActivityResult,
    client: ActorRef,
    defaultLimit: Int,
    domainName: String,
    taskListName: String
  )(implicit factory: ActorRefFactory): ActorRef = {
    Activities.createActor(
			name = name,
			pollerProps = Props(new Poller(swf, domainName, taskListName)),
			executorProps = Props(new Executor(swf, action)),
			client = client,
			defaultLimit = defaultLimit
		)
  }
}

class Poller(
  swf: AmazonSimpleWorkflow,
  defaultDomainName: String,
  defaultTaskListName: String
) extends Actor {
  def receive = {
    case pollingId: String => {
      sender ! ('polled, pollingId, poll(defaultDomainName, defaultTaskListName, pollingId))
    }
    case (taskListName: String, pollingId: String) => {
      sender ! ('polled, pollingId, poll(defaultDomainName, taskListName, pollingId))
    }
    case (domainName: String, taskListName: String, pollingId: String) => {
      sender ! ('polled, pollingId, poll(domainName, taskListName, pollingId))
    }
    case others => sender ! ('invalid, others)
  }
  def poll(domainName: String, taskListName: String, pollingId: String): Option[ActivityTask] = {
    val id = self.path.toString + "/" + pollingId
		println("polling activity start - " + pollingId + " , " + domainName + " , " + taskListName)
    val task = swf.pollForActivityTask(new PollForActivityTaskRequest()
      .withDomain(domainName)
      .withTaskList(new TaskList()
        .withName(taskListName)
      )
      .withIdentity(id)
    )
		println("polling activity end   - " + pollingId)
    if (task.getTaskToken == null) None else Some(task)
  }
}

class Executor(
  swf: AmazonSimpleWorkflow,
  action: ActivityTask => ActivityResult
) extends Actor {
  def receive = {
    case (pollingId: String, task: ActivityTask) => {
		  println("activity start    - " + pollingId)
      sender ! ('finished, pollingId, execute(task))
		  println("activity finished - " + pollingId)
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

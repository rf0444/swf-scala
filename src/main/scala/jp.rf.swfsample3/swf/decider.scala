package jp.rf.swfsample3.swf.decider

import akka.actor.{Actor, ActorRef, ActorRefFactory, Props}
import com.amazonaws.services.simpleworkflow.AmazonSimpleWorkflow
import com.amazonaws.services.simpleworkflow.model._

import jp.rf.swfsample3.Activities

object Decider {
  def createActor(
    name: String,
    swf: AmazonSimpleWorkflow,
    decide: DecisionTask => Decision,
    client: ActorRef,
    defaultLimit: Int,
    domainName: String,
    taskListName: String
  )(implicit factory: ActorRefFactory): ActorRef = {
    Activities.createActor(
			name = name,
			pollerProps = Props(new Poller(swf, domainName, taskListName)),
			executorProps = Props(new Executor(swf, decide)),
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
  def poll(domainName: String, taskListName: String, pollingId: String): Option[DecisionTask] = {
    val id = self.path.toString + "/" + pollingId
		println("polling decition start - " + pollingId + " , " + domainName + " , " + taskListName)
    val task = swf.pollForDecisionTask(new PollForDecisionTaskRequest()
      .withDomain(domainName)
      .withTaskList(new TaskList()
        .withName(taskListName)
      )
      .withIdentity(id)
    )
		println("polling decition end   - " + pollingId)
    if (task.getTaskToken == null) None else Some(task)
  }
}

class Executor(
  swf: AmazonSimpleWorkflow,
  decide: DecisionTask => Decision
) extends Actor {
  def receive = {
    case (pollingId: String, task: DecisionTask) => {
		  println("decision start    - " + pollingId)
      sender ! ('finished, pollingId, execute(task))
		  println("decision finished - " + pollingId)
      context.stop(self)
    }
    case others => {
      sender ! ('invalid, others)
      context.stop(self)
    }
  }
  def execute(task: DecisionTask): Decision = {
    val decision = decide(task)
    swf.respondDecisionTaskCompleted(new RespondDecisionTaskCompletedRequest()
      .withTaskToken(task.getTaskToken)
      .withDecisions(decision)
    )
    decision
  }
}

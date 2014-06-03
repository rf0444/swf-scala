package jp.rf.swfsample.actor.swf

import akka.actor.{ActorRef, ActorRefFactory}
import com.amazonaws.services.simpleworkflow._
import com.amazonaws.services.simpleworkflow.model._

object Decider {
  def create(
    swf: AmazonSimpleWorkflowClient,
    domainName: String,
    taskListName: String,
    decide: DecisionTask => Decision
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
        val decision = decide(task)
        swf.respondDecisionTaskCompleted(new RespondDecisionTaskCompletedRequest()
          .withTaskToken(task.getTaskToken)
          .withDecisions(decision)
        )
      }
    })
  }
}

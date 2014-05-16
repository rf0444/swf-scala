package jp.rf.swfsample.scalatra

import akka.actor.{ActorRef, ActorRefFactory}
import akka.util.Timeout
import com.amazonaws.services.simpleworkflow._
import com.amazonaws.services.simpleworkflow.model._

import jp.rf.swfsample.scalatra.data.{DeciderInput, DeciderOutput}
import jp.rf.swfsample.actor.swf.{ActivityActor, DeciderActorFactory}

object DecidersActor {
  def create(
    swf: AmazonSimpleWorkflowClient,
    domainName: String,
    workflowType: WorkflowType,
    activityType: ActivityType
  )(implicit factory: ActorRefFactory, timeout: Timeout): ActorRef = {
    val activityFactory = DeciderActorFactory.create(swf, domainName, workflowType, activityType)
    ActivitiesActor.create(new ActivitiesActorConf[DeciderInput, DeciderOutput] {
      override val name = "decider-admin-actor"
      override def createActivity = activityFactory.create
      override def createActivity(input: DeciderInput) = {
        val activity = createActivity
        if (input.active) {
          activity ! ActivityActor.Start
        }
        activity
      }
      override def modifyActivity(input: DeciderInput, activity: ActorRef) {
        activity ! (if (input.active) ActivityActor.Start else ActivityActor.Stop)
      }
      override def info(activity: ActorRef, state: ActivityActor.State) = {
        val active = state match {
          case ActivityActor.Inactive => false
          case ActivityActor.Active(_, cont) => cont
        }
        val status = state match {
          case ActivityActor.Inactive => state.toString
          case ActivityActor.Active(st, _) => st.toString
        }
        DeciderOutput(ActivitiesActor.actorIdOf(activity), active, status)
      }
    })
  }
}

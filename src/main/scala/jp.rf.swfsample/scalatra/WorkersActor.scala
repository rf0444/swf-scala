package jp.rf.swfsample.scalatra

import akka.actor.{ActorRef, ActorRefFactory}
import akka.util.Timeout
import com.amazonaws.services.simpleworkflow._
import com.amazonaws.services.simpleworkflow.model._

import jp.rf.swfsample.scalatra.data.{WorkerInput, WorkerOutput}
import jp.rf.swfsample.actor.swf.{ActivityActor, WorkerActor}

object WorkersActor {
  def create(
    swf: AmazonSimpleWorkflowClient,
    domainName: String,
    activityType: ActivityType
  )(implicit factory: ActorRefFactory, timeout: Timeout): ActorRef = {
    ActivitiesActor.create(new ActivitiesActorConf[WorkerInput, WorkerOutput] {
      override val name = "worker-admin-actor"
      override def createActivity = {
        WorkerActor.create(swf, domainName, activityType)
      }
      override def createActivity(input: WorkerInput) = {
        val activity = createActivity
        if (input.active) {
          activity ! ActivityActor.Start
        }
        activity
      }
      override def modifyActivity(input: WorkerInput, activity: ActorRef) {
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
        WorkerOutput(ActivitiesActor.actorIdOf(activity), active, status)
      }
    })
  }
}

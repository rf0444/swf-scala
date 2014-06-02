package jp.rf.swfsample.scalatra

import akka.actor.{ActorRef, ActorRefFactory}
import akka.util.Timeout
import com.amazonaws.services.simpleworkflow._
import com.amazonaws.services.simpleworkflow.model._

import jp.rf.swfsample.scalatra.data.{DeciderInput, DeciderOutput}
import jp.rf.swfsample.actor.swf.DeciderFactory
import jp.rf.swfsample.actor.swf.SwfActor.{Active, Inactive, Start, Stop, State => ActorState}

object DeciderManager {
  def create(
    name: String,
    swf: AmazonSimpleWorkflowClient,
    domainName: String,
    workflowType: WorkflowType,
    activityType: ActivityType
  )(implicit factory: ActorRefFactory, timeout: Timeout): ActorRef = {
    val actorFactory = DeciderFactory.create(swf, domainName, workflowType, activityType)
    val nm = name
    SwfActorManager.create(new SwfActorManagerConf[DeciderInput, DeciderOutput] {
      override val name = nm
      override def createActor = actorFactory.create
      override def createActor(input: DeciderInput) = {
        val actor = createActor
        if (input.active) {
          actor ! Start
        }
        actor
      }
      override def modifyActor(input: DeciderInput, actor: ActorRef) {
        actor ! (if (input.active) Start else Stop)
      }
      override def info(actor: ActorRef, state: ActorState) = {
        val active = state match {
          case Inactive => false
          case Active(_, cont) => cont
        }
        val status = state match {
          case Inactive => state.toString
          case Active(st, _) => st.toString
        }
        DeciderOutput(SwfActorManager.actorIdOf(actor), active, status)
      }
    })
  }
}

package jp.rf.swfsample.scalatra

import scala.collection.{GenSeq, SortedMap}

import akka.actor.ActorDSL.{Act, actor => act}
import akka.actor.{ActorRef, ActorRefFactory, ActorSystem}
import com.amazonaws.services.simpleworkflow._
import com.amazonaws.services.simpleworkflow.model._

case class DeciderInput(active: Boolean)
case class DeciderOutput(id: String, active: Boolean, status: String)
case class WorkerInput(active: Boolean)
case class WorkerOutput(id: String, active: Boolean, status: String)

sealed trait DeciderAction
case object GetDeciders extends DeciderAction
case class GetDecider(id: String) extends DeciderAction
case class AddDecider(data: DeciderInput) extends DeciderAction
case class SetDecider(id: String, data: DeciderInput) extends DeciderAction

sealed trait WorkerAction
case object GetWorkers extends WorkerAction
case class GetWorker(id: String) extends WorkerAction
case class AddWorker(data: WorkerInput) extends WorkerAction
case class SetWorker(id: String, data: WorkerInput) extends WorkerAction

class DeciderMainActor(
  val system: ActorSystem,
  val swf: AmazonSimpleWorkflowClient,
  val domainName: String,
  val workflowType: WorkflowType,
  val activityType: ActivityType
) {
  val initialDeciderNum = 1
  val actor = act(system, "decider-main-actor")(new Act {
    @volatile private[this] var deciders = SortedMap.empty[String, DeciderActor] ++ GenSeq.fill(initialDeciderNum) {
      val decider = DeciderActor.create(context, swf, domainName, workflowType, activityType)
      (decider.id, decider)
    }
    become {
      case GetDeciders => {
        sender ! deciders.values.map { decider =>
          val (active, status) = decider.status
          DeciderOutput(decider.id, active, status.toString)
        }
      }
      case GetDecider(id) => {
        sender ! deciders.get(id).map { decider =>
          val (active, status) = decider.status
          DeciderOutput(decider.id, active, status.toString)
        }
      }
      case AddDecider(input) => {
        val decider = DeciderActor.create(context, swf, domainName, workflowType, activityType)
        deciders += decider.id -> decider
        if (input.active) {
          decider.actor ! 'start
        }
        val (active, status) = decider.status
        sender ! DeciderOutput(decider.id, active, status.toString)
      }
      case SetDecider(id, input) => {
        sender ! deciders.get(id).map { decider =>
          decider.actor ! (if (input.active) 'start else 'stop)
          val (active, status) = decider.status
          DeciderOutput(decider.id, active, status.toString)
        }
      }
    }
  })
}

class WorkerMainActor(
  val system: ActorSystem,
  val swf: AmazonSimpleWorkflowClient,
  val domainName: String,
  val activityType: ActivityType
) {
  val initialWorkerNum = 1
  val actor = act(system, "worker-main-actor")(new Act {
    @volatile private[this] var workers = SortedMap.empty[String, WorkerActor] ++ GenSeq.fill(initialWorkerNum) {
      val worker = WorkerActor.create(context, swf, domainName, activityType)
      (worker.id, worker)
    }
    become {
      case GetWorkers => {
        sender ! workers.values.map { worker =>
          val (active, status) = worker.status
          WorkerOutput(worker.id, active, status.toString)
        }
      }
      case GetWorker(id) => {
        sender ! workers.get(id).map { worker =>
          val (active, status) = worker.status
          WorkerOutput(worker.id, active, status.toString)
        }
      }
      case AddWorker(input) => {
        val worker = WorkerActor.create(context, swf, domainName, activityType)
        workers += worker.id -> worker
        if (input.active) {
          worker.actor ! 'start
        }
        val (active, status) = worker.status
        sender ! WorkerOutput(worker.id, active, status.toString)
      }
      case SetWorker(id, input) => {
        sender ! workers.get(id).map { worker =>
          worker.actor ! (if (input.active) 'start else 'stop)
          val (active, status) = worker.status
          WorkerOutput(worker.id, active, status.toString)
        }
      }
    }
  })
}

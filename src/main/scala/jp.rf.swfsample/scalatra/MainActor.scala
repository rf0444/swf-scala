package jp.rf.swfsample.scalatra

import scala.collection.{GenSeq, SortedMap}

import akka.actor.ActorDSL.{Act, actor => act}
import akka.actor.{ActorRef, ActorRefFactory, ActorSystem}

sealed trait ActorStatus
case object Inactive extends ActorStatus
case object Polling extends ActorStatus
case object Waiting extends ActorStatus
case object Working extends ActorStatus

case class WorkerInput(active: Boolean)
case class WorkerOutput(id: String, status: String)

sealed trait Action
case object GetWorkers extends Action
case class GetWorker(id: String) extends Action
case class AddWorker(data: WorkerInput) extends Action
case class SetWorker(id: String, data: WorkerInput) extends Action

class MainActor(val system: ActorSystem) {
  val initialWorkerNum = 3
  val actor = act(system, "main-actor")(new Act {
    @volatile private[this] var workers = SortedMap.empty[String, WorkerActor] ++ GenSeq.fill(initialWorkerNum) {
      val worker = new WorkerActor(context)
      (worker.id, worker)
    }
    become {
      case GetWorkers => {
        sender ! workers.values.map { worker =>
          WorkerOutput(worker.id, worker.status.toString)
        }
      }
      case GetWorker(id) => {
        sender ! workers.get(id).map { worker =>
          WorkerOutput(worker.id, worker.status.toString)
        }
      }
      case AddWorker(input) => {
        val worker = new WorkerActor(context)
        workers += worker.id -> worker
        if (input.active) {
          worker.actor ! 'start
        }
        sender ! WorkerOutput(worker.id, worker.status.toString)
      }
      case SetWorker(id, input) => {
        sender ! workers.get(id).map { worker =>
          worker.actor ! (if (input.active) 'start else 'stop)
          WorkerOutput(worker.id, worker.status.toString)
        }
      }
    }
  })
}

class WorkerActor(val factory: ActorRefFactory) extends Comparable[WorkerActor] {
  @volatile private[this] var _isActive = false
  @volatile private[this] var _status : ActorStatus = Inactive
  def status = _status
  val actor = act(factory)(new Act {
    val name = self.path.name
    become {
      case 'execute => {
        if (_isActive) {
          // poll
          println(name + " - begin polling")
          _status = Polling
          Thread.sleep(2000)
          println(name + " - end polling")
          // work
          println(name + " - begin working")
          _status = Working
          Thread.sleep(2000)
          println(name + " - end working")
          _status = Waiting
          self ! 'execute
        }
      }
      case 'start => {
        if (!_isActive) {
          _isActive = true
          _status = Waiting
          self ! 'execute
        }
      }
      case 'stop => {
        _isActive = false
        _status = Inactive
      }
    }
  })
  val id = actor.path.name
  def compareTo(other: WorkerActor): Int = this.actor compareTo other.actor
}

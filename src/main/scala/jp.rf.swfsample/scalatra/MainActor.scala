package jp.rf.swfsample.scalatra

import akka.actor.ActorDSL.{Act, actor => act}
import akka.actor.{ActorRef, ActorRefFactory, ActorSystem}

sealed trait Action
case object GetDeciders extends Action
case class SetDeciders(num: Int) extends Action
case object GetWorkers extends Action
case class SetWorkers(num: Int) extends Action

case class Deciders(num: Int)
case class Workers(num: Int)

class MainActor(val system: ActorSystem) {
  val actor = act(system, "main-actor")(new Act {
    val worker = new WorkerActor(context)
    become {
      case 'hello => {
        println("hello")
        println(self)
      }
      case 'startWorker => {
        worker.actor ! 'start
      }
      case 'stopWorker => {
        worker.actor ! 'stop
      }
      case 'isActiveWorker => {
        sender ! worker.isActive
      }
      case GetDeciders => {
        sender ! Deciders(1)
      }
      case SetDeciders(num) => {
        sender ! Deciders(num)
      }
      case GetWorkers => {
        sender ! Workers(1)
      }
      case SetWorkers(num) => {
        sender ! Workers(num)
      }
    }
  })
}

class WorkerActor(val factory: ActorRefFactory) {
  private[this] var isActive_ = false
  def isActive = isActive_
  val actor = act(factory)(new Act {
    become {
      case 'execute => {
        if (isActive_) {
          println("execute")
          Thread.sleep(2000)
          self ! 'execute
        }
      }
      case 'start => {
        if (!isActive_) {
          isActive_ = true
          self ! 'execute
        }
      }
      case 'stop => {
        isActive_ = false
      }
    }
  })
}

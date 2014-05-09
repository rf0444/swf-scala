package jp.rf.swfsample.scalatra

import scala.concurrent.Await
import scala.concurrent.duration.{FiniteDuration, SECONDS}

import akka.actor.ActorDSL.{Act, actor => act}
import akka.actor.{ActorRef, ActorRefFactory, ActorSystem}
import akka.pattern.{ask, pipe}
import akka.util.Timeout

sealed trait Action
case object GetDeciders extends Action
case class SetDeciders(num: Int) extends Action
case object GetWorkers extends Action
case class SetWorkers(num: Int) extends Action

case class Deciders(num: Int)
case class Workers(num: Int)

class MainActor(val system: ActorSystem) {
  implicit val executor = system.dispatcher
  implicit val timeout = Timeout(FiniteDuration(5, SECONDS))
  val actor = act(system, "main-actor")(new Act {
    val worker = WorkerActor.create(context)
    become {
      case 'hello => {
        println("hello")
        println(self)
      }
      case 'startWorker => {
        worker ! 'start
      }
      case 'stopWorker => {
        worker ! 'stop
      }
      case 'isActiveWorker => {
        worker ? 'isActive pipeTo sender
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

object WorkerActor {
  def create(implicit factory: ActorRefFactory): ActorRef = {
    act("worker-actor")(new Act {
      var isActive = false
      become {
        case 'execute => {
          if (isActive) {
            println("execute")
            Thread.sleep(2000)
            self ! 'execute
          }
        }
        case 'start => {
          if (!isActive) {
            isActive = true
            self ! 'execute
          }
        }
        case 'stop => {
          isActive = false
        }
        case 'isActive => {
          sender ! isActive
        }
      }
    })
  }
}

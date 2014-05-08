package jp.rf.swfsample.scalatra

import akka.actor.ActorDSL.{Act, actor => act}
import akka.actor.{ActorRef, ActorSystem}

sealed trait Action
case object GetDeciders extends Action
case class SetDeciders(num: Int) extends Action
case object GetWorkers extends Action
case class SetWorkers(num: Int) extends Action

case class Deciders(num: Int)
case class Workers(num: Int)

class MainActor(val system: ActorSystem) {
  val actor = act(system, "MainActor")(new Act {
    become {
      case 'hello => {
        println("hello")
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

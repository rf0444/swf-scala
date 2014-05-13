package jp.rf.swfsample.main

import akka.actor.ActorDSL.{Act, actor => act}
import akka.actor.{ActorRef, ActorSystem, FSM}

object FSMSample {
  sealed trait State
  case object Inactive extends State
  case class Active(status: ActiveStatus, cont: Boolean) extends State
  
  sealed trait ActiveStatus
  case object Polling extends ActiveStatus
  case object Working extends ActiveStatus
  
  case class Task(name: String)
  
  sealed trait Action
  case object Start extends Action
  case object Stop extends Action
  case class Polled(task: Option[Task]) extends Action
  case object Done extends Action
  
  sealed trait ActualAction
  case object Poll extends ActualAction
  case class Execute(task: Task) extends ActualAction
  
  def main(args: Array[String]) {
    val system = ActorSystem()
    val actor = act(system)(new Act with FSM[State, Unit] {
      val actionActor = act(context)(new Act {
        become {
          case Poll => {
            println("polling")
            Thread.sleep(500)
            println("polled")
            sender ! Polled(Some(Task("hoge")))
          }
          case Execute(Task(name)) => {
            println("working - " + name)
            Thread.sleep(500)
            println("done")
            sender ! Done
          }
        }
      })
      startWith(Inactive, ())
      when(Inactive) {
        case Event(Start, _) => {
          actionActor ! Poll
          goto(Active(Polling, true))
        }
      }
      when(Active(Polling, true)) {
        case Event(Polled(None), data) => {
          actionActor ! Poll
          stay
        }
        case Event(Polled(Some(task)), _) => {
          actionActor ! Execute(task)
          goto(Active(Working, true))
        }
        case Event(Stop, _) => goto(Active(Polling, false))
      }
      when(Active(Polling, false)) {
        case Event(Polled(None), _) => goto(Inactive)
        case Event(Polled(Some(task)), _) => {
          actionActor ! Execute(task)
          goto(Active(Working, false))
        }
        case Event(Start, _) => goto(Active(Polling, true))
      }
      when(Active(Working, true)) {
        case Event(Done, _) => {
          actionActor ! Poll
          goto(Active(Polling, true))
        }
        case Event(Stop, _) => goto(Active(Working, false))
      }
      when(Active(Working, false)) {
        case Event(Done, _) => goto(Inactive)
        case Event(Start, _) => goto(Active(Working, true))
      }
      initialize
    })
    actor ! Start
    Thread.sleep(4000)
    actor ! Stop
    Thread.sleep(2000)
    actor ! Start
    Thread.sleep(4000)
    system.shutdown
  }
}

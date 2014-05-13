package jp.rf.swfsample.main

import scala.concurrent.Await
import scala.concurrent.duration._

import akka.actor.ActorDSL.{Act, actor => act}
import akka.actor.{ActorRef, ActorSystem, FSM}
import akka.pattern.ask
import akka.util.Timeout

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
      whenUnhandled {
        case Event('state, _) => {
          sender ! stateName
          stay
        }
      }
      initialize
    })
    implicit val timeout = Timeout(FiniteDuration(5, SECONDS))
    actor ! Start
    Thread.sleep(1000)
    println(Await.result(actor ? 'state, timeout.duration))
    Thread.sleep(1500)
    println(Await.result(actor ? 'state, timeout.duration))
    Thread.sleep(1500)
    println(Await.result(actor ? 'state, timeout.duration))
    actor ! Stop
    println(Await.result(actor ? 'state, timeout.duration))
    Thread.sleep(1000)
    println(Await.result(actor ? 'state, timeout.duration))
    actor ! Start
    Thread.sleep(1000)
    println(Await.result(actor ? 'state, timeout.duration))
    Thread.sleep(1400)
    println(Await.result(actor ? 'state, timeout.duration))
    actor ! Stop
    println(Await.result(actor ? 'state, timeout.duration))
    Thread.sleep(1500)
    println(Await.result(actor ? 'state, timeout.duration))
    system.shutdown
  }
}

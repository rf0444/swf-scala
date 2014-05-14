package jp.rf.swfsample.main

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.reflect.{ClassTag, classTag}

import akka.actor.ActorDSL.{Act, actor => act}
import akka.actor.{ActorRef, ActorRefFactory, ActorSystem, FSM}
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
  case object GetState extends Action
  
  sealed trait ActualAction
  case object Poll extends ActualAction
  case class Execute(task: Task) extends ActualAction
  
  def main(args: Array[String]) {
    val system = ActorSystem()
    val actionActor = act(system)(new Act {
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
    val actor = createActor(actionActor)(system)
    implicit val timeout = Timeout(FiniteDuration(5, SECONDS))
    actor ! Start
    Thread.sleep(1000)
    println(Await.result(actor ? GetState, timeout.duration))
    Thread.sleep(1500)
    println(Await.result(actor ? GetState, timeout.duration))
    Thread.sleep(1500)
    println(Await.result(actor ? GetState, timeout.duration))
    actor ! Stop
    println(Await.result(actor ? GetState, timeout.duration))
    Thread.sleep(1000)
    println(Await.result(actor ? GetState, timeout.duration))
    actor ! Start
    Thread.sleep(1000)
    println(Await.result(actor ? GetState, timeout.duration))
    Thread.sleep(1400)
    println(Await.result(actor ? GetState, timeout.duration))
    actor ! Stop
    println(Await.result(actor ? GetState, timeout.duration))
    Thread.sleep(1500)
    println(Await.result(actor ? GetState, timeout.duration))
    system.shutdown
  }
  def createActor(activity: ActorRef)(implicit factory: ActorRefFactory): ActorRef = {
    FSMActor.create(new FSMActorConf[State, Action] {
      val states = Seq(
        Inactive,
        Active(Polling, true),
        Active(Polling, false),
        Active(Working, true),
        Active(Working, false)
      )
      val initialState = Inactive
      override def transition(state: State, event: Action) = (state, event) match {
        case (Inactive,               Start)           => Active(Polling, true)
        case (Active(Polling, cont),  Polled(Some(_))) => Active(Working, cont)
        case (Active(Polling, true),  Stop)            => Active(Polling, false)
        case (Active(Polling, false), Polled(None))    => Inactive
        case (Active(Polling, false), Start)           => Active(Polling, true)
        case (Active(Working, true),  Done)            => Active(Polling, true)
        case (Active(Working, true),  Stop)            => Active(Working, false)
        case (Active(Working, false), Done)            => Inactive
        case (Active(Working, false), Start)           => Active(Working, true)
        case (status,                 _)               => status
      }
      override def action(state: State, event: Action, actor: FSMAct) {
        implicit val sender = actor.self
        (state, event) match {
          case (Inactive,              Start)              => activity ! Poll
          case (Active(Polling, true), Polled(None))       => activity ! Poll
          case (Active(Polling, _),    Polled(Some(task))) => activity ! Execute(task)
          case (Active(Working, true), Done)               => activity ! Poll
          case (_,                     GetState)           => actor.sender ! actor.stateName
          case _                                           => 
        }
      }
    })
  }
}

trait FSMActorConf[S, E] {
  type FSMAct = Act with FSM[S, Unit]
  val states: Seq[S]
  val initialState: S
  def transition(state: S, event: E): S
  def action(state: S, event: E, actor: FSMAct): Unit
  def unhandledTransition(state: S, event: Any): Option[S] = None
  def unhandledAction(state: S, event: Any, actor: FSMAct): Unit = {}
}

object FSMActor {
  def create[S, E: ClassTag](conf: FSMActorConf[S, E])(implicit factory: ActorRefFactory): ActorRef = {
    act(factory)(new Act with FSM[S, Unit] {
      startWith(conf.initialState, ())
      for (state <- conf.states) {
        when(state) {
          case Event(anyE, _) => safeCast[E](anyE) match {
            case None => {
              conf.unhandledAction(state, anyE, this)
              conf.unhandledTransition(state, anyE).map(goto).getOrElse(stay)
            }
            case Some(e) => {
              conf.action(state, e, this)
              goto(conf.transition(state, e))
            }
          }
        }
      }
      initialize
    })
  }
  def safeCast[A: ClassTag](x: Any): Option[A] = {
    val cls = classTag[A].runtimeClass.asInstanceOf[Class[A]]
    if (cls.isInstance(x)) Some(cls.cast(x)) else None
  }
}

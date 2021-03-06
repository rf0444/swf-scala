package jp.rf.swfsample.actor.swf

import scala.reflect.ClassTag

import akka.actor.ActorDSL.{Act, actor}
import akka.actor.{Actor, ActorRef, ActorRefFactory}
import akka.pattern.ask

import jp.rf.swfsample.actor.util.{FSMActor, FSMActorConf}
import jp.rf.swfsample.util.safeCast

sealed trait State
case object Inactive extends State
case class Active(status: ActiveStatus, cont: Boolean) extends State

sealed trait ActiveStatus
case object Polling extends ActiveStatus
case object Working extends ActiveStatus

sealed trait Action[+T]
case object Start extends Action[Nothing]
case object Stop extends Action[Nothing]
case class Polled[T](task: Option[T]) extends Action[T]
case object Done extends Action[Nothing]
case object GetState extends Action[Nothing]

sealed trait ActualAction[+T]
case object Poll extends ActualAction[Nothing]
case class Execute[T](task: T) extends ActualAction[T]

trait SwfActorConf[T] {
  val name: Option[String] = None
  def poll: Option[T]
  def execute(task: T)
}

object SwfActor {
  def create[T: ClassTag](conf: SwfActorConf[T])(implicit factory: ActorRefFactory): ActorRef = {
    val actionActor = actor(factory)(new Act {
      become {
        case Poll => {
          sender ! Polled(conf.poll)
        }
        case Execute(task) => {
          safeCast[T](task).foreach(conf.execute)
          sender ! Done
        }
      }
    })
    FSMActor.create(new FSMActorConf[State, Action[T]] {
      override val name = conf.name
      override val initialState = Inactive
      override def transition(state: State, event: Action[T]) = (state, event) match {
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
      override def action(state: State, event: Action[T], act: Actor) {
        implicit val sender = act.self
        (state, event) match {
          case (Inactive,              Start)              => actionActor ! Poll
          case (Active(Polling, true), Polled(None))       => actionActor ! Poll
          case (Active(Polling, _),    Polled(Some(task))) => actionActor ! Execute(task)
          case (Active(Working, true), Done)               => actionActor ! Poll
          case (state,                 GetState)           => act.sender ! state
          case _                                           => 
        }
      }
    })
  }
}

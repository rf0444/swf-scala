package jp.rf.swfsample.actor

import scala.reflect.ClassTag

import akka.actor.ActorDSL.{Act, actor}
import akka.actor.{ActorRef, ActorRefFactory, FSM}

import jp.rf.swfsample.util.safeCast

trait MutableActorConf[T, E] {
  type MutableAct = Act with FSM[Unit, T]
  val initialValue: T
  def action(value: T, event: E, actor: MutableAct): T
  def unhandledAction(value: T, event: Any, actor: MutableAct): T = value
}

object MutableActor {
  def create[T, E: ClassTag](conf: MutableActorConf[T, E])(implicit factory: ActorRefFactory): ActorRef = {
    actor(new Act with FSM[Unit, T] {
      startWith((), conf.initialValue)
      when(()) {
        case Event(anyE, x) => safeCast[E](anyE) match {
          case None => {
            stay using conf.unhandledAction(x, anyE, this)
          }
          case Some(e) => {
            stay using conf.action(x, e, this)
          }
        }
      }
    })
  }
}

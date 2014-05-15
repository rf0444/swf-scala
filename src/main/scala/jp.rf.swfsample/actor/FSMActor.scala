package jp.rf.swfsample.actor

import scala.reflect.ClassTag

import akka.actor.ActorDSL.{Act, actor}
import akka.actor.{ActorRef, ActorRefFactory, FSM}

import jp.rf.swfsample.util.safeCast

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
    actor(new Act with FSM[S, Unit] {
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
}

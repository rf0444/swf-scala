package jp.rf.swfsample.actor

import scala.reflect.ClassTag

import akka.actor.ActorDSL.actor
import akka.actor.{Actor, ActorRef, ActorRefFactory}

import jp.rf.swfsample.util.safeCast

trait FSMActorConf[S, E] {
  val initialState: S
  def transition(state: S, event: E): S
  def action(state: S, event: E, act: Actor): Unit
  def unhandledTransition(state: S, event: Any): Option[S] = None
  def unhandledAction(state: S, event: Any, act: Actor): Unit = {}
}

object FSMActor {
  def create[S, E: ClassTag](conf: FSMActorConf[S, E])(implicit factory: ActorRefFactory): ActorRef = {
    actor(new MutableActor(new MutableActorConf[S, E] {
      val initialValue = conf.initialState
      override def action(state: S, event: E, act: Actor) = {
        conf.action(state, event, act)
        conf.transition(state, event)
      }
      override def unhandledAction(state: S, event: Any, act: Actor) = {
        conf.unhandledAction(state, event, act)
        conf.unhandledTransition(state, event).getOrElse(state)
      }
    }))
  }
}

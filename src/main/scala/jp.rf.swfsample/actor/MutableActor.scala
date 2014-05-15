package jp.rf.swfsample.actor

import scala.reflect.ClassTag

import akka.actor.ActorDSL.actor
import akka.actor.{Actor, ActorRef, ActorRefFactory}

import jp.rf.swfsample.util.safeCast

trait MutableActorConf[T, E] {
  val initialValue: T
  def action(value: T, event: E, act: Actor): T
  def unhandledAction(value: T, event: Any, act: Actor): T = value
}

object MutableActor {
  def create[T, E: ClassTag](conf: MutableActorConf[T, E])(implicit factory: ActorRefFactory): ActorRef = {
    actor(new MutableActor(conf))
  }
}
class MutableActor[T, E: ClassTag](conf: MutableActorConf[T, E]) extends Actor {
  def behavior(x: T): Receive = { case anyE =>
    context.become(
      behavior(
        safeCast[E](anyE)
          .map(conf.action(x, _, this))
          .getOrElse(conf.unhandledAction(x, anyE, this))
      )
    )
  }
  def receive: Receive = behavior(conf.initialValue)
}

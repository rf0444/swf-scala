package jp.rf.swfsample.actor.manager

import scala.collection.{GenSeq, SortedMap}
import scala.concurrent.{Future, ExecutionContext}

import akka.actor.{Actor, ActorRef, ActorRefFactory}
import akka.pattern.{ask, pipe}
import akka.util.Timeout

import jp.rf.swfsample.actor.swf.{GetState, State => ActorState}
import jp.rf.swfsample.actor.util.{MutableActor, MutableActorConf}

sealed trait ManagerAction[+T]
case class Add[T](data: T) extends ManagerAction[T]
case class Get(id: String) extends ManagerAction[Nothing]
case object GetAll extends ManagerAction[Nothing]
case class Set[T](id: String, data: T) extends ManagerAction[T]
case class SetAll[T](data: T) extends ManagerAction[T]

trait SwfActorManagerConf[In, Out] {
  val initialNum: Int = 1
  val name: String
  def createActor: ActorRef
  def createActor(input: In): ActorRef
  def modifyActor(input: In, actor: ActorRef)
  def info(actor: ActorRef, state: ActorState): Out
}

object SwfActorManager {
  def create[In, Out](conf: SwfActorManagerConf[In, Out])(implicit factory: ActorRefFactory, timeout: Timeout): ActorRef = {
    implicit def executor: ExecutionContext = factory.dispatcher
    MutableActor.create(new MutableActorConf[SortedMap[String, ActorRef], ManagerAction[In]] {
      override val name = Some(conf.name)
      override val initialValue = SortedMap.empty[String, ActorRef] ++ GenSeq.fill(conf.initialNum) {
        val actor = conf.createActor
        (actorIdOf(actor), actor)
      }
      override def action(actors: SortedMap[String, ActorRef], event: ManagerAction[In], act: Actor) = event match {
        case GetAll => {
          Future.sequence(actors.values.map(infoOf)) pipeTo act.sender
          actors
        }
        case Get(id) => {
          actors.get(id) match {
            case None => act.sender ! None
            case Some(actor) => infoOf(actor) pipeTo act.sender
          }
          actors
        }
        case Add(input) => {
          val actor = conf.createActor(input)
          infoOf(actor) pipeTo act.sender
          actors + (actorIdOf(actor) -> actor)
        }
        case Set(id, input) => {
          actors.get(id) match {
            case None => act.sender ! None
            case Some(actor) => {
              conf.modifyActor(input, actor)
              infoOf(actor) pipeTo act.sender
            }
          }
          actors
        }
        case SetAll(input) => {
          actors.values.foreach(conf.modifyActor(input, _))
          Future.sequence(actors.values.map(infoOf)) pipeTo act.sender
          actors
        }
      }
      def infoOf(actor: ActorRef): Future[Out] = {
        ask(actor, GetState)
          .mapTo[ActorState]
          .map(conf.info(actor, _))
      }
    })
  }
  def actorIdOf(actor: ActorRef): String = actor.path.name
}

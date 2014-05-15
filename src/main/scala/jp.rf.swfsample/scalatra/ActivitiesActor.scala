package jp.rf.swfsample.scalatra

import scala.collection.{GenSeq, SortedMap}
import scala.concurrent.{Future, ExecutionContext}

import akka.actor.{Actor, ActorRef, ActorRefFactory}
import akka.pattern.{ask, pipe}
import akka.util.Timeout

import jp.rf.swfsample.actor.{MutableActor, MutableActorConf}
import jp.rf.swfsample.actor.swf.ActivityActor
import jp.rf.swfsample.scalatra.data.{ActivityAction, GetAll, Get, Add, Set}

trait ActivitiesActorConf[In, Out] {
  val initialNum: Int = 1
  val name: String
  def createActivity: ActorRef
  def createActivity(input: In): ActorRef
  def modifyActivity(input: In, activity: ActorRef)
  def info(activity: ActorRef, state: ActivityActor.State): Out
}

object ActivitiesActor {
  val initialNum = 1
  def create[In, Out](conf: ActivitiesActorConf[In, Out])(implicit factory: ActorRefFactory, timeout: Timeout): ActorRef = {
    implicit def executor: ExecutionContext = factory.dispatcher
    MutableActor.create(new MutableActorConf[SortedMap[String, ActorRef], ActivityAction[In]] {
      override val name = Some(conf.name)
      override val initialValue = SortedMap.empty[String, ActorRef] ++ GenSeq.fill(conf.initialNum) {
        val activity = conf.createActivity
        (actorIdOf(activity), activity)
      }
      override def action(activities: SortedMap[String, ActorRef], event: ActivityAction[In], act: Actor) = event match {
        case GetAll => {
          Future.sequence(activities.values.map(infoOf)) pipeTo act.sender
          activities
        }
        case Get(id) => {
          activities.get(id) match {
            case None => act.sender ! None
            case Some(activity) => infoOf(activity) pipeTo act.sender
          }
          activities
        }
        case Add(input) => {
          val activity = conf.createActivity(input)
          activities + (actorIdOf(activity) -> activity)
        }
        case Set(id, input) => {
          activities.get(id) match {
            case None => act.sender ! None
            case Some(activity) => {
              conf.modifyActivity(input, activity)
              infoOf(activity) pipeTo act.sender
            }
          }
          activities
        }
      }
      def infoOf(activity: ActorRef): Future[Out] = {
        ask(activity, ActivityActor.GetState)
          .mapTo[ActivityActor.State]
          .map(conf.info(activity, _))
      }
    })
  }
  def actorIdOf(actor: ActorRef): String = actor.path.name
}

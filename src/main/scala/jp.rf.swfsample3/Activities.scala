package jp.rf.swfsample3

import akka.actor.{Actor, ActorRef, ActorRefFactory, Props, Terminated}

import jp.rf.swfsample.util.{safeCast, uuid}

object Activities {
  def createActor(
    name: String,
    pollerProps: Props,
    executorProps: Props,
    client: ActorRef,
    defaultLimit: Int
  )(implicit factory: ActorRefFactory): ActorRef = {
    factory.actorOf(Props(new Activities(pollerProps, executorProps, client, defaultLimit)), name = name)
  }
}
class Activities(
  pollerProps: Props,
  executorProps: Props,
  client: ActorRef,
  defaultLimit: Int
) extends Actor {
  val poller = context.actorOf(pollerProps, name = "poller")
  def pollIfActive(state: ActivityState): ActivityState = {
    if (state.active) pollIfAllowable(state) else state
  }
  def pollIfAllowable(state: ActivityState): ActivityState = {
    val allowance = state.limit - state.pollings.size - state.executings.size
    if (allowance <= 0) {
      return state
    }
    val pollings = for (_ <- 1 to allowance) yield {
      val id = uuid
      poller ! id
      id
    }
    state.copy(pollings = state.pollings ++ pollings)
  }
  def behavior(state: ActivityState): Receive = {
    case 'start => {
      val afterPoll = pollIfAllowable(state)
      context.become(behavior(afterPoll.copy(active = true)))
    }
    case 'stop => {
      context.become(behavior(state.copy(active = false)))
    }
    case 'get => {
      sender ! state
      context.become(behavior(state))
    }
    case ('setLimit, newLimit: Int) => {
      context.become(behavior(
        pollIfActive(state.copy(limit = newLimit))
      ))
    }
    case ('polled, pollingId: String, None) => {
      if (!state.pollings.contains(pollingId)) {
        System.err.println("not started polling but polled: " + pollingId)
      }
      val afterPolled = state.copy(pollings = state.pollings - pollingId)
      val afterNewPoll = pollIfActive(afterPolled)
      context.become(behavior(afterNewPoll))
    }
    case ('polled, pollingId: String, Some(task)) => {
      if (!state.pollings.contains(pollingId)) {
        System.err.println("not started polling but polled: " + pollingId)
      }
      val afterPolled = state.copy(pollings = state.pollings - pollingId)
      val executor = context.actorOf(executorProps, name = pollingId)
      context.watch(executor)
      executor ! (pollingId, task)
      val afterExecute = afterPolled.copy(executings = afterPolled.executings + (pollingId -> task))
      val afterNewPoll = pollIfActive(afterExecute)
      context.become(behavior(afterNewPoll))
    }
    case ('finished, pollingId: String, result) => {
      state.executings.get(pollingId) match {
        case Some(task) => {
          val afterExecuted = state.copy(executings = state.executings - pollingId)
          context.become(behavior(afterExecuted))
        }
        case None => {
          System.err.println("not started but finished: " + pollingId + " , " + result)
          context.become(behavior(state))
        }
      }
    }
    case Terminated(actor) => {
      val afterNewPoll = pollIfActive(state)
      context.become(behavior(afterNewPoll))
    }
    case ('invalid, input) => {
      System.err.println("invalid input from sender: " + sender + " , " + input)
      context.become(behavior(state))
    }
    case other => {
      System.err.println("invalid command from sender: " + sender + " , " + other)
      context.become(behavior(state))
    }
  }
  def receive = behavior(ActivityState(
    active = false,
    limit = defaultLimit,
    pollings = Set.empty,
    executings = Map.empty
  ))
}
case class ActivityState(
  active: Boolean,
  limit: Int,
  pollings: Set[String],
  executings: Map[String, Any]
)

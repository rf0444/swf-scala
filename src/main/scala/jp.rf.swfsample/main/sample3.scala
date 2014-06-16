package jp.rf.swfsample.main.sample3

import scala.concurrent.Await
import scala.concurrent.duration._

import akka.actor.{Actor, ActorRef, ActorSystem, Props, Terminated}
import akka.pattern.ask
import akka.util.Timeout

case class MainState(finished: Boolean)
object Main {
  def main(args: Array[String]) {
    implicit val timeout = Timeout(FiniteDuration(5, SECONDS))
    val system = ActorSystem("child-sample-3")
    system.actorOf(Props(new Actor {
      val manager = context.actorOf(Props(new ManagerActor(
        client = self,
        defaultLimit = 2,
        pollerProps = Props(new PollerActor(poll)),
        workerProps = Props(new WorkerActor(action))
      )))
      def behavior(state: MainState): Receive = {
        case 'execute => {
          manager ! 'start
          println("start")
          Thread.sleep(4000)
          manager ! ('setLimit, 4)
          println("set limit 4")
          Thread.sleep(4000)
          manager ! 'stop
          println("stop")
          context.become(behavior(state.copy(finished = true)))
        }
        case 'allFinished => {
          println("all finished.")
          if (state.finished) {
            system.shutdown()
          }
          context.become(behavior(state.copy(finished = true)))
        }
      }
      def receive = behavior(MainState(finished = false))
    }), name = "tasks") ! 'execute
  }
  def poll(x: Any): Any = {
    println("polling start : " + x)
    Thread.sleep(100)
    println("polling finish: " + x)
    x
  }
  def action(x: Any): Any = {
    println("action start  : " + x)
    Thread.sleep(1500)
    println("action finish : " + x)
    x
  }
}

case class ManagerState(
  active: Boolean,
  limit: Int,
  pollings: Set[Any],
  workings: Map[ActorRef, (Any, Boolean)]
)
class ManagerActor(
  client: ActorRef,
  defaultLimit: Int,
  pollerProps: Props,
  workerProps: Props
)(implicit timeout: Timeout) extends Actor {
  implicit val ec = context.dispatcher
  val poller = context.actorOf(pollerProps, name = "poller")
  def pollIfActive(state: ManagerState): ManagerState = {
    if (state.active) pollIfAllowable(state) else state
  }
  def pollIfAllowable(state: ManagerState): ManagerState = {
    val allowance = state.limit - state.pollings.size - state.workings.size
    if (allowance <= 0) {
      return state
    }
    val pollings = for (_ <- 1 to allowance) yield {
      val id = newId
      poller ! ('poll, id)
      id
    }
    state.copy(pollings = state.pollings ++ pollings)
  }
  def behavior(state: ManagerState): Receive = {
    case 'start => {
      val afterPoll = pollIfAllowable(state)
      context.become(behavior(afterPoll.copy(active = true)))
    }
    case 'stop => {
      context.become(behavior(state.copy(active = false)))
    }
    case ('setLimit, newLimit: Int) => {
      context.become(behavior(
        pollIfActive(state.copy(limit = newLimit))
      ))
    }
    case ('polled, in) => {
      val pollings = pollIfActive(state).pollings - in
      val w = context.actorOf(workerProps)
      context.watch(w)
      w ! ('exec, in)
      context.become(behavior(state.copy(
        pollings = pollings,
        workings = state.workings + (w -> (in, false))
      )))
    }
    case ('finished, out) => {
      state.workings.get(sender) match {
        case Some((in, false)) => {
          println("task finished : " + out)
          context.become(behavior(state.copy(
            workings = state.workings + (sender -> (in, true))
          )))
        }
        case Some((in, true)) => {
          System.err.println("finished two times: " + sender + " , " + in + " , " + out)
          context.become(behavior(state))
        }
        case None => {
          System.err.println("not started but finished: " + sender + " , " + out)
          context.become(behavior(state))
        }
      }
    }
    case Terminated(w) => {
      state.workings.get(w) match {
        case Some((in, true)) => {
          val afterTreminated = state.copy(workings = state.workings - w)
          val afterNewPoll = pollIfActive(afterTreminated)
          if (afterNewPoll.pollings.isEmpty && afterNewPoll.workings.isEmpty) {
            client ! 'allFinished
          }
          context.become(behavior(afterNewPoll))
        }
        case Some((in, false)) => {
          System.err.println("not finished but terminated: " + w + " , " + in)
          val afterTreminated = state.copy(workings = state.workings - w)
          val afterNewPoll = pollIfActive(afterTreminated)
          if (afterNewPoll.pollings.isEmpty && afterNewPoll.workings.isEmpty) {
            client ! 'allFinished
          }
          context.become(behavior(afterNewPoll))
        }
        case None => {
          System.err.println("not started but terminated: " + w)
          context.become(behavior(state))
        }
      }
    }
  }
  def receive = behavior(ManagerState(
    active = false,
    limit = defaultLimit,
    pollings = Set.empty,
    workings = Map.empty
  ))
  def newId = java.util.UUID.randomUUID.toString
}
class WorkerActor(action: Any => Any) extends Actor {
  def receive = {
    case ('exec, x) => {
      sender ! ('finished, action(x))
      context.stop(self)
    }
  }
}
class PollerActor(poll: Any => Any) extends Actor {
  def receive = {
    case ('poll, x) => {
      sender ! ('polled, poll(x))
    }
  }
}

package jp.rf.swfsample.main.sample2

import scala.concurrent.Await
import scala.concurrent.duration._

import akka.actor.{Actor, ActorRef, ActorSystem, Props, Terminated}
import akka.pattern.ask
import akka.util.Timeout

object Main {
  def main(args: Array[String]) {
    implicit val timeout = Timeout(FiniteDuration(5, SECONDS))
    val system = ActorSystem("child-sample-2")
    system.actorOf(Props(new Actor {
      val main = context.actorOf(Props(new MainActor(self, 2)))
      def receive = behavior(false)
      def behavior(finished: Boolean): Receive = {
        case 'execute => {
          main ! 'start
          println("start")
          Thread.sleep(1000)
          main ! ('setLimit, 4)
          Thread.sleep(2000)
          main ! 'stop
          println("stop")
          context.become(behavior(true))
        }
        case 'allFinished => {
          println("all finished.")
          if (finished) {
            system.shutdown()
          }
          context.become(behavior(true))
        }
      }
    })) ! 'execute
  } 
} 
class MainActor(client: ActorRef, defaultLimit: Int)(implicit timeout: Timeout) extends Actor {
  implicit val ec = context.dispatcher
  val poller = context.actorOf(Props(new PollerActor(poll)), name = "poller")
  val workers = context.actorOf(Props(new WorkerManager(self, action, defaultLimit)), name = "workers")
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
  def receive = behavior(false, defaultLimit, Set.empty, Set.empty, Set.empty)
  def behavior(
    isActive: Boolean,
    limit: Int,
    pollings: Set[String],
    waitings: Set[String],
    workings: Set[String]
  ): Receive = {
    case 'start => {
      val newPollings = if (pollings.size + waitings.size + workings.size < limit) {
        val id = newId
        poller ! ('poll, id)
        pollings + id
      } else {
        pollings
      }
      println(newPollings, waitings, workings)
      context.become(behavior(true, limit, newPollings, waitings, workings))
    }
    case 'stop => {
      context.become(behavior(false, limit, pollings, waitings, workings))
    }
    case ('setLimit, newLimit: Int) => {
      workers ! ('setLimit, newLimit)
      context.become(behavior(isActive, newLimit, pollings, waitings, workings))
    }
    case ('polled, x: String) => {
      workers ! ('add, x)
      val newPollings = if (isActive && pollings.size + waitings.size + workings.size < limit) {
        val id = newId
        poller ! ('poll, id)
        pollings - x + id
      } else {
        pollings - x
      }
      println(newPollings, waitings + x, workings)
      context.become(behavior(isActive, limit, newPollings, waitings + x, workings))
    }
    case ('started, x: String) => {
      println("action started: " + x)
      println(pollings, waitings - x, workings + x)
      context.become(behavior(isActive, limit, pollings, waitings - x, workings + x))
    }
    case ('finished, x: String) => {
      println("task finished : " + x)
      val newWorlings = workings - x
      val newPollings = if (isActive && pollings.size + waitings.size + workings.size < limit) {
        val id = newId
        poller ! ('poll, id)
        pollings + id
      } else {
        pollings
      }
      if (newPollings.isEmpty && waitings.isEmpty && newWorlings.isEmpty) {
        client ! 'allFinished
      }
      println(newPollings, waitings, newWorlings)
      context.become(behavior(isActive, limit, newPollings, waitings, newWorlings))
    }
  }
  def newId = java.util.UUID.randomUUID.toString
}
class WorkerManager(client: ActorRef, action: Any => Any, defaultLimit: Int) extends Actor {
  import scala.collection.SortedMap
  import scala.collection.immutable.Queue
  def receive = behavior(defaultLimit, Queue.empty)
  def behavior(
    limit: Int,
    queue: Queue[(ActorRef, Any)]
  ): Receive = {
    case ('add, x) => {
      if (workers.size < limit) {
        val worker = context.actorOf(Props(new WorkerActor(action)))
        context.watch(worker)
        worker ! ('exec, x)
        client ! ('started, x)
        context.become(behavior(limit, queue))
      } else {
        context.become(behavior(limit, queue.enqueue(client, x)))
      }
    }
    case ('setLimit, limit: Int) => {
      context.become(behavior(limit, queue))
    }
    case ('finished, x) => {
      client ! ('finished, x)
      context.become(behavior(limit, queue))
    }
    case Terminated(worker) => {
      if (queue.isEmpty) {
        context.become(behavior(limit, queue))
      } else {
        val ((client, x), remains) = queue.dequeue
        self ! ('add, x)
        context.become(behavior(limit, remains))
      }
    }
  }
  def workers = context.children.to[Set]
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

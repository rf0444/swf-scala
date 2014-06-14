package jp.rf.swfsample.main.sample1

import scala.concurrent.Await
import scala.concurrent.duration._

import akka.actor.{Actor, ActorContext, ActorRef, ActorSystem, PoisonPill, Props, Terminated}
import akka.pattern.{ask, gracefulStop}

object Main {
  def main(args: Array[String]) {
    val timeout = FiniteDuration(5, SECONDS)
    val system = ActorSystem("child-sample")
    val actor = system.actorOf(Props(new ManagerActor(4)))
    for (i <- 1 to 10) {
      actor ! "hoge" + i
    } 
    Thread.sleep(2000)
    Await.ready(gracefulStop(actor, timeout)(system), timeout)
    system.shutdown()
  }
}
class ManagerActor(val limit: Int) extends Actor {
  val pool = context.actorOf(Props(new PoolActor))
  def workers = context.children.to[Set] - pool
  def receive = {
    case 'workers => {
      sender ! workers
    }
    case Terminated(a) => {
      pool ! 'finished
    }
    case x => {
      if (workers.size < limit) {
        val child = context.actorOf(Props(new WorkerActor))
        context.watch(child)
        child ! x
      } else {
        pool ! x
      }
    }
  }
}
class PoolActor extends Actor {
  import scala.collection.immutable.Queue
  def receive = behavior(Queue.empty)
  def behavior(queue: Queue[Any]): Receive = {
    case 'get => {
      sender ! queue
      context.become(behavior(queue))
    }
    case 'finished => {
      if (queue.isEmpty) {
        context.become(behavior(queue))
      } else {
        val (first, remains) = queue.dequeue
        sender ! first
        context.become(behavior(remains))
      }
    }
    case x => {
      context.become(behavior(queue.enqueue(x)))
    }
  }
}
class WorkerActor extends Actor {
  def receive = {
    case x => {
      println("start: " + x)
      Thread.sleep(500)
      println("finish: " + x)
      context.stop(self)
    }
  }
}

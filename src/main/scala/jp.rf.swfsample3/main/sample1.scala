package jp.rf.swfsample3.main.sample1

import scala.concurrent.Await
import scala.concurrent.duration._

import akka.actor.{Actor, ActorRef, ActorRefFactory, ActorSystem, Props}
import akka.pattern.ask
import akka.util.Timeout

import jp.rf.swfsample3.Activities

object Main {
  def main(args: Array[String]) {
    implicit val timeout = Timeout(FiniteDuration(5, SECONDS))
    val system = ActorSystem("child-sample-3")
    system.actorOf(Props(new Actor {
      val activities = Activities.createActor(
        name = "activities",
        pollerProps = Props(new Poller),
        executorProps = Props(new Executor),
        client = self,
        defaultLimit = 2
      )
      def receive = {
        case 'execute => {
          activities ! 'start
          println("start")
          Thread.sleep(4000)
          activities ! ('setLimit, 4)
          println("set limit 4")
          Thread.sleep(4000)
          activities ! 'stop
          println("stop")
          context.stop(self)
        }
      }
    }), name = "main") ! 'execute
    Thread.sleep(10000)
    system.shutdown()
  }
}

class Poller extends Actor {
  def receive = {
    case pollingId: String => {
      println(pollingId + " : 1. polling start")
      Thread.sleep(100)
      println(pollingId + " : 2. polling finish")
      sender ! ('polled, pollingId, Some(pollingId))
    }
    case others => sender ! ('invalid, others)
  }
}

class Executor extends Actor {
  def receive = {
    case (pollingId: String, task: String) => {
      println(task + " : 3. execution start")
      Thread.sleep(1500)
      println(task + " : 4. execution finish")
      sender ! ('finished, pollingId, task)
      context.stop(self)
    }
    case others => {
      sender ! ('invalid, others)
      context.stop(self)
    }
  }
}

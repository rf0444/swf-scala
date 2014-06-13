package jp.rf.swfsample.main

import scala.concurrent.Await
import scala.concurrent.duration._

import akka.actor.{Actor, ActorContext, ActorRef, ActorSystem, PoisonPill, Props, Terminated}
import akka.pattern.{ask, gracefulStop}
import akka.util.Timeout

object ChildSample {
  def main(args: Array[String]) {
    val timeout = FiniteDuration(5, SECONDS)
    val system = ActorSystem("child-sample")
    val actor = system.actorOf(Props(new Actor {
      def behavior(children: Set[ActorRef]): Receive = {
        case 'get => {
          sender ! children
          context.become(behavior(children))
        }
        case Terminated(a) => {
          println("terminated: " + a)
          context.become(behavior(children - a))
        }
        case x => {
          val child = context.actorOf(Props(new Actor {
            def receive = {
              case x => {
                println("start")
                Thread.sleep(5000)
                println(x)
                context stop self
              }
            }
          }))
          context.watch(child)
          child ! x
          context.become(behavior(children + child))
        }
      }
      def receive: Receive = behavior(Set.empty)
    }))
    println(Await.result(ask(actor, 'get)(Timeout(timeout)), timeout))
    actor ! "hoge"
    Thread.sleep(10)
    println(Await.result(ask(actor, 'get)(Timeout(timeout)), timeout))
    Thread.sleep(2000)
    actor ! "hoge"
    Thread.sleep(10)
    println(Await.result(ask(actor, 'get)(Timeout(timeout)), timeout))
    Thread.sleep(3000)
    println(Await.result(ask(actor, 'get)(Timeout(timeout)), timeout))
    Thread.sleep(2000)
    println(Await.result(ask(actor, 'get)(Timeout(timeout)), timeout))
    Await.ready(gracefulStop(actor, timeout)(system), timeout)
    system.shutdown()
  }
}

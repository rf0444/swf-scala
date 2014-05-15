package jp.rf.swfsample.main

import scala.concurrent.Await
import scala.concurrent.duration._

import akka.actor.ActorSystem
import akka.pattern.ask
import akka.util.Timeout

import jp.rf.swfsample.actor.swf.{ActivityActor, ActivityActorConf}
import jp.rf.swfsample.actor.swf.ActivityActor.{Start, Stop, GetState}

object FSMSample {
  case class Task(name: String)
  
  def main(args: Array[String]) {
    implicit val system = ActorSystem()
    val activity = ActivityActor.create(new ActivityActorConf[Task] {
      override def poll = {
         println("polling")
         Thread.sleep(500)
         println("polled")
         Some(Task("hoge"))
      }
      override def execute(task: Task) = {
        println("working - " + task.name)
        Thread.sleep(500)
        println("done")
      }
    })
    implicit val timeout = Timeout(FiniteDuration(5, SECONDS))
    activity ! Start
    Thread.sleep(1000)
    println(Await.result(activity ? GetState, timeout.duration))
    Thread.sleep(1500)
    println(Await.result(activity ? GetState, timeout.duration))
    Thread.sleep(1500)
    println(Await.result(activity ? GetState, timeout.duration))
    activity ! Stop
    println(Await.result(activity ? GetState, timeout.duration))
    Thread.sleep(1000)
    println(Await.result(activity ? GetState, timeout.duration))
    activity ! Start
    Thread.sleep(1000)
    println(Await.result(activity ? GetState, timeout.duration))
    Thread.sleep(1400)
    println(Await.result(activity ? GetState, timeout.duration))
    activity ! Stop
    println(Await.result(activity ? GetState, timeout.duration))
    Thread.sleep(1500)
    println(Await.result(activity ? GetState, timeout.duration))
    system.shutdown
  }
}

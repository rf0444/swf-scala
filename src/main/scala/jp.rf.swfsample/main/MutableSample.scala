package jp.rf.swfsample.main

import akka.actor.{Actor, ActorSystem}

import jp.rf.swfsample.actor.{MutableActor, MutableActorConf}

object MutableSample {
  sealed trait Action
  case class Add(str: String) extends Action
  case class Modify(f: List[String] => List[String]) extends Action
  case object Print extends Action
  
  def main(args: Array[String]) {
    implicit val system = ActorSystem()
    val a = MutableActor.create(new MutableActorConf[List[String], Action] {
      override val initialValue = Nil
      override def action(xs: List[String], e: Action, act: Actor) = e match {
        case Add(str) => {
          str :: xs
        }
        case Modify(f) => {
          f(xs)
        }
        case Print => {
          println(xs)
          xs
        }
      }
    })
    a ! Print
    a ! Add("test")
    a ! Print
    a ! Add("hoge")
    a ! Print
    a ! Modify(xs => xs ++ xs)
    a ! Print
    Thread.sleep(500)
    system.shutdown
  }
}

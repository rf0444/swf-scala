package jp.rf.swfsample.actor.manager

import akka.actor.{ActorRef, ActorRefFactory}
import akka.util.Timeout
import com.amazonaws.services.simpleworkflow._
import com.amazonaws.services.simpleworkflow.model._

import jp.rf.swfsample.data.{WorkerInput, WorkerOutput}
import jp.rf.swfsample.actor.swf.{Active, Inactive, Start, Stop, State => ActorState}
import jp.rf.swfsample.actor.swf.{ActivityCompleted, ActivityFailed, ActivityResult, Worker}

object WorkerManager {
  def create(
    name: String,
    swf: AmazonSimpleWorkflowClient,
    domainName: String,
    taskList: String,
    initialActorNum: Int = 1
  )(implicit factory: ActorRefFactory, timeout: Timeout): ActorRef = {
    val nm = name
    SwfActorManager.create(new SwfActorManagerConf[WorkerInput, WorkerOutput] {
      override val initialNum = initialActorNum
      override val name = nm
      override def createActor = Worker.create(swf, domainName, taskList, action)
      override def createActor(input: WorkerInput) = {
        val actor = createActor
        if (input.active) {
          actor ! Start
        }
        actor
      }
      override def modifyActor(input: WorkerInput, actor: ActorRef) {
        actor ! (if (input.active) Start else Stop)
      }
      override def info(actor: ActorRef, state: ActorState) = {
        val active = state match {
          case Inactive => false
          case Active(_, cont) => cont
        }
        val status = state match {
          case Inactive => state.toString
          case Active(st, _) => st.toString
        }
        WorkerOutput(SwfActorManager.actorIdOf(actor), active, status)
      }
    })
  }
  
  def action(task: ActivityTask): ActivityResult = {
    val input = task.getInput
    task.getActivityType.getName match {
      case "start-ec2-instance-request" => {
        val result = "start: " + input
        println(result)
        ActivityCompleted(input)
      }
      case "start-ec2-instance-check" => {
        val result = "check started: " + input
        println(result)
        ActivityCompleted(input)
      }
      case "stop-ec2-instance-request" => {
        val result = "stop: " + input
        println(result)
        ActivityCompleted(input)
      }
      case "stop-ec2-instance-check" => {
        val result = "check stopped: " + input
        println(result)
        ActivityCompleted(input)
      }
      case invalidActivityName => {
        println("invalid activity: " + invalidActivityName)
        ActivityFailed(details = invalidActivityName, reason = "invalid activity")
      }
    }
  }
}

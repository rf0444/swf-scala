package jp.rf.swfsample.actor.manager

import akka.actor.{ActorRef, ActorRefFactory}
import akka.util.Timeout
import com.amazonaws.services.simpleworkflow._
import com.amazonaws.services.simpleworkflow.model._

import jp.rf.swfsample.data.{DeciderInput, DeciderOutput}
import jp.rf.swfsample.actor.swf.{Active, Inactive, Start, Stop, State => ActorState}
import jp.rf.swfsample.actor.swf.Decider

object DeciderManager {
  def create(
    name: String,
    swf: AmazonSimpleWorkflowClient,
    domainName: String,
    version: String,
    taskList: String,
    initialActorNum: Int = 1
  )(implicit factory: ActorRefFactory, timeout: Timeout): ActorRef = {
    val nm = name
    SwfActorManager.create(new SwfActorManagerConf[DeciderInput, DeciderOutput] {
      override val initialNum = initialActorNum
      override val name = nm
      override def createActor = Decider.create(swf, domainName, taskList, decide(version))
      override def createActor(input: DeciderInput) = {
        val actor = createActor
        if (input.active) {
          actor ! Start
        }
        actor
      }
      override def modifyActor(input: DeciderInput, actor: ActorRef) {
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
        DeciderOutput(SwfActorManager.actorIdOf(actor), active, status)
      }
    })
  }
  
  val failEvents = scala.collection.Set("ScheduleActivityTaskFailed", "ActivityTaskFailed", "DecisionTaskTimedOut")
  val cancelEvents = scala.collection.Set("ActivityTaskCancelRequested", "ActivityTaskCanceled")
  def decide(version: String)(task: DecisionTask): Decision = {
    import scala.collection.JavaConverters._
    val events = task.getEvents.asScala
    if (events.exists(event => failEvents.contains(event.getEventType))) {
      return new Decision()
        .withDecisionType(DecisionType.FailWorkflowExecution)
    }
    if (events.exists(event => cancelEvents.contains(event.getEventType))) {
      return new Decision()
        .withDecisionType(DecisionType.CancelWorkflowExecution)
    }
    if (events.exists(_.getEventType == "ActivityTaskCompleted")) {
      return new Decision()
        .withDecisionType(DecisionType.CompleteWorkflowExecution)
    }
    val input = events
      .filter(_.getEventType == "WorkflowExecutionStarted").take(1)
      .map(_.getWorkflowExecutionStartedEventAttributes.getInput)
      .mkString("\n")
    val timestamp = System.currentTimeMillis.toString
    val activityType = new ActivityType()
      .withName("start-ec2-instance-request")
      .withVersion(version)
    new Decision()
      .withDecisionType(DecisionType.ScheduleActivityTask)
      .withScheduleActivityTaskDecisionAttributes(new ScheduleActivityTaskDecisionAttributes()
        .withActivityId(timestamp)
        .withActivityType(activityType)
        .withInput(input)
      )
  }
}

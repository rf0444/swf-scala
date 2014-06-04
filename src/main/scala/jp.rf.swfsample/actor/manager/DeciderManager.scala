package jp.rf.swfsample.actor.manager

import akka.actor.{ActorRef, ActorRefFactory}
import akka.util.Timeout
import com.amazonaws.services.simpleworkflow._
import com.amazonaws.services.simpleworkflow.model._

import jp.rf.swfsample.data.{DeciderInput, DeciderOutput}
import jp.rf.swfsample.actor.swf.{Active, Inactive, Start, Stop, State => ActorState}
import jp.rf.swfsample.actor.swf.Decider

sealed trait NextActivity
case object NoNextActivity extends NextActivity
case object NextActivityFailed extends NextActivity
case class HasNextActivity(activity: ActivityType, input: String) extends NextActivity

object DeciderManager {
  def create(
    name: String,
    swf: AmazonSimpleWorkflowClient,
    domainName: String,
    taskList: String,
    initialActorNum: Int = 1
  )(implicit factory: ActorRefFactory, timeout: Timeout): ActorRef = {
    val nm = name
    SwfActorManager.create(new SwfActorManagerConf[DeciderInput, DeciderOutput] {
      override val initialNum = initialActorNum
      override val name = nm
      override def createActor = Decider.create(swf, domainName, taskList, decide)
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
  
  val failEvents = scala.collection.Set(
    "ActivityTaskFailed",
    "ActivityTaskTimedOut",
    "DecisionTaskTimedOut",
    "ScheduleActivityTaskFailed"
  )
  val cancelEvents = scala.collection.Set(
    "ActivityTaskCancelRequested",
    "ActivityTaskCanceled",
    "WorkflowExecutionCancelRequested"
  )
  def decide(task: DecisionTask): Decision = try {
    import scala.collection.JavaConverters._
    if (task.getNextPageToken != null) {
      // too long execution is not supported
      return new Decision()
        .withDecisionType(DecisionType.FailWorkflowExecution)
    }
    val events = task.getEvents.asScala
    if (events.exists(event => failEvents.contains(event.getEventType))) {
      return new Decision()
        .withDecisionType(DecisionType.FailWorkflowExecution)
    }
    if (events.exists(event => cancelEvents.contains(event.getEventType))) {
      return new Decision()
        .withDecisionType(DecisionType.CancelWorkflowExecution)
    }
    val eventMap = events.map(e => (Long.unbox(e.getEventId), e)).toMap
    val nextActivity = lastActivityOf(events).map(nextActivityForCompleteActivity(_, eventMap))
    nextActivity.orElse(
      events.find(_.getEventType == "WorkflowExecutionStarted").map(firstActivityForExecutionStarted)
    ) match {
      case Some(NoNextActivity) => new Decision()
        .withDecisionType(DecisionType.CompleteWorkflowExecution)
      case Some(NextActivityFailed) => new Decision()
        .withDecisionType(DecisionType.FailWorkflowExecution)
      case Some(HasNextActivity(activity, input)) => {
        val activityId = java.util.UUID.randomUUID.toString
        new Decision()
          .withDecisionType(DecisionType.ScheduleActivityTask)
          .withScheduleActivityTaskDecisionAttributes(new ScheduleActivityTaskDecisionAttributes()
            .withActivityId(activityId)
            .withActivityType(activity)
            .withInput(input)
          )
      } 
      case None => new Decision()
        .withDecisionType(DecisionType.FailWorkflowExecution)
    }
  } catch {
    case e: Exception => {
      e.printStackTrace
      new Decision().withDecisionType(DecisionType.FailWorkflowExecution)
    }
  }
  
  def lastActivityOf(events: Seq[HistoryEvent]): Option[HistoryEvent] = {
    events.filter(_.getEventType == "ActivityTaskCompleted").lastOption
  }
  
  def nextActivityForCompleteActivity(event: HistoryEvent, eventMap: Map[Long, HistoryEvent]): NextActivity = {
    val attr = event.getActivityTaskCompletedEventAttributes
    if (attr == null) {
      return NextActivityFailed
    }
    eventMap.get(attr.getScheduledEventId).map { scheduleEvent =>
      val scheduleAttr = scheduleEvent.getActivityTaskScheduledEventAttributes
      if (scheduleAttr == null) {
        return NextActivityFailed
      }
      val activity = scheduleAttr.getActivityType
      nextActivityFor(activity, attr.getResult)
    }.getOrElse(NextActivityFailed)
  }
  def nextActivityFor(activity: ActivityType, result: String): NextActivity = activity.getName match {
    case "start-ec2-instance-request" => HasNextActivity(
      new ActivityType()
        .withName("start-ec2-instance-check")
        .withVersion(activity.getVersion),
      result
    )
    case "start-ec2-instance-check" => NoNextActivity
    case "stop-ec2-instance-request" => HasNextActivity(
      new ActivityType()
        .withName("start-ec2-instance-check")
        .withVersion(activity.getVersion),
      result
    )
    case "stop-ec2-instance-check" => NoNextActivity
    case _ => NextActivityFailed
  }
  def firstActivityForExecutionStarted(event: HistoryEvent): NextActivity = {
    val attr = event.getWorkflowExecutionStartedEventAttributes
    if (attr == null) {
      return NextActivityFailed
    }
    val workflow = attr.getWorkflowType
    firstActivityFor(workflow, attr.getInput)
  }
  def firstActivityFor(workflow: WorkflowType, input: String): NextActivity = workflow.getName match {
    case "start-ec2-instance" => HasNextActivity(
      new ActivityType()
        .withName("start-ec2-instance-request")
        .withVersion(workflow.getVersion),
      input
    )
    case "stop-ec2-instance" => HasNextActivity(
      new ActivityType()
        .withName("stop-ec2-instance-request")
        .withVersion(workflow.getVersion),
      input
    )
    case _ => NextActivityFailed
  }
}

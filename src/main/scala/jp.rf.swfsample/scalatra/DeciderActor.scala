package jp.rf.swfsample.scalatra

import akka.actor.ActorDSL.{Act, actor => act}
import akka.actor.ActorRefFactory
import com.amazonaws.services.simpleworkflow._
import com.amazonaws.services.simpleworkflow.model._

object DeciderActor {
  def create(
    factory: ActorRefFactory,
    swf: AmazonSimpleWorkflowClient,
    domainName: String,
    workflowType: WorkflowType,
    activityType: ActivityType
  ) = {
    val workflow = swf.describeWorkflowType(new DescribeWorkflowTypeRequest()
      .withDomain(domainName)
      .withWorkflowType(workflowType)
    )
    val workflowTaskListName = workflow.getConfiguration.getDefaultTaskList.getName
    new DeciderActor(factory, swf, domainName, workflowTaskListName, activityType)
  }
}

class DeciderActor(
  val factory: ActorRefFactory,
  val swf: AmazonSimpleWorkflowClient,
  val domainName: String,
  val workflowTaskListName: String,
  val activityType: ActivityType
) {
  @volatile private[this] var _isActive = false
  @volatile private[this] var _status: ActorStatus = Inactive
  def status = (_isActive, _status)
  val actor = act(factory)(new Act {
    val name = self.path.name
    become {
      case 'execute => {
        if (_isActive) {
          val result = execute()
          println(result)
          self ! 'execute
        }
      }
      case 'start => {
        if (!_isActive) {
          _isActive = true
          _status = Waiting
          self ! 'execute
        }
      }
      case 'stop => {
        _isActive = false
        _status = Inactive
      }
    }
  })
  private def execute(): (DecisionTask, Option[Decision]) = {
    _status = Polling
    val task = swf.pollForDecisionTask(new PollForDecisionTaskRequest()
      .withDomain(domainName)
      .withTaskList(new TaskList()
        .withName(workflowTaskListName)
      )
    )
    if (task.getTaskToken == null) {
      _status = Waiting
      return (task, None)
    }
    _status = Working
    val decision = decide(task)
    swf.respondDecisionTaskCompleted(new RespondDecisionTaskCompletedRequest()
      .withTaskToken(task.getTaskToken)
      .withDecisions(decision)
    )
    _status = Waiting
    (task, Some(decision))
  }
  def decide(decisionTask: DecisionTask): Decision = {
    import scala.collection.JavaConverters._
    
    val events = decisionTask.getEvents.asScala
    if (events.exists(_.getEventType == "ActivityTaskCompleted")) {
      return new Decision()
        .withDecisionType(DecisionType.CompleteWorkflowExecution)
    }
    val input = events
      .filter(_.getEventType == "WorkflowExecutionStarted").take(1)
      .map(_.getWorkflowExecutionStartedEventAttributes.getInput)
      .mkString("\n")
    val timestamp = System.currentTimeMillis.toString
    new Decision()
      .withDecisionType(DecisionType.ScheduleActivityTask)
      .withScheduleActivityTaskDecisionAttributes(new ScheduleActivityTaskDecisionAttributes()
        .withActivityId(timestamp)
        .withActivityType(activityType)
        .withInput(input)
      )
  }
  val id = actor.path.name
}


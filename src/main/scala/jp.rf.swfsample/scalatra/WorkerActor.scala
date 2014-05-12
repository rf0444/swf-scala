package jp.rf.swfsample.scalatra

import akka.actor.ActorDSL.{Act, actor => act}
import akka.actor.ActorRefFactory
import com.amazonaws.services.simpleworkflow._
import com.amazonaws.services.simpleworkflow.model._

object WorkerActor {
  def create(factory: ActorRefFactory, swf: AmazonSimpleWorkflowClient, domainName: String, activityType: ActivityType) = {
    val activity = swf.describeActivityType(new DescribeActivityTypeRequest()
      .withDomain(domainName)
      .withActivityType(activityType)
    )
    val activityTaskListName = activity.getConfiguration.getDefaultTaskList.getName
    new WorkerActor(factory, swf, domainName, activityTaskListName)
  }
}

class WorkerActor(
  val factory: ActorRefFactory,
  val swf: AmazonSimpleWorkflowClient,
  val domainName: String,
  val activityTaskListName: String
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
  private def execute(): (ActivityTask, Option[String]) = {
    _status = Polling
    val task = swf.pollForActivityTask(new PollForActivityTaskRequest()
      .withDomain(domainName)
      .withTaskList(new TaskList()
        .withName(activityTaskListName)
      )
    )
    if (task.getTaskToken == null) {
      _status = Waiting
      return (task, None)
    }
    _status = Working
    val result = action(task.getInput)
    swf.respondActivityTaskCompleted(new RespondActivityTaskCompletedRequest()
      .withTaskToken(task.getTaskToken)
      .withResult(result)
    )
    _status = Waiting
    (task, Some(result))
  }
  def action(input: String): String = {
    println(input)
    val result = "printed: " + input
    result
  }
  val id = actor.path.name
}

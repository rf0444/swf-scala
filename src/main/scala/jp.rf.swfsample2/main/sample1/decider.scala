package jp.rf.swfsample2.main.sample1.decider

import akka.actor.{Actor, ActorRef, ActorRefFactory, Props, Terminated}
import com.amazonaws.services.simpleworkflow.AmazonSimpleWorkflow
import com.amazonaws.services.simpleworkflow.model._

import jp.rf.swfsample.util.uuid

object Activities {
  def createActor(
    name: String,
    swf: AmazonSimpleWorkflow,
    decide: DecisionTask => Decision,
    client: ActorRef,
    defaultLimit: Int,
    defaultDomainName: String,
    defaultTaskListName: String
  )(implicit factory: ActorRefFactory): ActorRef = {
    factory.actorOf(
      Props(new Activities(
        swf, decide, client, defaultLimit, defaultDomainName, defaultTaskListName
      )),
      name = name
    )
  }
}
class Activities(
  swf: AmazonSimpleWorkflow,
  decide: DecisionTask => Decision,
  client: ActorRef,
  defaultLimit: Int,
  defaultDomainName: String,
  defaultTaskListName: String
) extends Actor {
  val poller = Poller.createActor("poller", swf, defaultDomainName, defaultTaskListName)
  def pollIfActive(state: ActivityState): ActivityState = {
    if (state.active) pollIfAllowable(state) else state
  }
  def pollIfAllowable(state: ActivityState): ActivityState = {
    val allowance = state.limit - state.pollings.size - state.executings.size
    if (allowance <= 0) {
      return state
    }
    val pollings = for (_ <- 1 to allowance) yield {
      val id = uuid
      poller ! id
      id
    }
    state.copy(pollings = state.pollings ++ pollings)
  }
  def behavior(state: ActivityState): Receive = {
    case 'start => {
      val afterPoll = pollIfAllowable(state)
      context.become(behavior(afterPoll.copy(active = true)))
    }
    case 'stop => {
      context.become(behavior(state.copy(active = false)))
    }
    case 'get => {
			sender ! state
      context.become(behavior(state))
    }
    case ('setLimit, newLimit: Int) => {
      context.become(behavior(
        pollIfActive(state.copy(limit = newLimit))
      ))
    }
    case (pollingId: String, None) => {
      if (!state.pollings.contains(pollingId)) {
      }
      val afterPolled = state.copy(pollings = state.pollings - pollingId)
      val afterNewPoll = pollIfActive(afterPolled)
      context.become(behavior(afterNewPoll))
    }
    case (pollingId: String, Some(task: DecisionTask)) => {
      if (!state.pollings.contains(pollingId)) {
      }
      val afterPolled = state.copy(pollings = state.pollings - pollingId)
      val executor = Executor.createActor(pollingId, swf, decide)
      context.watch(executor)
      executor ! (pollingId, task)
      val afterExecute = afterPolled.copy(executings = afterPolled.executings + (pollingId -> task))
      val afterNewPoll = pollIfActive(afterExecute)
      context.become(behavior(afterNewPoll))
    }
    case (pollingId: String, decision: Decision) => {
      state.executings.get(pollingId) match {
        case Some(task) => {
          val afterExecuted = state.copy(executings = state.executings - pollingId)
          context.become(behavior(afterExecuted))
        }
        case None => {
          System.err.println("not started but finished: " + pollingId + " , " + decision)
          context.become(behavior(state))
        }
      }
    }
    case Terminated(actor) => {
      val afterNewPoll = pollIfActive(state)
      context.become(behavior(afterNewPoll))
    }
    case ('invalid, input) => {
      System.err.println("invalid input for sender: " + sender + " , " + input)
      context.become(behavior(state))
    }
  }
  def receive = behavior(ActivityState(
    active = false,
    limit = defaultLimit,
    pollings = Set.empty,
    executings = Map.empty
  ))
}
case class ActivityState(
  active: Boolean,
  limit: Int,
  pollings: Set[String],
  executings: Map[String, DecisionTask]
)

object Poller {
  def createActor(
    name: String,
    swf: AmazonSimpleWorkflow,
    defaultDomainName: String,
    defaultTaskListName: String
  )(implicit factory: ActorRefFactory): ActorRef = {
    factory.actorOf(Props(new Poller(swf, defaultDomainName, defaultTaskListName)), name = name)
  }
}
class Poller(
  swf: AmazonSimpleWorkflow,
  defaultDomainName: String,
  defaultTaskListName: String
) extends Actor {
  def receive = {
    case pollingId: String => {
      sender ! poll(defaultDomainName, defaultTaskListName, pollingId)
    }
    case (taskListName: String, pollingId: String) => {
      sender ! poll(defaultDomainName, taskListName, pollingId)
    }
    case (domainName: String, taskListName: String, pollingId: String) => {
      sender ! poll(domainName, taskListName, pollingId)
    }
    case others => sender ! ('invalid, others)
  }
  def poll(domainName: String, taskListName: String, pollingId: String): (String, Option[DecisionTask]) = {
    val id = self.path.toString + "/" + pollingId
    val task = swf.pollForDecisionTask(new PollForDecisionTaskRequest()
      .withDomain(domainName)
      .withTaskList(new TaskList()
        .withName(taskListName)
      )
      .withIdentity(id)
    )
    (pollingId, if (task.getTaskToken == null) None else Some(task))
  }
}

object Executor {
  def createActor(
    name: String,
    swf: AmazonSimpleWorkflow,
    decide: DecisionTask => Decision
  )(implicit factory: ActorRefFactory): ActorRef = {
    factory.actorOf(Props(new Executor(swf, decide)), name = name)
  }
}
class Executor(
  swf: AmazonSimpleWorkflow,
  decide: DecisionTask => Decision
) extends Actor {
  def receive = {
    case (pollingId: String, task: DecisionTask) => {
      sender ! (pollingId, execute(task))
      context.stop(self)
    }
    case others => {
      sender ! ('invalid, others)
      context.stop(self)
    }
  }
  def execute(task: DecisionTask): Decision = {
    val decision = decide(task)
    swf.respondDecisionTaskCompleted(new RespondDecisionTaskCompletedRequest()
      .withTaskToken(task.getTaskToken)
      .withDecisions(decision)
    )
    decision
  }
}

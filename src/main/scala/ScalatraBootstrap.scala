import javax.servlet.ServletContext
import scala.concurrent.duration._

import akka.actor.ActorSystem
import akka.util.Timeout
import com.amazonaws.services.simpleworkflow.AmazonSimpleWorkflowClient
import com.amazonaws.services.simpleworkflow.model._
import com.typesafe.config.ConfigFactory
import org.scalatra.LifeCycle

import jp.rf.swfsample.aws.ClientFactory
import jp.rf.swfsample.scalatra.{SwfActorPage, DeciderManager, TaskPage, WorkerManager}
import jp.rf.swfsample.scalatra.data.{DeciderInput, SetAll, WorkerInput}

class ScalatraBootstrap extends LifeCycle {
  val conf = ConfigFactory.load
  implicit val system = ActorSystem("sample")
  implicit val timeout = Timeout(FiniteDuration(5, SECONDS))
  val swf = ClientFactory.create(
    clientClass = classOf[AmazonSimpleWorkflowClient],
    accessKey = conf.getString("aws.accessKey"),
    secretKey = conf.getString("aws.secretKey"),
    regionName = conf.getString("aws.swf.regionName")
  )
  val domainName = conf.getString("aws.swf.domainName")
  val workflowType = new WorkflowType()
    .withName(conf.getString("aws.swf.workflowType.name"))
    .withVersion(conf.getString("aws.swf.workflowType.version"))
  val activityType = new ActivityType()
    .withName(conf.getString("aws.swf.activityType.name"))
    .withVersion(conf.getString("aws.swf.activityType.version"))
  val deciderManager = DeciderManager.create("decider-manager", swf, domainName, workflowType, activityType)
  val workerManager = WorkerManager.create("worker-manager", swf, domainName, activityType)
  object DeciderPage extends SwfActorPage[DeciderInput](deciderManager)
  object WorkerPage extends SwfActorPage[WorkerInput](workerManager)
  override def init(context: ServletContext) {
    context.mount(DeciderPage, "/deciders")
    context.mount(WorkerPage, "/workers")
    context.mount(new TaskPage(swf, domainName, workflowType), "/tasks")
  }
  override def destroy(context:ServletContext) {
    system.shutdown()
  }
  deciderManager ! SetAll(DeciderInput(true))
  workerManager ! SetAll(WorkerInput(true))
}

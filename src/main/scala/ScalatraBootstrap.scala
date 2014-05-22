import javax.servlet.ServletContext
import scala.concurrent.duration._

import akka.actor.ActorSystem
import akka.util.Timeout
import com.amazonaws.services.simpleworkflow.AmazonSimpleWorkflowClient
import com.amazonaws.services.simpleworkflow.model._
import com.typesafe.config.ConfigFactory
import org.scalatra.LifeCycle

import jp.rf.swfsample.aws.ClientFactory
import jp.rf.swfsample.scalatra.{ActivityPage, DecidersActor, WorkersActor}
import jp.rf.swfsample.scalatra.data.{DeciderInput, WorkerInput}

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
  val decidersActor = DecidersActor.create("decider-admin-actor", swf, domainName, workflowType, activityType)
  val workersActor = WorkersActor.create("worker-admin-actor", swf, domainName, activityType)
  object DecidersPage extends ActivityPage[DeciderInput](decidersActor)
  object WorkersPage extends ActivityPage[WorkerInput](workersActor)
  override def init(context: ServletContext) {
    context.mount(DecidersPage, "/deciders")
    context.mount(WorkersPage, "/workers")
  }
  override def destroy(context:ServletContext) {
    system.shutdown()
  }
}

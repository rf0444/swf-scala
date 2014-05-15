import javax.servlet.ServletContext
import scala.concurrent.duration._

import akka.actor.ActorSystem
import akka.util.Timeout
import com.amazonaws.services.simpleworkflow._
import com.amazonaws.services.simpleworkflow.model._
import org.scalatra.LifeCycle

import jp.rf.swfsample.{Config, SWFFactory}
import jp.rf.swfsample.scalatra.{ActivityPage, DecidersActor, WorkersActor}
import jp.rf.swfsample.scalatra.data.{DeciderInput, WorkerInput}

class ScalatraBootstrap extends LifeCycle {
  implicit val system = ActorSystem("sample")
  implicit val timeout = Timeout(FiniteDuration(5, SECONDS))
  val swf = SWFFactory.create(Config.accessKey, Config.secretKey, Config.regionName)
  val domainName = Config.domainName
  val workflowType = new WorkflowType()
    .withName(Config.workflowType.name)
    .withVersion(Config.workflowType.version)
  val activityType = new ActivityType()
    .withName(Config.activityType.name)
    .withVersion(Config.activityType.version)
  val decidersActor = DecidersActor.create(swf, domainName, workflowType, activityType)
  val workersActor = WorkersActor.create(swf, domainName, activityType)
  override def init(context: ServletContext) {
    context.mount(new ActivityPage[DeciderInput](decidersActor), "/deciders")
    context.mount(new ActivityPage[WorkerInput](workersActor), "/workers")
  }
  override def destroy(context:ServletContext) {
    system.shutdown()
  }
}

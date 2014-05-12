import akka.actor.ActorSystem
import com.amazonaws.services.simpleworkflow._
import com.amazonaws.services.simpleworkflow.model._
import javax.servlet.ServletContext
import org.scalatra.LifeCycle

import jp.rf.swfsample.{Config, SWFFactory}
import jp.rf.swfsample.scalatra.{DeciderMainActor, DeciderPage, WorkerMainActor, WorkerPage}

class ScalatraBootstrap extends LifeCycle {
  val system = ActorSystem("sample")
  val swf = SWFFactory.create(Config.accessKey, Config.secretKey, Config.regionName)
  val domainName = Config.domainName
  val workflowType = new WorkflowType()
    .withName(Config.workflowType.name)
    .withVersion(Config.workflowType.version)
  val activityType = new ActivityType()
    .withName(Config.activityType.name)
    .withVersion(Config.activityType.version)
  val deciderMainActor = new DeciderMainActor(system, swf, domainName, workflowType, activityType)
  val workerMainActor = new WorkerMainActor(system, swf, domainName, activityType)
  override def init(context: ServletContext) {
    context.mount(new DeciderPage(deciderMainActor), "/deciders")
    context.mount(new WorkerPage(workerMainActor), "/workers")
  }
  override def destroy(context:ServletContext) {
    system.shutdown()
  }
}

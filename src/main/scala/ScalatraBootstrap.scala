import javax.servlet.ServletContext
import scala.concurrent.duration._

import akka.actor.ActorSystem
import akka.util.Timeout
import com.amazonaws.services.simpleworkflow.AmazonSimpleWorkflowClient
import com.amazonaws.services.simpleworkflow.model._
import com.typesafe.config.ConfigFactory
import org.scalatra.LifeCycle

import jp.rf.swfsample.actor.manager.{DeciderManager, WorkerManager}
import jp.rf.swfsample.actor.manager.SetAll
import jp.rf.swfsample.aws.ClientFactory
import jp.rf.swfsample.data.{DeciderInput, WorkerInput}
import jp.rf.swfsample.scalatra.{SwfActorPage, TaskPage}

class ScalatraBootstrap extends LifeCycle {
  val conf = ConfigFactory.load
  implicit val system = ActorSystem("operation-executor")
  implicit val timeout = Timeout(FiniteDuration(5, SECONDS))
  val swf = ClientFactory.create(
    clientClass = classOf[AmazonSimpleWorkflowClient],
    accessKey = conf.getString("aws.accessKey"),
    secretKey = conf.getString("aws.secretKey"),
    regionName = conf.getString("aws.swf.region")
  )
  val domainName = conf.getString("aws.swf.domain")
  val version = conf.getString("aws.swf.version")
  val deciderManager = DeciderManager.create(
    name = "decider-manager",
    swf = swf,
    domainName = domainName,
    version = version,
    taskList = conf.getString("aws.swf.taskList.decision"),
    initialActorNum = 2
  )
  val shortWorkerManager = WorkerManager.create(
    name = "short-worker-manager",
    swf = swf,
    domainName = domainName,
    version = version,
    taskList = conf.getString("aws.swf.taskList.activity.short"),
    initialActorNum = 2
  )
  val longWorkerManager = WorkerManager.create(
    name = "long-worker-manager",
    swf = swf,
    domainName = domainName,
    version = version,
    taskList = conf.getString("aws.swf.taskList.activity.long"),
    initialActorNum = 2
  )
  object DeciderPage extends SwfActorPage[DeciderInput](deciderManager)
  object ShortWorkerPage extends SwfActorPage[WorkerInput](shortWorkerManager)
  object LongWorkerPage extends SwfActorPage[WorkerInput](longWorkerManager)
  override def init(context: ServletContext) {
    context.mount(DeciderPage, "/deciders")
    context.mount(ShortWorkerPage, "/workers/short")
    context.mount(LongWorkerPage, "/workers/long")
    context.mount(new TaskPage(swf, domainName, version), "/tasks")
  }
  override def destroy(context:ServletContext) {
    system.shutdown()
  }
  deciderManager ! SetAll(DeciderInput(true))
  shortWorkerManager ! SetAll(WorkerInput(true))
  longWorkerManager ! SetAll(WorkerInput(true))
}

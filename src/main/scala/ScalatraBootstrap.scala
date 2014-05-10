import akka.actor.ActorSystem
import javax.servlet.ServletContext
import org.scalatra.LifeCycle

import jp.rf.swfsample.scalatra.{MainActor, WorkerPage}

class ScalatraBootstrap extends LifeCycle {
  val system = ActorSystem("sample")
  val mainActor = new MainActor(system)
  override def init(context: ServletContext) {
    context.mount(new WorkerPage(mainActor), "/workers")
  }
  override def destroy(context:ServletContext) {
    system.shutdown()
  }
}

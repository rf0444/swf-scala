import akka.actor.ActorSystem
import javax.servlet.ServletContext
import org.scalatra.LifeCycle

import jp.rf.swfsample.scalatra.{DeciderPage, MainActor, SamplePage, WorkerPage}

class ScalatraBootstrap extends LifeCycle {
  val system = ActorSystem("sample")
  val mainActor = new MainActor(system)
  override def init(context: ServletContext) {
    context.mount(new SamplePage(mainActor), "/sample")
    context.mount(new DeciderPage(mainActor), "/deciders")
    context.mount(new WorkerPage(mainActor), "/workers")
  }
  override def destroy(context:ServletContext) {
    system.shutdown()
  }
}

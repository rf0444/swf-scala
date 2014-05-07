import akka.actor.ActorDSL.{Act, actor}
import akka.actor.{ActorRef, ActorSystem}
import javax.servlet.ServletContext
import org.scalatra.LifeCycle

import jp.rf.swfsample.scalatra.SamplePage

class ScalatraBootstrap extends LifeCycle {
  implicit val system = ActorSystem()
  val mainActor = actor(new Act {
    become {
      case 'hello => {
        println("hello")
      }
    }
  })
  override def init(context: ServletContext) {
    context.mount(new SamplePage(mainActor), "/sample/*")
  }
  override def destroy(context:ServletContext) {
    system.shutdown()
  }
}

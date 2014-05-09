package jp.rf.swfsample.scalatra

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.{FiniteDuration, SECONDS}

import akka.pattern.ask
import akka.util.Timeout
import org.json4s.{DefaultFormats, Formats}
import org.scalatra.{AsyncResult, FutureSupport, ScalatraServlet}
import org.scalatra.json.JacksonJsonSupport

case class Hoge(name: String, value: String)

class SamplePage(mainActor: MainActor) extends ScalatraServlet with JacksonJsonSupport with FutureSupport {
  protected implicit def executor: ExecutionContext = mainActor.system.dispatcher
  protected implicit val jsonFormats: Formats = DefaultFormats
  before() {
    contentType = formats("json")
  }
  get("/hoge") {
    mainActor.actor ! 'hello
    Seq(Hoge("a", "100"), Hoge("b", "200"))
  }
  get("/actor/active") {
    new AsyncResult {
      val is = {
        implicit val timeout = Timeout(FiniteDuration(5, SECONDS))
        mainActor.actor ? 'isActiveWorker
      }
    }
  }
  post("/actor/start") {
    mainActor.actor ! 'startWorker
  }
  post("/actor/stop") {
    mainActor.actor ! 'stopWorker
  }
}

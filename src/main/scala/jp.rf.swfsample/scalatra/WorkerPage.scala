package jp.rf.swfsample.scalatra

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

import akka.pattern.ask
import akka.util.Timeout
import org.json4s.{DefaultFormats, Formats}
import org.scalatra.{AsyncResult, FutureSupport, ScalatraServlet}
import org.scalatra.json.JacksonJsonSupport

class WorkerPage(mainActor: MainActor) extends ScalatraServlet with JacksonJsonSupport with FutureSupport {
  protected implicit def executor: ExecutionContext = mainActor.system.dispatcher
  protected implicit val jsonFormats: Formats = DefaultFormats
  before() {
    contentType = formats("json")
  }
  get("/") {
    new AsyncResult {
      val is = {
        implicit val timeout = Timeout(FiniteDuration(5, SECONDS))
        mainActor.actor ? GetWorkers
      }
    }
  }
  put("/") {
    val workers = parsedBody.extract[Workers]
    new AsyncResult {
      val is = {
        implicit val timeout = Timeout(FiniteDuration(5, SECONDS))
        mainActor.actor ? SetWorkers(workers.num)
      }
    }
  }
}

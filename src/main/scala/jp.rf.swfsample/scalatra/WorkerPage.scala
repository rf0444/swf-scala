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
  get("/:id") {
    val id = params("id")
    new AsyncResult {
      val is = {
        implicit val timeout = Timeout(FiniteDuration(5, SECONDS))
        mainActor.actor ? GetWorker(id) map {
          case None => {
            status = 404
          }
          case Some(worker) => worker
        }
      }
    }
  }
  post("/") {
    val input = parsedBody.extract[WorkerInput]
    new AsyncResult {
      val is = {
        implicit val timeout = Timeout(FiniteDuration(5, SECONDS))
        mainActor.actor ? AddWorker(input)
      }
    }
  }
  put("/:id") {
    val id = params("id")
    val input = parsedBody.extract[WorkerInput]
    new AsyncResult {
      val is = {
        implicit val timeout = Timeout(FiniteDuration(5, SECONDS))
        mainActor.actor ? SetWorker(id, input) map {
          case None => {
            status = 404
          }
          case Some(worker) => worker
        }
      }
    }
  }
}

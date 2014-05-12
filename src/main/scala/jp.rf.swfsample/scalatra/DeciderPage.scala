package jp.rf.swfsample.scalatra

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

import akka.pattern.ask
import akka.util.Timeout
import org.json4s.{DefaultFormats, Formats}
import org.scalatra.{AsyncResult, FutureSupport, ScalatraServlet}
import org.scalatra.json.JacksonJsonSupport

class DeciderPage(decider: DeciderMainActor) extends ScalatraServlet with JacksonJsonSupport with FutureSupport {
  protected implicit def executor: ExecutionContext = decider.system.dispatcher
  protected implicit val jsonFormats: Formats = DefaultFormats
  before() {
    contentType = formats("json")
  }
  get("/") {
    new AsyncResult {
      val is = {
        implicit val timeout = Timeout(FiniteDuration(5, SECONDS))
        decider.actor ? GetDeciders
      }
    }
  }
  get("/:id") {
    val id = params("id")
    new AsyncResult {
      val is = {
        implicit val timeout = Timeout(FiniteDuration(5, SECONDS))
        decider.actor ? GetDecider(id) map {
          case None => {
            status = 404
          }
          case Some(decider) => decider
        }
      }
    }
  }
  post("/") {
    val input = parsedBody.extract[DeciderInput]
    new AsyncResult {
      val is = {
        implicit val timeout = Timeout(FiniteDuration(5, SECONDS))
        decider.actor ? AddDecider(input)
      }
    }
  }
  put("/:id") {
    val id = params("id")
    val input = parsedBody.extract[DeciderInput]
    new AsyncResult {
      val is = {
        implicit val timeout = Timeout(FiniteDuration(5, SECONDS))
        decider.actor ? SetDecider(id, input) map {
          case None => {
            status = 404
          }
          case Some(decider) => decider
        }
      }
    }
  }
}


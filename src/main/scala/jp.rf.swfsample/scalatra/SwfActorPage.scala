package jp.rf.swfsample.scalatra

import scala.concurrent.ExecutionContext

import akka.actor.{ActorRef, ActorRefFactory}
import akka.pattern.ask
import akka.util.Timeout
import org.json4s.{DefaultFormats, Formats}
import org.scalatra.{AsyncResult, FutureSupport, ScalatraServlet}
import org.scalatra.json.JacksonJsonSupport

import jp.rf.swfsample.actor.manager.{GetAll, Get, Add, Set, SetAll}

class SwfActorPage[Input: Manifest](val actor: ActorRef)(implicit val factory: ActorRefFactory, val timeout: Timeout)
  extends ScalatraServlet with JacksonJsonSupport with FutureSupport
{
  protected implicit def executor: ExecutionContext = factory.dispatcher
  protected implicit val jsonFormats: Formats = DefaultFormats
  val to = timeout
  before() {
    contentType = formats("json")
  }
  get("/") {
    new AsyncResult {
      val is = {
        implicit val timeout = to
        actor ? GetAll
      }
    }
  }
  post("/") {
    val input = parsedBody.extract[Input]
    new AsyncResult {
      val is = {
        implicit val timeout = to
        actor ? Add(input)
      }
    }
  }
  put("/") {
    val input = parsedBody.extract[Input]
    new AsyncResult {
      val is = {
        implicit val timeout = to
        actor ? SetAll(input)
      }
    }
  }
  get("/:id") {
    val id = params("id")
    new AsyncResult {
      val is = {
        implicit val timeout = to
        actor ? Get(id) map {
          case None => {
            status = 404
          }
          case Some(out) => out
        }
      }
    }
  }
  put("/:id") {
    val id = params("id")
    val input = parsedBody.extract[Input]
    new AsyncResult {
      val is = {
        implicit val timeout = to
        actor ? Set(id, input) map {
          case None => {
            status = 404
          }
          case Some(out) => out
        }
      }
    }
  }
}


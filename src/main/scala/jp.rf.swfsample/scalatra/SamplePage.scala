package jp.rf.swfsample.scalatra

import akka.actor.ActorRef
import org.json4s.{DefaultFormats, Formats}
import org.scalatra.ScalatraServlet
import org.scalatra.json.JacksonJsonSupport

case class Hoge(name: String, value: String)

class SamplePage(mainActor: MainActor) extends ScalatraServlet with JacksonJsonSupport {
  protected implicit val jsonFormats: Formats = DefaultFormats
  before() {
    contentType = formats("json")
  }
  get("/hoge") {
    mainActor.actor ! 'hello
    Seq(Hoge("a", "100"), Hoge("b", "200"))
  }
}

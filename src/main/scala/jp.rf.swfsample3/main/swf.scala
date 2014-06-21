package jp.rf.swfsample3.main.swf

import scala.concurrent.Await
import scala.concurrent.duration._

import akka.actor.{Actor, ActorRef, ActorRefFactory, ActorSystem, Props}
import akka.pattern.ask
import akka.util.Timeout
import com.amazonaws.services.simpleworkflow.AmazonSimpleWorkflowClient
import com.amazonaws.services.simpleworkflow.model._
import com.typesafe.config.ConfigFactory

import jp.rf.swfsample.aws.ClientFactory
import jp.rf.swfsample.actor.manager.DeciderManager
import jp.rf.swfsample.actor.manager.WorkerManager
import jp.rf.swfsample3.swf.decider.Decider
import jp.rf.swfsample3.swf.worker.Worker

object Main {
  def main(args: Array[String]) {
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
    val decisionTaskList = conf.getString("aws.swf.taskList.decision")
    val shortTaskList = conf.getString("aws.swf.taskList.activity.short")
    val longTaskList = conf.getString("aws.swf.taskList.activity.long")
    val decider = Decider.createActor("decider", swf, DeciderManager.decide, null, 2, domainName, decisionTaskList)
    val shortWorker = Worker.createActor("short-worker", swf, WorkerManager.action, null, 2, domainName, shortTaskList)
    val longWorker = Worker.createActor("long-worker", swf, WorkerManager.action, null, 2, domainName, longTaskList)
		
		decider ! 'start
		shortWorker ! 'start
		longWorker ! 'start
		println("started")
		Thread.sleep(10000)
		println("shutdown...")
    system.shutdown()
	}
}

package jp.rf.swfsample2.main.sample1

import scala.concurrent.duration._
import scala.reflect.ClassTag

import akka.actor.{Actor, ActorRef, ActorRefFactory, ActorSystem, Props}
import akka.util.Timeout
import com.amazonaws.services.simpleworkflow.AmazonSimpleWorkflowClient
import com.amazonaws.services.simpleworkflow.model._
import com.typesafe.config.ConfigFactory

import jp.rf.swfsample.aws.ClientFactory
import jp.rf.swfsample.util.uuid
import jp.rf.swfsample2.main.sample1.decider.{Activities => Decider}

object DeciderMain {
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
    val taskList = conf.getString("aws.swf.taskList.decision")
    val decider = Decider.createActor("decider", swf, decide, null, 4, domainName, taskList)
		decider ! 'start
		decider ! 'stop
    system.shutdown()
  }
	def decide(task: DecisionTask): Decision = {
    new Decision().withDecisionType(DecisionType.CancelWorkflowExecution)
	}
}
object StarterMain {
  def main(args: Array[String]) {
    val conf = ConfigFactory.load
    val swf = ClientFactory.create(
      clientClass = classOf[AmazonSimpleWorkflowClient],
      accessKey = conf.getString("aws.accessKey"),
      secretKey = conf.getString("aws.secretKey"),
      regionName = conf.getString("aws.swf.region")
    )
    val domainName = conf.getString("aws.swf.domain")
    val version = conf.getString("aws.swf.version")
    val workflowType = new WorkflowType()
      .withName("start-ec2-instance")
      .withVersion(version)
    val workflowId = uuid
    swf.startWorkflowExecution(new StartWorkflowExecutionRequest()
      .withDomain(domainName)
      .withWorkflowType(workflowType)
      .withWorkflowId(workflowId)
      .withInput("hello")
    )
  }
}

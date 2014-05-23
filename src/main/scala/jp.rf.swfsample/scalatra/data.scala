package jp.rf.swfsample.scalatra.data

case class DeciderInput(active: Boolean)
case class DeciderOutput(id: String, active: Boolean, status: String)
case class WorkerInput(active: Boolean)
case class WorkerOutput(id: String, active: Boolean, status: String)

sealed trait ActivityAction[+T]
case class Add[T](data: T) extends ActivityAction[T]
case class Get(id: String) extends ActivityAction[Nothing]
case object GetAll extends ActivityAction[Nothing]
case class Set[T](id: String, data: T) extends ActivityAction[T]
case class SetAll[T](data: T) extends ActivityAction[T]

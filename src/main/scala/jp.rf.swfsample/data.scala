package jp.rf.swfsample.data

case class DeciderInput(active: Boolean)
case class DeciderOutput(id: String, active: Boolean, status: String)
case class WorkerInput(active: Boolean)
case class WorkerOutput(id: String, active: Boolean, status: String)

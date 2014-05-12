package jp.rf.swfsample.scalatra

sealed trait ActorStatus
case object Inactive extends ActorStatus
case object Polling extends ActorStatus
case object Waiting extends ActorStatus
case object Working extends ActorStatus

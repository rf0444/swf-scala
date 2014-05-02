organization := "rf"

name := "SWFTest"

version := "0.1.0"

scalaVersion := "2.11.0"

scalacOptions ++= Seq("-unchecked", "-deprecation")

resolvers += "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/"

libraryDependencies ++= Seq(
  "com.amazonaws" % "aws-java-sdk" % "1.7.7",
  "com.typesafe.akka" %% "akka-actor" % "2.3.2"
)

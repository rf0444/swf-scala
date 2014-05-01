organization := "rf"

name := "SWFTest"

version := "0.1.0"

scalaVersion := "2.11.0"

javacOptions ++= Seq("-g", "-encoding", "UTF-8")

libraryDependencies ++= Seq(
  "com.amazonaws" % "aws-java-sdk" % "1.7.7"
)

compileOrder := CompileOrder.JavaThenScala

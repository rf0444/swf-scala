import sbt._
import Keys._

import org.scalatra.sbt._
import org.scalatra.sbt.PluginKeys._

object SampleBuild extends Build {
  lazy val project = Project(
    id = "scalatra-swf-sample",
    base = file("."),
    settings = Defaults.defaultSettings ++ ScalatraPlugin.scalatraWithJRebel ++ Seq(
      organization := "jp.rf",
      name := "ScalatraSWFSample",
      version := "0.1.0",
      scalaVersion := "2.10.4",
      scalacOptions ++= Seq("-unchecked", "-deprecation"),
      resolvers += "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/",
      libraryDependencies ++= Seq(
        "com.amazonaws" % "aws-java-sdk" % "1.7.7",
        "org.eclipse.jetty" % "jetty-plus" % "9.1.5.v20140505" % "container",
        "org.eclipse.jetty" % "jetty-webapp" % "9.1.5.v20140505" % "container",
        "org.eclipse.jetty.orbit" % "javax.servlet" % "3.0.0.v201112011016" % "container;provided;test" artifacts (Artifact("javax.servlet", "jar", "jar")),
        "org.json4s"   %% "json4s-jackson" % "3.2.6",
        "org.scalatra" %% "scalatra" % "2.2.2",
        "org.scalatra" %% "scalatra-json" % "2.2.2"
      )
    )
  )
}

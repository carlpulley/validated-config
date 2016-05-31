// Copyright 2016 Carl Pulley

import sbt.Keys._
import sbt._

/**
 * Common project settings
 */
object CommonProject {

  val settings =
    Seq(
      organization := "net.cakesolutions",
      scalaVersion := Dependencies.scalaVersion,
      scalacOptions in Compile ++= Seq(
        "-encoding", "UTF-8",
        "-target:jvm-1.8",
        "-deprecation",
        "-unchecked",
        "-Ywarn-dead-code",
        "-feature"
      ),
      scalacOptions in (Compile, doc) <++= (name in (Compile, doc), version in (Compile, doc)) map DefaultOptions.scaladoc,
      javacOptions in (Compile, compile) ++= Seq(
        "-source", "1.8",
        "-target", "1.8",
        "-Xlint:unchecked",
        "-Xlint:deprecation",
        "-Xlint:-options"
      ),
      javacOptions in doc := Seq(),
      javaOptions += "-Xmx2G",
      outputStrategy := Some(StdoutOutput),
      fork := true,
      fork in test := true
    )

}

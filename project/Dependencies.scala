// Copyright 2016 Carl Pulley

import sbt.Keys._
import sbt._

object Dependencies {
  val cats: ModuleID = "org.typelevel" %% "cats-core" % "1.0.1"
  val ficus: ModuleID = "com.iheart" %% "ficus" % "1.4.3"
  val refined: ModuleID = "eu.timepit" %% "refined" % "0.8.7"
  val scalatest: ModuleID = "org.scalatest" %% "scalatest" % "3.0.5"
  val typesafeConfig: ModuleID   = "com.typesafe" % "config" % "1.3.3"
}

// Copyright 2016 Carl Pulley

import sbt.Keys._
import sbt._

object Dependencies {
  val cats: ModuleID = "org.typelevel" %% "cats" % "0.9.0"
  val ficus: ModuleID = "com.iheart" %% "ficus" % "1.4.1"
  val refined: ModuleID = "eu.timepit" %% "refined" % "0.8.2"
  val scalatest: ModuleID = "org.scalatest" %% "scalatest" % "3.0.2"
  val typesafeConfig: ModuleID   = "com.typesafe" % "config" % "1.3.1"
}

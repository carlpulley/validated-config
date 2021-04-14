// Copyright 2016 Carl Pulley

import sbt._

object Dependencies {
  val cats: ModuleID = "org.typelevel" %% "cats-core" % "2.5.0"
  val ficus: ModuleID = "com.iheart" %% "ficus" % "1.5.0"
  val refined: ModuleID = "eu.timepit" %% "refined" % "0.9.23"
  val scalatest: ModuleID = "org.scalatest" %% "scalatest" % "3.2.5"
  val typesafeConfig: ModuleID   = "com.typesafe" % "config" % "1.3.3"
}

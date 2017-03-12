// Copyright 2016 Carl Pulley

import sbt.Keys._
import sbt._

object Dependencies {
  val cats = "org.typelevel" %% "cats" % "0.9.0"
  val ficus = "com.iheart" %% "ficus" % "1.4.0"
  val refined = "eu.timepit" %% "refined" % "0.7.0"
  val scalatest = "org.scalatest" %% "scalatest" % "3.0.1"
  val typesafeConfig   = "com.typesafe" % "config" % "1.3.1"
}

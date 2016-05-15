import Dependencies._
import sbt.Keys._
import sbt._

name := "validated-config"

CommonProject.settings

libraryDependencies ++= Seq(
  ficus,
  scalatest,
  scalaz,
  shapeless,
  typesafeConfig
)

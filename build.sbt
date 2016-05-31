// Copyright 2016 Carl Pulley

import Dependencies._

name := "validated-config"

version := "0.0.2-SNAPSHOT"

CommonProject.settings

ScalaDoc.settings

Publish.settings

resolvers += Resolver.jcenterRepo

libraryDependencies ++= Seq(
  ficus,
  scalatest % "test",
  shapeless,
  typesafeConfig
)

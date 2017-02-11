// Copyright 2016 Carl Pulley

import Dependencies._

name := "validated-config"

CommonProject.settings

ScalaDoc.settings
enablePlugins(GhpagesPlugin)
enablePlugins(SiteScaladocPlugin)

Publish.settings

resolvers += Resolver.jcenterRepo

libraryDependencies ++= Seq(
  ficus,
  scalatest % Test,
  shapeless,
  typesafeConfig
)

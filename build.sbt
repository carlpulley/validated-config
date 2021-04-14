// Copyright 2016 Carl Pulley

import Dependencies._

name := "validated-config"

CommonProject.settings
ScalaDoc.settings
enablePlugins(GhpagesPlugin)
enablePlugins(SiteScaladocPlugin)

Publish.settings

libraryDependencies ++= Seq(
  cats,
  ficus,
  refined,
  scalatest % Test,
  typesafeConfig
)

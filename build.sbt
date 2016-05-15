// Copyright 2016 Carl Pulley

import Dependencies._
import sbt.Keys._

name := "validated-config"

enablePlugins(SiteScaladocPlugin)

CommonProject.settings

ghpages.settings

resolvers += Resolver.jcenterRepo

libraryDependencies ++= Seq(
  ficus,
  scalatest,
  scalaz,
  shapeless,
  typesafeConfig
)

git.remoteRepo := s"git@github.com:carlpulley/${name.value}.git"

publishMavenStyle := true

publishArtifact in Test := false

pomIncludeRepository := { _ => false }

publishTo := {
  val nexus = "https://oss.sonatype.org/"
  if (isSnapshot.value) {
    Some("snapshots" at nexus + "content/repositories/snapshots")
  } else {
    Some("releases" at nexus + "service/local/staging/deploy/maven2")
  }
}

pomExtra := (
  <url>https://github.com/carlpulley/validated-config</url>
    <licenses>
      <license>
        <name>GNU General Public License, Version 3</name>
        <url>http://www.gnu.org/licenses/gpl-3.0.txt</url>
        <distribution>repo</distribution>
      </license>
    </licenses>
    <scm>
      <url>https://github.com/carlpulley/validated-config</url>
      <connection>https://github.com/carlpulley/validated-config</connection>
    </scm>
    <developers>
      <developer>
        <id>carlpulley</id>
        <name>Carl Pulley</name>
        <url>https://github.com/carlpulley</url>
      </developer>
    </developers>
  )

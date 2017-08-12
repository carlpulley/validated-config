// Copyright 2016 Carl Pulley

import sbt.Keys._
import sbt._

object Publish {
  val settings = Seq(
    publishMavenStyle := true,
    publishArtifact in Test := false,
    pomIncludeRepository := { _ => false },
    publishTo := {
      val nexus = "https://oss.sonatype.org/"
      if (isSnapshot.value) {
        Some("snapshots" at nexus + "content/repositories/snapshots")
      } else {
        Some("releases" at nexus + "service/local/staging/deploy/maven2")
      }
    },
    pomExtra := (
      <url>https://github.com/carlpulley/validated-config</url>
      <licenses>
        <license>
          <name>Apache License 2.0</name>
          <url>http://www.apache.org/licenses/LICENSE-2.0.txt</url>
          <distribution>repo</distribution>
        </license>
      </licenses>
      <developers>
        <developer>
          <id>carlpulley</id>
          <name>Carl Pulley</name>
          <url>https://github.com/carlpulley</url>
        </developer>
      </developers>
    )
  )
}

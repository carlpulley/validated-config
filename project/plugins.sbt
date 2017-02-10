// Copyright 2016 Carl Pulley

resolvers += "Typesafe Repository" at "https://repo.typesafe.com/typesafe/releases/"
resolvers += "jgit-repo" at "http://download.eclipse.org/jgit/maven"

addSbtPlugin("com.codacy" % "sbt-codacy-coverage" % "1.3.8")
addSbtPlugin("com.dwijnand" % "sbt-travisci" % "1.0.0-M4")
addSbtPlugin("com.jsuereth" % "sbt-pgp" % "1.0.1")
addSbtPlugin("com.typesafe.sbt" % "sbt-ghpages" % "0.6.0")
addSbtPlugin("com.typesafe.sbt" % "sbt-site" % "1.2.0")
addSbtPlugin("com.updateimpact" % "updateimpact-sbt-plugin" % "2.1.1")
addSbtPlugin("com.versioneye" % "sbt-versioneye-plugin" % "0.2.0")
addSbtPlugin("io.get-coursier" % "sbt-coursier" % "1.0.0-M15-1")
addSbtPlugin("org.scoverage" % "sbt-scoverage" % "1.5.0")
addSbtPlugin("org.xerial.sbt" % "sbt-sonatype" % "1.1")

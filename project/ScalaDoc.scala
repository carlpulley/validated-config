// Copyright 2016 Carl Pulley

import com.typesafe.sbt.SbtGit.git
import sbt.Keys._

object ScalaDoc {
  val settings =
    Seq(
      git.remoteRepo := s"https://github.com/carlpulley/${name.value}.git"
    )
}

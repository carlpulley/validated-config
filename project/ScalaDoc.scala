// Copyright 2016 Carl Pulley

import com.typesafe.sbt.SbtGhPages.ghpages
import com.typesafe.sbt.SbtGit.git
import com.typesafe.sbt.SbtSite.site
import sbt.Keys._

object ScalaDoc {
  val settings =
    site.settings ++
      ghpages.settings ++
      site.includeScaladoc() ++
      Seq(git.remoteRepo := s"https://github.com/carlpulley/${name.value}.git")
}

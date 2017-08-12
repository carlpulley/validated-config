// Copyright 2016 Carl Pulley

package net.cakesolutions.config

/**
 * General reasons for why a config value might fail to be validated by `validate`.
 */
sealed trait ConfigError
final case class ValueErrors(errors: ValueError*) extends ConfigError {
  override def toString: String = {
    s"ValueErrors(${errors.mkString(",")})"
  }
}
final case class FileNotFound(file: String, reason: Throwable) extends ConfigError

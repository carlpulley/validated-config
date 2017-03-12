// Copyright 2016 Carl Pulley

package cakesolutions.config

/**
 * Ensures that the specified path has a value defined for it. This may be achieved:
 *   - at the Typesafe configuration file level: the path must exist and have a non-null value
 *   - using a sentinal value: the path has the sentinal value if and only if it has not been set or assigned to.
 */
sealed trait PathSpec {
  def value: String
}
final case class optional(value: String) extends PathSpec
final case class required private (value: String, undefined: Option[String]) extends PathSpec
object required {
  def apply(value: String): required = {
    new required(value, None)
  }

  def apply(value: String, undefined: String): required = {
    new required(value, Some(undefined))
  }
}

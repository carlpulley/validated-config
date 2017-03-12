// Copyright 2016 Carl Pulley

package cakesolutions.config

/**
 * Reasons why we might fail to parse a value from the config file
 */
sealed trait ValueError
case object MissingValue extends Exception
case object NullValue extends Exception
case object RequiredValueNotSet extends Exception
final case class ValueFailure(path: String, reason: Throwable) extends ValueError

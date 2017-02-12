package cakesolutions.config

/**
 * Reasons why we might fail to parse a value from the config file
 */
sealed trait ValueError
final case class NestedConfigError(config: ConfigError) extends ValueError
final case class ValueFailure[Value](path: String, reason: Throwable) extends ValueError

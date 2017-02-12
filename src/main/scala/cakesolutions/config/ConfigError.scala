package cakesolutions.config

/**
 * General reasons for why a config value might fail to be validated by `validate`.
 */
case object MissingValue extends Exception
case object NullValue extends Exception
case object RequiredValueNotSet extends Exception
final case class ConfigError(errors: ValueError*) extends Exception {
  override def toString: String =
    s"ConfigError(${errors.map(_.toString).mkString(",")})"
}
final case class FileNotFound(file: String, reason: Throwable) extends Exception {
  override def toString: String =
    s"FileNotFound($file,$reason)"
}

// Copyright 2016 Carl Pulley

package cakesolutions

import com.typesafe.config.{ConfigParseOptions, ConfigResolveOptions, Config, ConfigFactory}
import net.ceedubs.ficus.readers.ValueReader
import net.ceedubs.ficus.{FicusConfig, FicusInstances, SimpleFicusConfig}
import shapeless._

import scala.language.implicitConversions
import scala.util.{Failure, Success, Try}
import scalaz.syntax.ToValidationOps
import scalaz.{-\/, \/, \/-}

package object config extends FicusInstances with ToValidationOps {

  /**
   * Reasons for why a config value might fail to be validated by `validate`. We use a trait so that user code may add
   * their failure reasons.
   */
  trait ConfigValidationFailure
  case object MissingValue extends ConfigValidationFailure
  case object NullValue extends ConfigValidationFailure
  final case class InvalidValueType[Value](reason: Throwable) extends ConfigValidationFailure

  /**
   * Reasons why we might fail to parse a value from the config file
   */
  sealed trait ValueError
  final case class FileNotFound(file: String, reason: Throwable) extends ValueError
  final case class NestedConfigError(config: ConfigError) extends ValueError
  final case class ValueFailure(path: String, reason: ConfigValidationFailure) extends ValueError

  final case class ConfigError(errors: ValueError*)

  implicit def toFicusConfig(config: Config): FicusConfig = SimpleFicusConfig(config)
  implicit def innerConfigValue[ConfigValue](config: ConfigError \/ ConfigValue): ValueError \/ ConfigValue = {
    config.leftMap(NestedConfigError)
  }

  // Configuration file loader

  /**
   * Loads Typesafe config file and then builds the validated case class `ValidConfig`
   *
   * @param configFile the root Typesafe config file name
   * @param check the builder and validator that we will use to construct the `ValidConfig` instance
   * @tparam ValidConfig the case class type that we are to construct
   * @return either a [[ConfigError]] or the validated case class `ValidConfig`
   */
  def validateConfig[ValidConfig](
    configFile: String
  )(check: Config => ConfigError \/ ValidConfig
  ): ConfigError \/ ValidConfig = {
    Try(ConfigFactory.load(configFile, ConfigParseOptions.defaults().setAllowMissing(false), ConfigResolveOptions.defaults())) match {
      case Success(config) =>
        check(config)
      case Failure(exn) =>
        -\/(ConfigError(FileNotFound(configFile, exn)))
    }
  }

  // Validated configuration builders

  /**
   *
   *
   * @param path we restrict our Typesafe config path to this path
   * @param inner configuration builder that we will apply to the restricted `Config` object
   * @param config the current in-scope `Config` object that we need to path restrict
   * @tparam ValidConfig the case class type that we are to construct
   * @return either a [[ValueError]] or the validated case class `ValidConfig`
   */
  def via[ValidConfig](path: String)(inner: Config => ConfigError \/ ValidConfig)(implicit config: Config): ValueError \/ ValidConfig = {
    innerConfigValue(inner(config.getConfig(path))).leftMap(addBasePathToValueErrors(path, _))
  }

  /**
   * Constructs and instance of the case class `ValidConfig` from a list of validated parameter values.
   *
   * @param validatedParams list of validated case class parameters (listed in the order they are declared in the case class)
   * @tparam ValidConfig the case class type that we are to construct
   * @return either a list of `ValueErrors` (wrapped in a [[ConfigError]]) or the validated case class `ValidConfig`
   */
  def build[ValidConfig](
    validatedParams: (ValueError \/ Any)*
  )(implicit gen: Generic[ValidConfig]
  ): ConfigError \/ ValidConfig = {
    val failuresHList = validatedParams.foldRight[(List[ValueError], HList)]((Nil, HNil)) {
      case (-\/(error), (failures, result)) =>
        (error +: failures, result)
      case (\/-(value), (failures, result)) =>
        (failures, value :: result)
    }

    failuresHList match {
      case (Nil, result: gen.Repr) =>
        \/-(gen.from(result))
      case (failures, _) =>
        -\/(ConfigError(failures: _*))
    }
  }

  // Parameter value checkers

  /**
   * Used to read in a value of type `Value`. Other than checking the values type, no other validation is performed.
   *
   * @param path Typesafe config path to the value we are validating
   * @param config the currently in scope config object that we use
   * @param reader Ficus `ValueReader` that we use for type checking the parsed config value
   * @tparam Value type we expect the parsed and checked config value to have
   * @return either a `ValueFailure` or the parsed and *unchecked* `Value` instance
   */
  def unchecked[Value](
    path: String
  )(implicit config: Config,
    reader: ValueReader[Value]
  ): ValueFailure \/ Value = {
    Try(config.hasPath(path)) match {
      case Success(true) =>
        Try(config.as[Value](path)) match {
          case Success(value) =>
            \/-(value)
          case Failure(exn) =>
            -\/(ValueFailure(path, InvalidValueType[Value](exn)))
        }
      case Success(false) =>
        -\/(ValueFailure(path, NullValue))
      // $COVERAGE-OFF$Requires `hasPath` to throw
      case Failure(_) =>
        -\/(ValueFailure(path, MissingValue))
      // $COVERAGE-ON$
    }
  }

  /**
   * Used to read in a value of type `Value` and to then check that value using `check`. If `check` returns false,
   * then we fail with `failureReason`.
   *
   * @param path Typesafe config path to the value we are validating
   * @param failureReason if `check` fails, the [[ConfigValidationFailure]] we return
   * @param check predicate used to check the configuration value
   * @param config the currently in scope config object that we use
   * @param reader Ficus `ValueReader` that we use for type checking the parsed config value
   * @tparam Value type we expect the parsed and checked config value to have
   * @return either a `ValueFailure` or the parsed and checked `Value` instance
   */
  def validate[Value](
    path: String,
    failureReason: ConfigValidationFailure
  )(check: Value => Boolean
  )(implicit config: Config,
    reader: ValueReader[Value]
  ): ValueFailure \/ Value = {
    Try(config.hasPath(path)) match {
      case Success(true) =>
        Try(config.as[Value](path)) match {
          case Success(value) =>
            Try(check(value)) match {
              case Success(true) =>
                \/-(value)
              case Success(false) =>
                -\/(ValueFailure(path, failureReason))
              case Failure(exn) =>
                -\/(ValueFailure(path, InvalidValueType[Value](exn)))
            }
          case Failure(exn) =>
            -\/(ValueFailure(path, InvalidValueType[Value](exn)))
        }
      case Success(false) =>
        -\/(ValueFailure(path, NullValue))
      // $COVERAGE-OFF$Requires `hasPath` to throw
      case Failure(_) =>
        -\/(ValueFailure(path, MissingValue))
      // $COVERAGE-ON$
    }
  }

  private def addBasePathToValueErrors(base: String, error: ValueError): ValueError = error match {
    case error: FileNotFound =>
      error
    case NestedConfigError(ConfigError(errors @ _*)) =>
      NestedConfigError(ConfigError(errors.map(addBasePathToValueErrors(base, _)): _*))
    case ValueFailure(path, reason) =>
      ValueFailure(s"$base.$path", reason)
  }
}

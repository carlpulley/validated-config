// Copyright 2016 Carl Pulley

package net.cakesolutions

import scala.language.implicitConversions
import scala.util.{Failure, Success, Try}

import cats.data.{NonEmptyList => NEL, Validated}
import com.typesafe.config.{Config, ConfigFactory, ConfigParseOptions, ConfigResolveOptions}
import eu.timepit.refined._
import eu.timepit.refined.api.{Validate, Refined}
import net.ceedubs.ficus.readers.ValueReader
import net.ceedubs.ficus.{FicusConfig, FicusInstances, SimpleFicusConfig}

/**
 * Using <a href="https://github.com/typesafehub/config">Typesafe config</a> we read in and parse configuration files.
 * Paths into these files are then retrieved and type checked using <a href="https://github.com/iheartradio/ficus">Ficus</a>.
 *
 * Using a lightweight DSL, we are able to then check and validate these
 * type checked values. For example, given that the Typesafe configuration:
 * {{{
 * top-level-name = "test"
 * test {
 *   nestedVal = 50.68
 *   nestedDuration = 4 h
 *   nestedList = []
 *   context {
 *     valueInt = 30
 *     valueStr = "test string"
 *     valueDuration = 12 ms
 *     valueStrList = [ "addr1:10", "addr2:20", "addr3:30" ]
 *     valueDoubleList = [ 10.2, 20, 0.123 ]
 *   }
 * }
 * }}}
 * has been parsed and read into an implicit of type `Config`, then we are
 * able to validate that the value at the path `test.nestedVal` has type
 * `Double` and that it satisfies specified size bounds as follows:
 * {{{
 * case object ShouldBeAPercentageValue extends Exception
 *
 * validate[Double]("test.nestedVal", ShouldBeAPercentageValue)(n => 0 <= n && n <= 100)
 * }}}
 * If the configuration value at path `test.nestedVal` fails to pass the
 * percentage bounds check, then `Left(ShouldBeAPercentageValue)` is
 * returned.
 *
 * Likewise, we can enforce that all values in the array at the path
 * `test.context.valueStrList` match the regular expression pattern
 * `[a-z0-9]+:[0-9]+` as follows:
 * {{{
 * case object ShouldBeASocketValue extends Exception
 *
 * validate[List[String]]("test.context.valueStrList", ShouldBeASocketValue)(_.matches("[a-z0-9]+:[0-9]+"))
 * }}}
 *
 * In some instances, we may not care about checking the value at a
 * configuration path. In these cases we can use `unchecked`:
 * {{{
 * unchecked[FiniteDuration]("test.nestedDuration")
 * }}}
 */
package object config extends FicusInstances {
  implicit def toFicusConfig(config: Config): FicusConfig = SimpleFicusConfig(config)

  implicit def toRefinementType[Base, Refinement](
    implicit reader: ValueReader[Base],
    witness: Validate[Base, Refinement]
  ): ValueReader[Base Refined Refinement] = new ValueReader[Base Refined Refinement] {
    override def read(config: Config, path: String): Base Refined Refinement = {
      refineV[Refinement](config.as[Base](path)).right.get
    }
  }

  // Configuration file loader

  /**
   * Loads Typesafe config file and then builds the validated case class `ValidConfig`
   *
   * @param configFile the root Typesafe config file name
   * @param check the builder and validator that we will use to construct the `ValidConfig` instance
   * @tparam ValidConfig the case class type that we are to construct
   * @return either a [[ConfigError]] throwable instance or the validated case class `ValidConfig`
   */
  def validateConfig[ValidConfig](
    configFile: String
  )(check: Config => Validated[NEL[ValueError], ValidConfig]
  ): Validated[ConfigError, ValidConfig] = {
    Try(ConfigFactory.load(
      configFile,
      ConfigParseOptions.defaults().setAllowMissing(false),
      ConfigResolveOptions.defaults()
    )) match {
      case Success(config) =>
        check(config).leftMap(errors => ValueErrors(errors.toList: _*))
      case Failure(exn) =>
        Validated.invalid(FileNotFound(configFile, exn))
    }
  }

  // Validated configuration builders

  /**
   * The currently in-scope implicit `Config` instance is restricted to a specified path
   *
   * @param path we restrict our Typesafe config path to this path
   * @param inner configuration builder that we will apply to the restricted `Config` object
   * @param config the current in-scope `Config` object that we need to path restrict
   * @tparam ValidConfig the case class type that we are to construct
   * @return either a [[ValueError]] or the validated case class `ValidConfig`
   */
  def via[ValidConfig](
    path: String
  )(inner: Config => Validated[NEL[ValueFailure], ValidConfig]
  )(implicit config: Config): Validated[NEL[ValueFailure], ValidConfig] = {
    inner(config.getConfig(path)) match {
      case Validated.Valid(value) =>
        Validated.valid(value)
      case Validated.Invalid(errors) =>
        Validated.invalid(errors.map { case ValueFailure(location, reason) => ValueFailure(s"$path.$location", reason) })
    }
  }

  // Parameter value checkers

  /**
   * Used to read in a value of type `Value`. Other than checking the values type, no other validation is performed.
   *
   * @param pathSpec Typesafe config path to the value we are validating
   * @param config the currently in scope config object that we use
   * @param reader Ficus `ValueReader` that we use for type checking the parsed config value
   * @tparam Value type we expect the parsed and checked config value to have
   * @return either a `ValueFailure` or the parsed and *unchecked* `Value` instance
   */
  def unchecked[Value](
    pathSpec: PathSpec
  )(implicit config: Config,
    reader: ValueReader[Value]
  ): Validated[NEL[ValueFailure], Value] = {
    Try(config.hasPath(pathSpec.value)) match {
      case Success(true) =>
        checkedPath[Value](pathSpec) match {
          case Validated.Valid(path) =>
            Try(config.as[Value](path)) match {
              case Success(value) =>
                Validated.valid(value)
              case Failure(exn) =>
                Validated.invalid(NEL.of(ValueFailure(path, exn)))
            }
          case Validated.Invalid(result) =>
            Validated.invalid(NEL.of(result))
        }
      case Success(false) =>
        Validated.invalid(NEL.of(ValueFailure(pathSpec.value, NullValue)))
      // $COVERAGE-OFF$ Requires `hasPath` to throw
      case Failure(_) =>
        Validated.invalid(NEL.of(ValueFailure(pathSpec.value, MissingValue)))
      // $COVERAGE-ON$
    }
  }

  def unchecked[Value](
    path: String
  )(implicit config: Config,
    reader: ValueReader[Value]
  ): Validated[NEL[ValueFailure], Value] = {
    unchecked[Value](optional(path))(config, reader)
  }

  /**
   * Used to read in a value of type `Value` and to then check that value using `check`. If `check` returns false,
   * then we fail with `failureReason`.
   *
   * @param pathSpec Typesafe config path to the value we are validating
   * @param failureReason if `check` fails, the [[Throwable]] instance we return
   * @param check predicate used to check the configuration value
   * @param config the currently in scope config object that we use
   * @param reader Ficus `ValueReader` that we use for type checking the parsed config value
   * @tparam Value type we expect the parsed and checked config value to have
   * @return either a `ValueFailure` or the parsed and checked `Value` instance
   */
  def validate[Value](
    pathSpec: PathSpec,
    failureReason: Throwable
  )(check: Value => Boolean
  )(implicit config: Config,
    reader: ValueReader[Value]
  ): Validated[NEL[ValueFailure], Value] = {
    checkedPath[Value](pathSpec) match {
      case Validated.Valid(path) =>
        Try(config.as[Value](path)) match {
          case Success(value) =>
            Try(check(value)) match {
              case Success(true) =>
                Validated.valid(value)
              case Success(false) =>
                Validated.invalid(NEL.of(ValueFailure(path, failureReason)))
              case Failure(exn) =>
                Validated.invalid(NEL.of(ValueFailure(path, exn)))
            }
          case Failure(exn) =>
            Validated.invalid(NEL.of(ValueFailure(path, exn)))
        }
      case Validated.Invalid(result) =>
        Validated.invalid(NEL.of(result))
    }
  }

  def validate[Value](
    path: String,
    failureReason: Throwable
  )(check: Value => Boolean
  )(implicit config: Config,
    reader: ValueReader[Value]
  ): Validated[NEL[ValueFailure], Value] = {
    validate[Value](optional(path), failureReason)(check)(config, reader)
  }

  private def checkedPath[Value](
    path: PathSpec
  )(implicit config: Config
  ): Validated[ValueFailure, String] = {
    path match {
      case optional(value) =>
        Validated.valid(value)
      case required(value, None) =>
        Try(config.hasPath(value)) match {
          case Success(true) =>
            Validated.valid(value)
          case Success(false) =>
            Validated.invalid(ValueFailure(path.value, RequiredValueNotSet))
          // $COVERAGE-OFF$ Requires `hasPath` to throw
          case Failure(exn) =>
            Validated.invalid(ValueFailure(value, exn))
          // $COVERAGE-ON$
        }
      case required(value, Some(undefined)) =>
        Try(config.getValue(value).unwrapped()) match {
          case actual @ Success(`undefined`) =>
            Validated.invalid(ValueFailure(path.value, RequiredValueNotSet))
          case Success(actual) =>
            Validated.valid(value)
          // $COVERAGE-OFF$ Requires `getValue` or `unwrapped` to throw
          case Failure(exn) =>
            Validated.invalid(ValueFailure(value, exn))
          // $COVERAGE-ON$
        }
    }
  }
}

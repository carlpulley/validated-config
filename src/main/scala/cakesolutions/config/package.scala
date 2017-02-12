// Copyright 2016 Carl Pulley

package cakesolutions

import com.typesafe.config.{Config, ConfigFactory, ConfigParseOptions, ConfigResolveOptions}
import net.ceedubs.ficus.readers.ValueReader
import net.ceedubs.ficus.{FicusConfig, FicusInstances, SimpleFicusConfig}
import shapeless._
import shapeless.ops.hlist.{Mapped, RightFolder}
import shapeless.syntax.std.tuple._

import scala.language.{implicitConversions, reflectiveCalls}
import scala.util.{Failure, Success, Try}

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
  implicit def innerConfigValue[ConfigValue](
    config: Either[ConfigError, ConfigValue]
  ): Either[ValueError, ConfigValue] = {
    config.left.map(NestedConfigError)
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
  )(check: Config => Either[ConfigError, ValidConfig]
  ): Try[ValidConfig] = {
    Try(ConfigFactory.load(
      configFile,
      ConfigParseOptions.defaults().setAllowMissing(false),
      ConfigResolveOptions.defaults()
    )) match {
      case Success(config) =>
        check(config).fold(Failure(_), Success(_))
      case Failure(exn) =>
        Failure(FileNotFound(configFile, exn))
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
  )(inner: Config => Either[ConfigError, ValidConfig]
  )(implicit config: Config): Either[ValueError, ValidConfig] = {
    innerConfigValue(inner(config.getConfig(path))).left.map(addBasePathToValueErrors(path, _))
  }

  /**
   * Constructs an instance of the case class `ValidConfig` from a list of validated parameter values. This function
   * is viewed as being unsafe as it may throw runtime class cast exceptions.
   *
   * @param validatedParams list of validated case class parameters (listed in the order they are declared in the case
   *   class)
   * @tparam ValidConfig the case class type that we are to construct
   * @return either a list of `ValueErrors` (wrapped in a [[ConfigError]]) or the validated case class `ValidConfig`
   */
  @throws[ClassCastException]
  def buildUnsafe[ValidConfig](
    validatedParams: Either[ValueError, Any]*
  )(implicit gen: Generic[ValidConfig]
  ): Either[ConfigError, ValidConfig] = {
    val failuresHList = validatedParams.foldRight[(List[ValueError], HList)]((Nil, HNil)) {
      case (Left(error), (failures, result)) =>
        (error +: failures, result)
      case (Right(value), (failures, result)) =>
        (failures, value :: result)
    }

    failuresHList match {
      case (Nil, result: (gen.Repr @unchecked)) =>
        Right(gen.from(result))
      case (failures, _) =>
        Left(ConfigError(failures: _*))
    }
  }

  /**
   * Constructs an instance of the case class `ValidConfig` from a list of validated parameter values. Unlike
   * `buildUnsafe`, this function does not throw runtime class cast exceptions.
   *
   * @param validatedParams HList of validated case class parameters (listed in the order they are declared in the case
   *   class)
   * @tparam ValidConfig the case class type that we are to construct
   * @return either a list of `ValueErrors` (wrapped in a [[ConfigError]]) or the validated case class `ValidConfig`
   */
  def buildSafe[ValidConfig](
    validatedParams: Mapped[Generic[ValidConfig]#Repr, ({ type M[X] = Either[ValueError, X] })#M]#Out
  )(implicit gen: Generic[ValidConfig],
    folder: ??? // FIXME:
  ): Either[ConfigError, ValidConfig] = {
    type BuildState = (List[ValueError], HList)
    object collectFailuresHList extends Poly2 {
      implicit def leftErrorCase[Value]: Case.Aux[Left[ValueError, Value], BuildState, BuildState] = {
        at {
          case (Left(error), (failures, result)) =>
            (error +: failures, result)
        }
      }
      implicit def rightErrorCase[Value]: Case.Aux[Right[ValueError, Value], BuildState, BuildState] = {
        at {
          case (Right(value), (failures, result)) =>
            (failures, value :: result)
        }
      }
    }
    implicit val folder: RightFolder[
        Mapped[gen.Repr, ({ type M[X] = Either[ValueError,X] })#M]#Out,
        BuildState,
        collectFailuresHList.type
      ] = ??? // TODO:
    val failuresHList: (List[ValueError], HList) =
      validatedParams
        .foldRight[BuildState]((Nil, HNil))(collectFailuresHList)

    failuresHList match {
      case (Nil, result: (gen.Repr @unchecked)) =>
        Right(gen.from(result))
      case (failures, _) =>
        Left(ConfigError(failures: _*))
    }
  }

  // TODO: introduce a macro(?) named `build` that avoids `productElements` boiler plate

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
  ): Either[ValueFailure[Value], Value] = {
    Try(config.hasPath(pathSpec.value)) match {
      case Success(true) =>
        checkedPath[Value](pathSpec) match {
          case Right(path) =>
            Try(config.as[Value](path)) match {
              case Success(value) =>
                Right(value)
              case Failure(exn) =>
                Left(ValueFailure[Value](path, exn))
            }
          case Left(result) =>
            Left(result)
        }
      case Success(false) =>
        Left(ValueFailure[Value](pathSpec.value, NullValue))
      // $COVERAGE-OFF$ Requires `hasPath` to throw
      case Failure(_) =>
        Left(ValueFailure[Value](pathSpec.value, MissingValue))
      // $COVERAGE-ON$
    }
  }

  def unchecked[Value](
    path: String
  )(implicit config: Config,
    reader: ValueReader[Value]
  ): Either[ValueFailure[Value], Value] = {
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
  ): Either[ValueFailure[Value], Value] = {
    checkedPath[Value](pathSpec) match {
      case Right(path) =>
        Try(config.as[Value](path)) match {
          case Success(value) =>
            Try(check(value)) match {
              case Success(true) =>
                Right(value)
              case Success(false) =>
                Left(ValueFailure[Value](path, failureReason))
              case Failure(exn) =>
                Left(ValueFailure[Value](path, exn))
            }
          case Failure(exn) =>
            Left(ValueFailure[Value](path, exn))
        }
      case Left(result) =>
        Left(result)
    }
  }

  def validate[Value](
    path: String,
    failureReason: Throwable
  )(check: Value => Boolean
  )(implicit config: Config,
    reader: ValueReader[Value]
  ): Either[ValueFailure[Value], Value] = {
    validate[Value](optional(path), failureReason)(check)(config, reader)
  }

  private def checkedPath[Value](
    path: PathSpec
  )(implicit config: Config
  ): Either[ValueFailure[Value], String] = {
    path match {
      case optional(value) =>
        Right(value)
      case required(value, None) =>
        Try(config.hasPath(value)) match {
          case Success(true) =>
            Right(value)
          case Success(false) =>
            Left(ValueFailure[Value](path.value, RequiredValueNotSet))
          // $COVERAGE-OFF$ Requires `hasPath` to throw
          case Failure(exn) =>
            Left(ValueFailure[Value](value, exn))
          // $COVERAGE-ON$
        }
      case required(value, Some(undefined)) =>
        Try(config.getValue(value).unwrapped()) match {
          case actual @ Success(`undefined`) =>
            Left(ValueFailure[Value](path.value, RequiredValueNotSet))
          case Success(actual) =>
            Right(value)
          // $COVERAGE-OFF$ Requires `getValue` or `unwrapped` to throw
          case Failure(exn) =>
            Left(ValueFailure[Value](value, exn))
          // $COVERAGE-ON$
        }
    }
  }

  private def addBasePathToValueErrors(base: String, error: ValueError): ValueError = error match {
    case NestedConfigError(ConfigError(errors @ _*)) =>
      NestedConfigError(ConfigError(errors.map(addBasePathToValueErrors(base, _)): _*))
    case ValueFailure(path, reason) =>
      ValueFailure(s"$base.$path", reason)
  }
}

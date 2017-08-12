// Copyright 2016 Carl Pulley

package net.cakesolutions.config

import scala.concurrent.duration._

import cats._
import cats.data.{NonEmptyList => NEL, Validated}
import com.typesafe.config.{Config, ConfigException, ConfigFactory}
import eu.timepit.refined._
import eu.timepit.refined.api.Refined
import eu.timepit.refined.auto._
import eu.timepit.refined.numeric._
import eu.timepit.refined.string._
import org.scalatest.FreeSpec

object ValidatedConfigTest {
  case object GenericTestFailure extends Exception
  case object NameShouldBeNonEmptyAndLowerCase extends Exception
  case object ShouldBePositive extends Exception
  case object ShouldNotBeNegative extends Exception

  // Permissive case class construction - instances may be altered after creation
  final case class HttpConfig(host: String, port: Int Refined Positive)
  final case class Settings(name: String Refined MatchesRegex[W.`"[a-z0-9_-]+"`.T], timeout: FiniteDuration, http: HttpConfig)
}

class ValidatedConfigTest extends FreeSpec {
  import ValidatedConfigTest._

  type ValidationFailure[Value] = Validated[NEL[ValueFailure], Value]

  private def matchOrFail[Value](value: => Value)(matcher: PartialFunction[Value, Unit]): Unit = {
    matcher.orElse[Value, Unit] { case result => assert(false, result) }(value)
  }

  "parameter checking" - {
    val fakeException = new RuntimeException("fake exception")

    implicit val config = ConfigFactory.parseString(
      """
        |top-level-name = "test"
        |top-level-required = "NOT_SET"
        |top-level-null = null
        |test {
        |  nestedVal = 50.68
        |  nestedDuration = 4 h
        |  nestedList = []
        |  nestedRequired = "UNDEFINED"
        |  context {
        |    valueInt = 30
        |    valueStr = "test string"
        |    valueDuration = 12 ms
        |    valueStrList = [ "addr1:10", "addr2:20", "addr3:30" ]
        |    valueDoubleList = [ 10.2, 20, 0.123 ]
        |  }
        |}
      """.stripMargin)

    "ConfigError toString" in {
      assert(ValueErrors().toString == "ValueErrors()")
      assert(ValueErrors(ValueFailure("some.path", ShouldBePositive)).toString == s"ValueErrors(ValueFailure(some.path,$ShouldBePositive))")
    }

    "FileNotFound toString" in {
      assert(FileNotFound("/some/path", GenericTestFailure).toString == s"FileNotFound(/some/path,$GenericTestFailure)")
    }

    "validate method" in {
      assert(validate[String]("top-level-name", GenericTestFailure)("test" == _) == Validated.Valid("test"))
      assert(validate[String](required("top-level-name"), GenericTestFailure)("test" == _) == Validated.Valid("test"))
      assert(validate[String](required("top-level-name", "NOT_SET"), GenericTestFailure)("test" == _) == Validated.Valid("test"))
      assert(validate[String](required("top-level-required", "NOT_SET"), GenericTestFailure)("test" == _) == Validated.Invalid(NEL.of(ValueFailure("top-level-required", RequiredValueNotSet))))
      assert(validate[String](required("top-level-null"), GenericTestFailure)("test" == _) == Validated.Invalid(NEL.of(ValueFailure("top-level-null", RequiredValueNotSet))))
      assert(validate[Double]("test.nestedVal", GenericTestFailure)(50.68 == _) == Validated.Valid(50.68))
      assert(validate[Double](required("test.nestedVal", "NOT_SET"), GenericTestFailure)(50.68 == _) == Validated.Valid(50.68))
      assert(validate[FiniteDuration]("test.nestedDuration", GenericTestFailure)(4.hours == _) == Validated.Valid(4.hours))
      assert(validate[FiniteDuration](required("test.nestedDuration", "NOT_SET"), GenericTestFailure)(4.hours == _) == Validated.Valid(4.hours))
      assert(validate[List[Double]]("test.nestedList", GenericTestFailure)(_.isEmpty) == Validated.Valid(List.empty[Double]))
      assert(validate[List[Double]](required("test.nestedList", "NOT_SET"), GenericTestFailure)(_.isEmpty) == Validated.Valid(List.empty[Double]))
      assert(validate[String](required("test.nestedRequired", "NOT_SET"), GenericTestFailure)("UNDEFINED" == _) == Validated.Valid("UNDEFINED"))
      assert(validate[String](required("test.nestedRequired", "UNDEFINED"), GenericTestFailure)("UNDEFINED" == _) == Validated.Invalid(NEL.of(ValueFailure("test.nestedRequired", RequiredValueNotSet))))
      assert(validate[Int]("test.context.valueInt", GenericTestFailure)(30 == _) == Validated.Valid(30))
      assert(validate[Int](required("test.context.valueInt", "NOT_SET"), GenericTestFailure)(30 == _) == Validated.Valid(30))
      assert(validate[Int Refined Positive]("test.context.valueInt", GenericTestFailure)(_.value != 30) == Validated.Invalid(NEL.of(ValueFailure("test.context.valueInt", GenericTestFailure))))
      assert(validate[Int Refined Positive](required("test.context.valueInt", "NOT_SET"), GenericTestFailure)(_.value != 30) == Validated.Invalid(NEL.of(ValueFailure("test.context.valueInt", GenericTestFailure))))
      assert(validate[String]("test.context.valueStr", GenericTestFailure)("test string" == _) == Validated.Valid("test string"))
      assert(validate[String](required("test.context.valueStr", "NOT_SET"), GenericTestFailure)("test string" == _) == Validated.Valid("test string"))
      assert(validate[FiniteDuration]("test.context.valueDuration", GenericTestFailure)(12.milliseconds == _) == Validated.Valid(12.milliseconds))
      assert(validate[FiniteDuration](required("test.context.valueDuration", "NOT_SET"), GenericTestFailure)(12.milliseconds == _) == Validated.Valid(12.milliseconds))
      assert(validate[List[String]]("test.context.valueStrList", GenericTestFailure)(List("addr1:10", "addr2:20", "addr3:30") == _) == Validated.Valid(List("addr1:10", "addr2:20", "addr3:30")))
      assert(validate[List[String]](required("test.context.valueStrList", "NOT_SET"), GenericTestFailure)(List("addr1:10", "addr2:20", "addr3:30") == _) == Validated.Valid(List("addr1:10", "addr2:20", "addr3:30")))
      assert(validate[List[Double]]("test.context.valueDoubleList", GenericTestFailure)(List(10.2, 20, 0.123) == _) == Validated.Valid(List(10.2, 20, 0.123)))
      assert(validate[List[Double]](required("test.context.valueDoubleList", "NOT_SET"), GenericTestFailure)(List(10.2, 20, 0.123) == _) == Validated.Valid(List(10.2, 20, 0.123)))
      assert(validate[List[Int]]("test.context.valueDoubleList", GenericTestFailure)(_ => true) == Validated.Valid(List(10, 20, 0)))
      assert(validate[List[Int]](required("test.context.valueDoubleList", "NOT_SET"), GenericTestFailure)(_ => true) == Validated.Valid(List(10, 20, 0)))
      assert(validate[List[String]]("test.context.valueDoubleList", GenericTestFailure)(_ => true) == Validated.Valid(List("10.2", "20", "0.123")))
      assert(validate[List[String]](required("test.context.valueDoubleList", "NOT_SET"), GenericTestFailure)(_ => true) == Validated.Valid(List("10.2", "20", "0.123")))

      assert(validate[String]("top-level-name", GenericTestFailure)(_ => false) == Validated.Invalid(NEL.of(ValueFailure("top-level-name", GenericTestFailure))))
      matchOrFail(validate[String]("invalid-path", GenericTestFailure)(_ => true)) {
        case Validated.Invalid(errors) => errors.toList match {
          case List(ValueFailure("invalid-path", _: ConfigException.Missing)) =>
            assert(true)
        }
      }
      matchOrFail(validate[String](required("invalid-path"), GenericTestFailure)(_ => true)) {
        case Validated.Invalid(errors) => errors.toList match {
          case List(ValueFailure("invalid-path", RequiredValueNotSet)) =>
            assert(true)
        }
      }
      matchOrFail(validate[String](required("invalid-path", "NOT_SET"), GenericTestFailure)(_ => true)) {
        case Validated.Invalid(errors) => errors.toList match {
          case List(ValueFailure("invalid-path", _: ConfigException.Missing)) =>
            assert(true)
        }
      }
      matchOrFail(validate[String]("test.invalid-path", GenericTestFailure)(_ => true)) {
        case Validated.Invalid(errors) => errors.toList match {
          case List(ValueFailure("test.invalid-path", _: ConfigException.Missing)) =>
            assert(true)
        }
      }
      matchOrFail(validate[String](required("test.invalid-path", "NOT_SET"), GenericTestFailure)(_ => true)) {
        case Validated.Invalid(errors) => errors.toList match {
          case List(ValueFailure("test.invalid-path", _: ConfigException.Missing)) =>
            assert(true)
        }
      }
      matchOrFail(validate[Int]("top-level-name", GenericTestFailure)(_ => true)) {
        case Validated.Invalid(errors) => errors.toList match {
          case List(ValueFailure("top-level-name", _: ConfigException.WrongType)) =>
            assert(true)
        }
      }
      matchOrFail(validate[Int](required("top-level-name", "NOT_SET"), GenericTestFailure)(_ => true)) {
        case Validated.Invalid(errors) => errors.toList match {
          case List(ValueFailure("top-level-name", _: ConfigException.WrongType)) =>
            assert(true)
        }
      }
      matchOrFail(validate[String]("top-level-name", GenericTestFailure)(_ => throw fakeException)) {
        case Validated.Invalid(errors) => errors.toList match {
          case List(ValueFailure("top-level-name", `fakeException`)) =>
            assert(true)
        }
      }
      matchOrFail(validate[String](required("top-level-name", "NOT_SET"), GenericTestFailure)(_ => throw fakeException)) {
        case Validated.Invalid(errors) => errors.toList match {
          case List(ValueFailure("top-level-name", `fakeException`)) =>
            assert(true)
        }
      }
    }

    "unchecked method" in {
      assert(unchecked[String]("top-level-name") == Validated.Valid("test"))
      assert(unchecked[String](required("top-level-name")) == Validated.Valid("test"))
      assert(unchecked[String](required("top-level-name", "NOT_SET")) == Validated.Valid("test"))
      assert(unchecked[String](required("top-level-required", "NOT_SET")) == Validated.Invalid(NEL.of(ValueFailure("top-level-required", RequiredValueNotSet))))
      assert(unchecked[Double]("test.nestedVal") == Validated.Valid(50.68))
      assert(unchecked[Double](required("test.nestedVal", "NOT_SET")) == Validated.Valid(50.68))
      assert(unchecked[FiniteDuration]("test.nestedDuration") == Validated.Valid(4.hours))
      assert(unchecked[FiniteDuration](required("test.nestedDuration", "NOT_SET")) == Validated.Valid(4.hours))
      assert(unchecked[List[Double]]("test.nestedList") == Validated.Valid(List.empty[Double]))
      assert(unchecked[List[Double]](required("test.nestedList", "NOT_SET")) == Validated.Valid(List.empty[Double]))
      assert(unchecked[String](required("test.nestedRequired", "NOT_SET")) == Validated.Valid("UNDEFINED"))
      assert(unchecked[String](required("test.nestedRequired", "UNDEFINED")) == Validated.Invalid(NEL.of(ValueFailure("test.nestedRequired", RequiredValueNotSet))))
      assert(unchecked[Int]("test.context.valueInt") == Validated.Valid(30))
      assert(unchecked[Int](required("test.context.valueInt", "NOT_SET")) == Validated.Valid(30))
      assert(unchecked[String]("test.context.valueStr") == Validated.Valid("test string"))
      assert(unchecked[String](required("test.context.valueStr", "NOT_SET")) == Validated.Valid("test string"))
      assert(unchecked[FiniteDuration]("test.context.valueDuration") == Validated.Valid(12.milliseconds))
      assert(unchecked[FiniteDuration](required("test.context.valueDuration", "NOT_SET")) == Validated.Valid(12.milliseconds))
      assert(unchecked[List[String]]("test.context.valueStrList") == Validated.Valid(List("addr1:10", "addr2:20", "addr3:30")))
      assert(unchecked[List[String]](required("test.context.valueStrList", "NOT_SET")) == Validated.Valid(List("addr1:10", "addr2:20", "addr3:30")))
      assert(unchecked[List[Double]]("test.context.valueDoubleList") == Validated.Valid(List(10.2, 20, 0.123)))
      assert(unchecked[List[Double]](required("test.context.valueDoubleList", "NOT_SET")) == Validated.Valid(List(10.2, 20, 0.123)))
      assert(unchecked[List[Int]]("test.context.valueDoubleList") == Validated.Valid(List(10, 20, 0)))
      assert(unchecked[List[Int]](required("test.context.valueDoubleList", "NOT_SET")) == Validated.Valid(List(10, 20, 0)))
      assert(unchecked[List[String]]("test.context.valueDoubleList") == Validated.Valid(List("10.2", "20", "0.123")))
      assert(unchecked[List[String]](required("test.context.valueDoubleList", "NOT_SET")) == Validated.Valid(List("10.2", "20", "0.123")))

      matchOrFail(unchecked[String]("invalid-path")) {
        case Validated.Invalid(errors) => errors.toList match {
          case List(ValueFailure("invalid-path", NullValue)) =>
            assert(true)
        }
      }
      matchOrFail(unchecked[String](required("invalid-path"))) {
        case Validated.Invalid(errors) => errors.toList match {
          case List(ValueFailure("invalid-path", NullValue)) =>
            assert(true)
        }
      }
      matchOrFail(unchecked[String](required("invalid-path", "NOT_SET"))) {
        case Validated.Invalid(errors) => errors.toList match {
          case List(ValueFailure("invalid-path", NullValue)) =>
            assert(true)
        }
      }
      matchOrFail(unchecked[String]("test.invalid-path")) {
        case Validated.Invalid(errors) => errors.toList match {
          case List(ValueFailure("test.invalid-path", NullValue)) =>
            assert(true)
        }
      }
      matchOrFail(unchecked[String](required("test.invalid-path", "NOT_SET"))) {
        case Validated.Invalid(errors) => errors.toList match {
          case List(ValueFailure("test.invalid-path", NullValue)) =>
            assert(true)
        }
      }
      matchOrFail(unchecked[Int]("top-level-name")) {
        case Validated.Invalid(errors) => errors.toList match {
          case List(ValueFailure("top-level-name", _)) =>
            assert(true)
        }
      }
      matchOrFail(unchecked[Int](required("top-level-name", "NOT_SET"))) {
        case Validated.Invalid(errors) => errors.toList match {
          case List(ValueFailure("top-level-name", _)) =>
            assert(true)
        }
      }
    }

    "case class building methods" - {
      case class TestSettings(str: String, int: Int, double: Double, duration: FiniteDuration, strList: List[String], doubleList: List[Double])

      "validated building" in {
        val testConfig1 = via[TestSettings]("test") { implicit config =>
          Applicative[ValidationFailure].map6(
            validate[String]("context.valueStr", GenericTestFailure)(_ => true),
            validate[Int]("context.valueInt", GenericTestFailure)(_ => true),
            validate[Double]("nestedVal", GenericTestFailure)(_ => true),
            validate[FiniteDuration]("nestedDuration", GenericTestFailure)(_ => true),
            validate[List[String]]("context.valueStrList", GenericTestFailure)(_ => true),
            validate[List[Double]]("context.valueDoubleList", GenericTestFailure)(_ => true)
          )(TestSettings)
        }
        matchOrFail(testConfig1) {
          case Validated.Valid(TestSettings("test string", 30, 50.68, duration, List("addr1:10", "addr2:20", "addr3:30"), List(10.2, 20, 0.123))) =>
            assert(duration == 4.hours)
        }
        val testConfig3 = via[TestSettings]("test") { implicit config =>
          Applicative[ValidationFailure].map6(
            validate[String]("context.valueStr", GenericTestFailure)(_ => true),
            validate[Int](required("context.valueInt", "NOT_SET"), GenericTestFailure)(_ => true),
            validate[Double]("bad-path.nestedVal", GenericTestFailure)(_ => true),
            validate[FiniteDuration]("nestedDuration", GenericTestFailure)(_ => true),
            validate[List[String]]("context.valueStrList", GenericTestFailure)(_ => false),
            validate[List[Double]]("context.valueDoubleList", GenericTestFailure)(_ => true)
          )(TestSettings)
        }
        matchOrFail(testConfig3) {
          case Validated.Invalid(errors) => errors.toList match {
            case List(ValueFailure("test.bad-path.nestedVal", _: ConfigException.Missing), ValueFailure("test.context.valueStrList", GenericTestFailure)) =>
              assert(true)
          }
        }
        val testConfig4 = via[TestSettings]("test") { implicit config =>
          Applicative[ValidationFailure].map6(
            validate[String]("context.valueStr", GenericTestFailure)(_ => true),
            validate[Int]("context.valueInt", GenericTestFailure)(_ => true),
            validate[Double]("nestedVal", GenericTestFailure)(_ => true),
            validate[FiniteDuration](required("nestedRequired", "NOT_SET"), GenericTestFailure)(_ => true),
            validate[List[String]]("context.valueStrList", GenericTestFailure)(_ => false),
            validate[List[Double]]("context.valueDoubleList", GenericTestFailure)(_ => true)
          )(TestSettings)
        }
        matchOrFail(testConfig4) {
          case Validated.Invalid(errors) => errors.toList match {
            case List(ValueFailure("test.nestedRequired", _: ConfigException.BadValue), ValueFailure("test.context.valueStrList", GenericTestFailure)) =>
              assert(true)
          }
        }
        val testConfig5 = via[TestSettings]("test") { implicit config =>
          Applicative[ValidationFailure].map6(
            validate[String]("context.valueStr", GenericTestFailure)(_ => true),
            validate[Int]("context.valueInt", GenericTestFailure)(_ => true),
            validate[Double]("nestedVal", GenericTestFailure)(_ => true),
            validate[FiniteDuration](required("nestedRequired", "UNDEFINED"), GenericTestFailure)(_ => true),
            validate[List[String]]("context.valueStrList", GenericTestFailure)(_ => false),
            validate[List[Double]]("context.valueDoubleList", GenericTestFailure)(_ => true)
          )(TestSettings)
        }
        matchOrFail(testConfig5) {
          case Validated.Invalid(errors) => errors.toList match {
            case List(ValueFailure("test.nestedRequired", RequiredValueNotSet), ValueFailure("test.context.valueStrList", GenericTestFailure)) =>
              assert(true)
          }
        }
      }

      "unchecked building" in {
        val testConfig1 = via[TestSettings]("test") { implicit config =>
          Applicative[ValidationFailure].map6(
            unchecked[String]("context.valueStr"),
            unchecked[Int]("context.valueInt"),
            unchecked[Double]("nestedVal"),
            unchecked[FiniteDuration]("nestedDuration"),
            unchecked[List[String]]("context.valueStrList"),
            unchecked[List[Double]]("context.valueDoubleList")
          )(TestSettings)
        }
        matchOrFail(testConfig1) {
          case Validated.Valid(TestSettings("test string", 30, 50.68, duration, List("addr1:10", "addr2:20", "addr3:30"), List(10.2, 20, 0.123))) =>
            assert(duration == 4.hours)
        }
        val testConfig3 = via[TestSettings]("test") { implicit config =>
          Applicative[ValidationFailure].map6(
            unchecked[String]("context.valueStr"),
            unchecked[Int]("context.valueInt"),
            unchecked[Double]("bad-path.nestedVal"),
            unchecked[FiniteDuration]("nestedDuration"),
            unchecked[List[String]]("context.valueStrList"),
            unchecked[List[Double]]("context.valueDoubleList")
          )(TestSettings)
        }
        matchOrFail(testConfig3) {
          case Validated.Invalid(errors) => errors.toList match {
            case List(ValueFailure("test.bad-path.nestedVal", NullValue)) =>
              assert(true)
          }
        }
        val testConfig4 = via[TestSettings]("test") { implicit config =>
          Applicative[ValidationFailure].map6(
            unchecked[String]("context.valueStr"),
            unchecked[Int]("context.valueInt"),
            unchecked[Double]("nestedVal"),
            unchecked[FiniteDuration](required("nestedRequired", "NOT_SET")),
            unchecked[List[String]]("context.valueStrList"),
            unchecked[List[Double]]("context.valueDoubleList")
          )(TestSettings)
        }
        matchOrFail(testConfig4) {
          case Validated.Invalid(errors) => errors.toList match {
            case List(ValueFailure("test.nestedRequired", _: ConfigException.BadValue)) =>
              assert(true)
          }
        }
        val testConfig5 = via[TestSettings]("test") { implicit config =>
          Applicative[ValidationFailure].map6(
            unchecked[String]("context.valueStr"),
            unchecked[Int]("context.valueInt"),
            unchecked[Double]("nestedVal"),
            unchecked[FiniteDuration](required("nestedRequired", "UNDEFINED")),
            unchecked[List[String]]("context.valueStrList"),
            unchecked[List[Double]]("context.valueDoubleList")
          )(TestSettings)
        }
        matchOrFail(testConfig5) {
          case Validated.Invalid(errors) => errors.toList match {
            case List(ValueFailure("test.nestedRequired", RequiredValueNotSet)) =>
              assert(true)
          }
        }
      }
    }
  }

  "Ensure config files may be correctly parsed and validated" - {
    "invalid files fail to load" in {
      val validatedConfig =
        validateConfig[Settings]("non-existent.conf") { implicit config =>
          Applicative[ValidationFailure].map3(
            validate[String Refined MatchesRegex[W.`"[a-z0-9_-]+"`.T]]("name", NameShouldBeNonEmptyAndLowerCase)(_.value.matches("[a-z0-9_-]+")),
            validate[FiniteDuration]("http.timeout", ShouldNotBeNegative)(_ >= 0.seconds),
            via[HttpConfig]("http") { implicit config =>
              Applicative[ValidationFailure].map2(
                unchecked[String]("host"),
                validate[Int Refined Positive]("port", ShouldBePositive)(_.value > 0)
              )(HttpConfig)
            }
          )(Settings)
        }

      matchOrFail(validatedConfig) {
        case Validated.Invalid(FileNotFound("non-existent.conf", _)) =>
          assert(true)
      }
    }

    "files referencing non-existent (required) includes fail to load" in {
      val validatedConfig =
        validateConfig[Settings]("invalid.conf") { implicit config =>
          Applicative[ValidationFailure].map3(
            validate[String Refined MatchesRegex[W.`"[a-z0-9_-]+"`.T]]("name", NameShouldBeNonEmptyAndLowerCase)(_.value.matches("[a-z0-9_-]+")),
            validate[FiniteDuration]("http.timeout", ShouldNotBeNegative)(_ >= 0.seconds),
            via[HttpConfig]("http") { implicit config =>
              Applicative[ValidationFailure].map2(
                unchecked[String]("host"),
                validate[Int Refined Positive]("port", ShouldBePositive)(_.value > 0)
              )(HttpConfig)
            }
          )(Settings)
        }

      matchOrFail(validatedConfig) {
        case Validated.Invalid(FileNotFound(_, _: ConfigException)) =>
          assert(true)
      }
    }

    "valid file but with validation failure" in {
      case object NotAHttpPort extends Exception

      val validatedConfig =
        validateConfig[HttpConfig]("http.conf") { implicit config =>
          Applicative[ValidationFailure].map2(
            unchecked[String]("http.host"),
            validate[Int Refined Positive]("http.port", NotAHttpPort)(_.value != 80)
          )(HttpConfig)
        }

      assert(validatedConfig == Validated.Invalid(ValueErrors(ValueFailure("http.port", NotAHttpPort))))
    }

    "valid file but required values not set" in {
      val validatedConfig =
        validateConfig[Settings]("application.conf") { implicit config =>
          Applicative[ValidationFailure].map3(
            validate[String Refined MatchesRegex[W.`"[a-z0-9_-]+"`.T]]("name", NameShouldBeNonEmptyAndLowerCase)(_.value.matches("[a-z0-9_-]+")),
            validate[FiniteDuration](required("http.heartbeat", "NOT_SET"), ShouldNotBeNegative)(_ >= 0.seconds),
            via[HttpConfig]("http") { implicit config =>
              Applicative[ValidationFailure].map2(
                unchecked[String]("host"),
                validate[Int Refined Positive]("port", ShouldBePositive)(_.value > 0)
              )(HttpConfig)
            }
          )(Settings)
        }

      matchOrFail(validatedConfig) {
        case Validated.Invalid(ValueErrors(ValueFailure("http.heartbeat", RequiredValueNotSet))) =>
          assert(true)
      }
    }

    "with environment variable overrides" in {
      val envMapping: Config = ConfigFactory.parseString(
        """
          |env {
          |  AKKA_HOST = docker-local
          |  AKKA_PORT = 2552
          |  AKKA_BIND_HOST = google.co.uk
          |  AKKA_BIND_PORT = 123
          |
          |  optional.HTTP_ADDR = 192.168.99.100
          |  optional.HTTP_PORT = 5678
          |
          |  required.HEARTBEAT = 20s
          |}
        """.
          stripMargin
      )
      implicit val config = envMapping.withFallback(ConfigFactory.parseResourcesAnySyntax("application.conf")).resolve()

      val validatedConfig =
        Applicative[ValidationFailure].map3(
          validate[String Refined MatchesRegex[W.`"[a-z0-9_-]+"`.T]]("name", NameShouldBeNonEmptyAndLowerCase)(_.value.matches("[a-z0-9_-]+")),
          validate[FiniteDuration](required("http.heartbeat", "NOT_SET"), ShouldNotBeNegative)(_ >= 0.seconds),
          via[HttpConfig]("http") { implicit config =>
            Applicative[ValidationFailure].map2(
              unchecked[String]("host"),
              validate[Int Refined Positive]("port", ShouldBePositive)(_.value > 0)
            )(HttpConfig)
          }
        )(Settings)

      assert(validatedConfig.isValid)
      matchOrFail(validatedConfig) {
        case Validated.Valid(Settings(name, timeout, HttpConfig("192.168.99.100", port))) =>
        assert(name.value == "test-data")
        assert(port.value == 5678)
          assert(timeout == 20.seconds)
      }
    }

    "using system environment variable overrides" in {
      val validatedConfig =
        validateConfig[Settings]("application.conf") { implicit config =>
          Applicative[ValidationFailure].map3(
            unchecked[String Refined MatchesRegex[W.`"[a-z0-9_-]+"`.T]]("name"),
            validate[FiniteDuration]("http.timeout", ShouldNotBeNegative)(_ >= 0.seconds),
            via[HttpConfig]("http") { implicit config =>
              Applicative[ValidationFailure].map2(
                unchecked[String]("host"),
                unchecked[Int Refined Positive]("port")
              )(HttpConfig)
            }
          )(Settings)
        }

      assert(validatedConfig.isValid)
      matchOrFail(validatedConfig) {
        case Validated.Valid(Settings(name, timeout, HttpConfig("localhost", port))) =>
          assert(name.value == "test-data")
          assert(port.value == 80)
          assert(timeout == 30.seconds)
      }
    }
  }
}

// Copyright 2016 Carl Pulley

package cakesolutions.config

import cakesolutions.config.ValidatedConfigTest._
import com.typesafe.config.ConfigFactory
import org.scalatest.FreeSpec

import scala.concurrent.duration._
import scalaz.{-\/, \/-}

object ValidatedConfigTest {
  case object GenericTestFailure extends ConfigValidationFailure
  case object NameShouldBeNonEmptyAndLowerCase extends ConfigValidationFailure
  case object ShouldBePositive extends ConfigValidationFailure
  case object ShouldNotBeNegative extends ConfigValidationFailure

  final case class HttpConfig(host: String, port: Int)
  final case class Settings(name: String, timeout: FiniteDuration, http: HttpConfig)
}

class ValidatedConfigTest extends FreeSpec {
  "parameter checking" - {
    val fakeException = new RuntimeException("fake exception")
    implicit val config = ConfigFactory.parseString(
      """
        |top-level-name = "test"
        |test {
        |  nestedVal = 50.68
        |  nestedDuration = 4 h
        |  nestedList = []
        |  context {
        |    valueInt = 30
        |    valueStr = "test string"
        |    valueDuration = 12 ms
        |    valueStrList = [ "addr1:10", "addr2:20", "addr3:30" ]
        |    valueDoubleList = [ 10.2, 20, 0.123 ]
        |  }
        |}
      """.stripMargin)

    "validate method" in {
      assert(validate[String]("top-level-name", GenericTestFailure)("test" == _) == \/-("test"))
      assert(validate[Double]("test.nestedVal", GenericTestFailure)(50.68 == _) == \/-(50.68))
      assert(validate[FiniteDuration]("test.nestedDuration", GenericTestFailure)(4.hours == _) == \/-(4.hours))
      assert(validate[List[Double]]("test.nestedList", GenericTestFailure)(_.isEmpty) == \/-(List.empty[Double]))
      assert(validate[Int]("test.context.valueInt", GenericTestFailure)(30 == _) == \/-(30))
      assert(validate[String]("test.context.valueStr", GenericTestFailure)("test string" == _) == \/-("test string"))
      assert(validate[FiniteDuration]("test.context.valueDuration", GenericTestFailure)(12.milliseconds == _) == \/-(12.milliseconds))
      assert(validate[List[String]]("test.context.valueStrList", GenericTestFailure)(List("addr1:10", "addr2:20", "addr3:30") == _) == \/-(List("addr1:10", "addr2:20", "addr3:30")))
      assert(validate[List[Double]]("test.context.valueDoubleList", GenericTestFailure)(List(10.2, 20, 0.123) == _) == \/-(List(10.2, 20, 0.123)))
      assert(validate[List[Int]]("test.context.valueDoubleList", GenericTestFailure)(_ => true) == \/-(List(10, 20, 0)))
      assert(validate[List[String]]("test.context.valueDoubleList", GenericTestFailure)(_ => true) == \/-(List("10.2", "20", "0.123")))

      assert(validate[String]("top-level-name", GenericTestFailure)(_ => false) == -\/(ValueFailure("top-level-name", GenericTestFailure)))
      validate[String]("invalid-path", GenericTestFailure)(_ => true) match {
        case -\/(ValueFailure("invalid-path", NullValue)) =>
          assert(true)
        case result =>
          assert(false, result)
      }
      validate[String]("test.invalid-path", GenericTestFailure)(_ => true) match {
        case -\/(ValueFailure("test.invalid-path", NullValue)) =>
          assert(true)
        case result =>
          assert(false, result)
      }
      validate[Int]("top-level-name", GenericTestFailure)(_ => true) match {
        case -\/(ValueFailure("top-level-name", InvalidValueType(_))) =>
          assert(true)
        case result =>
          assert(false, result)
      }
      validate[String]("top-level-name", GenericTestFailure)(_ => throw fakeException) match {
        case -\/(ValueFailure("top-level-name", InvalidValueType(fakeException))) =>
          assert(true)
        case result =>
          assert(false, result)
      }
    }

    "unchecked method" in {
      assert(unchecked[String]("top-level-name") == \/-("test"))
      assert(unchecked[Double]("test.nestedVal") == \/-(50.68))
      assert(unchecked[FiniteDuration]("test.nestedDuration") == \/-(4.hours))
      assert(unchecked[List[Double]]("test.nestedList") == \/-(List.empty[Double]))
      assert(unchecked[Int]("test.context.valueInt") == \/-(30))
      assert(unchecked[String]("test.context.valueStr") == \/-("test string"))
      assert(unchecked[FiniteDuration]("test.context.valueDuration") == \/-(12.milliseconds))
      assert(unchecked[List[String]]("test.context.valueStrList") == \/-(List("addr1:10", "addr2:20", "addr3:30")))
      assert(unchecked[List[Double]]("test.context.valueDoubleList") == \/-(List(10.2, 20, 0.123)))
      assert(unchecked[List[Int]]("test.context.valueDoubleList") == \/-(List(10, 20, 0)))
      assert(unchecked[List[String]]("test.context.valueDoubleList") == \/-(List("10.2", "20", "0.123")))

      unchecked[String]("invalid-path") match {
        case -\/(ValueFailure("invalid-path", NullValue)) =>
          assert(true)
        case result =>
          assert(false, result)
      }
      unchecked[String]("test.invalid-path") match {
        case -\/(ValueFailure("test.invalid-path", NullValue)) =>
          assert(true)
        case result =>
          assert(false, result)
      }
      unchecked[Int]("top-level-name") match {
        case -\/(ValueFailure("top-level-name", InvalidValueType(_))) =>
          assert(true)
        case result =>
          assert(false, result)
      }
    }

    "case class building methods" - {
      case class TestSettings(str: String, int: Int, double: Double, duration: FiniteDuration, strList: List[String], doubleList: List[Double])

      "validated building" in {
        via("test") { implicit config =>
          build[TestSettings](
            validate[String]("context.valueStr", GenericTestFailure)(_ => true),
            validate[Int]("context.valueInt", GenericTestFailure)(_ => true),
            validate[Double]("nestedVal", GenericTestFailure)(_ => true),
            validate[FiniteDuration]("nestedDuration", GenericTestFailure)(_ => true),
            validate[List[String]]("context.valueStrList", GenericTestFailure)(_ => true),
            validate[List[Double]]("context.valueDoubleList", GenericTestFailure)(_ => true)
          )
        } match {
          case \/-(TestSettings("test string", 30, 50.68, duration, List("addr1:10", "addr2:20", "addr3:30"), List(10.2, 20, 0.123))) =>
            assert(duration == 4.hours)
          case result =>
            assert(false, result)
        }
        via("test") { implicit config =>
          build[TestSettings](
            validate[Int]("context.valueStr", GenericTestFailure)(_ => true),
            validate[Int]("context.valueInt", GenericTestFailure)(_ => true),
            validate[Double]("nestedVal", GenericTestFailure)(_ => true),
            validate[FiniteDuration]("nestedDuration", GenericTestFailure)(_ => true),
            validate[List[String]]("context.valueStrList", GenericTestFailure)(_ => true),
            validate[List[Double]]("context.valueDoubleList", GenericTestFailure)(_ => true)
          )
        } match {
          case -\/(NestedConfigError(ConfigError(ValueFailure("test.context.valueStr", InvalidValueType(_))))) =>
            assert(true)
          case result =>
            assert(false, result)
        }
        via("test") { implicit config =>
          build[TestSettings](
            validate[Int]("context.valueStr", GenericTestFailure)(_ => true),
            validate[Int]("context.valueInt", GenericTestFailure)(_ => true),
            validate[Double]("bad-path.nestedVal", GenericTestFailure)(_ => true),
            validate[FiniteDuration]("nestedDuration", GenericTestFailure)(_ => true),
            validate[List[String]]("context.valueStrList", GenericTestFailure)(_ => false),
            validate[List[Double]]("context.valueDoubleList", GenericTestFailure)(_ => true)
          )
        } match {
          case -\/(NestedConfigError(ConfigError(ValueFailure("test.context.valueStr", InvalidValueType(_)), ValueFailure("test.bad-path.nestedVal", NullValue), ValueFailure("test.context.valueStrList", GenericTestFailure)))) =>
            assert(true)
          case result =>
            assert(false, result)
        }
      }

      "unchecked building" in {
        via("test") { implicit config =>
          build[TestSettings](
            unchecked[String]("context.valueStr"),
            unchecked[Int]("context.valueInt"),
            unchecked[Double]("nestedVal"),
            unchecked[FiniteDuration]("nestedDuration"),
            unchecked[List[String]]("context.valueStrList"),
            unchecked[List[Double]]("context.valueDoubleList")
          )
        } match {
          case \/-(TestSettings("test string", 30, 50.68, duration, List("addr1:10", "addr2:20", "addr3:30"), List(10.2, 20, 0.123))) =>
            assert(duration == 4.hours)
          case result =>
            assert(false, result)
        }
        via("test") { implicit config =>
          build[TestSettings](
            unchecked[Int]("context.valueStr"),
            unchecked[Int]("context.valueInt"),
            unchecked[Double]("nestedVal"),
            unchecked[FiniteDuration]("nestedDuration"),
            unchecked[List[String]]("context.valueStrList"),
            unchecked[List[Double]]("context.valueDoubleList")
          )
        } match {
          case -\/(NestedConfigError(ConfigError(ValueFailure("test.context.valueStr", InvalidValueType(_))))) =>
            assert(true)
          case result =>
            assert(false, result)
        }
        via("test") { implicit config =>
          build[TestSettings](
            unchecked[Int]("context.valueStr"),
            unchecked[Int]("context.valueInt"),
            unchecked[Double]("bad-path.nestedVal"),
            unchecked[FiniteDuration]("nestedDuration"),
            unchecked[List[String]]("context.valueStrList"),
            unchecked[List[Double]]("context.valueDoubleList")
          )
        } match {
          case -\/(NestedConfigError(ConfigError(ValueFailure("test.context.valueStr", InvalidValueType(_)), ValueFailure("test.bad-path.nestedVal", NullValue)))) =>
            assert(true)
          case result =>
            assert(false, result)
        }
      }
    }
  }

  "Ensure config files may be correctly parsed and validated" - {
    "invalid files fail to load" in {
      val validatedConfig =
        validateConfig("non-existent.conf") { implicit config =>
          build[Settings](
            validate[String]("name", NameShouldBeNonEmptyAndLowerCase)(_.matches("[a-z0-9_-]+")),
            validate[FiniteDuration]("http.timeout", ShouldNotBeNegative)(_ >= 0.seconds),
            via("http") { implicit config =>
              build[HttpConfig](
                unchecked[String]("host"),
                validate[Int]("port", ShouldBePositive)(_ > 0)
              )
            }
          )
        }

      validatedConfig match {
        case -\/(ConfigError(FileNotFound("non-existent.conf", _))) =>
          assert(true)
        case result =>
          assert(false, result)
      }
    }

    "with environment variable overrides" in {
      val envMapping = ConfigFactory.parseString(
        """
          |env {
          |  AKKA_HOST = docker-local
          |  AKKA_PORT = 2552
          |  AKKA_BIND_HOST = google.co.uk
          |  AKKA_BIND_PORT = 123
          |
          |  HTTP_ADDR = 192.168.99.100
          |  HTTP_PORT = 5678
          |}
        """.
          stripMargin
      )
      implicit val config = ConfigFactory.parseResourcesAnySyntax("application.conf").withFallback(envMapping).resolve()

      val validatedConfig =
        build[Settings](
          validate[String]("name", NameShouldBeNonEmptyAndLowerCase)(_.matches("[a-z0-9_-]+")),
          validate[FiniteDuration]("http.timeout", ShouldNotBeNegative)(_ >= 0.seconds),
          via("http") { implicit config =>
            build[HttpConfig](
              unchecked[String]("host"),
              validate[Int]("port", ShouldBePositive)(_ > 0)
            )
          }
        )

      assert(validatedConfig.isRight)
      validatedConfig match {
        case \/-(Settings("test-data", timeout, HttpConfig("192.168.99.100", 5678))) =>
          assert(timeout == 30.seconds)
        case result =>
          assert(false, result)
      }
    }

    "using system environment variable overrides" in {
      val validatedConfig =
        validateConfig("application.conf") { implicit config =>
          build[Settings](
            validate[String]("name", NameShouldBeNonEmptyAndLowerCase)(_.matches("[a-z0-9_-]+")),
            validate[FiniteDuration]("http.timeout", ShouldNotBeNegative)(_ >= 0.seconds),
            via("http") { implicit config =>
              build[HttpConfig](
                unchecked[String]("host"),
                validate[Int]("port", ShouldBePositive)(_ > 0)
              )
            }
          )
        }

      assert(validatedConfig.isRight)
      validatedConfig match {
        case \/-(Settings("test-data", timeout, HttpConfig("localhost", 80))) =>
          assert(timeout == 30.seconds)
        case result =>
          assert(false, result)
      }
    }
  }
}

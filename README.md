# Validated Typesafe Configuration

When building reactive applications, it is important to fail early and 
to avoid throwing exceptions. Here, we apply these principles to the
[Typesafe config](https://github.com/typesafehub/config) library without
introducing unnecessary boilerplate code.

[![Build Status](https://secure.travis-ci.org/carlpulley/validated-config.png?tag=0.0.1)](http://travis-ci.org/carlpulley/validated-config)
[![Maven Central](https://img.shields.io/maven-central/v/org.apache.maven/apache-maven.svg?maxAge=2592000)](http://search.maven.org/#artifactdetails%7Cnet.cakesolutions%7Cvalidated-config_2.11%7C0.0.1%7Cjar)
[![Apache 2](https://img.shields.io/hexpm/l/plug.svg?maxAge=2592000)](http://www.apache.org/licenses/LICENSE-2.0.txt)
[![API](https://readthedocs.org/projects/pip/badge/)](https://carlpulley.github.io/validated-config/latest/api#cakesolutions.config.package)
[![Codacy Badge](https://api.codacy.com/project/badge/Grade/4cb77ad257344e6185603dceb7b2af65)](https://www.codacy.com/app/c-pulley/validated-config)
[![Codacy Badge](https://api.codacy.com/project/badge/Coverage/4cb77ad257344e6185603dceb7b2af65)](https://www.codacy.com/app/c-pulley/validated-config)

## Setup and Usage

To use this library, add the following dependency to your `build.sbt`
file:
```
libraryDependencies += "net.cakesolutions" %% "validated-config" % "0.0.2-SNAPSHOT"
```

To access the validated Typesafe configuration library code in your
project code, simply `import cakesolutions.config._`.

## Validating Configuration Values

Using [Typesafe config](https://github.com/typesafehub/config) we read in and parse configuration files.
Paths into these files are then retrieved and type checked using [Ficus](https://github.com/iheartradio/ficus).

Using a lightweight DSL, we are able to then check and validate these
type checked values. For example, given that the Typesafe configuration:
```
top-level-name = "test"
test {
  nestedVal = 50.68
  nestedDuration = 4 h
  nestedList = []
  context {
    valueInt = 30
    valueStr = "test string"
    valueDuration = 12 ms
    valueStrList = [ "addr1:10", "addr2:20", "addr3:30" ]
    valueDoubleList = [ 10.2, 20, 0.123 ]
  }
}
```
has been parsed and read into an implicit of type `Config`, then we are
able to validate that the value at the path `test.nestedVal` has type
`Double` and that it satisfies specified size bounds as follows:
```scala
case object ShouldBeAPercentageValue extends ConfigValidationFailure

validate[Double]("test.nestedVal", ShouldBeAPercentageValue)(n => 0 <= n && n <= 100)
```
If the configuration value at path `test.nestedVal` fails to pass the
percentage bounds check, then `-\/(ShouldBeAPercentageValue)` is
returned.

Likewise, we can enforce that all values in the array at the path
`test.context.valueStrList` match the regular expression pattern
`[a-z0-9]+:[0-9]+` as follows:
```scala
case object ShouldBeASocketValue extends ConfigValidationFailure

validate[List[String]]("test.context.valueStrList", ShouldBeASocketValue)(_.matches("[a-z0-9]+:[0-9]+"))
```

In some instances, we may not care about checking the value at a
configuration path. In these cases we can use `unchecked`:
```scala
unchecked[FiniteDuration]("test.nestedDuration")
```

## Building Validated `Config` Instances

Building validated configuration case class instances is performed using
the methods `via` and `build`. `via` allows the currently in-scope
implicit `Config` instance to be restricted to a specified path. `build`
constructs the case class specified in its type constraint. To do this,
`build` takes a list of arguments that should be the results of either
building inner validated case class instances or from using the
`validate` or `unchecked` methods to validate values at a given path.

## Secure Validated `Config` Instances

Sometimes, after validating and building a `Config` instance, we do not
wish the constructed case class instance to be modified (e.g. by calling
a `copy` constructor) - allowing this could allow validation guarantees
to be broken! In such scenarios, secure configuration objects may then
be constructed as follows:
```scala
import cakesolutions.config.secure._

object SecureValidatedConfig {
  @CaseClassLike
  final case class SecureHttpConfig private[SecureValidatedConfig] (host: String, port: Int)
  @CaseClassLike
  final case class SecureSettings private[SecureValidatedConfig] (name: String, timeout: FiniteDuration, http: SecureHttpConfig)

  def apply(resource: String): SecureSettings = {
    validateConfig(resource) { implicit config =>
      build[SecureSettings](
        validate[String]("name", NameShouldBeNonEmptyAndLowerCase)(_.matches("[a-z0-9_-]+")),
        validate[FiniteDuration]("http.timeout", ShouldNotBeNegative)(_ >= 0.seconds),
        via("http") { implicit config =>
          build[SecureHttpConfig](
            unchecked[String]("host"),
            validate[Int]("port", ShouldBePositive)(_ > 0)
          )
        }
      )
    }
  }
}

SecureValidatedConfig("application.conf") foreach {
  case \/-(SecureSettings(name, _, SecureHttpConfig(host, port))) =>
    println(s"$name = $host:$port")
}
```

Note how:
* the class annotation `@CaseClassLike` constructs a case class like class
  (with parameter `val`'s, `toString` and equality methods) and a companion
  object (whose `apply` has the same access modifiers applied as the classes
  constructor
* the resulting validated configuration instance `SecureSettings` can not
  be modified or copied after it has been created
* how there is only one way to create instances of `SecureSettings`
* and, the resulting validated configuration instance can still be used
  (e.g. in pattern matching) as a typical case class.

In order to use the `@CaseClassLike` annotation it is necessary to include
the [Macro Paradise](http://docs.scala-lang.org/overviews/macros/paradise.html) plugin in your project.

## Parsing Custom Configuration Values

As both `unchecked` and `validate` use [Ficus](https://github.com/iheartradio/ficus) [ValueReader](https://github.com/iheartradio/ficus/blob/master/src/main/scala/net/ceedubs/ficus/readers/ValueReader.scala)'s to parse
and type check configuration values, we only need to define a [custom extractor](https://github.com/iheartradio/ficus#custom-extraction).

## Example

Given the following Scala case classes:
```scala
case object NameShouldBeNonEmptyAndLowerCase extends ConfigValidationFailure
case object ShouldBePositive extends ConfigValidationFailure

final case class HttpConfig(host: String, port: Int)
final case class Settings(name: String, timeout: FiniteDuration, http: HttpConfig)
```
and the following Typesafe configuration objects (e.g. stored in a file named `application.conf`):
```
name = "test-data"
http {
  host = "localhost"
  host = ${?HTTP_ADDR}
  port = 80
  port = ${?HTTP_PORT}

  timeout = 30 s
}
```
then we can generate a validated `Settings` case class instance as
follows:
```scala
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
```

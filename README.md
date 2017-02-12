# Validated Typesafe Configuration

When building reactive applications, it is important to fail early and 
to avoid throwing exceptions. Here, we apply these principles to the
[Typesafe config](https://github.com/typesafehub/config) library without
introducing unnecessary boilerplate code.

[![Build Status](https://secure.travis-ci.org/carlpulley/validated-config.png?tag=0.1.2)](http://travis-ci.org/carlpulley/validated-config)
[![Maven Central](https://img.shields.io/badge/maven--central-v0.1.2-blue.svg)](http://search.maven.org/#artifactdetails%7Cnet.cakesolutions%7Cvalidated-config_2.12%7C0.1.2%7Cjar)
[![Apache 2](https://img.shields.io/hexpm/l/plug.svg?maxAge=2592000)](http://www.apache.org/licenses/LICENSE-2.0.txt)
[![API](https://readthedocs.org/projects/pip/badge/)](https://carlpulley.github.io/validated-config/latest/api#cakesolutions.config.package)
[![Codacy Badge](https://api.codacy.com/project/badge/Grade/4cb77ad257344e6185603dceb7b2af65)](https://www.codacy.com/app/c-pulley/validated-config)
[![Codacy Badge](https://api.codacy.com/project/badge/Coverage/4cb77ad257344e6185603dceb7b2af65)](https://www.codacy.com/app/c-pulley/validated-config)

## Setup and Usage

To use this library, add the following dependency to your `build.sbt`
file:
```
libraryDependencies += "net.cakesolutions" %% "validated-config" % "0.1.2"
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
case object ShouldBeAPercentageValue extends Exception

validate[Double]("test.nestedVal", ShouldBeAPercentageValue)(n => 0 <= n && n <= 100)
```
If the configuration value at path `test.nestedVal` fails to pass the
percentage bounds check, then `Left(ShouldBeAPercentageValue)` is
returned.

Likewise, we can enforce that all values in the array at the path
`test.context.valueStrList` match the regular expression pattern
`[a-z0-9]+:[0-9]+` as follows:
```scala
case object ShouldBeASocketValue extends Exception

validate[List[String]]("test.context.valueStrList", ShouldBeASocketValue)(_.matches("[a-z0-9]+:[0-9]+"))
```

In some instances, we may not care about checking the value at a
configuration path. In these cases we can use `unchecked`:
```scala
unchecked[FiniteDuration]("test.nestedDuration")
```

When we require a path to have a value set, then we have two possible
options:
- check if the configuration path exists and is defined
- or, use a sentinal value and validate the path has a differing value.

When configuration paths are specific to your application, then the first
of these approaches is suitable to use. However, if you are overriding the
values in 3rd party libraries and require a value to be set, it is necessary
to use sentinal values.

In the first case, we use `required` as follows:
```scala
unchecked[FiniteDuration](required("test.nestedDuration"))
```
When using `required` with sentinal values, it is necessary to specify what
the expected sentinal value is as follows:
```scala
unchecked[FiniteDuration](required("test.nestedDuration", "UNDEFINED"))
```

## Building Validated `Config` Instances

Building validated configuration case class instances is performed using
the methods `via` and `build`. `via` allows the currently in-scope
implicit `Config` instance to be restricted to a specified path. `build`
constructs the case class specified in its type constraint. To do this,
`build` takes a list of arguments that should be the results of either
building inner validated case class instances or from using the
`validate` or `unchecked` methods to validate values at a given path.

## Parsing Custom Configuration Values

As both `unchecked` and `validate` use [Ficus](https://github.com/iheartradio/ficus) [ValueReader](https://github.com/iheartradio/ficus/blob/master/src/main/scala/net/ceedubs/ficus/readers/ValueReader.scala)'s to parse
and type check configuration values, we only need to define a [custom extractor](https://github.com/iheartradio/ficus#custom-extraction).

## Example

Given the following Scala case classes:
```scala
case object NameShouldBeNonEmptyAndLowerCase extends Exception
case object ShouldBePositive extends Exception

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
     validate[FiniteDuration]("http.timeout", ShouldBePositive)(_ >= 0.seconds),
     via("http") { implicit config =>
       build[HttpConfig](
         unchecked[String]("host"),
         validate[Int]("port", ShouldBePositive)(_ > 0)
       )
     }
   )
 }
```
Internally, we use `scala.Either` for error signal management - however, `validateConfig` aggregates and materialises
such errors as a `Try` instance.

## Secure Validated Configuration

Using abstract sealed case classes, we can validate configuration
data and then ensure that the validated case class instances can
not be faked (e.g. via copy constructors or uses of the apply method).

If we want to do this, then the previous example's case classes could
be rewritten as say:
```scala
package cakesolutions.example

import cakesolutions.config._
import scala.concurrent.duration._
import scala.util.Try

object LoadValidatedConfig {
  sealed abstract case class HttpConfig(host: String, port: Int)
  sealed abstract case class Settings(name: String, timeout: FiniteDuration, http: HttpConfig)
  
  // Following allows Shapeless to create instances of our sealed abstract case classes
  private implicit val genHttpConfig: Generic[HttpConfig] = new Generic[HttpConfig] {
    type Repr = String :: Int :: HNil
 
    def to(t: HttpConfig): Repr =
      t.host :: t.port :: HNil
    def from(r: Repr): HttpConfig =
      new HttpConfig(r(0), r(1)) {}
  }
  private implicit val genSettings: Generic[Settings] = new Generic[Settings] {
    type Repr = String :: FiniteDuration :: HttpConfig :: HNil
 
    def to(t: Settings): Repr =
      t.name :: t.timeout :: t.http :: HNil
    def from(r: Repr): Settings =
      new Settings(r(0), r(1), r(2)) {}
  }

  def apply(): Try[Settings] =
    validateConfig("application.conf") { implicit config =>
      build[Settings](
        validate[String]("name", NameShouldBeNonEmptyAndLowerCase)(_.matches("[a-z0-9_-]+")),
        validate[FiniteDuration]("http.timeout", ShouldBePositive)(_ >= 0.seconds),
        via("http") { implicit config =>
          build[HttpConfig](
            unchecked[String]("host"),
            validate[Int]("port", ShouldBePositive)(_ > 0)
          )
        }
      )
    }
}
```
Here, package `cakesolutions.example` is responsible for loading and parsing our configuration files.

As first reported by [@tpolecat](https://gist.github.com/tpolecat/a5cb0dc9adeacc93f846835ed21c92d2) and discussed further in 
[Enforcing invariants in Scala datatypes](http://www.cakesolutions.net/teamblogs/enforcing-invariants-in-scala-datatypes), the use of an sealed abstract case class
ensures that constructors, copy constructors and companion apply methods are not created 
by the compiler. Hence, the only way that instances of `HttpConfig` and `Settings` can be
created is via the package protected code in the respective implicits - and so
we ensure that all such validated configurations are compile time checked as being invariant!

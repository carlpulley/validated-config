# Validated Typesafe Configuration

When building reactive applications, it is important to fail early and 
to avoid throwing exceptions. Here, we apply these principles to the
[Typesafe config](https://github.com/typesafehub/config) library.

## Setup and Usage

To use this library, add the following dependency to your `build.sbt`
file:
```
libraryDependencies += "cakesolutions.net" %% "validated-config" % "0.1-SNAPSHOT"
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

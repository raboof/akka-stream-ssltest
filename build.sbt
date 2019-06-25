name := """akka-stream-ssltest"""

scalaVersion := "2.12.8"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-stream" % "2.5.23"
)

mainClass in (Compile, run) := Some("SslTest")

fork in run := true

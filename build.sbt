name := """akka-stream-ssltest"""

scalaVersion := "2.11.7"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-stream-experimental" % "2.0.3"
)

mainClass in (Compile, run) := Some("SslTest")

fork in run := true

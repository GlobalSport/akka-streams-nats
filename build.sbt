name := "akka-streams-nats"

organization := "com.mycoachsport"

version := IO.read(new File("VERSION")).mkString.trim + "-SNAPSHOT"

scalaVersion := "2.13.12"

crossScalaVersions := Seq("2.12.18", "2.13.9")

isSnapshot := true

publishMavenStyle := true

publishArtifact in Test := false

// Native sbt Sonatype Central support (sbt >= 1.11, requires >= 1.12.15 to be usable -
// see project/build.properties). Replaces the old oss.sonatype.org Ivy resolver, which
// can only send HTTP Basic auth - the OSSRH Staging API compatibility bridge that
// replaced oss.sonatype.org requires a Bearer-scheme Authorization header instead,
// which sbt's built-in Credentials/Ivy publish mechanism cannot produce.
publishTo := {
  val centralSnapshots = "https://central.sonatype.com/repository/maven-snapshots/"
  if (isSnapshot.value)
    Some("central-snapshots" at centralSnapshots)
  else
    localStaging.value
}

description := "An Akka Streams source connecting to a nats.io server"

credentials += Credentials(
  "Sonatype Nexus Repository Manager",
  "central.sonatype.com",
  sys.env.getOrElse("SONATYPE_USERNAME", ""),
  sys.env.getOrElse("SONATYPE_PASSWORD", "")
)

pomIncludeRepository := { _ =>
  false
}

pomExtra := (<url>https://github.com/GlobalSport/akka-streams-nats</url>
  <licenses>
    <license>
      <name>MIT</name>
      <url>https://opensource.org/licenses/MIT</url>
      <distribution>repo</distribution>
    </license>
  </licenses>
  <scm>
    <url>git@github.com:GlobalSport/akka-streams-nats.git</url>
    <connection>scm:git:git@github.com:GlobalSport/akka-streams-nats.git</connection>
  </scm>
  <developers>
    <developer>
      <id>imclem</id>
      <name>Clément Agarini</name>
      <url>https://github.com/imclem</url>
    </developer>
  </developers>)

usePgpKeyHex("06173C5A215C0905B5E8DAEDAE04AE010E5DD622")
pgpPassphrase := sys.env.get("PGP_PASS").map(_.toArray)

val AkkaVersion = "2.6.20"

libraryDependencies := Seq(
  "com.typesafe.akka" %% "akka-stream" % AkkaVersion,
  "com.typesafe.akka" %% "akka-stream-testkit" % AkkaVersion,
  "com.typesafe.akka" %% "akka-stream-testkit" % AkkaVersion % Test,
  "org.scalatest" %% "scalatest" % "3.0.8" % Test,
  "org.scalamock" %% "scalamock" % "4.4.0" % Test,
  "org.testcontainers" % "testcontainers" % "1.17.5" % Test,
  "io.nats" % "jnats" % "2.26.2"
)

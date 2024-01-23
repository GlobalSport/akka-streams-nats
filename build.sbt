name := "akka-streams-nats"

organization := "com.mycoachsport"

version := IO.read(new File("VERSION")).mkString.trim + "-SNAPSHOT"

scalaVersion := "2.13.12"

crossScalaVersions := Seq( "2.12.18", "2.13.9")

isSnapshot := true

publishMavenStyle := true

publishArtifact in Test := false

publishTo := {
  val nexus = "https://oss.sonatype.org/"
  if (isSnapshot.value)
    Some("snapshots" at nexus + "content/repositories/snapshots")
  else
    Some("releases" at nexus + "service/local/staging/deploy/maven2")
}

credentials += Credentials(
  file(s"${baseDirectory.value.getAbsolutePath}/nexus.credentials")
)

credentials in GlobalScope += Credentials(
  file(s"${baseDirectory.value.getAbsolutePath}/pgp.credentials")
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

pgpPublicRing := file("public.key")
pgpSecretRing := file("private.key")

val AkkaVersion = "2.6.20"

libraryDependencies := Seq(
  "com.typesafe.akka" %% "akka-stream" % AkkaVersion,
  "com.typesafe.akka" %% "akka-stream-testkit" % AkkaVersion,
  "com.typesafe.akka" %% "akka-stream-testkit" % AkkaVersion % Test,
  "org.scalatest" %% "scalatest" % "3.0.8" % Test,
  "org.scalamock" %% "scalamock" % "4.4.0" % Test,
  "org.testcontainers" % "testcontainers" % "1.17.5" % Test,
  "io.nats" % "jnats" % "2.17.0"
)

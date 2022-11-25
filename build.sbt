import Dependencies._

showCurrentGitBranch

git.useGitDescribe := true

lazy val commonSettings = Seq(
  organization := "org.hathitrust.htrc",
  organizationName := "HathiTrust Research Center",
  organizationHomepage := Some(url("https://www.hathitrust.org/htrc")),
  scalaVersion := "2.13.10",
  scalacOptions ++= Seq(
    "-feature",
    "-deprecation",
    "-language:postfixOps",
    "-language:implicitConversions"
  ),
  resolvers ++= Seq(
    Resolver.mavenLocal,
    "HTRC Nexus Repository" at "https://nexus.htrc.illinois.edu/repository/maven-public"
  ),
  externalResolvers := Resolver.combineDefaultResolvers(resolvers.value.toVector, mavenCentral = false),
  Compile / packageBin / packageOptions += Package.ManifestAttributes(
    ("Git-Sha", git.gitHeadCommit.value.getOrElse("N/A")),
    ("Git-Branch", git.gitCurrentBranch.value),
    ("Git-Version", git.gitDescribedVersion.value.getOrElse("N/A")),
    ("Git-Dirty", git.gitUncommittedChanges.value.toString),
    ("Build-Date", new java.util.Date().toString)
  )
)

lazy val wartRemoverSettings = Seq(
  Compile / compile / wartremoverWarnings ++= Warts.unsafe.diff(Seq(
    Wart.DefaultArguments,
    Wart.NonUnitStatements,
    Wart.Any,
    Wart.StringPlusAny,
    Wart.OptionPartial
  ))
)

lazy val ammoniteSettings = Seq(
  libraryDependencies +=
    {
      val version = scalaBinaryVersion.value match {
        case "2.10" => "1.0.3"
        case _ ⇒  "2.5.5"
      }
      "com.lihaoyi" % "ammonite" % version % Test cross CrossVersion.full
    },
  Test / sourceGenerators += Def.task {
    val file = (Test / sourceManaged).value / "amm.scala"
    IO.write(file, """object amm extends App { ammonite.Main.main(args) }""")
    Seq(file)
  }.taskValue,
  connectInput := true,
  outputStrategy := Some(StdoutOutput)
)

lazy val `marcjson2bibframexml` = (project in file("."))
  .enablePlugins(GitVersioning, GitBranchPrompt, JavaAppPackaging)
  .settings(commonSettings)
  .settings(ammoniteSettings)
//  .settings(spark("3.3.1"))
  .settings(spark_dev("3.3.1"))
  .settings(
    name := "marcjson2bibframexml",
    description := "Used to convert MARC-in-JSON to BIBFRAME-XML",
    licenses += "Apache2" -> url("http://www.apache.org/licenses/LICENSE-2.0"),
    libraryDependencies ++= Seq(
      "org.rogach"                    %% "scallop"              % "4.1.0",
      "org.marc4j"                    %  "marc4j"               % "2.9.2",
//      "org.scala-lang.modules"        %% "scala-xml"            % "1.2.0",
      "org.hathitrust.htrc"           %% "scala-utils"          % "2.13",
      "org.hathitrust.htrc"           %% "spark-utils"          % "1.4",
      "com.github.nscala-time"        %% "nscala-time"          % "2.32.0",
      "ch.qos.logback"                %  "logback-classic"      % "1.4.5",
      "org.codehaus.janino"           %  "janino"               % "3.1.9",
      "org.scalacheck"                %% "scalacheck"           % "1.17.0"      % Test,
      "org.scalatest"                 %% "scalatest"            % "3.2.14"       % Test
    ),
//    dependencyOverrides ++= Seq(
//      "com.google.guava" % "guava" % "15.0",
//      "com.fasterxml.jackson.core" % "jackson-databind" % "2.6.7.1"
//    )
  )
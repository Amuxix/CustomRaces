import java.text.SimpleDateFormat
import java.util.{Calendar, Properties}

name    := "CustomRaces"
version := "0.1"

scalaVersion   := "3.3.1"
// format: off
javacOptions ++= Seq("-Xlint", "-encoding", "UTF-8")
scalacOptions ++= Seq(
  "-explain",                          // Explain errors in more detail.
  "-explain-types",                    // Explain type errors in more detail.
  "-indent",                           // Allow significant indentation.
  "-new-syntax",                       // Require `then` and `do` in control expressions.
  "-feature",                          // Emit warning and location for usages of features that should be imported explicitly.
  "-source:future",                    // better-monadic-for
  "-language:implicitConversions",     // Allow implicit conversions
  "-language:higherKinds",             // Allow higher-kinded types
  "-language:postfixOps",              // Explicitly enables the postfix ops feature
  "-deprecation",                      // Emit warning and location for usages of deprecated APIs.
  "-Wunused:all",
  "-Wnonunit-statement",
  "-Xfatal-warnings",                  // Fail the compilation if there are any warnings.
  //"-Yexplicit-nulls",                  // Make reference types non-nullable. Nullable types can be expressed with unions: e.g. String|Null.
  //"-Yshow-suppressed-errors",          // Also show follow-on errors and warnings that are normally suppressed.
  //"-rewrite",
)
// format: on

resolvers ++= Seq(
  Opts.resolver.sonatypeSnapshots,
  Opts.resolver.sonatypeReleases,
  // "Spigot Snapshots Repo" at "https://hub.spigotmc.org/nexus/content/repositories/snapshots/",
  // "Spigot Repo" at "https://hub.spigotmc.org/nexus/content/repositories/releases/",
  "papermc" at "https://papermc.io/repo/repository/maven-public/",
)

libraryDependencies ++= Seq(
  papermc,
  circeCore,
  circeParser,
  fs2,
  fs2IO,
)
lazy val apiVersion   = "1.20.2"
lazy val papermc = "io.papermc.paper" % "paper-api" % s"$apiVersion-R0.1-SNAPSHOT" % Provided
lazy val fs2         = "co.fs2"   %% "fs2-core"     % "3.7.0"
lazy val fs2IO       = "co.fs2"   %% "fs2-io"       % fs2.revision
lazy val circeCore   = "io.circe" %% "circe-core"   % "0.14.5"
lazy val circeParser = "io.circe" %% "circe-parser" % circeCore.revision

assembly / assemblyJarName := s"${name.value}-${version.value}.jar"

(Compile / resourceGenerators) += Def.task {
  val file      = (Compile / resourceManaged).value / "plugin.yml"
  val date      = new SimpleDateFormat("yyyyMMdd-HH:mm").format(Calendar.getInstance().getTime)
  val pluginYml =
    s"""name: ${name.value}
       |version: ${version.value}-$date
       |author: Amuxix
       |main: win.amuxix.customraces.CustomRaces
       |api-version: $apiVersion
       |permissions:
       |  races.*:
       |    description: Gives access to all Races commands
       |    children:
       |      races.profile: true
       |      races.regenerate: true
       |      races.set: true
       |      races.debug: true
       |      races.pick: true
       |    default: op
       |  races.profile:
       |    description: Allows the use of the /races command which shows a user his profile.
       |    default: true
       |  races.regenerate:
       |    description: Allows the use of the /races regenerate command which reverts the default races to their default values.
       |    default: op
       |  races.set:
       |    description: Allows the use of the /races set command which forcibly changes a player's race.
       |    default: op
       |  races.debug:
       |    description: Allows the use of the /races debug command which shows current potion effects and attributes the player has..
       |    default: op
       |  races.pick:
       |    description: Allows the use of the /races debug command which shows current potion effects and attributes the player has..
       |    default: true
       |commands:
       |  race:
       |    description: Check current race and level.
       |    permission: races.profile
       |    permission-message: You do not have /<permission>
       |  race regenerate:
       |    description: Reverts the default races to their default values.
       |    permission: races.regenerate
       |    permission-message: You do not have /<permission>
       |  race set:
       |    description: Forcibly changes a player's race.
       |    permission: races.set
       |    permission-message: You do not have /<permission>
       |""".stripMargin
  IO.write(file, pluginYml)
  Seq(file)
}.taskValue

Compile / packageBin / mappings += {
  (resourceManaged.value / "plugin.yml") -> "plugin.yml"
}

val serverProperties = settingKey[Properties]("The test server properties")
serverProperties := {
  val prop = new Properties()
  IO.load(prop, new File("project/server.properties"))
  prop
}

assembly / assemblyOutputPath := new File(
  serverProperties.value.getProperty("testServerPluginsLocation", assemblyOutputPath.toString),
) / (assembly / assemblyJarName).value

lazy val reloadServer = taskKey[Unit]("Reload the server using mcrcon")

reloadServer := {
  import scala.sys.process.*
  "mcrcon -s -H localhost -P 25575 -p YTLjJiAIORYCMlneyOvp \"reload confirm\"" !
}

lazy val assemblyAndReload = taskKey[Unit]("runs assembly and then reloads the server")
assemblyAndReload := (reloadServer dependsOn assembly).value

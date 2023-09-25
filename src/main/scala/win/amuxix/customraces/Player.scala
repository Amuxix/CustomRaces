package win.amuxix.customraces

import win.amuxix.customraces.Components.*
import win.amuxix.customraces.Extensions.sendBlocking
import win.amuxix.customraces.Player.levels
import win.amuxix.customraces.command.{Commands, Sub}
import win.amuxix.customraces.race.Race
import win.amuxix.customraces.race.Race.*

import java.util.UUID

import cats.effect.{IO, Ref}
import cats.syntax.foldable.*
import io.circe.Codec

object Player:
  private val playersFolder = "Players"

  val players: Ref[IO, Map[UUID, Player]] = Ref.unsafe[IO, Map[UUID, Player]](Map.empty)

  def loadOrCreate(uuid: UUID): IO[Unit] =
    for
      _      <- Logger.trace(s"Loading $uuid")
      player <- Persistable.load[Player, Player](playersFolder, s"$uuid.json", (_, _) => IO.unit)
      _      <- players.update(_ + (uuid -> player.getOrElse(Player(uuid))))
    yield ()

  def savePlayer(uuid: UUID): IO[Unit] =
    for
      _       <- Logger.trace(s"Saving $uuid")
      players <- players.get
      _       <- players.get(uuid).fold(Logger.severe(s"No player with uuid $uuid loaded, impossible to save!"))(Persistable.save(playersFolder, s"$uuid.json", _))
    yield ()

  def saveAndRemovePlayer(uuid: UUID): IO[Unit] =
    for
      _ <- savePlayer(uuid)
      _ <- Logger.trace(s"Removing $uuid from memory")
      _ <- players.update(_ - uuid)
    yield ()

  def saveAll: IO[Unit] =
    for
      players <- players.get
      _       <- players.keys.toList.traverse_(savePlayer)
    yield ()

  def loadOrCreateAllOnline: IO[Unit] = CustomRaces.onlinePlayers.map(_.getUniqueId).traverse_(loadOrCreate)

  lazy val levels: Map[Int, Int] =
    def xpForLevel(level: Int) = (Config.baseXP * math.pow(Config.exponentialFactor, level - 1)).toInt
    val maxLevel               = Config.maxLevel + 1
    List
      .range(1, maxLevel)
      .zip(List.range(2, maxLevel).foldLeft(List(Config.baseXP)) { (previous, level) =>
        previous :+ (previous.last + xpForLevel(level))
      })
      .toMap

  def setRace(uuid: UUID, race: Race, level: Option[Int], xp: Option[Int]): IO[Unit] =
    players.update(_.updatedWith(uuid)(_.map(_.setRace(race, level, xp))))

case class Player(
  uuid: UUID,
  raceName: String = Race.human.name,
  level: Int = 1,
  xp: Int = 0,
) derives Codec.AsObject:
  lazy val race: Race = Race.racesByName(raceName)

  def addXP(amount: Int): Player =
    if level >= Config.maxLevel then this
    else
      val updatedXP = xp + amount
      if updatedXP >= xpToLevel then
        val newLevel = level + 1
        val player   = CustomRaces.server.getPlayer(uuid)
        player.sendBlocking(RichText(s"You are now level $newLevel").color(0x004514))
        Race.subRacesUnlock.get((raceName, newLevel)).foreach:
          case List(race) => player.sendBlocking(RichText(s"You have unlocked ${race.name} use /${Commands.prefix} ${Sub.command} to pick it.").color(0x001987))
          case races      =>
            player.sendBlocking(
              RichText(s"You have unlocked ${races.map(_.name).mkString(", ")} use /${Commands.prefix} ${Sub.command} to pick one.").color(0x001987),
            )
        copy(xp = updatedXP, level = newLevel)
      else copy(xp = updatedXP)

  lazy val xpToLevel: Int = Player.levels(level)

  def setRace(race: Race, level: Option[Int], xp: Option[Int]): Player =
    copy(
      raceName = race.name,
      level = level.getOrElse(1),
      xp = xp.getOrElse(level.filter(_ > 1).fold(0)(level => Player.levels(level - 1))),
    )

  def changeRace(race: Race): Player = copy(raceName = race.name, level = 1, xp = 0)

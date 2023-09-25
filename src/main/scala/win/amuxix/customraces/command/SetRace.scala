package win.amuxix.customraces.command

import win.amuxix.customraces.{Config, CustomRaces, Player}
import win.amuxix.customraces.Extensions.send
import win.amuxix.customraces.race.Race

import org.bukkit.command.CommandSender
import org.bukkit.permissions.PermissionDefault

import java.util.UUID

import cats.effect.IO

object SetRace extends Command:
  override val permissionDescription: String = "Forcibly changes a player's race."

  override val permissionDefaultValue: PermissionDefault = PermissionDefault.OP

  override val command: String = "set"

  override def effect(sender: CommandSender, playerUUID: Option[UUID], args: Array[String]): IO[Unit | Boolean] =
    def setRace(raceName: String, playerName: String, level: Option[Int]): IO[Unit | Boolean] = (for
      player <- CustomRaces.onlinePlayers.find(_.getName == playerName)
      race   <- Race.racesByName.get(raceName)
      io      = Player.setRace(player.getUniqueId, race, level, None) *> player.send(s"Race changed to ${race.name}")
    yield io).getOrElse(IO.pure(false))

    args match
      case Array(raceName) if playerUUID.isDefined => setRace(raceName, sender.getName, None)
      case Array(raceName, playerName)             => setRace(raceName, playerName, None)
      case Array(raceName, playerName, level)      => setRace(raceName, playerName, level.toIntOption.filter(level => level > 1 && level <= Config.maxLevel))
      case _                                       => IO.pure(false)

package win.amuxix.customraces.command

import win.amuxix.customraces.{CustomRaces, Player}
import win.amuxix.customraces.Extensions.send

import org.bukkit.command.CommandSender
import org.bukkit.permissions.PermissionDefault

import java.util.UUID

import cats.data.OptionT
import cats.effect.IO

object SetLevel extends Command with PlayerCommand:
  override val permissionDescription: String = "Set a player's level."

  override val permissionDefaultValue: PermissionDefault = PermissionDefault.OP

  override val command: String = "level"

  override def effect(sender: CommandSender, playerUUID: UUID, args: Array[String]): IO[Unit | Boolean] =
    def setLevel(level: Int): IO[Unit | Boolean] = (for
      bukkitPlayer <- OptionT.fromOption[IO](CustomRaces.onlinePlayers.find(_.getUniqueId == playerUUID))
      players      <- OptionT.liftF(Player.players.get)
      player        = players(playerUUID)
      _             = Player.setRace(bukkitPlayer.getUniqueId, player.race, Some(level), None) *> bukkitPlayer.send(s"You are now level $level")
    yield true).getOrElse(false)

    args.flatMap(_.toIntOption) match
      case Array(level) => setLevel(level)
      case _            => IO.pure(false)

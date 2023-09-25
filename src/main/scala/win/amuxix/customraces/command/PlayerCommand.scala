package win.amuxix.customraces.command

import win.amuxix.customraces.Extensions.send

import org.bukkit.Bukkit
import org.bukkit.command.CommandSender

import java.util.UUID

import cats.effect.IO

trait PlayerCommand:
  this: Command =>
  override def effect(sender: CommandSender, playerUUID: Option[UUID], args: Array[String]): IO[Unit | Boolean] =
    playerUUID.fold(Bukkit.getConsoleSender.send(s"$command is a player only command!")) { uuid =>
      effect(sender, uuid, args)
    }

  def effect(sender: CommandSender, playerUUID: UUID, args: Array[String]): IO[Unit | Boolean]

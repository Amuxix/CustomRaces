package win.amuxix.customraces.command

import org.bukkit.command.CommandSender
import org.bukkit.permissions.{Permission, PermissionDefault}

import java.util.UUID

import cats.effect.IO

abstract class Command:
  val permissionDescription: String
  val permissionDefaultValue: PermissionDefault
  val command: String
  val permission: Permission = new Permission(s"races.$command", permissionDescription, permissionDefaultValue)
  def effect(sender: CommandSender, playerUUID: Option[UUID], args: Array[String]): IO[Unit | Boolean]

package win.amuxix.customraces.command

import win.amuxix.customraces.{Logger, Player}
import win.amuxix.customraces.Components.*
import win.amuxix.customraces.Extensions.send
import org.bukkit.command.CommandSender
import org.bukkit.permissions.{Permission, PermissionDefault}

import java.util.UUID
import cats.effect.IO
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.NamedTextColor.DARK_RED

object Empty extends Command with PlayerCommand:
  override val permissionDescription: String = "Permission to view current race and experience"

  override val permissionDefaultValue: PermissionDefault = PermissionDefault.TRUE

  override val permission: Permission = new Permission("races.profile", permissionDescription, permissionDefaultValue)

  override val command: String = ""

  override def effect(sender: CommandSender, playerUUID: UUID, args: Array[String]): IO[Unit] =
    for
      players <- Player.players.get
      _       <- players.get(playerUUID).fold(Logger.warn(Component.text(s"Failed to get info for ${sender.getName} uuid: $playerUUID", DARK_RED))) { player =>
                   sender.send(
                     RichText(s"You are a level ${player.level} ${player.raceName} with ${player.xp}/${player.xpToLevel} xp").color(0x00a831),
                   )
                 }
    yield ()

package win.amuxix.customraces.command
import win.amuxix.customraces.CustomRaces
import win.amuxix.customraces.Extensions.send

import org.bukkit.attribute.Attribute
import org.bukkit.command.CommandSender
import org.bukkit.permissions.PermissionDefault
import org.bukkit.potion.PotionEffectType

import java.util.UUID

import cats.effect.IO
import cats.syntax.foldable.*

object Debug extends Command with PlayerCommand:
  override val permissionDescription: String = "Shows current potion effects and attributes the player has."

  override val permissionDefaultValue: PermissionDefault = PermissionDefault.OP

  override val command: String = "debug"

  override def effect(sender: CommandSender, playerUUID: UUID, args: Array[String]): IO[Unit] =
    CustomRaces.onlinePlayers
      .find(_.getUniqueId == playerUUID)
      .map { player =>
        for
          _ <- Attribute.values.toList.flatMap(attribute => Option(player.getAttribute(attribute))).traverse_ { attribute =>
                 player.send(s"${attribute.getAttribute}, value: ${attribute.getValue}, default: ${attribute.getDefaultValue}, base: ${attribute.getBaseValue}")
               }
          _ <- PotionEffectType.values.toList.flatMap(potionEffect => Option(player.getPotionEffect(potionEffect))).traverse_ { potionEffect =>
                 player.send(s"${potionEffect.getType}, value: ${potionEffect.getAmplifier}, duration: ${potionEffect.getDuration}")
               }
        yield ()
      }
      .getOrElse(IO.unit)

package win.amuxix.customraces.command

import win.amuxix.customraces.Player
import win.amuxix.customraces.race.Race

import org.bukkit.permissions.PermissionDefault

object Sub extends InventoryCommand:
  override val command: String = "sub"

  override val permissionDescription: String = s"Allows usage of /races $command that allows players to see and pick sub races."

  override val permissionDefaultValue: PermissionDefault = PermissionDefault.TRUE

  override val inventoryName: String = "Choosing Subrace"

  override def raceBySlot(player: Player): Map[Int, Race] =
    Race.subRacesBySlot(player.race)

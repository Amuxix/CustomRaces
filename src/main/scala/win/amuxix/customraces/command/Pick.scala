package win.amuxix.customraces.command
import win.amuxix.customraces.Player
import win.amuxix.customraces.race.Race

import org.bukkit.permissions.PermissionDefault

object Pick extends InventoryCommand:
  override val inventoryName: String = "Choosing Race"

  override val command: String = "pick"

  override val permissionDescription: String = s"Allows using /race $command that allows players pick a race or switching to another race."

  override val permissionDefaultValue: PermissionDefault = PermissionDefault.TRUE

  override def raceBySlot(player: Player): Map[Int, Race] = Race.racesBySlot

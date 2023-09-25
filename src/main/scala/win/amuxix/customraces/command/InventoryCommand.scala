package win.amuxix.customraces.command

import win.amuxix.customraces.{CustomRaces, Player}
import win.amuxix.customraces.Components.*
import win.amuxix.customraces.race.Race

import org.bukkit.Material
import org.bukkit.command.CommandSender
import org.bukkit.entity.HumanEntity
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.{ItemMeta, SkullMeta}

import java.util.UUID
import scala.jdk.CollectionConverters.*

import cats.effect.IO
import cats.syntax.foldable.*
import net.kyori.adventure.text.Component as AdventureComponent

abstract class InventoryCommand extends Command with PlayerCommand:
  def inventoryName: String
  def raceBySlot(player: Player): Map[Int, Race]

  override def effect(sender: CommandSender, playerUUID: UUID, args: Array[String]): IO[Unit] = for
    bukkitPlayer <- IO(CustomRaces.server.getPlayer(playerUUID))
    _            <- InventoryCommand.createAndOpenInventory(RichText(inventoryName), raceBySlot, bukkitPlayer, false)
  yield ()

object InventoryCommand:
  val playerSlot: Int = 4
  val backSlot: Int   = 8

  protected def createItem[Meta <: ItemMeta](material: Material, transformers: (Meta => Unit)*): IO[ItemStack] =
    for
      itemStack <- IO(new ItemStack(material, 1))
      itemMeta  <- IO(itemStack.getItemMeta.asInstanceOf[Meta])
      _         <- transformers.toList.traverse_(transformer => IO(transformer(itemMeta)))
      _         <- IO(itemStack.setItemMeta(itemMeta))
    yield itemStack

  extension (meta: ItemMeta)
    def addLore(component: RichText): Unit =
      val lore = Option.when(meta.hasLore)(meta.lore).fold(List.empty[AdventureComponent])(_.asScala)
      meta.lore((lore ++ component.asComponents).asJava)
    def addLore(component: String): Unit   = addLore(RichText(component))

  def createAndOpenInventory(inventoryName: RichText, raceBySlot: Player => Map[Int, Race], bukkitPlayer: HumanEntity, includeBackButton: Boolean): IO[Unit] =
    for
      players    <- Player.players.get
      uuid        = bukkitPlayer.getUniqueId
      player      = players(uuid)
      playerHead <- createItem[SkullMeta](
                      Material.PLAYER_HEAD,
                      _.setOwningPlayer(CustomRaces.server.getOfflinePlayer(uuid)),
                      _.displayName(RichText("You")),
                      _.addLore(s"Level ${player.level} ${player.raceName}"),
                    )
      inventory  <- IO(CustomRaces.server.createInventory(bukkitPlayer, 54, inventoryName))
      _          <- IO(inventory.setItem(playerSlot, playerHead))
      _          <- if !includeBackButton then IO.unit
                    else createItem(Material.DARK_OAK_DOOR, _.displayName(RichText("Back"))).flatMap(i => IO(inventory.setItem(backSlot, i)))
      _          <- raceBySlot(player).toList.traverse_ { (slot, race) =>
                      def addDescriptionLore(meta: ItemMeta): Unit =
                        meta.addLore(race.fullDescription(player))

                      //val lines = List.tabulate(10)(i => s"Linha $i")
                      createItem(race.material, _.displayName(RichText(race.name)), addDescriptionLore)
                        .flatMap(item => IO(inventory.setItem(slot, item)))
                    }
      _          <- IO(bukkitPlayer.openInventory(inventory))
    yield ()

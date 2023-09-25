package win.amuxix.customraces

import org.bukkit.event.{EventHandler, Listener}
import org.bukkit.event.EventPriority.MONITOR
import org.bukkit.event.block.{BlockBreakEvent, BlockPlaceEvent}
import org.bukkit.event.enchantment.EnchantItemEvent
import org.bukkit.event.inventory.{InventoryCloseEvent, InventoryOpenEvent}
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.scheduler.BukkitTask

import java.util.UUID
import scala.concurrent.duration.*

import cats.effect.{IO, Ref}
import cats.effect.unsafe.implicits.global

object Experience extends Listener:
  val activePlayers: Ref[IO, Set[UUID]] = Ref.unsafe[IO, Set[UUID]](Set.empty)

  def setActive(uuid: UUID): IO[Unit] = activePlayers.update(_ + uuid)

  lazy val awardExperience: IO[BukkitTask] =
    val task: IO[Unit] =
      for
        activePlayers <- activePlayers.getAndUpdate(_ => Set.empty)
        _             <- Player.players.update { players =>
                           players ++ activePlayers.toList.flatMap { uuid =>
                             players.get(uuid).map(player => uuid -> player.addXP(Config.experience))
                           }
                         }
        _             <- Player.saveAll
      yield ()
    CustomRaces.repeatingTask(task, Config.interval.seconds)

  @EventHandler(priority = MONITOR)
  def onBlockBreak(event: BlockBreakEvent): Unit = setActive(event.getPlayer.getUniqueId).unsafeRunSync()

  @EventHandler(priority = MONITOR)
  def onBlockPlace(event: BlockPlaceEvent): Unit = setActive(event.getPlayer.getUniqueId).unsafeRunSync()

  @EventHandler(priority = MONITOR)
  def onEnchantItem(event: EnchantItemEvent): Unit = setActive(event.getEnchanter.getUniqueId).unsafeRunSync()

  @EventHandler(priority = MONITOR)
  def onInventoryOpen(event: InventoryOpenEvent): Unit = setActive(event.getPlayer.getUniqueId).unsafeRunSync()

  @EventHandler(priority = MONITOR)
  def onInventoryClose(event: InventoryCloseEvent): Unit = setActive(event.getPlayer.getUniqueId).unsafeRunSync()

  @EventHandler(priority = MONITOR)
  def onPlayerInteract(event: PlayerInteractEvent): Unit = setActive(event.getPlayer.getUniqueId).unsafeRunSync()

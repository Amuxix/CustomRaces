package win.amuxix.customraces

import win.amuxix.customraces.Components.*
import win.amuxix.customraces.CustomRaces.runOnMainThread
import win.amuxix.customraces.Extensions.*
import win.amuxix.customraces.command.{InventoryCommand, Pick}
import win.amuxix.customraces.race.Race

import org.bukkit.entity.HumanEntity
import org.bukkit.event.EventHandler
import org.bukkit.event.inventory.{InventoryClickEvent, InventoryCloseEvent}
import org.bukkit.event.player.{PlayerJoinEvent, PlayerQuitEvent}

import scala.util.matching.Regex

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import net.kyori.adventure.text.TextComponent

object Listener extends org.bukkit.event.Listener:
  @EventHandler
  def onPlayerDisconnect(event: PlayerQuitEvent): Unit =
    Player.saveAndRemovePlayer(event.getPlayer.getUniqueId).unsafeRunSync()

  @EventHandler
  def onPlayerJoin(event: PlayerJoinEvent): Unit =
    Player.loadOrCreate(event.getPlayer.getUniqueId).unsafeRunSync()

  private def closeAndCancel(event: InventoryClickEvent, bukkitPlayer: HumanEntity): IO[Unit] =
    IO(event.setCancelled(false)) *> IO(bukkitPlayer.closeInventory(InventoryCloseEvent.Reason.PLUGIN))

  private def pickRace(bukkitPlayer: HumanEntity, event: InventoryClickEvent): IO[Unit] =
    for
      players <- Player.players.get
      slot     = event.getSlot
      _       <- IO.whenA(event.getSlot == InventoryCommand.playerSlot)(closeAndCancel(event, bukkitPlayer))
      _       <- IO.whenA(event.getSlot == InventoryCommand.backSlot)(closeAndCancel(event, bukkitPlayer))
      newRace  = Race.racesBySlot.get(slot)
      _       <- newRace.fold(IO.unit):
                   case race if Race.fullSubRaces(race).nonEmpty =>
                     InventoryCommand.createAndOpenInventory(RichText(s"Choosing subrace of ${race.name}"), _ => Race.subRacesBySlot(race), bukkitPlayer, true)
                   case race                                     =>
                     for
                       _ <- Player.setRace(bukkitPlayer.getUniqueId, race, None, None)
                       _ <- Race.applyEffects
                       _ <- bukkitPlayer.send(RichText(s"Your race is now: ${race.name}").color(0x004514))
                     yield ()
    yield ()

  private def pickSubRace(bukkitPlayer: HumanEntity, event: InventoryClickEvent, race: Option[Race]): IO[Unit] =
    for
      players <- Player.players.get
      player   = players(bukkitPlayer.getUniqueId)
      slot     = event.getSlot
      _       <- IO.whenA(slot == InventoryCommand.playerSlot)(closeAndCancel(event, bukkitPlayer))
      _       <- IO.whenA(event.getSlot == InventoryCommand.backSlot):
                   InventoryCommand.createAndOpenInventory(RichText(Pick.inventoryName), _ => Race.racesBySlot, bukkitPlayer, false)
      newRace  = Race.subRacesBySlot(race.getOrElse(player.race)).get(slot)
      _       <- newRace.fold(IO.unit):
                   case race if race == player.race                                                                               => IO.unit *> closeAndCancel(event, bukkitPlayer)
                   case race if Race.fullSubRaces(race).contains(player.race)                                                     => IO.unit *> closeAndCancel(event, bukkitPlayer)
                   case Race(raceName, Some(parentRace, level), _, _, _) if player.raceName != parentRace || player.level < level =>
                     bukkitPlayer.send(RichText(s"You must be a $parentRace of level $level to become $raceName!").color(0xff0000)) *> closeAndCancel(
                       event,
                       bukkitPlayer,
                     )
                   case newRace                                                                                                   =>
                     for
                       _ <- Player.setRace(bukkitPlayer.getUniqueId, newRace, Some(player.level), Some(player.xp))
                       _ <- Race.applyEffects
                       _ <- bukkitPlayer.send(RichText(s"Your race is now: ${newRace.name}").color(0x004514))
                       _ <- closeAndCancel(event, bukkitPlayer)
                     yield ()
    yield ()

  @EventHandler
  def onInventoryClick(event: InventoryClickEvent): Unit =
    val player         = event.getWhoClicked
    val matcher: Regex = raw"Choosing subrace of (.+)".r
    (player.getOpenInventory.title match {
      case component: TextComponent =>
        component.content match {
          case Pick.inventoryName => pickRace(player, event)
          case matcher(raceName)  => pickSubRace(player, event, Some(Race.racesByName(raceName)))
          case _                  => IO.unit
        }
      case _                        => IO.unit
    }).runOnMainThread

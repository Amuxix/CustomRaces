package win.amuxix.customraces.command

import win.amuxix.customraces.CustomRaces

import org.bukkit.command.{Command as BukkitCommand, CommandExecutor, CommandSender}

import cats.effect.IO
import cats.effect.unsafe.IORuntime

object Commands extends CommandExecutor:
  val prefix: String           = "race"
  val commands: Array[Command] = Array(SetRace, Debug, Pick)
  given IORuntime              = CustomRaces.mainThreadRuntime

  override def onCommand(sender: CommandSender, bukkitCommand: BukkitCommand, label: String, args: Array[String]): Boolean =
    if !label.equalsIgnoreCase(prefix) then return false
    val playerUUID = CustomRaces.onlinePlayers.collectFirst:
      case player if player.getName == sender.getName => player.getUniqueId
    (if args.isEmpty then Empty.effect(sender, playerUUID, args).as(true)
     else
       commands.collectFirst {
         case command if command.command.equalsIgnoreCase(args.head) && sender.hasPermission(command.permission) =>
           command.effect(sender, playerUUID, args.tail)
       }.fold(IO.pure(false))(_.map {
         case boolean: Boolean => boolean
         case _                => true
       })
    )
      .unsafeRunSync()

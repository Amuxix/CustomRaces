package win.amuxix.customraces

import win.amuxix.customraces.CustomRaces.*
import win.amuxix.customraces.command.Commands
import win.amuxix.customraces.race.Race

import org.bukkit.{Bukkit, Server}
import org.bukkit.entity.Player as BukkitPlayer
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.{BukkitRunnable, BukkitTask}

import java.util.concurrent.Executor
import scala.compiletime.uninitialized
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.*
import scala.jdk.CollectionConverters.*

import cats.effect.IO
import cats.effect.unsafe.{IORuntime, IORuntimeConfig, Scheduler}
import cats.effect.unsafe.implicits.global

object CustomRaces:
  var server: Server   = uninitialized
  var self: JavaPlugin = uninitialized

  def onlinePlayers: List[BukkitPlayer] = server.getOnlinePlayers.asScala.toList

  val mainThreadRuntime: IORuntime =
    val mainThreadEC = ExecutionContext.fromExecutor(_.run())
    val scheduler    = new Scheduler:
      override def sleep(delay: FiniteDuration, task: Runnable): Runnable =
        Thread.sleep(delay.toMillis)
        task

      override def nowMillis(): Long = System.currentTimeMillis()

      override def monotonicNanos(): Long = System.nanoTime()
    IORuntime(mainThreadEC, mainThreadEC, scheduler, () => (), IORuntimeConfig())

  extension [A](io: IO[A])
    def runOnMainThread: A       = io.unsafeRunSync()(mainThreadRuntime)
    def delayToMainThread: IO[A] = IO(io.runOnMainThread)

  def repeatingTask(task: IO[Unit], interval: FiniteDuration, async: Boolean = true): IO[BukkitTask] =
    given IORuntime = if async then global else mainThreadRuntime
    val runnable    = new BukkitRunnable:
      override def run(): Unit =
        task.unsafeRunSync()
    IO:
      if async then runnable.runTaskTimerAsynchronously(self, 0L, interval.toSeconds * 20)
      else runnable.runTaskTimer(self, 0L, interval.toSeconds * 20)

class CustomRaces extends JavaPlugin:
  override def onEnable(): Unit =
    CustomRaces.server = getServer
    CustomRaces.self = this
    Config.config = getConfig
    Logger.logger = getLogger

    (for
      _ <- IO(saveDefaultConfig())
      _ <- IO(getCommand(Commands.prefix).setExecutor(Commands))
      _ <- IO(Bukkit.getPluginManager.registerEvents(Listener, this))
      _ <- IO(Bukkit.getPluginManager.registerEvents(Experience, this))
      _ <- Race.loadAll
      _ <- Player.loadOrCreateAllOnline
      _ <- Experience.awardExperience
      _ <- Race.applyEffectsTask
      _ <- Logger.info(s"Loading complete!")
    yield ()).unsafeRunSync()

  override def onDisable(): Unit = Player.saveAll.unsafeRunSync()

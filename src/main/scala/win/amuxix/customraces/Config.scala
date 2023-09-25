package win.amuxix.customraces

import org.bukkit.configuration.file.FileConfiguration

import scala.compiletime.uninitialized

object Config:
  var config: FileConfiguration        = uninitialized
  lazy val experience: Int             = config.getInt("experience.experience-per-interval")
  lazy val interval: Int               = config.getInt("experience.interval-seconds")
  lazy val maxLevel: Int               = config.getInt("experience.max-level")
  lazy val baseXP: Int                 = config.getInt("experience.base-experience")
  lazy val exponentialFactor: Double   = config.getDouble("experience.exponential-factor")
  lazy val reapplyEffectsInterval: Int = config.getInt("reapply-effects-interval")

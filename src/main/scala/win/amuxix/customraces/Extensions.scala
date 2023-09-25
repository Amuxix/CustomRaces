package win.amuxix.customraces

import win.amuxix.customraces.Components.*
import win.amuxix.customraces.race.Effect

import org.bukkit.attribute.{Attribute, AttributeInstance}
import org.bukkit.entity.Player as BukkitPlayer
import org.bukkit.potion.{PotionEffect, PotionEffectType}

import cats.data.OptionT
import cats.effect.IO
import cats.syntax.foldable.*
import net.kyori.adventure.audience.Audience

object Extensions:
  extension (audience: Audience)
    def sendBlocking(component: RichText): Unit = audience.sendMessage(component)
    def sendBlocking(text: String): Unit        = audience.sendMessage(RichText(text))
    def send(component: RichText): IO[Unit]     = IO(sendBlocking(component))
    def send(text: String): IO[Unit]            = send(RichText(text))

  extension (double: Double)
    def round(digits: Int = 2): Double =
      val exp = Math.pow(10, digits)
      math.round(exp * double) / exp

  extension (attributeInstance: AttributeInstance)
    def getPlayerDefaultValue: Double =
      attributeInstance.getAttribute match
        case Attribute.GENERIC_MOVEMENT_SPEED => 0.1
        case _                                => attributeInstance.getDefaultValue

  extension (player: BukkitPlayer)
    private def addRacePotionEffect(potionEffectType: PotionEffectType, value: Int): IO[Unit] =
      Option(player.getPotionEffect(potionEffectType))
        .filter(_.getAmplifier < value - 1)
        .filter(_.getDuration < Config.reapplyEffectsInterval * 10)
        .fold {
          for
            _           <- IO(player.removePotionEffect(potionEffectType))
            potionEffect = new PotionEffect(potionEffectType, Int.MaxValue, value - 1, false)
            _           <- IO(player.addPotionEffect(potionEffect))
            // _ <- addActiveEffect(player.getUniqueId, effect)
          yield ()
        }(_ => IO.unit)

    private def modifyAttributeBaseValue(attribute: Attribute, modifier: Double): IO[Unit] =
      (for
        attributeInstance <- OptionT.fromOption(Option(player.getAttribute(attribute)))
        _                 <- OptionT.liftF(IO(attributeInstance.setBaseValue(attributeInstance.getPlayerDefaultValue + modifier)))
        // _ <- addActiveEffect(player.getUniqueId, attribute)
      yield ()).getOrElseF(IO.unit)

    def racePotionEffects: IO[List[PotionEffectType]] =
      for
        effects               <- IO.pure(PotionEffectType.values.toList)
        potionEffectsInPlayer <- IO(effects.flatMap(potionEffectType => Option(player.getPotionEffect(potionEffectType))))
        racePotionEffects      = potionEffectsInPlayer.collect:
                                   case effect if effect.getDuration > Int.MaxValue / 2 => effect.getType
      yield racePotionEffects

    def raceAttributes: IO[List[Attribute]] =
      for
        attributes         <- IO.pure(Attribute.values.toList)
        attributesInPlayer <- IO(attributes.flatMap(attribute => Option(player.getAttribute(attribute))))
        raceAttributes      = attributesInPlayer.collect:
                                case attribute if attribute.getPlayerDefaultValue != attribute.getBaseValue => attribute.getAttribute
      yield raceAttributes

    def updateEffects(effects: List[Effect]): IO[Unit] =
      val (attributes, potionEffects)                                                                                                       = effects.foldLeft(List.empty[(Attribute, Double)], List.empty[(PotionEffectType, Int)]):
        case ((attributes, potionEffects), Effect(_, _, (attribute: Attribute, value: Double)))        => (attributes :+ (attribute -> value), potionEffects)
        case ((attributes, potionEffects), Effect(_, _, (potionEffect: PotionEffectType, value: Int))) => (attributes, potionEffects :+ (potionEffect -> value))
        case (unreachable, _)                                                                          => unreachable
      def updateEffectType[A, B](raceEffects: IO[List[A]], effects: List[(A, B)], remove: A => IO[Unit], add: (A, B) => IO[Unit]): IO[Unit] =
        for
          raceEffects    <- raceEffects
          effectsMap      = effects.toMap
          effectTypes     = effectsMap.keys.toList
          effectsToRemove = raceEffects diff effectTypes
          effectsToAdd    = effectTypes diff raceEffects
          _              <- effectsToRemove.traverse_(remove)
          _              <- effectsToAdd.traverse_(effectType => add(effectType, effectsMap(effectType)))
        yield ()
      for
        _ <- updateEffectType(racePotionEffects, potionEffects, removeRacePotionEffect, addRacePotionEffect)
        _ <- updateEffectType(raceAttributes, attributes, modifyAttributeBaseValue(_, 0), modifyAttributeBaseValue)
      yield ()

    private def removeRacePotionEffect(potionEffectType: PotionEffectType): IO[Unit] =
      Option(player.getPotionEffect(potionEffectType))
        .fold(IO.unit)(effect => IO(player.removePotionEffect(effect.getType)))

    def revertEffectsToDefault: IO[Unit] =
      for
        _ <- Attribute.values.toList.traverse_(modifyAttributeBaseValue(_, 0))
        _ <- PotionEffectType.values.toList.traverse_(removeRacePotionEffect)
      yield ()

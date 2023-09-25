package win.amuxix.customraces.race

import win.amuxix.customraces.*
import win.amuxix.customraces.Components.*
import win.amuxix.customraces.Extensions.*
import win.amuxix.customraces.Persistable.*
import win.amuxix.customraces.Time.*

import org.bukkit.{Bukkit, Material}
import org.bukkit.attribute.{Attribute, AttributeInstance}
import org.bukkit.potion.PotionEffectType
import org.bukkit.scheduler.BukkitTask

import scala.compiletime.uninitialized
import scala.concurrent.duration.*

import cats.effect.{IO, Ref}
import cats.syntax.foldable.*
import fs2.Stream
import io.circe.{Codec, Decoder, Encoder}

object Race:
  lazy val human: Race          = Race("Human", None, Material.PLAYER_HEAD, "A basic human.", List.empty)
  lazy val slime: Race          = Race(
    "Slime",
    None,
    Material.SLIME_BALL,
    "A squishy slime known for their regenerative abilities.",
    List(
      Effect(None, List.empty, (PotionEffectType.JUMP, 2)),
      Effect(None, List.empty, (PotionEffectType.REGENERATION, 2)),
      Effect(None, List.empty, (Attribute.GENERIC_MAX_HEALTH, -10d)),
    ),
  )
  lazy val goldGolem: Race      = Race(
    "Gold Golem",
    None,
    Material.GOLD_BLOCK,
    "A slow and sturdy construct, hard to kill, easy to outrun!\nVery shiny during the day",
    List(
      Effect(Some(TimeFrame(Day, Noon)), List.empty, (PotionEffectType.GLOWING, 1)),
      Effect(None, List.empty, (Attribute.GENERIC_ATTACK_DAMAGE, 1d)),
      Effect(None, List.empty, (Attribute.GENERIC_ATTACK_SPEED, -3d)),
      Effect(None, List.empty, (Attribute.GENERIC_KNOCKBACK_RESISTANCE, 0.8d)),
      Effect(None, List.empty, (Attribute.GENERIC_MOVEMENT_SPEED, -0.05d)),
      Effect(None, List.empty, (Attribute.GENERIC_MAX_HEALTH, 2d)),
    ),
  )
  lazy val ironGolem: Race      = Race(
    "Iron Golem",
    Some((goldGolem.name, 25)),
    Material.IRON_BLOCK,
    "Iron is lighter and even harder than gold!!",
    List(
      Effect(None, List.empty, (Attribute.GENERIC_ATTACK_DAMAGE, 1.5d)),
      Effect(None, List.empty, (Attribute.GENERIC_ATTACK_SPEED, -2d)),
      Effect(None, List.empty, (Attribute.GENERIC_KNOCKBACK_RESISTANCE, 0.8d)),
      Effect(None, List.empty, (Attribute.GENERIC_ARMOR, 5d)),
      Effect(None, List.empty, (Attribute.GENERIC_MOVEMENT_SPEED, -0.03d)),
      Effect(None, List.empty, (Attribute.GENERIC_MAX_HEALTH, 4d)),
    ),
  )
  lazy val diamondGolem: Race   = Race(
    "Diamond Golem",
    Some((ironGolem.name, 50)),
    Material.DIAMOND_BLOCK,
    "Now we're talkin' diamond is the way to go in terms of strength/weight.",
    List(
      Effect(None, List.empty, (Attribute.GENERIC_ATTACK_DAMAGE, 2d)),
      Effect(None, List.empty, (Attribute.GENERIC_KNOCKBACK_RESISTANCE, 0.8d)),
      Effect(None, List.empty, (Attribute.GENERIC_ARMOR, 10d)),
      Effect(None, List.empty, (Attribute.GENERIC_MOVEMENT_SPEED, -0.01d)),
      Effect(None, List.empty, (Attribute.GENERIC_MAX_HEALTH, 6d)),
    ),
  )
  lazy val netheriteGolem: Race = Race(
    "Netherite Golem",
    Some((diamondGolem.name, 100)),
    Material.NETHERITE_BLOCK,
    "The ultimate golem form, no drawbacks all power, there is no stopping you",
    List(
      Effect(None, List.empty, (Attribute.GENERIC_ATTACK_DAMAGE, 3d)),
      Effect(None, List.empty, (Attribute.GENERIC_KNOCKBACK_RESISTANCE, 0.8d)),
      Effect(None, List.empty, (Attribute.GENERIC_ARMOR_TOUGHNESS, 10d)),
      Effect(None, List.empty, (Attribute.GENERIC_ARMOR, 10d)),
      Effect(None, List.empty, (Attribute.GENERIC_MAX_HEALTH, 10d)),
    ),
  )
  val golems                    = List(goldGolem, ironGolem, diamondGolem, netheriteGolem)

  lazy val defaultRaces: List[Race] = List(human, slime) ++ golems

  var races: List[Race]                                    = uninitialized
  val effects: Ref[IO, Map[Time, Map[Race, List[Effect]]]] = Ref.unsafe(Map.empty)
  // val activeEffects: Ref[IO, Map[UUID, Set[Attribute | PotionEffectType]]] = Ref.unsafe(Map.empty)

  lazy val racesByName: Map[String, Race]       = races.map(race => race.name -> race).toMap
  lazy val directSubRaces: Map[Race, Set[Race]] =
    def findSubrace(race: Race): Set[Race] = races.toSet.collect:
      case subRace if subRace.parentRace.exists(_._1.equalsIgnoreCase(race.name)) => subRace
    races.map(race => race -> findSubrace(race)).toMap

  lazy val fullSubRaces: Map[Race, Set[Race]] =
    def subraces(race: Race): Set[Race] =
      directSubRaces.get(race).fold(Set.empty)(_.flatMap(subraces)) + race
    races.map(race => race -> subraces(race)).toMap

  private def createRaceBySlotMap(races: Iterable[Race]): Map[Int, Race] =
    val firstSlot = 9
    (firstSlot to firstSlot + races.size).zip(races).toMap

  lazy val racesBySlot: Map[Int, Race] = createRaceBySlotMap(races.filter(_.parentRace.isEmpty))

  lazy val subRacesBySlot: Map[Race, Map[Int, Race]] =
    races.map { race =>
      race -> createRaceBySlotMap(fullSubRaces(race).toList.sortBy(fullSubRaces(_).size)(Ordering[Int].reverse))
    }.toMap

  lazy val subRaces: List[Race]                           = races.filter(_.parentRace.nonEmpty)
  lazy val subRacesUnlock: Map[(String, Int), List[Race]] = subRaces.groupBy(_.parentRace.get)

  private val racesFolder           = "Races"
  lazy val createDefaults: IO[Unit] = Persistable.saveAll(racesFolder, defaultRaces, race => s"${race.name}.json")

  lazy val updateActiveEffects: IO[Unit] =
    effects.update(_ =>
      Time.values.map { time =>
        time -> races.map(race => race -> race.effects.filter(_.time.forall(_.contains(time)))).toMap
      }.toMap,
    )

  lazy val loadAll: IO[Unit] =
    for
      racesFromFiles <- Persistable.loadAll[Nameless, Race](
                          racesFolder,
                          (name, error) => Logger.severe(s"Failed to load race from $name error ${error.getMessage}"),
                          (nameless, path) => nameless.toRace(path.strippedName),
                        )
      races          <- if racesFromFiles.isEmpty then Logger.info(s"No races found, creating defaults") *> createDefaults.as(defaultRaces)
                        else IO.pure(racesFromFiles)
      _               = Race.races = races
      _              <- Race.fullSubRaces.values.toList.flatten.filter(_.parentRace.isDefined).traverse_ {
                          case race if !Race.racesByName.contains(race.parentRace.get._1) =>
                            Logger.severe(s"${race.name} has ${race.parentRace.get._1} as parent race, but parent race was not found!")
                          case _                                                          => IO.unit
                        }
      _              <- updateActiveEffects
      _              <- Logger.info(s"Loaded ${races.map(_.name).mkString(", ")}")
    yield ()

  lazy val applyEffects: IO[Unit] = (for
    _             <- Stream.eval(Logger.trace(s"Applying effects"))
    activeEffects <- Stream.eval(effects.get)
    loadedPlayers <- Stream.eval(Player.players.get)
    playersByRace  = loadedPlayers.values.toList.groupBy(player => racesByName(player.raceName))

    (world, bukkitPlayersInWorld) <- Stream.emits(CustomRaces.onlinePlayers.groupBy(_.getWorld).toList)
    worldTime                      = Time.fromGameTime(world.getTime)
    _                             <- Stream.eval(Logger.trace(s"World times is $worldTime"))
    bukkitPlayer                  <- Stream.emits(bukkitPlayersInWorld)
    player                         = loadedPlayers(bukkitPlayer.getUniqueId)
    race                           = player.race
    currentEffects                 = activeEffects.get(worldTime).flatMap(_.get(race)).toList.flatten
    _                             <- Stream.eval(Logger.trace(s"Current effects are $currentEffects"))
    _                             <- Stream.eval(bukkitPlayer.updateEffects(currentEffects))
  yield ()).compile.drain

  lazy val applyEffectsTask: IO[BukkitTask] =
    CustomRaces.repeatingTask(applyEffects, Config.reapplyEffectsInterval.seconds, false)

  given Encoder[Race]   = Encoder[Nameless].contramap[Race](_.toNameless)
  given Codec[Material] = Codec.from(_.as[String].map(Material.getMaterial), Encoder[String].contramap[Material](_.toString))
  given Codec[Nameless] =
    Codec.forProduct4[Nameless, Option[(String, Int)], Material, String, List[Effect]]("parentRace", "material", "description", "effects")(Nameless.apply)(
      nameless => (nameless.parentRace, nameless.material, nameless.description, nameless.effects),
    )

  def fromScreamingSnakeCase(string: String): String =
    string.split("_").map(_.toLowerCase.capitalize).mkString(" ")

  val attributeToName: Map[Attribute, String] = Attribute.values.toList.map { attribute =>
    attribute -> fromScreamingSnakeCase(attribute.key.value.split("\\.").last)
  }.toMap

  val potionEffectToName: Map[PotionEffectType, String] = PotionEffectType.values.toList.map { potionEffectType =>
    potionEffectType -> fromScreamingSnakeCase(potionEffectType.getName)
  }.toMap

private case class Nameless(
  parentRace: Option[(String, Int)],
  material: Material,
  description: String,
  effects: List[Effect],
) /* derives Codec.AsObject*/:
  def toRace(name: String): Race = Race(name, parentRace, material, description, effects)

case class Race(
  name: String,
  parentRace: Option[(String, Int)],
  material: Material,
  description: String,
  effects: List[Effect],
):
  def toNameless: Nameless = Nameless(parentRace, material, description, effects)

  def fullDescription(player: Player): RichText =
    lazy val bukkitPlayer                                        = Bukkit.getPlayer(player.uuid)
    lazy val playerAttributes: Map[Attribute, AttributeInstance] =
      Attribute.values.toList
        .flatMap(attribute => Option(bukkitPlayer.getAttribute(attribute)))
        .map { attribute =>
          attribute.getAttribute -> attribute
        }
        .toMap
    extension (effects: List[Effect])
      def component: RichText                                    =
        effects
          .map(_.effect)
          .map:
            case (attributeType: Attribute, value: Double)        =>
              val attribute  = playerAttributes(attributeType)
              val finalValue = (attribute.getPlayerDefaultValue + value).round()
              val valueText  =
                (finalValue - attribute.getValue).round() match
                  case 0          => RichText.empty
                  case x if x > 0 => RichText(s"(+$x)").color(0x008526)
                  case x          => RichText(s"($x)").color(0x380000)
              RichText(s"${Race.attributeToName(attributeType)} $finalValue ").color(0x6600ff) append valueText
            case (potionEffectType: PotionEffectType, value: Int) =>
              RichText(s"${Race.potionEffectToName(potionEffectType)} $value").color(0x6600ff)
          .join

    val description = RichText.justified(s"${this.description}").color(0xd2d2d2)

    val effectsDescription =
      if effects.isEmpty then RichText.emptyLine + RichText(s"No bonuses, nothing to call home about really...")
      else
        val (fullTimeEffects, partTimeEffects) = effects.partition(_.time.isEmpty)

        val fullTimeDescription =
          if fullTimeEffects.isEmpty then RichText.empty else RichText.emptyLine + RichText("Permanent Bonuses") + fullTimeEffects.component

        val partTimeDescription =
          if partTimeEffects.isEmpty then RichText.empty
          else
            RichText.emptyLine + partTimeEffects
              .groupBy(_.time.get)
              .map { case (timeframe, effects) =>
                RichText(s"${timeframe.start} to end of ${timeframe.end} Bonuses") + effects.component
              }
              .join

        fullTimeDescription + partTimeDescription

    val subraces =
      if !Race.directSubRaces.get(this).exists(_.nonEmpty) then RichText.emptyLine + RichText("Final form").color(0x008526)
      else
        List(
          RichText.emptyLine,
          RichText("Direct Subraces").color(0x008526),
          Race.directSubRaces(this).toList.map(race => RichText(race.name)).join.color(0x004514),
          RichText.emptyLine,
          RichText(s"Total Subraces: ${Race.fullSubRaces(this).size - 1}").color(0x008526),
        ).join

    val raceRequirement =
      RichText.emptyLine + Option
        .when(this == player.race)(RichText("Your current race").color(0x0009a1))
        .orElse(Option.when(Race.fullSubRaces(this).contains(player.race))(RichText("Like you, but worse").color(0xffc400)))
        .orElse:
          parentRace.collect:
            case (parentRace, level) if !player.raceName.equalsIgnoreCase(parentRace) || player.level < level =>
              RichText(s"Requires level $level $parentRace").color(0x380000)
        .getOrElse(RichText(s"You can pick this race").color(0x0009a1))

    List(description, effectsDescription.color(0x4400ba), subraces, raceRequirement).filter(_ != RichText.empty).join

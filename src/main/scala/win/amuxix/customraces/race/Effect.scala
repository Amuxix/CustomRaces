package win.amuxix.customraces.race

import win.amuxix.customraces.TimeFrame

import org.bukkit.attribute.Attribute
import org.bukkit.potion.PotionEffectType

import io.circe.{Codec, Decoder, Encoder, HCursor, Json}
import io.circe.Decoder.Result

object Effect:
  given Decoder[Attribute] = Decoder.decodeString.emap { effectName =>
    Attribute.values.collectFirst {
      case attribute: Attribute if attribute.getKey.getKey.equalsIgnoreCase(effectName) => attribute
    }.toRight(s"Failed to decode Attribute with name $effectName")
  }

  given Encoder[Attribute] = Encoder.encodeString.contramap[Attribute](_.getKey.getKey)

  given Decoder[PotionEffectType] = Decoder.decodeString.emap { effectName =>
    PotionEffectType.values.collectFirst {
      case potionEffectType: PotionEffectType if potionEffectType.getName.equalsIgnoreCase(effectName) => potionEffectType
    }.toRight(s"Failed to decode PotionEffectType with name $effectName")
  }

  given Encoder[PotionEffectType] = Encoder.encodeString.contramap[PotionEffectType](_.getName)

  given Decoder[(PotionEffectType, Int) | (Attribute, Double)] =
    Decoder[(PotionEffectType, Int)].or(Decoder[(Attribute, Double)].asInstanceOf[Decoder[(PotionEffectType, Int) | (Attribute, Double)]])

  given Encoder[(PotionEffectType, Int) | (Attribute, Double)] = new Encoder[(PotionEffectType, Int) | (Attribute, Double)]:
    override def apply(a: (PotionEffectType, Int) | (Attribute, Double)): Json = a match
      case (attribute: Attribute, value: Double)     => Encoder[(Attribute, Double)].apply(attribute -> value)
      case (potionEffectType: PotionEffectType, value: Int) => Encoder[(PotionEffectType, Int)].apply(potionEffectType -> value)

case class Effect(
  time: Option[TimeFrame], // A tuple of a start and an end time
  biome: List[String],
  effect: (PotionEffectType, Int) | (Attribute, Double),
) derives Codec.AsObject

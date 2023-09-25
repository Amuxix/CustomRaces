package win.amuxix.customraces

import io.circe.{Codec, Encoder}

enum Time(val start: Long, val end: Long) /* derives Codec.AsObject*/:
  case Day      extends Time(1000, 6000)
  case Noon     extends Time(6000, 13000)
  case Night    extends Time(13000, 18000)
  case Midnight extends Time(18000, 1000)

object Time:
  given Codec[Time] = Codec.from(_.as[String].map(Time.valueOf), Encoder[String].contramap[Time](_.toString))

  def fromGameTime(gameTime: Long): Time = gameTime match
    case time if time >= Day.start && time < Day.end           => Day
    case time if time >= Noon.start && time < Noon.end         => Noon
    case time if time >= Night.start && time < Night.end       => Night
    case time if time >= Midnight.start || time < Midnight.end => Midnight

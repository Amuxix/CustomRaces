package win.amuxix.customraces

import io.circe.Codec

case class TimeFrame(
  start: Time,
  end: Time,
) derives Codec.AsObject:
  def contains(time: Time): Boolean =
    val rangeEnd = if end.end <= start.start then 24000 + end.end else end.end
    val timeEnd  = if time.end <= time.start then 24000 + time.end else time.end

    val nextDayTimeStart = time.start + 24000
    val nextDayTimeEnd   = time.end + 24000
    (start.start <= time.start && time.start < rangeEnd) || (start.start <= timeEnd && timeEnd < rangeEnd) ||
    (start.start <= nextDayTimeStart && nextDayTimeStart < rangeEnd) || (start.start <= nextDayTimeEnd && nextDayTimeEnd < rangeEnd)

package dev.danields.aall.automation

import java.time.*

object Rules {
  fun remaining(sameBoot: Boolean, elapsedDeadline: Long, elapsedNow: Long, wallDeadline: Long, wallNow: Long, duration: Long): Long =
    (if(sameBoot) elapsedDeadline-elapsedNow else wallDeadline-wallNow).coerceIn(0,duration)
  fun verifies(answer: String, expected: Int) = answer.trim().toIntOrNull() == expected
  fun requests(kind: RoutineKind, scheduled: Boolean, focusRemaining: Long, earlyUntil: Long, now: Long): Boolean = when(kind) {
    RoutineKind.FOCUS -> focusRemaining > 0
    RoutineKind.ACCESS -> !scheduled
    RoutineKind.BEDTIME -> scheduled || earlyUntil > now
    RoutineKind.GRAYSCALE -> scheduled
  }
  fun grayRequest(kind: RoutineKind, exempt: Boolean) = kind != RoutineKind.ACCESS && !exempt
  fun suppress(dayOff: Boolean, pauseRemaining: Long) = dayOff || pauseRemaining > 0

  fun overrideExpiry(r: Routine, date: LocalDate, zone: ZoneId): Long =
    r.windows.fold(date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()) { until, w -> maxOf(until,interval(w,date,zone).second) }
  fun interval(w: Window, date: LocalDate, zone: ZoneId): Pair<Long, Long> =
    date.atTime(w.start).atZone(zone).toInstant().toEpochMilli() to
      date.plusDays(if (w.end <= w.start) 1 else 0).atTime(w.end).atZone(zone).toInstant().toEpochMilli()
  fun runsOn(w: Window, date: LocalDate, anchor: LocalDate?) = anchor == date || date.dayOfWeek.value in w.days
  fun active(r: Routine, now: Long, zone: ZoneId, anchor: LocalDate? = null): Boolean {
    if (!r.enabled) return false
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val dates = if (anchor != null) listOf(anchor) else listOf(today.minusDays(1), today)
    return r.windows.any { w ->
      dates.any { d ->
        val (start, end) = interval(w, d, zone)
        (anchor != null || d.dayOfWeek.value in w.days) && now >= start && now < end
      }
    }
  }
  fun canBook(date: String, now: Long, zone: ZoneId): Boolean =
    LocalDate.parse(date).atStartOfDay(zone).toInstant().toEpochMilli() - now >= 72L * 3600000
  fun challenge(): Pair<String, Int> {
    val random = java.security.SecureRandom()
    while (true) {
      val a = 50 + random.nextInt(950); val b = 50 + random.nextInt(950); val c = 50 + random.nextInt(950)
      val s = if (random.nextBoolean()) 1 else -1; val t = if (random.nextBoolean()) 1 else -1
      val answer = a + s*b + t*c
      if (answer in -999..999) return "$a ${if(s == 1) "+" else "-"} $b ${if(t == 1) "+" else "-"} $c" to answer
    }
  }
}

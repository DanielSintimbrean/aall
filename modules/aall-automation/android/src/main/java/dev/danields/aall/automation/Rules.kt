package dev.danields.aall.automation

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

private const val DAY_OFF_NOTICE = 72L * 3600000

object Rules {
  // Keep system tasks usable even when bedtime blocks every unlisted app.
  val alwaysAllowed = setOf(
    "com.android.settings",
    "com.android.settings.intelligence",
    "com.android.providers.downloads",
    "com.android.providers.downloads.ui",
    "com.android.documentsui",
    "com.google.android.documentsui",
    "com.google.android.apps.nbu.files",
    "com.amaze.filemanager",
    "com.android.packageinstaller",
    "com.android.certinstaller",
    "com.android.systemui",
    "com.google.android.dialer",
    "com.samsung.android.dialer",
    "com.android.dialer",
    "com.android.server.telecom",
    "com.android.emergency",
    "com.android.permissioncontroller",
    "com.google.android.permissioncontroller",
    "com.google.android.packageinstaller"
  )

  fun remaining(
    sameBoot: Boolean,
    elapsedDeadline: Long,
    elapsedNow: Long,
    wallDeadline: Long,
    wallNow: Long,
    duration: Long
  ): Long =
    (if (sameBoot) elapsedDeadline - elapsedNow else wallDeadline - wallNow).coerceIn(0, duration)

  fun verifies(answer: String, expected: Int) = answer.trim().toIntOrNull() == expected

  fun suppress(dayOff: Boolean, pauseRemaining: Long) = dayOff || pauseRemaining > 0

  fun overrideExpiry(r: Routine, date: LocalDate, zone: ZoneId): Long =
    r.windows.fold(date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()) { until, w ->
      maxOf(until, interval(w, date, zone).second)
    }

  fun interval(w: Window, date: LocalDate, zone: ZoneId): Pair<Long, Long> =
    date.atTime(w.start).atZone(zone).toInstant().toEpochMilli() to date.plusDays(
      if (w.end <= w.start) {
        1
      } else {
        0
      }
    ).atTime(w.end).atZone(zone).toInstant().toEpochMilli()

  fun runsOn(w: Window, date: LocalDate, anchor: LocalDate?) =
    anchor == date || date.dayOfWeek.value in w.days

  fun active(r: Routine, now: Long, zone: ZoneId, anchor: LocalDate? = null) =
    activeSince(r, now, zone, anchor) != null

  /** When the window running now began, or null when none is. */
  fun activeSince(r: Routine, now: Long, zone: ZoneId, anchor: LocalDate? = null): Long? {
    if (!r.enabled) return null
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val dates = if (anchor != null) listOf(anchor) else listOf(today.minusDays(1), today)
    return r.windows.firstNotNullOfOrNull { w ->
      dates.firstNotNullOfOrNull { d ->
        val (start, end) = interval(w, d, zone)
        start.takeIf {
          (anchor != null || d.dayOfWeek.value in w.days) && now >= start && now < end
        }
      }
    }
  }

  fun canBook(date: String, now: Long, zone: ZoneId): Boolean =
    LocalDate.parse(date).atStartOfDay(zone).toInstant().toEpochMilli() - now >= DAY_OFF_NOTICE

  fun earliestDayOff(now: Long, zone: ZoneId): LocalDate {
    val date = Instant.ofEpochMilli(now + DAY_OFF_NOTICE).atZone(zone).toLocalDate()
    return if (canBook(date.toString(), now, zone)) date else date.plusDays(1)
  }

  fun challenge(): Pair<String, Int> {
    val random = java.security.SecureRandom()
    while (true) {
      val a = 50 + random.nextInt(950)
      val b = 50 + random.nextInt(950)
      val c = 50 + random.nextInt(950)
      val s = if (random.nextBoolean()) 1 else -1
      val t = if (random.nextBoolean()) 1 else -1
      val answer = a + s * b + t * c
      if (answer in -999..999) {
        return "$a ${if (s == 1) "+" else "-"} $b ${if (t == 1) "+" else "-"} $c" to answer
      }
    }
  }
}

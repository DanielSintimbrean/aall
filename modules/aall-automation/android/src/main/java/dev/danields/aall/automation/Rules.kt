package dev.danields.aall.automation

import org.json.JSONObject
import java.time.*

object Rules {
  fun remaining(sameBoot: Boolean, elapsedDeadline: Long, elapsedNow: Long, wallDeadline: Long, wallNow: Long, duration: Long): Long =
    (if(sameBoot) elapsedDeadline-elapsedNow else wallDeadline-wallNow).coerceIn(0,duration)
  fun verifies(answer: String, expected: Int) = answer.trim().toIntOrNull() == expected
  fun requests(id: String, scheduled: Boolean, focusRemaining: Long, earlyUntil: Long, now: Long): Boolean = when(id) {
    "focus" -> focusRemaining > 0
    "access" -> !scheduled
    "bedtime" -> scheduled || earlyUntil > now
    else -> scheduled
  }
  fun suppress(dayOff: Boolean, pauseRemaining: Long) = dayOff || pauseRemaining > 0

  fun interval(w: JSONObject, date: LocalDate, zone: ZoneId): Pair<Long, Long> {
    val start = LocalTime.parse(w.getString("start"))
    val end = LocalTime.parse(w.getString("end"))
    return date.atTime(start).atZone(zone).toInstant().toEpochMilli() to
      date.plusDays(if (end <= start) 1 else 0).atTime(end).atZone(zone).toInstant().toEpochMilli()
  }
  fun active(r: JSONObject, now: Long, zone: ZoneId, anchor: LocalDate? = null): Boolean {
    if (!r.getBoolean("enabled")) return false
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val windows = r.getJSONArray("windows")
    return (0 until windows.length()).any { i ->
      val w = windows.getJSONObject(i)
      (if (anchor != null) listOf(anchor) else listOf(today.minusDays(1), today)).any { d ->
        val days = w.getJSONArray("days")
        val matches = anchor != null || (0 until days.length()).any { days.getInt(it) == d.dayOfWeek.value }
        val (start, end) = interval(w, d, zone)
        matches && now >= start && now < end
      }
    }
  }
  fun canBook(date: String, now: Long, zone: ZoneId): Boolean =
    LocalDate.parse(date).atStartOfDay(zone).toInstant().toEpochMilli() - now >= 72L * 3600000
  fun validate(c: JSONObject) {
    require(c.getInt("version") == 1)
    require(c.getInt("focusMinutes") in 1..1440)
    val rs = c.getJSONArray("routines")
    require(rs.length() == 4)
    val ids = mutableSetOf<String>()
    for (i in 0 until rs.length()) {
      val r = rs.getJSONObject(i)
      require(ids.add(r.getString("id")))
      r.getBoolean("enabled")
      LocalTime.parse(r.getString("earlyStart"))
      require(r.getString("earlyStart") >= "20:00")
      for (key in listOf("apps", "colorApps")) {
        val apps = r.getJSONArray(key)
        require(apps.length() <= 1000)
        for (j in 0 until apps.length()) require(apps.getString(j).matches(Regex("[A-Za-z0-9_.]+")))
      }
      val ws = r.getJSONArray("windows")
      require(ws.length() <= 20)
      if (r.getString("id") == "bedtime") require(ws.length() == 1)
      for (j in 0 until ws.length()) {
        val w = ws.getJSONObject(j)
        require(w.getString("start").matches(Regex("[0-2][0-9]:[0-5][0-9]")))
        LocalTime.parse(w.getString("start")); LocalTime.parse(w.getString("end"))
        val days = w.getJSONArray("days")
        require(days.length() > 0)
        for (k in 0 until days.length()) require(days.getInt(k) in 1..7)
      }
    }
    require(ids == setOf("access", "focus", "bedtime", "grayscale"))
  }
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

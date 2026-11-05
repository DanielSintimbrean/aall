package dev.danields.aall.automation
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RulesTest {
  private val zone = ZoneId.of("Europe/Madrid")

  private fun time(s: String) = ZonedDateTime.parse(s).toInstant().toEpochMilli()

  private fun routine() = Routine(
    RoutineKind.BEDTIME,
    "Bedtime",
    true,
    listOf(Window(LocalTime.of(22, 30), LocalTime.of(7, 0), listOf(1))),
    emptyList(),
    emptyList(),
    LocalTime.of(20, 0)
  )

  @Test fun timersUseElapsedWithinBootAndWallOnRecovery() {
    assertEquals(500L, Rules.remaining(true, 1000, 500, 9000, 999999, 1000))
    assertEquals(0L, Rules.remaining(true, 1000, 1000, 9000, 0, 1000))
    assertEquals(100L, Rules.remaining(false, 1000, 1, 9000, 8900, 1000))
    assertEquals(1000L, Rules.remaining(false, 1000, 1, 9000, 1, 1000))
  }

  @Test fun bypassAndDayOffSuppressAllOwnersUntilExpiry() {
    assertTrue(Rules.suppress(true, 0))
    assertTrue(Rules.suppress(false, 1))
    assertFalse(Rules.suppress(false, 0))
    assertTrue(RoutineKind.BEDTIME.runs(true, 0, 0, 100))
    assertFalse(RoutineKind.FOCUS.runs(false, 0, 0, 100))
    assertTrue(RoutineKind.BEDTIME.runs(false, 0, 200, 100))
    assertFalse(RoutineKind.BEDTIME.runs(false, 0, 200, 200))
  }

  @Test fun wrongAnswersRetrySameExpectedValue() {
    assertFalse(Rules.verifies("123", 456))
    assertFalse(Rules.verifies("456.0", 456))
    assertTrue(Rules.verifies("456", 456))
  }

  @Test fun temporaryExpiryFollowsLocalWakeAcrossZoneChanges() {
    val day = LocalDate.parse("2026-11-02")
    val madrid = Rules.overrideExpiry(routine(), day, zone)
    val utc = Rules.overrideExpiry(routine(), day, ZoneId.of("UTC"))
    assertEquals(3600000L, utc - madrid)
  }

  @Test fun colorExceptionDoesNotReleaseAnotherOwnersGrayscale() {
    fun kind(kind: RoutineKind, colorApps: List<String>) =
      routine().copy(kind = kind, colorApps = colorApps)
    assertFalse(kind(RoutineKind.GRAYSCALE, listOf("maps")).requestsGrayscale("maps"))
    assertTrue(kind(RoutineKind.FOCUS, emptyList()).requestsGrayscale("maps"))
    assertFalse(kind(RoutineKind.ACCESS, emptyList()).requestsGrayscale("maps"))
  }

  @Test fun overnightBelongsToStartingWeekday() {
    assertTrue(Rules.active(routine(), time("2026-11-03T06:59:00+01:00[Europe/Madrid]"), zone))
    assertFalse(Rules.active(routine(), time("2026-11-03T07:00:00+01:00[Europe/Madrid]"), zone))
    assertFalse(Rules.active(routine(), time("2026-11-04T02:00:00+01:00[Europe/Madrid]"), zone))
  }

  @Test fun todayIncludesFollowingMorning() {
    assertTrue(
      Rules.active(
        routine(),
        time("2026-11-04T06:59:00+01:00[Europe/Madrid]"),
        zone,
        LocalDate.parse("2026-11-03")
      )
    )
    assertFalse(
      Rules.active(
        routine(),
        time("2026-11-04T07:00:00+01:00[Europe/Madrid]"),
        zone,
        LocalDate.parse("2026-11-03")
      )
    )
  }

  @Test fun bookingUsesElapsedHoursAcrossDst() {
    val start = LocalDate.parse("2026-03-30").atStartOfDay(zone).toInstant().toEpochMilli()
    assertTrue(Rules.canBook("2026-03-30", start - 72 * 3600000, zone))
    assertFalse(Rules.canBook("2026-03-30", start - 72 * 3600000 + 1, zone))
    assertFalse(
      Rules.canBook(
        "2026-03-30",
        LocalDate.parse("2026-03-27").atStartOfDay(zone).toInstant().toEpochMilli(),
        zone
      )
    )
  }

  @Test fun earliestDayOffIsTheFirstMidnightAtLeast72HoursAway() {
    val start = LocalDate.parse("2026-03-30").atStartOfDay(zone).toInstant().toEpochMilli()
    val exact = start - 72 * 3600000
    assertEquals(LocalDate.parse("2026-03-30"), Rules.earliestDayOff(exact, zone))
    assertEquals(LocalDate.parse("2026-03-31"), Rules.earliestDayOff(exact + 1, zone))
    assertEquals(
      LocalDate.parse("2026-03-31"),
      Rules.earliestDayOff(time("2026-03-27T12:00:00+01:00[Europe/Madrid]"), zone)
    )
  }

  @Test fun earliestDayOffAgreesWithCanBookAcrossClockChanges() {
    // Havana's clocks skip midnight on 2026-03-08, so that day starts at 01:00.
    for (id in listOf("Europe/Madrid", "America/Havana", "UTC")) {
      val tz = ZoneId.of(id)
      val first = LocalDate.parse("2026-01-01").atStartOfDay(tz).toInstant().toEpochMilli()
      for (halfHour in 0L until 365 * 48) {
        val now = first + halfHour * 1800000 + 1
        val earliest = Rules.earliestDayOff(now, tz)
        assertTrue(Rules.canBook(earliest.toString(), now, tz))
        assertFalse(Rules.canBook(earliest.minusDays(1).toString(), now, tz))
      }
    }
    val havana = ZoneId.of("America/Havana")
    val skipped = LocalDate.parse("2026-03-08").atStartOfDay(havana).toInstant().toEpochMilli()
    assertEquals(
      LocalDate.parse("2026-03-08"),
      Rules.earliestDayOff(skipped - 72 * 3600000, havana)
    )
  }

  @Test fun arithmeticAlwaysSatisfiesBounds() {
    repeat(10000) {
      val (expression, answer) = Rules.challenge()
      val parts = expression.split(" ")
      assertEquals(5, parts.size)
      for (i in listOf(0, 2, 4)) assertTrue(parts[i].toInt() in 50..999)
      assertTrue(answer in -999..999)
      assertEquals(
        parts[0].toInt() + (if (parts[1] == "+") 1 else -1) * parts[2].toInt() +
          (if (parts[3] == "+") 1 else -1) * parts[4].toInt(),
        answer
      )
    }
  }

  private fun config(routines: String) =
    """{"version":1,"focusMinutes":50,"routines":[$routines]}"""

  private fun stored(id: String, windows: String = "[]", extra: String = "") =
    """{"id":"$id","title":"$id","enabled":false,"windows":$windows,"apps":["com.example.app"],"colorApps":[],"earlyStart":"20:00"$extra}"""

  private val validRoutines = listOf(
    stored("access"),
    stored("focus"),
    stored("grayscale"),
    stored("bedtime", """[{"start":"22:30","end":"07:00","days":[1,3]}]""")
  )

  @Test fun savedEnglishTitlesAreTranslatedWithoutChangingSettings() {
    val titles = mapOf(
      "access" to "Acceso a aplicaciones",
      "focus" to "Concentración",
      "bedtime" to "Descanso",
      "grayscale" to "Escala de grises"
    )
    validRoutines.forEach { raw ->
      val saved = org.json.JSONObject(raw).put("title", "Old English title")
      val parsed = Routine.parse(saved).toJson()
      assertEquals(titles[saved.getString("id")], parsed.getString("title"))
      saved.keys().forEach { key ->
        if (key != "title") assertEquals(saved.get(key).toString(), parsed.get(key).toString())
      }
    }
  }

  @Test fun storedConfigSurvivesRoundTrip() {
    val parsed = Config.parse(config(validRoutines.joinToString(",")))
    assertEquals(parsed, Config.parse(parsed.toJson().toString()))
    val bedtime = parsed.routines.first { it.kind == RoutineKind.BEDTIME }
    assertEquals(listOf(1, 3), bedtime.windows[0].days)
    assertEquals(
      "22:30",
      bedtime.toJson().getJSONArray("windows").getJSONObject(0).getString("start")
    )
  }

  @Test fun configSavedBeforeNightLightGainsItDisabledAndKeepsItsRoutines() {
    val parsed = Config.parse(config(validRoutines.joinToString(",")))
    val added = parsed.routines.single { it.kind == RoutineKind.NIGHT_LIGHT }
    assertEquals(false, added.enabled)
    assertEquals(90, added.rampMinutes)
    assertEquals(50, added.finalIntensity)
    assertEquals(5, parsed.routines.size)
  }

  @Test(expected = IllegalArgumentException::class)
  fun configNeedsEveryKind() {
    Config.parse(config(validRoutines.drop(1).joinToString(",")))
  }

  @Test(expected = IllegalArgumentException::class)
  fun bedtimeNeedsOneWindow() {
    Config.parse(config((validRoutines.dropLast(1) + stored("bedtime")).joinToString(",")))
  }

  @Test(expected = IllegalArgumentException::class)
  fun earlyStartStartsAtTwenty() {
    Routine.parse(org.json.JSONObject(stored("focus").replace("20:00", "19:59")))
  }
}

package dev.danields.aall.stories

import dev.danields.aall.core.Permissions
import dev.danields.aall.core.RoutineKind
import dev.danields.aall.core.StatusNotice
import dev.danields.aall.harness.Phone
import dev.danields.aall.harness.bedtime
import dev.danields.aall.harness.grayscale
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BedtimeTest {
  @Test fun chargingStartsBedtimeEarlyAndUnpluggingKeepsItUntilWake() {
    val phone = Phone("2026-11-02T21:00", bedtime())
    assertFalse(phone.dnd)
    phone.plug(true)
    assertTrue(phone.dnd)
    assertTrue(phone.grayscale)
    assertEquals(listOf("bedtime"), phone.active())
    phone.advanceTo("21:30")
    phone.plug(false)
    assertTrue(phone.dnd)
    phone.advanceTo("06:59")
    assertTrue(phone.dnd)
    phone.advanceTo("07:00")
    assertFalse(phone.dnd)
    assertFalse(phone.grayscale)
    assertEquals(emptyList<String>(), phone.active())
    assertEquals(null, phone.notice)
  }

  @Test fun chargingBeforeTheEarlyStartWaitsForIt() {
    val phone = Phone("2026-11-02T19:59", bedtime())
    phone.plug(true)
    assertFalse(phone.dnd)
    phone.advanceTo("20:00")
    assertTrue(phone.dnd)
  }

  @Test fun chargingOnAnUnscheduledWeekdayOrWithBedtimeOffStartsNothing() {
    val tuesdayOnly = Phone("2026-11-02T21:00", bedtime(days = listOf(2)))
    tuesdayOnly.plug(true)
    tuesdayOnly.advanceTo("23:00")
    assertFalse(tuesdayOnly.dnd)
    val off = Phone("2026-11-02T21:00", bedtime(enabled = false))
    off.plug(true)
    off.advanceTo("23:00")
    assertFalse(off.dnd)
  }

  @Test fun dndIsWrittenOnlyWhenBedtimeStartsAndEnds() {
    val phone = Phone("2026-11-02T22:00", bedtime())
    phone.advanceTo("23:00")
    phone.open("a")
    phone.open("b")
    assertEquals(listOf(true), phone.device.dndWrites)
    phone.advanceTo("07:00")
    assertEquals(listOf(true, false), phone.device.dndWrites)
    val gray = Phone("2026-11-02T08:00", grayscale("09:00" to "18:00"))
    gray.advanceTo("12:00")
    assertTrue(gray.grayscale)
    gray.advanceTo("18:00")
    assertFalse(gray.grayscale)
    assertEquals(emptyList<Boolean>(), gray.device.dndWrites)
  }

  @Test fun missingPermissionsSkipEffectsAndReportErrors() {
    val phone =
      Phone("2026-11-02T23:00", bedtime(apps = listOf("clock"), colorApps = listOf("maps"))) {
        permissions = Permissions(false, false, false, false)
      }
    phone.open("game")
    assertEquals(emptyList<Boolean>(), phone.device.dndWrites)
    assertEquals(emptyMap<String, Int>(), phone.device.secure)
    assertFalse(phone.blocked)
    assertEquals(
      listOf(
        "El bloqueo de aplicaciones necesita el permiso de accesibilidad.",
        "Falta el permiso de No molestar.",
        "Las excepciones de color necesitan el permiso de accesibilidad.",
        "La escala de grises necesita el permiso mediante ADB.",
        "Falta el permiso de alarmas exactas; los cambios pueden retrasarse."
      ),
      phone.errors()
    )
  }

  @Test fun noticeSaysBedtimeEndsAtWakeUpNotAtMidnight() {
    val phone = Phone("2026-11-02T23:00", bedtime())
    assertEquals(StatusNotice("Descanso", "Hasta mañana las 07:00"), phone.notice)
  }

  @Test fun noticeDoesNotClaimProtectionWhenBedtimeCannotRun() {
    val missing =
      Phone("2026-11-02T23:00", bedtime()) { permissions = permissions.copy(dnd = false) }
    assertEquals(
      StatusNotice(
        "Descanso · requiere configuración",
        "Falta el permiso de No molestar.\nHasta mañana las 07:00"
      ),
      missing.notice
    )
    val failed = Phone("2026-11-02T23:00", bedtime()) { failDnd = true }
    assertEquals(
      StatusNotice(
        "Descanso · requiere configuración",
        "No se ha podido cambiar No molestar.\nHasta mañana las 07:00"
      ),
      failed.notice
    )
  }

  @Test fun onlyTonightRunsAWeeklyOffBedtimeUntilWakeThenWeeklyResumes() {
    val phone = Phone("2026-11-02T12:00", bedtime(enabled = false))
    phone.overrideToday(bedtime())
    assertEquals("ready", phone.status(RoutineKind.BEDTIME))
    phone.advanceTo("23:00")
    assertTrue(phone.dnd)
    assertEquals("active", phone.status(RoutineKind.BEDTIME))
    phone.advanceTo("06:59")
    assertTrue(phone.dnd)
    phone.advanceTo("07:00")
    assertFalse(phone.dnd)
    assertEquals(emptySet<String>(), phone.overrides())
    phone.advanceTo("23:00")
    assertFalse(phone.dnd)
  }

  @Test fun aDisabledOverrideFromAnOlderBuildNoLongerTurnsBedtimeOff() {
    val phone = Phone("2026-11-02T22:00", bedtime())
    phone.stored(
      "today.bedtime",
      JSONObject()
        .put("routine", bedtime(enabled = false).toJson())
        .put("date", "2026-11-02")
        .put("until", 0)
    )
    phone.advanceTo("23:00")
    assertTrue(phone.dnd)
    assertEquals(emptySet<String>(), phone.overrides())
  }
}

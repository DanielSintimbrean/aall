package dev.danields.aall.stories

import dev.danields.aall.core.RoutineKind
import dev.danields.aall.harness.Phone
import dev.danields.aall.harness.access
import dev.danields.aall.harness.at
import dev.danields.aall.harness.bedtime
import dev.danields.aall.harness.focus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RebootAndClockTest {
  @Test fun focusEndsOnTimeAcrossAReboot() {
    val phone = Phone("2026-11-02T10:00", focus())
    phone.startFocus()
    phone.advanceTo("10:16:40")
    phone.reboot()
    assertEquals(at("2026-11-02T10:50"), phone.snapshot().getLong("focusUntil"))
    phone.advanceTo("10:49:59")
    assertTrue(phone.dnd)
    phone.advanceTo("10:50")
    assertFalse(phone.dnd)
  }

  @Test fun aTurnOffNoticeSurvivesARebootAndEndsByTheWallClock() {
    val phone = Phone("2026-11-02T12:00", bedtime())
    phone.requestTurnOff(RoutineKind.BEDTIME)
    phone.advanceTo("2026-11-05T11:00")
    phone.reboot()
    assertTrue(phone.weekly(RoutineKind.BEDTIME).getBoolean("enabled"))
    assertEquals(at("2026-11-05T12:00"), phone.turningOff(RoutineKind.BEDTIME))
    phone.advanceTo("12:00")
    assertFalse(phone.weekly(RoutineKind.BEDTIME).getBoolean("enabled"))
  }

  @Test fun movingTheClockDoesNotShortenATurnOffNotice() {
    val phone = Phone("2026-11-02T12:00", bedtime())
    phone.requestTurnOff(RoutineKind.BEDTIME)
    phone.advanceTo("13:00")
    phone.setClock("2026-11-09T23:00")
    assertTrue(phone.weekly(RoutineKind.BEDTIME).getBoolean("enabled"))
    assertTrue(phone.dnd)
  }

  @Test fun theAlarmIsTheEarliestUpcomingBoundary() {
    val noon = "2026-11-02T12:00"
    assertEquals(at("2026-11-03T00:00"), Phone(noon).alarm)
    assertEquals(at("2026-11-02T12:30"), Phone(noon, access("12:30" to "13:15")).alarm)
    assertEquals(at("2026-11-02T20:00"), Phone(noon, bedtime()).alarm)
    val focusing = Phone(noon, access("12:30" to "13:15"), focus())
    focusing.setFocusMinutes(1)
    focusing.startFocus()
    assertEquals(at("2026-11-02T12:01"), focusing.alarm)
  }
}

package dev.danields.aall.stories

import dev.danields.aall.core.StatusNotice
import dev.danields.aall.harness.Phone
import dev.danields.aall.harness.at
import dev.danields.aall.harness.bedtime
import dev.danields.aall.harness.focus
import dev.danields.aall.harness.nightLight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PauseAndDayOffTest {
  private val dayOff =
    StatusNotice("Día libre", "Todas las restricciones están suspendidas hasta medianoche.")

  @Test fun aPauseLiftsBedtimeForFifteenMinutes() {
    val phone = Phone("2026-11-02T23:00", bedtime())
    assertTrue(phone.dnd)
    phone.solvePause()
    assertFalse(phone.dnd)
    assertFalse(phone.grayscale)
    assertEquals("En pausa", phone.notice?.title)
    assertEquals(at("2026-11-02T23:15"), phone.notice?.countdownTo)
    phone.advanceTo("23:14:59")
    assertFalse(phone.dnd)
    phone.advanceTo("23:15")
    assertTrue(phone.dnd)
  }

  @Test fun theAlarmKeepsABoundaryMillisecondsAway() {
    val phone = Phone("2026-11-02T23:00", bedtime())
    phone.solvePause()
    phone.advanceTo("23:14:59.950")
    phone.open("app")
    assertEquals(at("2026-11-02T23:15"), phone.alarm)
  }

  @Test fun aDayOffEndsLastNightsBedtimeAtMidnightUntilTheNextMidnight() {
    val phone = Phone("2026-10-30T12:00", bedtime())
    phone.book("2026-11-03")
    phone.advanceTo("2026-11-02T23:00")
    assertTrue(phone.dnd)
    phone.advanceTo("2026-11-03T00:00")
    assertFalse(phone.dnd)
    assertFalse(phone.grayscale)
    assertTrue(phone.snapshot().getBoolean("dayOff"))
    assertEquals(emptyList<String>(), phone.active())
    assertEquals(dayOff, phone.notice)
    phone.advanceTo("23:00")
    assertFalse(phone.dnd)
    phone.advanceTo("2026-11-04T00:00")
    assertTrue(phone.dnd)
    assertEquals(listOf("bedtime"), phone.active())
  }

  @Test fun pauseAndDayOffLeaveNightLightOn() {
    val paused = Phone("2026-11-04T23:30", nightLight(), bedtime())
    paused.solvePause()
    assertEquals(listOf("nightLight"), paused.active())
    assertFalse(paused.grayscale)
    assertEquals(1, paused.secure("night_display_activated"))
    val off = Phone("2026-10-30T12:00", nightLight(), bedtime())
    off.book("2026-11-04")
    off.advanceTo("2026-11-04T23:30")
    assertEquals(listOf("nightLight"), off.active())
    assertFalse(off.dnd)
    assertEquals(1, off.secure("night_display_activated"))
  }

  @Test fun pauseOutranksFocusAndADayOffOutranksPause() {
    val phone = Phone("2026-10-30T12:00", focus())
    phone.startFocus()
    phone.solvePause()
    assertEquals("En pausa", phone.notice?.title)
    assertEquals(at("2026-10-30T12:15"), phone.notice?.countdownTo)
    phone.book("2026-11-04")
    phone.advanceTo("2026-11-04T12:00")
    phone.startFocus()
    phone.solvePause()
    assertEquals(dayOff, phone.notice)
  }
}

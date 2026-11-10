package dev.danields.aall.stories

import dev.danields.aall.core.RoutineKind
import dev.danields.aall.harness.Phone
import dev.danields.aall.harness.at
import dev.danields.aall.harness.bedtime
import dev.danields.aall.harness.focus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TurnOffNoticeTest {
  private val bedtimeKind = RoutineKind.BEDTIME

  private fun Phone.bedtimeOn() = weekly(bedtimeKind).getBoolean("enabled")

  @Test fun bedtimeKeepsRunningUntilTheNoticeEndsThenTurnsOff() {
    val phone = Phone("2026-11-02T12:00", bedtime())
    phone.requestTurnOff(bedtimeKind)
    assertEquals(at("2026-11-05T12:00"), phone.turningOff(bedtimeKind))
    phone.advanceTo("23:00")
    assertTrue(phone.dnd)
    phone.advanceTo("2026-11-05T11:59")
    assertTrue(phone.bedtimeOn())
    assertEquals(at("2026-11-05T12:00"), phone.alarm)
    phone.advanceTo("12:00")
    assertFalse(phone.bedtimeOn())
    assertNull(phone.turningOff(bedtimeKind))
    phone.advanceTo("23:00")
    assertFalse(phone.dnd)
  }

  @Test fun repeatedRequestsAndWeeklySavesKeepTheFirstDeadline() {
    val phone = Phone("2026-11-02T12:00", bedtime())
    phone.requestTurnOff(bedtimeKind)
    phone.advanceTo("2026-11-03T12:00")
    phone.requestTurnOff(bedtimeKind)
    assertEquals(at("2026-11-05T12:00"), phone.turningOff(bedtimeKind))
    phone.saveWeekly(bedtime())
    assertEquals(at("2026-11-05T12:00"), phone.turningOff(bedtimeKind))
  }

  @Test fun cancellingKeepsBedtimeOn() {
    val phone = Phone("2026-11-02T12:00", bedtime())
    phone.requestTurnOff(bedtimeKind)
    phone.cancelTurnOff(bedtimeKind)
    phone.advanceTo("2026-11-06T23:00")
    assertTrue(phone.bedtimeOn())
    assertTrue(phone.dnd)
  }

  @Test fun onlyAnEnabledWeeklyRoutineCanStartANotice() {
    val phone = Phone("2026-11-02T12:00", bedtime(enabled = false))
    assertThrows(IllegalArgumentException::class.java) { phone.requestTurnOff(bedtimeKind) }
  }

  @Test fun savesCannotTurnAWeeklyRoutineOffAtOnce() {
    val on = Phone("2026-11-02T12:00", bedtime())
    assertThrows(IllegalArgumentException::class.java) { on.saveWeekly(bedtime(enabled = false)) }
    assertThrows(IllegalArgumentException::class.java) {
      on.overrideToday(bedtime(enabled = false))
    }
    assertTrue(on.bedtimeOn())
    val off = Phone("2026-11-02T12:00", bedtime(enabled = false))
    off.saveWeekly(bedtime(enabled = false))
    off.saveWeekly(bedtime())
    assertTrue(off.bedtimeOn())
  }

  @Test fun finishingEndsTheOverrideAndWhatTheRoutineHadRunning() {
    val phone = Phone("2026-11-02T21:00", bedtime(), focus())
    phone.requestTurnOff(bedtimeKind)
    phone.requestTurnOff(RoutineKind.FOCUS)
    phone.advanceTo("2026-11-05T20:30")
    phone.overrideToday(bedtime("22:00" to "07:00"))
    phone.plug(true)
    phone.startFocus()
    assertEquals(listOf("focus", "bedtime"), phone.active())
    phone.advanceTo("21:00")
    assertEquals(emptyList<String>(), phone.active())
    assertEquals(emptySet<String>(), phone.overrides())
    assertFalse(phone.dnd)
    phone.plug(false)
    phone.saveWeekly(bedtime())
    phone.saveWeekly(focus())
    assertEquals(emptyList<String>(), phone.active())
    assertEquals(phone.now, phone.snapshot().getLong("focusUntil"))
  }
}

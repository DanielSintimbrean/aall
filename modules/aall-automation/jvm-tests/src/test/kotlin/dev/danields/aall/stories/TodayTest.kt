package dev.danields.aall.stories

import dev.danields.aall.core.Permissions
import dev.danields.aall.core.Routine
import dev.danields.aall.core.RoutineKind
import dev.danields.aall.core.StatusNotice
import dev.danields.aall.harness.Phone
import dev.danields.aall.harness.access
import dev.danields.aall.harness.bedtime
import dev.danields.aall.harness.grayscale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TodayTest {
  private val all = Permissions(accessibility = true, dnd = true, alarms = true, grayscale = true)
  private val none = Permissions(false, false, false, false)

  private fun status(routine: Routine, permissions: Permissions = all) =
    Phone("2026-11-02T12:00", routine) { this.permissions = permissions }.status(routine.kind)

  @Test fun statusReportsWhatEachKindRequires() {
    val social = listOf("social")
    assertEquals("disabled", status(access(apps = social, enabled = false), none))
    assertEquals("active", status(access(apps = social)))
    assertEquals("ready", status(grayscale()))
    assertEquals("needsSetup", status(grayscale(), all.copy(alarms = false)))
    assertEquals("needsSetup", status(grayscale(), all.copy(grayscale = false)))
    assertEquals("ready", status(grayscale(), all.copy(accessibility = false)))
    assertEquals(
      "needsSetup",
      status(grayscale(colorApps = listOf("maps")), all.copy(accessibility = false))
    )
    assertEquals(
      "ready",
      status(access("09:00" to "18:00", apps = social), all.copy(dnd = false, grayscale = false))
    )
    assertEquals("needsSetup", status(bedtime(), all.copy(dnd = false)))
  }

  @Test fun aWeeklySaveEndsThatRoutinesOverrideOnly() {
    val phone = Phone("2026-11-02T12:00", access(), grayscale())
    phone.overrideToday(access())
    phone.overrideToday(grayscale())
    phone.saveWeekly(access(apps = listOf("social")))
    assertEquals(setOf("grayscale"), phone.overrides())
    assertEquals(
      "social",
      phone.weekly(RoutineKind.ACCESS).getJSONArray("apps").getString(0)
    )
  }

  @Test fun noticeNamesRunningRoutinesAndTheNextChangeAndGoesWhenNothingRuns() {
    val phone = Phone("2026-11-02T12:00", grayscale("09:00" to "18:00"))
    assertEquals(StatusNotice("Escala de grises", "Próximo cambio a las 18:00"), phone.notice)
    phone.advanceTo("18:00")
    assertNull(phone.notice)
  }

  @Test fun grayscaleTestRunsTenSecondsWithoutANoticeButNotDuringAPause() {
    val phone = Phone("2026-11-02T12:00")
    phone.testGrayscale(true)
    assertTrue(phone.grayscale)
    assertNull(phone.notice)
    phone.advanceTo("12:00:10")
    assertFalse(phone.grayscale)
    phone.solvePause()
    phone.testGrayscale(true)
    assertFalse(phone.grayscale)
  }
}

package dev.danields.aall.stories

import dev.danields.aall.core.RoutineKind
import dev.danields.aall.harness.Phone
import dev.danields.aall.harness.nightLight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NightLightStoriesTest {
  private val weakest = 4082
  private val half = 3339

  private fun Phone.filter() = Triple(
    secure("night_display_activated"),
    secure("night_display_color_temperature"),
    secure("night_display_auto_mode")
  )

  @Test fun rampsFromTheWindowStartAndRestoresAtTheEnd() {
    val phone = Phone("2026-11-04T20:00", nightLight())
    assertNull(phone.secure("night_display_activated"))
    phone.advanceTo("21:00")
    assertEquals(Triple(1, weakest, 0), phone.filter())
    phone.advanceTo("21:45")
    val midway = phone.secure("night_display_color_temperature")!!
    assertTrue("$midway", midway in (half + 1) until weakest)
    phone.advanceTo("22:30")
    assertEquals(Triple(1, half, 0), phone.filter())
    phone.advanceTo("06:59")
    assertEquals(Triple(1, half, 0), phone.filter())
    phone.advanceTo("07:00")
    assertEquals(Triple(0, -1, -1), phone.filter())
  }

  @Test fun aPhoneThatStartsMidWindowAnchorsTheRampToTheWindowStart() {
    val phone = Phone("2026-11-04T23:30", nightLight())
    assertEquals(Triple(1, half, 0), phone.filter())
  }

  @Test fun turnsOffWithoutNoticeAndOnlyTodayCanDisableIt() {
    val weekly = Phone("2026-11-04T12:00", nightLight())
    weekly.saveWeekly(nightLight(enabled = false))
    assertEquals(false, weekly.weekly(RoutineKind.NIGHT_LIGHT).getBoolean("enabled"))
    val tonight = Phone("2026-11-04T23:30", nightLight())
    tonight.overrideToday(nightLight(enabled = false))
    assertEquals(Triple(0, -1, -1), tonight.filter())
    assertEquals(setOf("nightLight"), tonight.overrides())
    tonight.advanceTo("21:00")
    assertEquals(Triple(1, weakest, 0), tonight.filter())
  }

  @Test fun needsTheSecureSettingsGrant() {
    val phone = Phone("2026-11-04T23:30", nightLight()) {
      permissions = permissions.copy(grayscale = false)
    }
    assertEquals("needsSetup", phone.status(RoutineKind.NIGHT_LIGHT))
    assertTrue(phone.errors().any { it.contains("luz nocturna") })
    assertNull(phone.secure("night_display_activated"))
  }
}

package dev.danields.aall.stories

import dev.danields.aall.harness.Phone
import dev.danields.aall.harness.grayscale
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GrayscaleStoriesTest {
  private val enabled = "accessibility_display_daltonizer_enabled"
  private val mode = "accessibility_display_daltonizer"

  @Test fun oldWeeklyAndTodayExceptionsStayMergedAfterTodayExpires() {
    val phone = Phone("2026-11-02T12:00", grayscale("09:00" to "18:00"))
    val config = phone.snapshot().getJSONObject("config").apply { remove("colorApps") }
    val routine = config.getJSONArray("routines").let { rs ->
      (0 until rs.length()).map(rs::getJSONObject).single { it.getString("id") == "grayscale" }
    }
    routine.put("colorApps", JSONArray(listOf("maps")))
    phone.stored("config", config)
    phone.overrideToday(grayscale("09:00" to "18:00"))
    val override = JSONObject(phone.store.getString("today.grayscale", null)!!)
    override.getJSONObject("routine").put("colorApps", JSONArray(listOf("photos")))
    phone.stored("config", config)
    phone.stored("today.grayscale", override)
    phone.engine.reconcile()
    assertEquals(
      listOf("maps", "photos"),
      JSONObject(phone.store.getString("config", null)!!).getJSONArray("colorApps").toList()
    )
    phone.advanceTo("2026-11-03T12:00")
    for (app in listOf("maps", "photos")) {
      phone.open(app)
      assertFalse(phone.grayscale)
    }
    assertEquals(emptySet<String>(), phone.overrides())
  }

  @Test fun failedWritesAreRetriedAndTheUsersColorCorrectionComesBack() {
    val phone = Phone("2026-11-02T08:00", grayscale("09:00" to "18:00")) {
      secure[enabled] = 1
      secure[mode] = 12
      failWrites += setOf(enabled, mode)
    }
    phone.advanceTo("09:00")
    assertFalse(phone.grayscale)
    phone.device.failWrites.clear()
    phone.open("app")
    assertTrue(phone.grayscale)
    phone.device.failWrites += setOf(enabled, mode)
    phone.advanceTo("18:00")
    assertTrue(phone.grayscale)
    phone.device.failWrites.clear()
    phone.open("app")
    assertEquals(1 to 12, phone.secure(enabled) to phone.secure(mode))
  }
}

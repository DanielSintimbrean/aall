package dev.danields.aall.stories

import dev.danields.aall.harness.Phone
import dev.danields.aall.harness.grayscale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GrayscaleStoriesTest {
  private val enabled = "accessibility_display_daltonizer_enabled"
  private val mode = "accessibility_display_daltonizer"

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

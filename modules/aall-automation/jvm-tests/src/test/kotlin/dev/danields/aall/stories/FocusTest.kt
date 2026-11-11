package dev.danields.aall.stories

import dev.danields.aall.harness.Phone
import dev.danields.aall.harness.bedtime
import dev.danields.aall.harness.focus
import dev.danields.aall.harness.grayscale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusTest {
  @Test fun focusComesFirstInTheNoticeWithItsCountdown() {
    val phone = Phone("2026-11-02T12:00", grayscale("09:00" to "18:00"), focus())
    phone.startFocus()
    assertTrue(phone.dnd)
    assertEquals("2 rutinas activas", phone.notice?.title)
    assertEquals(
      "Concentración · hasta las 12:50\nEscala de grises · hasta las 18:00",
      phone.notice?.text
    )
    assertEquals(phone.now + 50 * 60000, phone.notice?.countdownTo)
  }

  @Test fun endingFocusKeepsDndOnForBedtime() {
    val phone = Phone("2026-11-02T22:00", bedtime(), focus())
    phone.startFocus()
    assertTrue(phone.dnd)
    phone.advanceTo("23:00")
    assertEquals(listOf("bedtime"), phone.active())
    assertTrue(phone.dnd)
    assertEquals(listOf(true), phone.device.dndWrites)
  }
}

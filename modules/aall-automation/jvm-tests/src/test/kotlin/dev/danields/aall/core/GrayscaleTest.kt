package dev.danields.aall.core

import dev.danields.aall.core.Grayscale.GRAY
import org.junit.Assert.assertEquals
import org.junit.Test

class GrayscaleTest {
  private val user = ColorCorrection(1, 12)
  private val off = ColorCorrection(0, -1)

  @Test fun leavesAUsersChangeAloneWhileOnAndAfterwards() {
    assertEquals(GrayscaleStep(off, null), Grayscale.advance(off, user, on = true))
    assertEquals(GrayscaleStep(null, null), Grayscale.advance(off, user, on = false))
  }

  @Test fun grayThatWasAlreadyOnStaysOn() {
    val step = Grayscale.advance(null, GRAY, on = true)
    assertEquals(GrayscaleStep(GRAY, GRAY), step)
    assertEquals(GrayscaleStep(null, GRAY), Grayscale.advance(step.owned, GRAY, on = false))
  }
}

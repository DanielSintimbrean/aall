package dev.danields.aall.automation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NightLightTest {
  private val range = 686..4082
  private val ramp = NightLightRamp(startedAt = 0, minutes = 90, intensity = 50)
  private val half = NightLight.temperature(50, range)
  private val off = NightLightSettings(0, 2327, 1)

  private fun at(minutes: Int) = minutes * 60000L

  private fun started(read: NightLightSettings = off) =
    NightLight.advance(null, read, ramp, at(0), range)

  @Test fun intensityUsesAndroidsSliderScale() {
    assertEquals(4082, NightLight.temperature(0, range))
    assertEquals(686, NightLight.temperature(100, range))
    assertEquals(2384, half)
  }

  @Test fun startsAtWeakestAndTakesTheSystemScheduleOff() {
    val step = started()
    assertEquals(NightLightSettings(1, 4082, 0), step.settings)
    assertEquals(off, step.session!!.before)
  }

  @Test fun rampDependsOnlyOnElapsedWallTimeSoMissedStepsCatchUp() {
    val mid = NightLight.advance(started().session, started().settings, ramp, at(45), range)
    assertEquals((4082 + half) / 2, mid.settings.temperature)
    val late = NightLight.advance(mid.session, mid.settings, ramp, at(300), range)
    assertEquals(half, late.settings.temperature)
  }

  @Test fun rampNeverStepsBackward() {
    var step = started()
    var last = step.settings.temperature
    for (minute in 1..90) {
      step = NightLight.advance(step.session, step.settings, ramp, at(minute), range)
      assertTrue(step.settings.temperature <= last)
      assertTrue(last - step.settings.temperature <= 20)
      last = step.settings.temperature
    }
  }

  @Test fun filterAlreadyOnIsNeverWeakened() {
    val strong = NightLightSettings(1, 2000, 1)
    val step = started(strong)
    assertEquals(2000, step.settings.temperature)
    val end = NightLight.advance(step.session, step.settings, ramp, at(90), range)
    assertEquals(2000, end.settings.temperature)
    val warm = NightLightSettings(1, 3000, 1)
    val fromWarm = started(warm)
    assertEquals(3000, fromWarm.settings.temperature)
    assertEquals(
      half,
      NightLight.advance(
        fromWarm.session,
        fromWarm.settings,
        ramp,
        at(90),
        range
      ).settings.temperature
    )
  }

  @Test fun unsetTemperatureStartsAtWeakest() {
    assertEquals(4082, started(NightLightSettings(1, -1, -1)).settings.temperature)
  }

  @Test fun userChangeStopsTheRampUntilTheWindowEnds() {
    val step = started()
    val moved = step.settings.copy(temperature = 3000)
    val held = NightLight.advance(step.session, moved, ramp, at(30), range)
    assertEquals(moved, held.settings)
    assertTrue(held.session!!.yielded)
    val turnedBack = NightLight.advance(held.session, step.settings, ramp, at(31), range)
    assertEquals(step.settings, turnedBack.settings)
    assertTrue(turnedBack.session!!.yielded)
  }

  @Test fun releaseRestoresEverythingAaallStillOwns() {
    val step = started()
    val end = NightLight.advance(step.session, step.settings, null, at(600), range)
    assertEquals(off, end.settings)
    assertNull(end.session)
  }

  @Test fun releaseKeepsWhatTheUserChanged() {
    val step = started()
    val turnedOff = step.settings.copy(activated = 0)
    val held = NightLight.advance(step.session, turnedOff, ramp, at(30), range)
    val end = NightLight.advance(held.session, turnedOff, null, at(600), range)
    assertEquals(NightLightSettings(0, 2327, 1), end.settings)
    val own = NightLightSettings(1, 3000, 2)
    assertEquals(
      NightLightSettings(0, 3000, 2),
      NightLight.advance(step.session, own, null, at(600), range).settings
    )
  }

  @Test fun releaseWithoutSessionTouchesNothing() {
    val step = NightLight.advance(null, off, null, at(0), range)
    assertEquals(off, step.settings)
    assertNull(step.session)
  }

  @Test fun stepsFallOnMinuteBoundariesAndStopAtTheEnd() {
    assertEquals(at(1), NightLight.nextStep(ramp, 10))
    assertEquals(at(90), NightLight.nextStep(ramp, at(90) - 1))
    assertNull(NightLight.nextStep(ramp, at(90)))
    assertFalse(NightLight.nextStep(ramp, at(5))!! <= at(5))
  }
}

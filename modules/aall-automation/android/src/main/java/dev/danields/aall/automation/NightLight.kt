package dev.danields.aall.automation

import kotlin.math.roundToInt

/** Android's Night Light settings. -1 means a setting has no stored value. */
data class NightLightSettings(val activated: Int, val temperature: Int, val autoMode: Int)

/** One running occurrence of the routine: when its window began and where the ramp ends. */
data class NightLightRamp(val startedAt: Long, val minutes: Int, val intensity: Int) {
  val endsAt get() = startedAt + minutes * 60000L
}

/** What aall took over from Android, kept so the window's end can give it back. */
data class NightLightSession(
  val startedAt: Long,
  val before: NightLightSettings,
  val startTemperature: Int,
  val wrote: NightLightSettings,
  val yielded: Boolean = false
)

data class NightLightStep(val session: NightLightSession?, val settings: NightLightSettings)

object NightLight {
  const val STEP = 60000L

  /** Same scale as Android's slider: 0 is the weakest temperature it offers, 100 the strongest. */
  fun temperature(intensity: Int, range: IntRange) =
    range.last - ((range.last - range.first) * intensity / 100.0).roundToInt()

  fun nextStep(ramp: NightLightRamp, now: Long): Long? {
    if (now >= ramp.endsAt) return null
    val next = ramp.startedAt + ((now - ramp.startedAt).coerceAtLeast(0) / STEP + 1) * STEP
    return minOf(next, ramp.endsAt)
  }

  /**
   * Decides the settings for this moment. It leaves the filter alone once someone else has
   * changed it, and at the end it restores only the fields that still hold what aall wrote.
   */
  fun advance(
    session: NightLightSession?,
    read: NightLightSettings,
    ramp: NightLightRamp?,
    now: Long,
    range: IntRange
  ): NightLightStep {
    if (ramp == null) return NightLightStep(null, session?.let { release(it, read) } ?: read)
    // A session from another occurrence ended unseen, for example with the phone switched off.
    if (session != null && session.startedAt != ramp.startedAt) {
      return advance(null, release(session, read), ramp, now, range)
    }
    if (session != null && (session.yielded || read != session.wrote)) {
      return NightLightStep(session.copy(yielded = true), read)
    }
    val start = session?.startTemperature ?: startTemperature(read, range)
    val target = minOf(start, temperature(ramp.intensity, range))
    // Whole steps only, because every change can flash and app switches also reach this.
    val progress = ((now - ramp.startedAt) / STEP * STEP).toDouble()
      .div(ramp.endsAt - ramp.startedAt)
      .coerceIn(0.0, 1.0)
    val wanted = NightLightSettings(1, start + ((target - start) * progress).roundToInt(), 0)
    return NightLightStep(
      (session ?: NightLightSession(ramp.startedAt, read, start, wanted)).copy(wrote = wanted),
      wanted
    )
  }

  /** A filter that is already on sets the starting point, so the ramp never makes it weaker. */
  private fun startTemperature(read: NightLightSettings, range: IntRange) =
    if (read.activated == 1 && read.temperature > 0) {
      read.temperature.coerceIn(range)
    } else {
      range.last
    }

  private fun release(session: NightLightSession, read: NightLightSettings) = NightLightSettings(
    activated = if (read.activated == session.wrote.activated) {
      session.before.activated
    } else {
      read.activated
    },
    temperature = if (read.temperature == session.wrote.temperature) {
      session.before.temperature
    } else {
      read.temperature
    },
    autoMode = if (read.autoMode == session.wrote.autoMode) {
      session.before.autoMode
    } else {
      read.autoMode
    }
  )
}
